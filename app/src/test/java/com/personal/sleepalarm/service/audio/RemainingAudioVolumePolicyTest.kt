package com.personal.sleepalarm.service.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class RemainingAudioVolumePolicyTest {

    @Test
    fun `focus master uses perceptual scale once while duck and fade stay linear`() {
        assertEquals(
            0.25f,
            FocusSoundGainPolicy.outputGain(masterVolume = 0.5f),
            0.0001f,
        )
        assertEquals(
            0.0625f,
            FocusSoundGainPolicy.outputGain(
                masterVolume = 0.5f,
                duckMultiplier = 0.5f,
                fade = 0.5f,
            ),
            0.0001f,
        )
    }

    @Test
    fun `focus gain keeps true silence and full scale`() {
        assertEquals(0f, FocusSoundGainPolicy.outputGain(-1f), 0.0001f)
        assertEquals(1f, FocusSoundGainPolicy.outputGain(2f), 0.0001f)
        assertEquals(
            0f,
            FocusSoundGainPolicy.outputGain(1f, duckMultiplier = -1f, fade = 1f),
            0.0001f,
        )
    }

    @Test
    fun `focus limiter preserves normal level and clamps only mixed peaks`() {
        assertEquals(0.5f, FocusSoundGainPolicy.limitMixedSample(0.5f), 0.0001f)
        assertEquals(-0.75f, FocusSoundGainPolicy.limitMixedSample(-0.75f), 0.0001f)
        assertEquals(1f, FocusSoundGainPolicy.limitMixedSample(1.4f), 0.0001f)
        assertEquals(-1f, FocusSoundGainPolicy.limitMixedSample(-1.4f), 0.0001f)
    }

    @Test
    fun `briefing volume covers complete application scale`() {
        assertEquals(0f, BriefingVolumePolicy.gainForPercent(0), 0.0001f)
        assertEquals(0.25f, BriefingVolumePolicy.gainForPercent(50), 0.0001f)
        assertEquals(1f, BriefingVolumePolicy.gainForPercent(100), 0.0001f)
    }
}
