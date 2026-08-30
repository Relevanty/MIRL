package com.personal.sleepalarm.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyPlanNudgePreferencesTest {
    @Test
    fun `defaults match product policy`() {
        val decoded = DailyPlanNudgePreferences.decode(mutablePreferencesOf())

        assertTrue(decoded.enabled)
        assertEquals(60, decoded.bufferMinutes)
        assertTrue(decoded.repeatEnabled)
        assertEquals(15, decoded.repeatIntervalMinutes)
        assertTrue(decoded.morningReminderEnabled)
        assertEquals(0, decoded.cutoffMinutesOfDay)
    }

    @Test
    fun `persisted controls are normalized`() {
        val decoded = DailyPlanNudgePreferences.decode(
            mutablePreferencesOf(
                booleanPreferencesKey("enabled") to false,
                intPreferencesKey("buffer_minutes") to 50_000,
                intPreferencesKey("repeat_interval_minutes") to 2,
                intPreferencesKey("cutoff_minutes_of_day") to 2_000
            )
        )

        assertFalse(decoded.enabled)
        assertEquals(DailyPlanNudgeSettings.MAX_BUFFER_MINUTES, decoded.bufferMinutes)
        assertEquals(DailyPlanNudgeSettings.MIN_REPEAT_INTERVAL_MINUTES, decoded.repeatIntervalMinutes)
        assertEquals(1_439, decoded.cutoffMinutesOfDay)
    }
}
