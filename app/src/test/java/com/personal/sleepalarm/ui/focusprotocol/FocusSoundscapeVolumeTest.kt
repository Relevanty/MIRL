package com.personal.sleepalarm.ui.focusprotocol

import com.personal.sleepalarm.domain.focusaudio.FocusSoundSelection
import com.personal.sleepalarm.domain.focusaudio.FocusSoundscapeSelection
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusSoundscapeVolumeTest {

    @Test
    fun `primary generated noise stays at full layer gain`() {
        val selection = FocusSoundscapeSelection(
            primary = FocusSoundSelection("brown_noise")
        )

        assertEquals(1f, soundscapeNoiseLayerVolume(selection, secondaryPercent = 22), 0.0001f)
    }

    @Test
    fun `secondary noise uses its own slider gain`() {
        val selection = FocusSoundscapeSelection(
            primary = FocusSoundSelection("large_library"),
            secondaryLayerId = "pink_noise"
        )

        assertEquals(0.37f, soundscapeNoiseLayerVolume(selection, secondaryPercent = 37), 0.0001f)
    }
}
