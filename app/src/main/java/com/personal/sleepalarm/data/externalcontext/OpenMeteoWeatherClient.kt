package com.personal.sleepalarm.data.externalcontext

import com.personal.sleepalarm.domain.externalcontext.CoarseLocation
import com.personal.sleepalarm.domain.externalcontext.WeatherClient
import com.personal.sleepalarm.domain.externalcontext.WeatherContext
import com.personal.sleepalarm.domain.externalcontext.WeatherContextOrigin
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Minimal fixed-endpoint Open-Meteo client; it sends only rounded city-level coordinates. */
class OpenMeteoWeatherClient(
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : WeatherClient {
    override suspend fun fetch(location: CoarseLocation): WeatherContext =
        withContext(ioDispatcher) {
            val safeLocation = location.normalizedOrNull()
                ?: throw IllegalArgumentException("Invalid coarse location")
            val url = URL(buildRequestUrl(safeLocation))
            require(url.protocol == "https") { "Only HTTPS is allowed" }
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "MIRL-Android/1")
            }
            try {
                val status = connection.responseCode
                if (status !in 200..299) throw IOException("Open-Meteo HTTP $status")
                val contentLength = connection.contentLengthLong
                if (contentLength > MAX_RESPONSE_BYTES) {
                    throw IOException("Open-Meteo response is too large")
                }
                val payload = connection.inputStream.use(::readLimitedUtf8)
                OpenMeteoWeatherParser.parse(
                    payload = payload,
                    requestedLocation = safeLocation,
                    fetchedAt = clock.instant()
                )
            } finally {
                connection.disconnect()
            }
        }

    private fun buildRequestUrl(location: CoarseLocation): String {
        val latitude = String.format(Locale.US, "%.2f", location.latitude)
        val longitude = String.format(Locale.US, "%.2f", location.longitude)
        return "$ENDPOINT?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m," +
            "precipitation,weather_code,wind_speed_10m,is_day" +
            "&timezone=auto&forecast_days=1"
    }

    private fun readLimitedUtf8(connectionInput: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4_096)
        var total = 0
        while (true) {
            val count = connectionInput.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_RESPONSE_BYTES) throw IOException("Open-Meteo response is too large")
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val ENDPOINT = "https://api.open-meteo.com/v1/forecast"
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 7_000
        const val MAX_RESPONSE_BYTES = 64 * 1_024
    }
}

internal object OpenMeteoWeatherParser {
    fun parse(
        payload: String,
        requestedLocation: CoarseLocation,
        fetchedAt: Instant
    ): WeatherContext {
        val safeLocation = requestedLocation.normalizedOrNull()
            ?: throw IllegalArgumentException("Invalid coarse location")
        val root = JSONObject(payload)
        val current = root.optJSONObject("current")
            ?: throw IllegalArgumentException("Open-Meteo current weather is missing")
        val temperature = current.finiteDouble("temperature_2m")
            ?.takeIf { it in -100.0..70.0 }
            ?: throw IllegalArgumentException("Open-Meteo temperature is invalid")
        val sourceTimeZone = root.optString("timezone")
            .takeIf { it.isNotBlank() && runCatching { ZoneId.of(it) }.isSuccess }
            ?: safeLocation.zoneId
        val observedAt = parseObservedAt(current.optString("time"), sourceTimeZone)

        return WeatherContext(
            location = safeLocation,
            fetchedAt = fetchedAt,
            observedAt = observedAt,
            temperatureCelsius = temperature,
            apparentTemperatureCelsius = current.finiteDouble("apparent_temperature")
                ?.takeIf { it in -120.0..80.0 },
            relativeHumidityPercent = current.finiteDouble("relative_humidity_2m")
                ?.toInt()
                ?.takeIf { it in 0..100 },
            precipitationMillimeters = current.finiteDouble("precipitation")
                ?.takeIf { it in 0.0..1_000.0 },
            weatherCode = current.finiteDouble("weather_code")
                ?.toInt()
                ?.takeIf { it in 0..99 },
            windSpeedKilometersPerHour = current.finiteDouble("wind_speed_10m")
                ?.takeIf { it in 0.0..500.0 },
            isDay = current.finiteDouble("is_day")?.let { value ->
                when (value.toInt()) {
                    0 -> false
                    1 -> true
                    else -> null
                }
            },
            sourceTimeZone = sourceTimeZone,
            origin = WeatherContextOrigin.NETWORK
        )
    }

    private fun JSONObject.finiteDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = optDouble(key, Double.NaN)
        return value.takeIf(Double::isFinite)
    }

    private fun parseObservedAt(raw: String, zoneId: String): Instant? {
        if (raw.isBlank()) return null
        return runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(raw).atZone(ZoneId.of(zoneId)).toInstant()
            }.getOrNull()
    }
}
