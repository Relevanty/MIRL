package com.personal.sleepalarm.domain.externalcontext

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.round

/**
 * City-level location chosen explicitly by the user. MIRL never obtains a GPS fix for this
 * feature. Coordinates are rounded before persistence and before any network request.
 */
data class CoarseLocation(
    val cityLabel: String = "",
    val latitude: Double,
    val longitude: Double,
    val zoneId: String = ZoneId.systemDefault().id
) {
    fun normalizedOrNull(): CoarseLocation? {
        if (!latitude.isFinite() || latitude !in -90.0..90.0) return null
        if (!longitude.isFinite() || longitude !in -180.0..180.0) return null
        val safeZone = runCatching { ZoneId.of(zoneId) }.getOrNull() ?: return null
        return copy(
            cityLabel = cityLabel.trim().replace(WHITESPACE, " ").take(MAX_CITY_LENGTH),
            latitude = latitude.roundTo(COORDINATE_DECIMALS),
            longitude = longitude.roundTo(COORDINATE_DECIMALS),
            zoneId = safeZone.id
        )
    }

    fun sameWeatherLocation(other: CoarseLocation): Boolean {
        val left = normalizedOrNull() ?: return false
        val right = other.normalizedOrNull() ?: return false
        return left.latitude == right.latitude && left.longitude == right.longitude
    }

    private fun Double.roundTo(decimals: Int): Double {
        val scale = POWERS_OF_TEN[decimals]
        return round(this * scale) / scale
    }

    private companion object {
        const val MAX_CITY_LENGTH = 80
        const val COORDINATE_DECIMALS = 2
        val POWERS_OF_TEN = doubleArrayOf(1.0, 10.0, 100.0, 1_000.0)
        val WHITESPACE = Regex("\\s+")
    }
}

/** External context is strictly opt-in. Weather has its own second switch. */
data class ExternalContextSettings(
    val enabled: Boolean = false,
    val weatherEnabled: Boolean = false,
    val location: CoarseLocation? = null
) {
    fun normalized(): ExternalContextSettings = copy(location = location?.normalizedOrNull())
}

enum class WeatherContextOrigin {
    NETWORK,
    FRESH_CACHE,
    STALE_CACHE
}

/** A small, non-medical weather snapshot from Open-Meteo. */
data class WeatherContext(
    val location: CoarseLocation,
    val fetchedAt: Instant,
    val observedAt: Instant?,
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double?,
    val relativeHumidityPercent: Int?,
    val precipitationMillimeters: Double?,
    val weatherCode: Int?,
    val windSpeedKilometersPerHour: Double?,
    val isDay: Boolean?,
    val sourceTimeZone: String,
    val origin: WeatherContextOrigin = WeatherContextOrigin.NETWORK
)

data class CachedWeatherContext(
    val weather: WeatherContext
)

enum class PolarDaylightState {
    NORMAL,
    POLAR_DAY,
    POLAR_NIGHT
}

/** Sunrise/sunset are local calculations; no network call is needed. */
data class DaylightContext(
    val date: LocalDate,
    val zoneId: String,
    val sunrise: Instant?,
    val sunset: Instant?,
    val daylightMinutes: Int,
    val state: PolarDaylightState
)

enum class WeatherUnavailableReason {
    NOT_ENABLED,
    NETWORK_OR_RESPONSE_ERROR,
    CACHE_EXPIRED
}

sealed interface WeatherContextState {
    data object Disabled : WeatherContextState

    data class Available(val value: WeatherContext) : WeatherContextState

    data class Unavailable(val reason: WeatherUnavailableReason) : WeatherContextState
}

data class ExternalContextSnapshot(
    val generatedAt: Instant,
    val location: CoarseLocation,
    val daylight: DaylightContext,
    val weather: WeatherContextState
)

sealed interface ExternalContextResult {
    data object Disabled : ExternalContextResult
    data object NotConfigured : ExternalContextResult
    data class Available(val snapshot: ExternalContextSnapshot) : ExternalContextResult
}

fun interface ExternalContextProvider {
    suspend fun getContext(): ExternalContextResult
}

fun interface ExternalContextSettingsStore {
    suspend fun getSettings(): ExternalContextSettings
}

interface WeatherContextCache {
    suspend fun readWeatherCache(): CachedWeatherContext?
    suspend fun writeWeatherCache(value: CachedWeatherContext)
}

fun interface WeatherClient {
    suspend fun fetch(location: CoarseLocation): WeatherContext
}

sealed interface WeatherCacheUse {
    data class Fresh(val weather: WeatherContext) : WeatherCacheUse
    data class StaleFallback(val weather: WeatherContext) : WeatherCacheUse
    data object Miss : WeatherCacheUse
}

/** Pure policy so expiry behavior is deterministic and unit-testable. */
object WeatherCachePolicy {
    val DEFAULT_FRESH_TTL: Duration = Duration.ofMinutes(30)
    val DEFAULT_MAX_STALE: Duration = Duration.ofHours(6)
    val DEFAULT_FUTURE_TOLERANCE: Duration = Duration.ofMinutes(5)

    fun evaluate(
        cached: CachedWeatherContext?,
        requestedLocation: CoarseLocation,
        now: Instant,
        freshTtl: Duration = DEFAULT_FRESH_TTL,
        maxStale: Duration = DEFAULT_MAX_STALE,
        futureTolerance: Duration = DEFAULT_FUTURE_TOLERANCE
    ): WeatherCacheUse {
        val weather = cached?.weather ?: return WeatherCacheUse.Miss
        if (!weather.location.sameWeatherLocation(requestedLocation)) return WeatherCacheUse.Miss
        if (freshTtl.isNegative || maxStale < freshTtl || futureTolerance.isNegative) {
            return WeatherCacheUse.Miss
        }

        val age = Duration.between(weather.fetchedAt, now)
        if (age.isNegative) {
            return if (age.abs() <= futureTolerance) {
                WeatherCacheUse.Fresh(weather)
            } else {
                WeatherCacheUse.Miss
            }
        }
        return when {
            age <= freshTtl -> WeatherCacheUse.Fresh(weather)
            age <= maxStale -> WeatherCacheUse.StaleFallback(weather)
            else -> WeatherCacheUse.Miss
        }
    }
}
