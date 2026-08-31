package com.personal.sleepalarm.data.externalcontext

import com.personal.sleepalarm.domain.externalcontext.CachedWeatherContext
import com.personal.sleepalarm.domain.externalcontext.CoarseLocation
import com.personal.sleepalarm.domain.externalcontext.ExternalContextResult
import com.personal.sleepalarm.domain.externalcontext.ExternalContextSettings
import com.personal.sleepalarm.domain.externalcontext.ExternalContextSettingsStore
import com.personal.sleepalarm.domain.externalcontext.WeatherClient
import com.personal.sleepalarm.domain.externalcontext.WeatherContext
import com.personal.sleepalarm.domain.externalcontext.WeatherContextCache
import com.personal.sleepalarm.domain.externalcontext.WeatherContextOrigin
import com.personal.sleepalarm.domain.externalcontext.WeatherContextState
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultExternalContextProviderTest {
    private val now = Instant.parse("2026-08-30T12:00:00Z")
    private val location = CoarseLocation("Moscow", 55.75, 37.62, "Europe/Moscow")

    @Test
    fun `disabled opt-in never reads cache or calls network`() = runTest {
        val cache = FakeCache(AssertionError("cache must not be read"))
        val client = FakeClient(AssertionError("network must not be called"))
        val provider = provider(ExternalContextSettings(), cache, client)

        assertEquals(ExternalContextResult.Disabled, provider.getContext())
    }

    @Test
    fun `fresh cache avoids network and is labelled as cache`() = runTest {
        val cache = FakeCache(cached(now.minus(Duration.ofMinutes(10))))
        val client = FakeClient(AssertionError("network must not be called"))
        val provider = provider(enabledSettings(), cache, client)

        val result = provider.getContext() as ExternalContextResult.Available
        val weather = result.snapshot.weather as WeatherContextState.Available

        assertEquals(WeatherContextOrigin.FRESH_CACHE, weather.value.origin)
        assertEquals(0, client.calls)
    }

    @Test
    fun `stale cache is an offline fallback when refresh fails`() = runTest {
        val cache = FakeCache(cached(now.minus(Duration.ofHours(2))))
        val client = FakeClient(IOException("offline"))
        val provider = provider(enabledSettings(), cache, client)

        val result = provider.getContext() as ExternalContextResult.Available
        val weather = result.snapshot.weather as WeatherContextState.Available

        assertEquals(WeatherContextOrigin.STALE_CACHE, weather.value.origin)
        assertEquals(1, client.calls)
    }

    @Test
    fun `weather switch off still provides local daylight without network`() = runTest {
        val cache = FakeCache(AssertionError("cache must not be read"))
        val client = FakeClient(AssertionError("network must not be called"))
        val provider = provider(
            enabledSettings().copy(weatherEnabled = false),
            cache,
            client
        )

        val result = provider.getContext() as ExternalContextResult.Available

        assertTrue(result.snapshot.weather is WeatherContextState.Disabled)
        assertTrue(result.snapshot.daylight.daylightMinutes > 0)
        assertEquals(0, client.calls)
    }

    private fun provider(
        settings: ExternalContextSettings,
        cache: FakeCache,
        client: FakeClient
    ) = DefaultExternalContextProvider(
        settingsStore = ExternalContextSettingsStore { settings },
        weatherCache = cache,
        weatherClient = client,
        clock = Clock.fixed(now, ZoneOffset.UTC)
    )

    private fun enabledSettings() = ExternalContextSettings(
        enabled = true,
        weatherEnabled = true,
        location = location
    )

    private fun cached(fetchedAt: Instant) = CachedWeatherContext(weather(fetchedAt))

    private fun weather(fetchedAt: Instant) = WeatherContext(
        location = location,
        fetchedAt = fetchedAt,
        observedAt = fetchedAt,
        temperatureCelsius = 18.0,
        apparentTemperatureCelsius = 17.5,
        relativeHumidityPercent = 60,
        precipitationMillimeters = 0.0,
        weatherCode = 1,
        windSpeedKilometersPerHour = 8.0,
        isDay = true,
        sourceTimeZone = location.zoneId
    )

    private class FakeCache(
        private val readValue: Any
    ) : WeatherContextCache {
        var written: CachedWeatherContext? = null

        override suspend fun readWeatherCache(): CachedWeatherContext? = when (readValue) {
            is Throwable -> throw readValue
            else -> readValue as CachedWeatherContext?
        }

        override suspend fun writeWeatherCache(value: CachedWeatherContext) {
            written = value
        }
    }

    private class FakeClient(
        private val value: Any
    ) : WeatherClient {
        var calls: Int = 0

        override suspend fun fetch(location: CoarseLocation): WeatherContext {
            calls++
            if (value is Throwable) throw value
            return value as WeatherContext
        }
    }
}
