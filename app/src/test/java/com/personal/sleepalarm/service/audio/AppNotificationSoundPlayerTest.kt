package com.personal.sleepalarm.service.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNotificationSoundPlayerTest {
    @Test
    fun `notification volume is clamped to supported range`() {
        assertEquals(0, AppNotificationSoundPlayer.normalizeVolumePercent(-1))
        assertEquals(45, AppNotificationSoundPlayer.normalizeVolumePercent(45))
        assertEquals(100, AppNotificationSoundPlayer.normalizeVolumePercent(140))
    }

    @Test
    fun `notification volume converts to player fraction`() {
        assertEquals(0f, AppNotificationSoundPlayer.volumeFraction(0), 0.0001f)
        assertEquals(0.25f, AppNotificationSoundPlayer.volumeFraction(50), 0.0001f)
        assertEquals(1f, AppNotificationSoundPlayer.volumeFraction(100), 0.0001f)
    }
}
