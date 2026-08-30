package com.personal.sleepalarm.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sleepAutomationDataStore by preferencesDataStore(name = "sleep_automation_prefs")

data class SleepAutomationSettings(
    val enabled: Boolean = false,
    val windowStartMinutes: Int = 22 * 60,
    val windowEndMinutes: Int = 2 * 60,
    val skippedWindowStartEpochDay: Long? = null,
    val handledWindowStartEpochDay: Long? = null
) {
    fun normalized(): SleepAutomationSettings = copy(
        windowStartMinutes = windowStartMinutes.coerceIn(0, 24 * 60 - 1),
        windowEndMinutes = windowEndMinutes.coerceIn(0, 24 * 60 - 1)
    )
}

/** Настройки полностью локальной ночной автоматизации. */
class SleepAutomationPreference(
    private val context: Context
) {
    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("enabled")
        val KEY_START_MINUTES = intPreferencesKey("window_start_minutes")
        val KEY_END_MINUTES = intPreferencesKey("window_end_minutes")
        val KEY_SKIPPED_WINDOW = longPreferencesKey("skipped_window_start_epoch_day")
        val KEY_HANDLED_WINDOW = longPreferencesKey("handled_window_start_epoch_day")
    }

    fun observe(): Flow<SleepAutomationSettings> =
        context.sleepAutomationDataStore.data.map(::decode)

    suspend fun get(): SleepAutomationSettings =
        decode(context.sleepAutomationDataStore.data.first())

    suspend fun setEnabled(enabled: Boolean) {
        context.sleepAutomationDataStore.edit {
            it[KEY_ENABLED] = enabled
            if (enabled) it.remove(KEY_HANDLED_WINDOW)
        }
    }

    suspend fun setWindowStart(minutes: Int) {
        context.sleepAutomationDataStore.edit {
            it[KEY_START_MINUTES] = minutes.coerceIn(0, 24 * 60 - 1)
            it.remove(KEY_SKIPPED_WINDOW)
            it.remove(KEY_HANDLED_WINDOW)
        }
    }

    suspend fun setWindowEnd(minutes: Int) {
        context.sleepAutomationDataStore.edit {
            it[KEY_END_MINUTES] = minutes.coerceIn(0, 24 * 60 - 1)
            it.remove(KEY_SKIPPED_WINDOW)
            it.remove(KEY_HANDLED_WINDOW)
        }
    }

    suspend fun skipWindow(windowStartEpochDay: Long) {
        context.sleepAutomationDataStore.edit {
            it[KEY_SKIPPED_WINDOW] = windowStartEpochDay
            it[KEY_HANDLED_WINDOW] = windowStartEpochDay
        }
    }

    suspend fun markWindowHandled(windowStartEpochDay: Long) {
        context.sleepAutomationDataStore.edit {
            it[KEY_HANDLED_WINDOW] = windowStartEpochDay
        }
    }

    /**
     * Makes the current automation window eligible again after an explicit
     * awake action (for example, starting a focus block). The conditional
     * removal avoids reopening a different/newer night by accident.
     */
    suspend fun releaseHandledWindow(windowStartEpochDay: Long) {
        context.sleepAutomationDataStore.edit {
            if (it[KEY_HANDLED_WINDOW] == windowStartEpochDay) {
                it.remove(KEY_HANDLED_WINDOW)
            }
        }
    }

    suspend fun clearSkippedWindow() {
        context.sleepAutomationDataStore.edit { it.remove(KEY_SKIPPED_WINDOW) }
    }

    suspend fun replace(settings: SleepAutomationSettings) {
        val safe = settings.normalized()
        context.sleepAutomationDataStore.edit {
            it[KEY_ENABLED] = safe.enabled
            it[KEY_START_MINUTES] = safe.windowStartMinutes
            it[KEY_END_MINUTES] = safe.windowEndMinutes
            it.remove(KEY_SKIPPED_WINDOW)
            it.remove(KEY_HANDLED_WINDOW)
        }
    }

    private fun decode(preferences: androidx.datastore.preferences.core.Preferences): SleepAutomationSettings =
        SleepAutomationSettings(
            enabled = preferences[KEY_ENABLED] ?: false,
            windowStartMinutes = preferences[KEY_START_MINUTES] ?: 22 * 60,
            windowEndMinutes = preferences[KEY_END_MINUTES] ?: 2 * 60,
            skippedWindowStartEpochDay = preferences[KEY_SKIPPED_WINDOW],
            handledWindowStartEpochDay = preferences[KEY_HANDLED_WINDOW]
        ).normalized()
}
