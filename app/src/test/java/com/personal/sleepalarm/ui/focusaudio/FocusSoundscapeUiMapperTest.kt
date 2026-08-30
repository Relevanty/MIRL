package com.personal.sleepalarm.ui.focusaudio

import com.personal.sleepalarm.domain.focusaudio.CustomFocusSoundFile
import com.personal.sleepalarm.domain.focusaudio.FocusSoundCatalog
import com.personal.sleepalarm.domain.focusaudio.FocusSoundSelection
import com.personal.sleepalarm.domain.focusaudio.FocusSoundSettings
import com.personal.sleepalarm.domain.focusaudio.FocusSoundscapeSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSoundscapeUiMapperTest {

    @Test
    fun `different recent custom files have unique cards and keep their names`() {
        val first = FocusSoundSelection.custom(
            CustomFocusSoundFile("content://audio/first", "Дождь в комнате")
        )
        val second = FocusSoundSelection.custom(
            CustomFocusSoundFile("content://audio/second", "Шум мастерской")
        )
        val settings = FocusSoundSettings(recentSelections = listOf(first, second))

        val items = settings.toUiItems(FocusSoundscapeSelection(), "ru")
        val customItems = items.filter { it.catalogId == FocusSoundCatalog.CUSTOM_FILE_ID }

        assertEquals(2, customItems.size)
        assertEquals(2, customItems.map { it.id }.distinct().size)
        assertEquals(setOf("Дождь в комнате", "Шум мастерской"), customItems.map { it.title }.toSet())
        assertTrue(settings.recentSelections.all { recent ->
            customItems.any { item -> item.id == recent.historyKey() }
        })
    }

    @Test
    fun `russian catalogue subtitle is fully localized`() {
        val items = FocusSoundSettings().toUiItems(FocusSoundscapeSelection(), "ru-RU")
        val library = items.single { it.catalogId == "large_library" }

        assertTrue(library.subtitle.contains("без интернета"))
        assertTrue(!library.subtitle.contains("offline"))
    }

    @Test
    fun `persistent custom library remains visible without recent history`() {
        val file = CustomFocusSoundFile(
            uriString = "content://audio/library",
            displayName = "Long focus mix",
            durationMillis = 245_000
        )
        val key = FocusSoundSelection.custom(file).historyKey()
        val item = FocusSoundSettings(
            customLibrary = listOf(file),
            favoriteIds = setOf(key)
        ).toUiItems(FocusSoundscapeSelection(), "en").single { it.id == key }

        assertTrue(item.isFavorite)
        assertTrue(item.subtitle.contains("4:05"))
    }
}
