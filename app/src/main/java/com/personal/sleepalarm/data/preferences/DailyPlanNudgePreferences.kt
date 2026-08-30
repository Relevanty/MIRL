package com.personal.sleepalarm.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dailyPlanNudgeDataStore by preferencesDataStore(name = "daily_plan_nudge_prefs")

data class DailyPlanNudgeSettings(
    val enabled: Boolean = true,
    val bufferMinutes: Int = 60,
    val repeatEnabled: Boolean = true,
    val repeatIntervalMinutes: Int = 15,
    val morningReminderEnabled: Boolean = true,
    /** Fallback cutoff in local minutes after midnight. 0 means the next 00:00. */
    val cutoffMinutesOfDay: Int = 0,
    val dismissedLocalDate: String? = null,
    val snoozedLocalDate: String? = null,
    val snoozedUntilMillis: Long? = null,
    val lastMorningLocalDate: String? = null,
    val lastUrgencyLocalDate: String? = null,
    val lastUrgencyAtMillis: Long? = null
) {
    fun normalized(): DailyPlanNudgeSettings = copy(
        bufferMinutes = bufferMinutes.coerceIn(MIN_BUFFER_MINUTES, MAX_BUFFER_MINUTES),
        repeatIntervalMinutes = repeatIntervalMinutes.coerceIn(
            MIN_REPEAT_INTERVAL_MINUTES,
            MAX_REPEAT_INTERVAL_MINUTES
        ),
        cutoffMinutesOfDay = cutoffMinutesOfDay.coerceIn(0, MINUTES_PER_DAY - 1),
        dismissedLocalDate = dismissedLocalDate?.takeIf(String::isNotBlank),
        snoozedLocalDate = snoozedLocalDate?.takeIf(String::isNotBlank),
        snoozedUntilMillis = snoozedUntilMillis?.takeIf { it > 0L },
        lastMorningLocalDate = lastMorningLocalDate?.takeIf(String::isNotBlank),
        lastUrgencyLocalDate = lastUrgencyLocalDate?.takeIf(String::isNotBlank),
        lastUrgencyAtMillis = lastUrgencyAtMillis?.takeIf { it > 0L }
    )

    companion object {
        const val MIN_BUFFER_MINUTES = 0
        const val MAX_BUFFER_MINUTES = 12 * 60
        const val MIN_REPEAT_INTERVAL_MINUTES = 5
        const val MAX_REPEAT_INTERVAL_MINUTES = 120
        private const val MINUTES_PER_DAY = 24 * 60
    }
}

/** Local, offline settings and one-day suppression state for the daily-plan nudge. */
class DailyPlanNudgePreferences(context: Context) {
    private val appContext = context.applicationContext

    fun observe(): Flow<DailyPlanNudgeSettings> =
        appContext.dailyPlanNudgeDataStore.data.map(::decode)

    suspend fun get(): DailyPlanNudgeSettings = observe().first()

    suspend fun setEnabled(enabled: Boolean) = update { copy(enabled = enabled) }

    suspend fun setBufferMinutes(minutes: Int) = update { copy(bufferMinutes = minutes) }

    suspend fun setRepeatEnabled(enabled: Boolean) = update { copy(repeatEnabled = enabled) }

    suspend fun setRepeatIntervalMinutes(minutes: Int) =
        update { copy(repeatIntervalMinutes = minutes) }

    suspend fun setMorningReminderEnabled(enabled: Boolean) =
        update { copy(morningReminderEnabled = enabled) }

    suspend fun setCutoffMinutesOfDay(minutes: Int) =
        update { copy(cutoffMinutesOfDay = minutes) }

    suspend fun dismissForDate(localDate: String) = update {
        copy(
            dismissedLocalDate = localDate,
            snoozedLocalDate = null,
            snoozedUntilMillis = null
        )
    }

    suspend fun snooze(localDate: String, untilMillis: Long) = update {
        copy(
            dismissedLocalDate = null,
            snoozedLocalDate = localDate,
            snoozedUntilMillis = untilMillis
        )
    }

    suspend fun clearSuppression() = update {
        copy(
            dismissedLocalDate = null,
            snoozedLocalDate = null,
            snoozedUntilMillis = null
        )
    }

    suspend fun markMorningShown(localDate: String) =
        update { copy(lastMorningLocalDate = localDate) }

    suspend fun markUrgencyShown(localDate: String, atMillis: Long) = update {
        copy(lastUrgencyLocalDate = localDate, lastUrgencyAtMillis = atMillis)
    }

    /** Removes transient state belonging to earlier calendar days. */
    suspend fun clearExpiredDayState(currentLocalDate: String) = update {
        copy(
            dismissedLocalDate = dismissedLocalDate.takeIf { it == currentLocalDate },
            snoozedLocalDate = snoozedLocalDate.takeIf { it == currentLocalDate },
            snoozedUntilMillis = snoozedUntilMillis.takeIf {
                snoozedLocalDate == currentLocalDate
            },
            lastMorningLocalDate = lastMorningLocalDate.takeIf { it == currentLocalDate },
            lastUrgencyLocalDate = lastUrgencyLocalDate.takeIf { it == currentLocalDate },
            lastUrgencyAtMillis = lastUrgencyAtMillis.takeIf {
                lastUrgencyLocalDate == currentLocalDate
            }
        )
    }

