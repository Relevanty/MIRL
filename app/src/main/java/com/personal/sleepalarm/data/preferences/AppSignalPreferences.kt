package com.personal.sleepalarm.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appSignalDataStore by preferencesDataStore(name = "pomodoro_sound_prefs")

enum class AppSignalType(val storagePrefix: String) {
    POMODORO("pomodoro"),
    REMINDER("reminder"),
    CALENDAR("calendar"),
    DAILY_PLAN("daily_plan")
}

enum class AppSoundMode {
    /** Android's default sound, or another sound chosen from the system picker. */
    SYSTEM,

    /** Any readable audio document selected through Android's document picker. */
    FILE,

    /** Keep the visual notification but do not play audio. */
    SILENT
}

data class AppSoundSelection(
    val mode: AppSoundMode = AppSoundMode.SYSTEM,
    val uriString: String? = null
) {
    fun normalized(): AppSoundSelection {
        val normalizedUri = uriString?.trim()?.takeIf(String::isNotEmpty)
        return when (mode) {
            AppSoundMode.SYSTEM -> copy(uriString = normalizedUri)
            AppSoundMode.FILE -> if (normalizedUri == null) AppSoundSelection()
                else copy(uriString = normalizedUri)
            AppSoundMode.SILENT -> copy(uriString = null)
        }
    }
}

/**
 * Independent sound and volume settings for every short application signal.
 * A null [volumePercent] means that an upgraded install still uses the old
 * shared notification volume until the user changes this particular slider.
 */
data class AppSignalSettings(
    val sound: AppSoundSelection = AppSoundSelection(),
    val volumePercent: Int? = null
) {
    fun effectiveVolume(legacyVolumePercent: Int): Int =
        (volumePercent ?: legacyVolumePercent).coerceIn(0, 100)
}

/**
 * Offline DataStore-backed mixer for Pomodoro/focus, reminders, calendar and daily plan.
 * The old Pomodoro-only URI is migrated lazily and only for [AppSignalType.POMODORO].
 */
class AppSignalPreferences(private val context: Context) {
    private val appContext = context.applicationContext

    fun observe(type: AppSignalType): Flow<AppSignalSettings> =
        appContext.appSignalDataStore.data.map { preferences ->
            decode(
                type = type,
                modeValue = preferences[modeKey(type)],
                uriValue = preferences[uriKey(type)] ?: preferences[legacyUriKey(type)],
                volumeValue = preferences[volumeKey(type)]
            )
        }

    suspend fun get(type: AppSignalType): AppSignalSettings = observe(type).first()

    suspend fun setSound(type: AppSignalType, sound: AppSoundSelection) {
        val normalized = sound.normalized()
        appContext.appSignalDataStore.edit { preferences ->
            preferences[modeKey(type)] = normalized.mode.name
            normalized.uriString?.let { preferences[uriKey(type)] = it }
                ?: preferences.remove(uriKey(type))
            preferences.remove(legacyUriKey(type))
        }
    }

    suspend fun setVolume(type: AppSignalType, volumePercent: Int) {
        appContext.appSignalDataStore.edit { preferences ->
            preferences[volumeKey(type)] = volumePercent.coerceIn(0, 100)
        }
    }

    internal companion object {
        private fun modeKey(type: AppSignalType) =
            stringPreferencesKey("${type.storagePrefix}_sound_mode")

        private fun uriKey(type: AppSignalType) =
            stringPreferencesKey("${type.storagePrefix}_sound_uri_v2")

        private fun volumeKey(type: AppSignalType) =
            intPreferencesKey("${type.storagePrefix}_volume_percent")

        private fun legacyUriKey(type: AppSignalType) = if (type == AppSignalType.POMODORO) {
            stringPreferencesKey("notification_sound_uri")
        } else {
            stringPreferencesKey("unused_${type.storagePrefix}_legacy_uri")
        }

        internal fun decode(
            type: AppSignalType,
            modeValue: String?,
            uriValue: String?,
            volumeValue: Int?
        ): AppSignalSettings {
            val uri = uriValue?.trim()?.takeIf(String::isNotEmpty)
            val mode = modeValue?.let { raw ->
                AppSoundMode.entries.firstOrNull { it.name == raw }
            } ?: if (type == AppSignalType.POMODORO && uri != null) {
                AppSoundMode.FILE
            } else {
                AppSoundMode.SYSTEM
            }
            return AppSignalSettings(
                sound = AppSoundSelection(mode, uri).normalized(),
                volumePercent = volumeValue?.coerceIn(0, 100)
            )
        }
    }
}
