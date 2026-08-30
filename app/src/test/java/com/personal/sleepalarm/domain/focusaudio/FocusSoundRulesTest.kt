package com.personal.sleepalarm.domain.focusaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSoundRulesTest {
    @Test
    fun `volume and favourites are normalized`() {
        assertEquals(0, FocusSoundRules.normalizeVolume(-30))
        assertEquals(35, FocusSoundRules.normalizeVolume(35))
        assertEquals(100, FocusSoundRules.normalizeVolume(180))

        val favorites = FocusSoundRules.sanitizeFavoriteIds(
            listOf("brown_noise", "silence", "custom_file", "missing", "brown_noise")
        )
        assertEquals(setOf("brown_noise"), favorites)
        assertTrue("large_library" in FocusSoundRules.toggleFavorite(favorites, "large_library"))
        assertFalse("brown_noise" in FocusSoundRules.toggleFavorite(favorites, "brown_noise"))
    }

    @Test
    fun `recent selection is moved to front and deduplicated`() {
        val library = FocusSoundSelection("large_library")
        val rain = FocusSoundSelection("rain_on_window")
        val result = FocusSoundRules.pushRecent(
            current = listOf(library, rain, library),
            selected = rain
        )
        assertEquals(listOf("rain_on_window", "large_library"), result.map { it.catalogId })
    }

    @Test
    fun `different custom files keep independent recent entries`() {
        val first = FocusSoundSelection.custom(CustomFocusSoundFile("content://audio/1", "One"))
        val second = FocusSoundSelection.custom(CustomFocusSoundFile("content://audio/2", "Two"))
        val result = FocusSoundRules.pushRecent(listOf(first), second)
        assertEquals(2, result.size)
        assertEquals("content://audio/2", result.first().customFile?.uriString)
    }

    @Test
    fun `two layer scene only accepts noise as a secondary layer`() {
        val valid = FocusSoundscapeSelection(
            primary = FocusSoundSelection("large_library"),
            secondaryLayerId = "brown_noise",
            secondaryVolumePercent = 130,
            playDuringRecovery = true
        ).normalized()
        assertEquals("brown_noise", valid.secondaryLayerId)
        assertEquals(100, valid.secondaryVolumePercent)
        assertTrue(valid.playDuringRecovery)

        val melodyAsSecond = valid.copy(secondaryLayerId = "pumping_drone").normalized()
        assertNull(melodyAsSecond.secondaryLayerId)
        val noisePlusNoise = valid.copy(
            primary = FocusSoundSelection("white_noise"),
            secondaryLayerId = "brown_noise"
        ).normalized()
        assertNull(noisePlusNoise.secondaryLayerId)
    }

    @Test
    fun `custom library and custom favourites are normalized by uri`() {
        val first = CustomFocusSoundFile(" content://audio/one ", "One")
        val duplicate = CustomFocusSoundFile("content://audio/one", "Duplicate")
        val second = CustomFocusSoundFile("content://audio/two", "Two")
        val library = FocusSoundRules.normalizeCustomLibrary(listOf(first, duplicate, second))
        assertEquals(listOf("content://audio/one", "content://audio/two"), library.map { it.uriString })

        val customKey = FocusSoundSelection.custom(first).historyKey()
        val favorites = FocusSoundRules.toggleFavorite(emptySet(), customKey)
        assertTrue(customKey in favorites)
        assertFalse(customKey in FocusSoundRules.toggleFavorite(favorites, customKey))
    }

    @Test
    fun `missing custom permission metadata remains usable but empty uri becomes silence`() {
        val custom = FocusSoundSelection.custom(
            CustomFocusSoundFile(
                uriString = " content://picked/focus ",
                displayName = " Session | rain ",
                mimeType = " audio/ogg "
            )
        )
        assertEquals("content://picked/focus", custom.customFile?.uriString)
        assertEquals("Session | rain", custom.customFile?.displayName)
        assertFalse(custom.customFile?.persistablePermissionTaken ?: true)

        assertEquals(
            FocusSoundCatalog.SILENCE_ID,
            FocusSoundSelection.custom(CustomFocusSoundFile("   ")).catalogId
        )
    }
}
