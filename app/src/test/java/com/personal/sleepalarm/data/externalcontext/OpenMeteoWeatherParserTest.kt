package com.personal.sleepalarm.data.externalcontext

import com.personal.sleepalarm.domain.externalcontext.CoarseLocation
import com.personal.sleepalarm.domain.externalcontext.WeatherContextOrigin
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoWeatherParserTest {
    private val location = CoarseLocation(
        cityLabel = "Berlin",
        latitude = 52.5208,
        longitude = 13.4095,
        zoneId = "Europe/Berlin"
    )
    private val fetchedAt = Instant.parse("2026-08-30T10:20:00Z")

    @Test
    fun `parses requested current weather fields and local observation time`() {
        val payload = """
            {
              "timezone": "Europe/Berlin",
              "current": {
                "time": "2026-08-30T12:15",
                "temperature_2m": 22.4,
                "apparent_temperature": 21.8,
                "relative_humidity_2m": 58,
                "precipitation": 0.2,
                "weather_code": 3,
                "wind_speed_10m": 14.7,
                "is_day": 1
              }
            }
        """.trimIndent()

        val result = OpenMeteoWeatherParser.parse(payload, location, fetchedAt)

        assertEquals(52.52, result.location.latitude, 0.0)
        assertEquals(13.41, result.location.longitude, 0.0)
        assertEquals(fetchedAt, result.fetchedAt)
        assertEquals(Instant.parse("2026-08-30T10:15:00Z"), result.observedAt)
        assertEquals(22.4, result.temperatureCelsius, 0.0)
        assertEquals(21.8, result.apparentTemperatureCelsius!!, 0.0)
        assertEquals(58, result.relativeHumidityPercent)
        assertEquals(0.2, result.precipitationMillimeters!!, 0.0)
        assertEquals(3, result.weatherCode)
        assertEquals(14.7, result.windSpeedKilometersPerHour!!, 0.0)
        assertTrue(result.isDay == true)
        assertEquals(WeatherContextOrigin.NETWORK, result.origin)
    }

    @Test
    fun `keeps optional fields absent without inventing values`() {
        val payload = """
            {
              "timezone": "Invalid/Zone",
              "current": {
                "temperature_2m": 4.5,
                "is_day": 0
              }
            }
        """.trimIndent()

        val result = OpenMeteoWeatherParser.parse(payload, location, fetchedAt)

        assertEquals("Europe/Berlin", result.sourceTimeZone)
        assertNull(result.observedAt)
        assertNull(result.apparentTemperatureCelsius)
        assertNull(result.relativeHumidityPercent)
        assertNull(result.precipitationMillimeters)
        assertNull(result.weatherCode)
        assertNull(result.windSpeedKilometersPerHour)
        assertFalse(result.isDay!!)
    }

    @Test
    fun `rejects missing or unsafe primary temperature`() {
        val missing = runCatching {
            OpenMeteoWeatherParser.parse(
                """{"current":{"relative_humidity_2m":50}}""",
                location,
                fetchedAt
            )
        }
        val unsafe = runCatching {
            OpenMeteoWeatherParser.parse(
                """{"current":{"temperature_2m":500}}""",
                location,
                fetchedAt
            )
        }

        assertTrue(missing.exceptionOrNull() is IllegalArgumentException)
        assertTrue(unsafe.exceptionOrNull() is IllegalArgumentException)
    }
}
