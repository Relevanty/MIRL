package com.personal.sleepalarm.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.personal.sleepalarm.domain.externalcontext.CachedWeatherContext
import com.personal.sleepalarm.domain.externalcontext.CoarseLocation
import com.personal.sleepalarm.domain.externalcontext.ExternalContextSettings
import com.personal.sleepalarm.domain.externalcontext.ExternalContextSettingsStore
import com.personal.sleepalarm.domain.externalcontext.WeatherContext
import com.personal.sleepalarm.domain.externalcontext.WeatherContextCache
import com.personal.sleepalarm.domain.externalcontext.WeatherContextOrigin
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.externalContextDataStore by preferencesDataStore(
    name = "external_context_prefs"
)

/**
 * Dedicated opt-in store for coarse external context. It is intentionally independent of Room,
 * profile backup and existing Settings UI.
 */
class ExternalContextPreferences(
    context: Context
) : ExternalContextSettingsStore, WeatherContextCache {
    private val appContext = context.applicationContext

    fun observeSettings(): Flow<ExternalContextSettings> =
        appContext.externalContextDataStore.data.map(::decodeSettings)

    override suspend fun getSettings(): ExternalContextSettings =
        decodeSettings(appContext.externalContextDataStore.data.first())

    suspend fun setEnabled(enabled: Boolean) {
        appContext.externalContextDataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setWeatherEnabled(enabled: Boolean) {
        appContext.externalContextDataStore.edit { it[KEY_WEATHER_ENABLED] = enabled }
    }

    /** Returns false and leaves the previous location untouched if coordinates are invalid. */
    suspend fun setLocation(location: CoarseLocation): Boolean {
        val safe = location.normalizedOrNull() ?: return false
        appContext.externalContextDataStore.edit { preferences ->
            clearCacheWhenLocationChanges(preferences, safe)
            preferences[KEY_CITY] = safe.cityLabel
            preferences[KEY_LATITUDE] = safe.latitude
            preferences[KEY_LONGITUDE] = safe.longitude
            preferences[KEY_ZONE_ID] = safe.zoneId
        }
        return true
    }

    suspend fun clearLocation() {
        appContext.externalContextDataStore.edit { preferences ->
            preferences.remove(KEY_CITY)
            preferences.remove(KEY_LATITUDE)
            preferences.remove(KEY_LONGITUDE)
            preferences.remove(KEY_ZONE_ID)
            clearWeatherCache(preferences)
        }
    }

    suspend fun replaceSettings(settings: ExternalContextSettings) {
        val safe = settings.normalized()
        appContext.externalContextDataStore.edit { preferences ->
            preferences[KEY_ENABLED] = safe.enabled
            preferences[KEY_WEATHER_ENABLED] = safe.weatherEnabled
            val location = safe.location
            if (location == null) {
                preferences.remove(KEY_CITY)
                preferences.remove(KEY_LATITUDE)
                preferences.remove(KEY_LONGITUDE)
                preferences.remove(KEY_ZONE_ID)
                clearWeatherCache(preferences)
            } else {
                clearCacheWhenLocationChanges(preferences, location)
                preferences[KEY_CITY] = location.cityLabel
                preferences[KEY_LATITUDE] = location.latitude
                preferences[KEY_LONGITUDE] = location.longitude
                preferences[KEY_ZONE_ID] = location.zoneId
            }
        }
    }

    override suspend fun readWeatherCache(): CachedWeatherContext? =
        decodeWeatherCache(appContext.externalContextDataStore.data.first())

    override suspend fun writeWeatherCache(value: CachedWeatherContext) {
        val weather = value.weather
        val location = weather.location.normalizedOrNull() ?: return
        if (!weather.temperatureCelsius.isFinite() ||
            weather.temperatureCelsius !in MIN_TEMPERATURE..MAX_TEMPERATURE
        ) {
            return
        }
        appContext.externalContextDataStore.edit { preferences ->
            preferences[KEY_CACHE_CITY] = location.cityLabel
            preferences[KEY_CACHE_LATITUDE] = location.latitude
            preferences[KEY_CACHE_LONGITUDE] = location.longitude
            preferences[KEY_CACHE_ZONE_ID] = location.zoneId
            preferences[KEY_CACHE_FETCHED_AT] = weather.fetchedAt.toEpochMilli()
            preferences.setOrRemove(
                KEY_CACHE_OBSERVED_AT,
                weather.observedAt?.toEpochMilli()
            )
            preferences[KEY_CACHE_TEMPERATURE] = weather.temperatureCelsius
            preferences.setOrRemove(
                KEY_CACHE_APPARENT_TEMPERATURE,
                weather.apparentTemperatureCelsius
            )
            preferences.setOrRemove(
                KEY_CACHE_HUMIDITY,
                weather.relativeHumidityPercent?.toLong()
            )
            preferences.setOrRemove(
                KEY_CACHE_PRECIPITATION,
                weather.precipitationMillimeters
            )
            preferences.setOrRemove(
                KEY_CACHE_WEATHER_CODE,
                weather.weatherCode?.toLong()
            )
            preferences.setOrRemove(
                KEY_CACHE_WIND_SPEED,
                weather.windSpeedKilometersPerHour
            )
            preferences.setOrRemove(KEY_CACHE_IS_DAY, weather.isDay)
            preferences[KEY_CACHE_SOURCE_ZONE] = weather.sourceTimeZone
        }
    }

    suspend fun clearWeatherCache() {
        appContext.externalContextDataStore.edit(::clearWeatherCache)
    }

    suspend fun clearAll() {
        appContext.externalContextDataStore.edit { it.clear() }
    }

    private fun decodeSettings(preferences: Preferences): ExternalContextSettings {
        val latitude = preferences[KEY_LATITUDE]
        val longitude = preferences[KEY_LONGITUDE]
        val location = if (latitude != null && longitude != null) {
            CoarseLocation(
                cityLabel = preferences[KEY_CITY].orEmpty(),
                latitude = latitude,
                longitude = longitude,
                zoneId = preferences[KEY_ZONE_ID] ?: java.time.ZoneId.systemDefault().id
            ).normalizedOrNull()
        } else {
            null
        }
        return ExternalContextSettings(
            enabled = preferences[KEY_ENABLED] ?: false,
            weatherEnabled = preferences[KEY_WEATHER_ENABLED] ?: false,
            location = location
        )
    }

    private fun decodeWeatherCache(preferences: Preferences): CachedWeatherContext? {
        val latitude = preferences[KEY_CACHE_LATITUDE] ?: return null
        val longitude = preferences[KEY_CACHE_LONGITUDE] ?: return null
        val fetchedAtMillis = preferences[KEY_CACHE_FETCHED_AT] ?: return null
        val temperature = preferences[KEY_CACHE_TEMPERATURE] ?: return null
        if (!temperature.isFinite() || temperature !in MIN_TEMPERATURE..MAX_TEMPERATURE) {
            return null
        }
        val location = CoarseLocation(
            cityLabel = preferences[KEY_CACHE_CITY].orEmpty(),
            latitude = latitude,
            longitude = longitude,
            zoneId = preferences[KEY_CACHE_ZONE_ID] ?: java.time.ZoneId.systemDefault().id
        ).normalizedOrNull() ?: return null
        val fetchedAt = runCatching { Instant.ofEpochMilli(fetchedAtMillis) }.getOrNull()
            ?: return null
        val observedAt = preferences[KEY_CACHE_OBSERVED_AT]?.let { millis ->
            runCatching { Instant.ofEpochMilli(millis) }.getOrNull()
        }
        return CachedWeatherContext(
            WeatherContext(
                location = location,
                fetchedAt = fetchedAt,
                observedAt = observedAt,
                temperatureCelsius = temperature,
                apparentTemperatureCelsius = preferences[KEY_CACHE_APPARENT_TEMPERATURE],
                relativeHumidityPercent = preferences[KEY_CACHE_HUMIDITY]
                    ?.toInt()
                    ?.takeIf { it in 0..100 },
                precipitationMillimeters = preferences[KEY_CACHE_PRECIPITATION]
                    ?.takeIf { it.isFinite() && it >= 0.0 },
                weatherCode = preferences[KEY_CACHE_WEATHER_CODE]
                    ?.toInt()
                    ?.takeIf { it >= 0 },
                windSpeedKilometersPerHour = preferences[KEY_CACHE_WIND_SPEED]
                    ?.takeIf { it.isFinite() && it >= 0.0 },
                isDay = preferences[KEY_CACHE_IS_DAY],
                sourceTimeZone = preferences[KEY_CACHE_SOURCE_ZONE] ?: location.zoneId,
                origin = WeatherContextOrigin.NETWORK
            )
        )
    }

    private fun clearCacheWhenLocationChanges(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        newLocation: CoarseLocation
    ) {
        val cachedLatitude = preferences[KEY_CACHE_LATITUDE]
        val cachedLongitude = preferences[KEY_CACHE_LONGITUDE]
        if (cachedLatitude == null || cachedLongitude == null) return
        val cachedLocation = CoarseLocation(
            latitude = cachedLatitude,
            longitude = cachedLongitude,
            zoneId = preferences[KEY_CACHE_ZONE_ID] ?: newLocation.zoneId
        )
        if (!cachedLocation.sameWeatherLocation(newLocation)) clearWeatherCache(preferences)
    }

    private fun clearWeatherCache(
        preferences: androidx.datastore.preferences.core.MutablePreferences
    ) {
        preferences.remove(KEY_CACHE_CITY)
        preferences.remove(KEY_CACHE_LATITUDE)
        preferences.remove(KEY_CACHE_LONGITUDE)
        preferences.remove(KEY_CACHE_ZONE_ID)
        preferences.remove(KEY_CACHE_FETCHED_AT)
        preferences.remove(KEY_CACHE_OBSERVED_AT)
        preferences.remove(KEY_CACHE_TEMPERATURE)
        preferences.remove(KEY_CACHE_APPARENT_TEMPERATURE)
        preferences.remove(KEY_CACHE_HUMIDITY)
        preferences.remove(KEY_CACHE_PRECIPITATION)
        preferences.remove(KEY_CACHE_WEATHER_CODE)
        preferences.remove(KEY_CACHE_WIND_SPEED)
        preferences.remove(KEY_CACHE_IS_DAY)
        preferences.remove(KEY_CACHE_SOURCE_ZONE)
    }

    private companion object {
        const val MIN_TEMPERATURE = -100.0
        const val MAX_TEMPERATURE = 70.0

        val KEY_ENABLED = booleanPreferencesKey("enabled")
        val KEY_WEATHER_ENABLED = booleanPreferencesKey("weather_enabled")
        val KEY_CITY = stringPreferencesKey("city_label")
        val KEY_LATITUDE = doublePreferencesKey("latitude")
        val KEY_LONGITUDE = doublePreferencesKey("longitude")
        val KEY_ZONE_ID = stringPreferencesKey("zone_id")

        val KEY_CACHE_CITY = stringPreferencesKey("cache_city_label")
        val KEY_CACHE_LATITUDE = doublePreferencesKey("cache_latitude")
        val KEY_CACHE_LONGITUDE = doublePreferencesKey("cache_longitude")
        val KEY_CACHE_ZONE_ID = stringPreferencesKey("cache_zone_id")
        val KEY_CACHE_FETCHED_AT = longPreferencesKey("cache_fetched_at")
        val KEY_CACHE_OBSERVED_AT = longPreferencesKey("cache_observed_at")
        val KEY_CACHE_TEMPERATURE = doublePreferencesKey("cache_temperature_c")
        val KEY_CACHE_APPARENT_TEMPERATURE = doublePreferencesKey("cache_apparent_temperature_c")
        val KEY_CACHE_HUMIDITY = longPreferencesKey("cache_humidity_percent")
        val KEY_CACHE_PRECIPITATION = doublePreferencesKey("cache_precipitation_mm")
        val KEY_CACHE_WEATHER_CODE = longPreferencesKey("cache_weather_code")
        val KEY_CACHE_WIND_SPEED = doublePreferencesKey("cache_wind_kph")
        val KEY_CACHE_IS_DAY = booleanPreferencesKey("cache_is_day")
        val KEY_CACHE_SOURCE_ZONE = stringPreferencesKey("cache_source_zone")

    }
}

private fun <T> androidx.datastore.preferences.core.MutablePreferences.setOrRemove(
    key: Preferences.Key<T>,
    value: T?
) {
    if (value == null) remove(key) else this[key] = value
}
