package com.personal.sleepalarm.data.preferences

import com.personal.sleepalarm.domain.focusaudio.CustomFocusSoundFile
import com.personal.sleepalarm.domain.focusaudio.FocusSoundSelection
import com.personal.sleepalarm.domain.focusaudio.FocusSoundscapeSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusSoundPreferenceCodecTest {
    @Test
    fun `full custom two-layer scene survives a codec round trip`() {
        val original = FocusSoundscapeSelection(
            primary = FocusSoundSelection.custom(
                CustomFocusSoundFile(
                    uriString = "content://picked/audio?id=1|2",
                    displayName = "Дождь | ночь\nверсия 2",
                    mimeType = "audio/ogg",
                    durationMillis = 123_456L,
                    sizeBytes = 77_000L,
                    persistablePermissionTaken = true,
                    artist = "Night Recorder",
                    album = "Quiet Sessions",
                    addedAtMillis = 123_000L
                )
            ),
            secondaryLayerId = "pink_noise",
            secondaryVolumePercent = 27,
            playDuringRecovery = true
        )

        val restored = FocusSoundPreferenceCodec.decodeSoundscape(
            FocusSoundPreferenceCodec.encodeSoundscape(original)
        )

        assertEquals(original, restored)
    }

    @Test
    fun `corrupt and removed values fail safely`() {
        assertNull(FocusSoundPreferenceCodec.decodeSoundscape("broken"))
        assertNull(FocusSoundPreferenceCodec.decodeSelection("9|unknown"))
        assertEquals(emptyList<FocusSoundSelection>(), FocusSoundPreferenceCodec.decodeRecents("broken"))
    }

    @Test
    fun `recent codec preserves order and custom metadata`() {
        val items = listOf(
            FocusSoundSelection("large_library"),
            FocusSoundSelection.custom(CustomFocusSoundFile("content://custom/2", "My rain")),
            FocusSoundSelection("brown_noise")
        )
        assertEquals(
            items,
            FocusSoundPreferenceCodec.decodeRecents(FocusSoundPreferenceCodec.encodeRecents(items))
        )
    }

    @Test
    fun `custom library codec preserves persistent metadata`() {
        val files = listOf(
            CustomFocusSoundFile(
                uriString = "content://custom/library/1",
                displayName = "Forest",
                artist = "Field Recordist",
                durationMillis = 70_000L,
                addedAtMillis = 99L
            ),
            CustomFocusSoundFile("content://custom/library/2", "Piano")
        )
        assertEquals(
            files,
            FocusSoundPreferenceCodec.decodeCustomLibrary(
                FocusSoundPreferenceCodec.encodeCustomLibrary(files)
            )
        )
    }
}
