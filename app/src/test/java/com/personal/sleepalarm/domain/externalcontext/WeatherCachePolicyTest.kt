package com.personal.sleepalarm.domain.externalcontext

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherCachePolicyTest {
    private val now = Instant.parse("2026-08-30T12:00:00Z")
    private val location = CoarseLocation("Moscow", 55.75, 37.62, "Europe/Moscow")

    @Test
    fun `fresh cache is used without refresh`() {
        val use = WeatherCachePolicy.evaluate(
            cached = cached(now.minus(Duration.ofMinutes(20))),
            requestedLocation = location,
            now = now
        )

        assertTrue(use is WeatherCacheUse.Fresh)
    }

    @Test
    fun `older cache is retained only as bounded offline fallback`() {
        val use = WeatherCachePolicy.evaluate(
            cached = cached(now.minus(Duration.ofHours(2))),
            requestedLocation = location,
            now = now
        )

        assertTrue(use is WeatherCacheUse.StaleFallback)
    }

    @Test
    fun `expired cache and another location are misses`() {
        val expired = WeatherCachePolicy.evaluate(
            cached = cached(now.minus(Duration.ofHours(7))),
            requestedLocation = location,
            now = now
        )
        val moved = WeatherCachePolicy.evaluate(
            cached = cached(now.minus(Duration.ofMinutes(5))),
            requestedLocation = CoarseLocation("Kazan", 55.79, 49.12, "Europe/Moscow"),
            now = now
        )

        assertTrue(expired is WeatherCacheUse.Miss)
        assertTrue(moved is WeatherCacheUse.Miss)
    }

    @Test
    fun `small clock correction is tolerated but large future timestamp is rejected`() {
        val smallCorrection = WeatherCachePolicy.evaluate(
            cached = cached(now.plus(Duration.ofMinutes(2))),
            requestedLocation = location,
            now = now
        )
        val unsafeFuture = WeatherCachePolicy.evaluate(
            cached = cached(now.plus(Duration.ofMinutes(10))),
            requestedLocation = location,
            now = now
        )

        assertTrue(smallCorrection is WeatherCacheUse.Fresh)
        assertTrue(unsafeFuture is WeatherCacheUse.Miss)
    }

    private fun cached(fetchedAt: Instant): CachedWeatherContext = CachedWeatherContext(
        WeatherContext(
            location = location,
            fetchedAt = fetchedAt,
            observedAt = fetchedAt,
            temperatureCelsius = 18.0,
            apparentTemperatureCelsius = null,
            relativeHumidityPercent = null,
            precipitationMillimeters = null,
            weatherCode = null,
            windSpeedKilometersPerHour = null,
            isDay = null,
            sourceTimeZone = location.zoneId
        )
    )
}