    /** Backup/restore only durable controls; notification history is intentionally excluded. */
    suspend fun replaceControls(settings: DailyPlanNudgeSettings) {
        val safe = settings.normalized()
        update {
            copy(
                enabled = safe.enabled,
                bufferMinutes = safe.bufferMinutes,
                repeatEnabled = safe.repeatEnabled,
                repeatIntervalMinutes = safe.repeatIntervalMinutes,
                morningReminderEnabled = safe.morningReminderEnabled,
                cutoffMinutesOfDay = safe.cutoffMinutesOfDay
            )
        }
    }

    private suspend fun update(transform: DailyPlanNudgeSettings.() -> DailyPlanNudgeSettings) {
        appContext.dailyPlanNudgeDataStore.edit { preferences ->
            encode(preferences, decode(preferences).transform().normalized())
        }
    }

    internal companion object {
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_BUFFER_MINUTES = intPreferencesKey("buffer_minutes")
        private val KEY_REPEAT_ENABLED = booleanPreferencesKey("repeat_enabled")
        private val KEY_REPEAT_INTERVAL_MINUTES = intPreferencesKey("repeat_interval_minutes")
        private val KEY_MORNING_ENABLED = booleanPreferencesKey("morning_reminder_enabled")
        private val KEY_CUTOFF_MINUTES = intPreferencesKey("cutoff_minutes_of_day")
        private val KEY_DISMISSED_DATE = stringPreferencesKey("dismissed_local_date")
        private val KEY_SNOOZED_DATE = stringPreferencesKey("snoozed_local_date")
        private val KEY_SNOOZED_UNTIL = longPreferencesKey("snoozed_until_millis")
        private val KEY_LAST_MORNING_DATE = stringPreferencesKey("last_morning_local_date")
        private val KEY_LAST_URGENCY_DATE = stringPreferencesKey("last_urgency_local_date")
        private val KEY_LAST_URGENCY_AT = longPreferencesKey("last_urgency_at_millis")

        internal fun decode(preferences: Preferences): DailyPlanNudgeSettings =
            DailyPlanNudgeSettings(
                enabled = preferences[KEY_ENABLED] ?: true,
                bufferMinutes = preferences[KEY_BUFFER_MINUTES] ?: 60,
                repeatEnabled = preferences[KEY_REPEAT_ENABLED] ?: true,
                repeatIntervalMinutes = preferences[KEY_REPEAT_INTERVAL_MINUTES] ?: 15,
                morningReminderEnabled = preferences[KEY_MORNING_ENABLED] ?: true,
                cutoffMinutesOfDay = preferences[KEY_CUTOFF_MINUTES] ?: 0,
                dismissedLocalDate = preferences[KEY_DISMISSED_DATE],
                snoozedLocalDate = preferences[KEY_SNOOZED_DATE],
                snoozedUntilMillis = preferences[KEY_SNOOZED_UNTIL],
                lastMorningLocalDate = preferences[KEY_LAST_MORNING_DATE],
                lastUrgencyLocalDate = preferences[KEY_LAST_URGENCY_DATE],
                lastUrgencyAtMillis = preferences[KEY_LAST_URGENCY_AT]
            ).normalized()

        private fun encode(preferences: androidx.datastore.preferences.core.MutablePreferences, value: DailyPlanNudgeSettings) {
            preferences[KEY_ENABLED] = value.enabled
            preferences[KEY_BUFFER_MINUTES] = value.bufferMinutes
            preferences[KEY_REPEAT_ENABLED] = value.repeatEnabled
            preferences[KEY_REPEAT_INTERVAL_MINUTES] = value.repeatIntervalMinutes
            preferences[KEY_MORNING_ENABLED] = value.morningReminderEnabled
            preferences[KEY_CUTOFF_MINUTES] = value.cutoffMinutesOfDay
            preferences.putOrRemove(KEY_DISMISSED_DATE, value.dismissedLocalDate)
            preferences.putOrRemove(KEY_SNOOZED_DATE, value.snoozedLocalDate)
            preferences.putOrRemove(KEY_SNOOZED_UNTIL, value.snoozedUntilMillis)
            preferences.putOrRemove(KEY_LAST_MORNING_DATE, value.lastMorningLocalDate)
            preferences.putOrRemove(KEY_LAST_URGENCY_DATE, value.lastUrgencyLocalDate)
            preferences.putOrRemove(KEY_LAST_URGENCY_AT, value.lastUrgencyAtMillis)
        }

        private fun <T> androidx.datastore.preferences.core.MutablePreferences.putOrRemove(
            key: Preferences.Key<T>,
            value: T?
        ) {
            if (value == null) remove(key) else this[key] = value
        }
    }
}
