package com.personal.sleepalarm.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSignalPreferencesTest {
    @Test
    fun `legacy pomodoro uri is preserved as a file selection`() {
        val decoded = AppSignalPreferences.decode(
            type = AppSignalType.POMODORO,
            modeValue = null,
            uriValue = " content://audio/old ",
            volumeValue = null
        )

        assertEquals(AppSoundMode.FILE, decoded.sound.mode)
        assertEquals("content://audio/old", decoded.sound.uriString)
        assertNull(decoded.volumePercent)
    }

    @Test
    fun `missing file uri safely falls back to system sound`() {
        val decoded = AppSignalPreferences.decode(
            type = AppSignalType.REMINDER,
            modeValue = AppSoundMode.FILE.name,
            uriValue = "  ",
            volumeValue = 130
        )

        assertEquals(AppSoundMode.SYSTEM, decoded.sound.mode)
        assertNull(decoded.sound.uriString)
        assertEquals(100, decoded.volumePercent)
    }

    @Test
    fun `silent mode discards stale uri and keeps independent volume`() {
        val decoded = AppSignalPreferences.decode(
            type = AppSignalType.CALENDAR,
            modeValue = AppSoundMode.SILENT.name,
            uriValue = "content://audio/stale",
            volumeValue = 35
        )

        assertEquals(AppSoundMode.SILENT, decoded.sound.mode)
        assertNull(decoded.sound.uriString)
        assertEquals(35, decoded.effectiveVolume(80))
    }

    @Test
    fun `legacy shared volume remains fallback until signal slider changes`() {
        val settings = AppSignalSettings(volumePercent = null)

        assertEquals(72, settings.effectiveVolume(72))
    }

    @Test
    fun `daily plan has an independent default signal`() {
        val decoded = AppSignalPreferences.decode(
            type = AppSignalType.DAILY_PLAN,
            modeValue = null,
            uriValue = null,
            volumeValue = 42
        )

        assertEquals(AppSoundMode.SYSTEM, decoded.sound.mode)
        assertEquals(42, decoded.volumePercent)
    }
}
