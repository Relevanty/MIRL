package com.personal.sleepalarm.data.externalcontext

import com.personal.sleepalarm.domain.externalcontext.CachedWeatherContext
import com.personal.sleepalarm.domain.externalcontext.DaylightCalculator
import com.personal.sleepalarm.domain.externalcontext.ExternalContextProvider
import com.personal.sleepalarm.domain.externalcontext.ExternalContextResult
import com.personal.sleepalarm.domain.externalcontext.ExternalContextSettingsStore
import com.personal.sleepalarm.domain.externalcontext.ExternalContextSnapshot
import com.personal.sleepalarm.domain.externalcontext.WeatherCachePolicy
import com.personal.sleepalarm.domain.externalcontext.WeatherCacheUse
import com.personal.sleepalarm.domain.externalcontext.WeatherClient
import com.personal.sleepalarm.domain.externalcontext.WeatherContext
import com.personal.sleepalarm.domain.externalcontext.WeatherContextCache
import com.personal.sleepalarm.domain.externalcontext.WeatherContextOrigin
import com.personal.sleepalarm.domain.externalcontext.WeatherContextState
import com.personal.sleepalarm.domain.externalcontext.WeatherUnavailableReason
import com.personal.sleepalarm.domain.externalcontext.localDateAt
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

/**
 * Opt-in coordinator. A fresh cache avoids the network; a bounded stale cache is returned only
 * when a refresh fails. Nothing in this class is connected to task ranking yet.
 */
class DefaultExternalContextProvider(
    private val settingsStore: ExternalContextSettingsStore,
    private val weatherCache: WeatherContextCache,
    private val weatherClient: WeatherClient,
    private val clock: Clock = Clock.systemUTC()
) : ExternalContextProvider {
    override suspend fun getContext(): ExternalContextResult {
        val settings = settingsStore.getSettings().normalized()
        if (!settings.enabled) return ExternalContextResult.Disabled
        val location = settings.location ?: return ExternalContextResult.NotConfigured
        val now = clock.instant()
        val daylight = DaylightCalculator.calculate(
            date = now.localDateAt(location),
            location = location,
            zoneId = ZoneId.of(location.zoneId)
        )
        val weatherState = if (!settings.weatherEnabled) {
            WeatherContextState.Disabled
        } else {
            resolveWeather(location, now)
        }
        return ExternalContextResult.Available(
            ExternalContextSnapshot(
                generatedAt = now,
                location = location,
                daylight = daylight,
                weather = weatherState
            )
        )
    }

    private suspend fun resolveWeather(
        location: com.personal.sleepalarm.domain.externalcontext.CoarseLocation,
        now: java.time.Instant
    ): WeatherContextState {
        val cached = readCacheSafely()
        return when (val use = WeatherCachePolicy.evaluate(cached, location, now)) {
            is WeatherCacheUse.Fresh -> WeatherContextState.Available(
                use.weather.forLocation(location, WeatherContextOrigin.FRESH_CACHE)
            )
            is WeatherCacheUse.StaleFallback -> {
                val refreshed = fetchSafely(location)
                if (refreshed != null) {
                    writeCacheSafely(refreshed)
                    WeatherContextState.Available(refreshed)
                } else {
                    WeatherContextState.Available(
                        use.weather.forLocation(location, WeatherContextOrigin.STALE_CACHE)
                    )
                }
            }
            WeatherCacheUse.Miss -> {
                val refreshed = fetchSafely(location)
                if (refreshed != null) {
                    writeCacheSafely(refreshed)
                    WeatherContextState.Available(refreshed)
                } else {
                    val sameExpiredLocation = cached?.weather?.location
                        ?.sameWeatherLocation(location) == true
                    WeatherContextState.Unavailable(
                        if (sameExpiredLocation) WeatherUnavailableReason.CACHE_EXPIRED
                        else WeatherUnavailableReason.NETWORK_OR_RESPONSE_ERROR
                    )
                }
            }
        }
    }

    private suspend fun readCacheSafely(): CachedWeatherContext? = try {
        weatherCache.readWeatherCache()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private suspend fun fetchSafely(
        location: com.personal.sleepalarm.domain.externalcontext.CoarseLocation
    ): WeatherContext? = try {
        weatherClient.fetch(location).copy(origin = WeatherContextOrigin.NETWORK)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private suspend fun writeCacheSafely(weather: WeatherContext) {
        try {
            weatherCache.writeWeatherCache(CachedWeatherContext(weather))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The fresh network value is still valid for this call; persistence can retry later.
        }
    }

    private fun WeatherContext.forLocation(
        requested: com.personal.sleepalarm.domain.externalcontext.CoarseLocation,
        cacheOrigin: WeatherContextOrigin
    ): WeatherContext = copy(
        location = requested,
        origin = cacheOrigin
    )
}
