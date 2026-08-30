package com.personal.sleepalarm.service.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVolumeScaleTest {
    @Test
    fun `loudness scale clamps and preserves silence and full scale`() {
        assertEquals(0f, AppVolumeScale.gainForPercent(-10), 0.0001f)
        assertEquals(0f, AppVolumeScale.gainForPercent(0), 0.0001f)
        assertEquals(1f, AppVolumeScale.gainForPercent(100), 0.0001f)
        assertEquals(1f, AppVolumeScale.gainForPercent(140), 0.0001f)
    }

    @Test
    fun `loudness scale is monotonic across the complete range`() {
        var previous = AppVolumeScale.gainForPercent(0)
        for (percent in 1..100) {
            val current = AppVolumeScale.gainForPercent(percent)
            assertTrue("gain decreased at $percent%", current >= previous)
            previous = current
        }
    }

    @Test
    fun `loudness curve leaves useful control near the upper end`() {
        assertEquals(0.25f, AppVolumeScale.gainForPercent(50), 0.0001f)
        val upperStep = AppVolumeScale.gainForPercent(100) - AppVolumeScale.gainForPercent(80)
        val middleStep = AppVolumeScale.gainForPercent(80) - AppVolumeScale.gainForPercent(60)
        assertTrue(upperStep > middleStep)
    }
}
