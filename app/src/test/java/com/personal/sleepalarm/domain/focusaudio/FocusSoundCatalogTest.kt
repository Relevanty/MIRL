package com.personal.sleepalarm.domain.focusaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSoundCatalogTest {
    @Test
    fun `catalog has stable unique ids and the promised offline content`() {
        assertEquals(FocusSoundCatalog.all.size, FocusSoundCatalog.all.map { it.id }.distinct().size)
        assertTrue(FocusSoundCatalog.all.all { it.id.matches(Regex("[a-z0-9_]+")) })
        assertEquals(3, FocusSoundCatalog.noiseEntries.size)
        assertTrue(FocusSoundCatalog.ambientEntries.size >= 30)
        assertEquals(55, FocusSoundCatalog.ambientEntries.size)
        assertEquals(2, FocusSoundCatalog.melodyEntries.size)
        assertEquals(62, FocusSoundCatalog.all.size)
    }

    @Test
    fun `every category is represented and browse ordering excludes utility categories`() {
        FocusSoundCategory.entries.forEach { category ->
            assertTrue("No entries in ${category.id}", FocusSoundCatalog.inCategory(category).isNotEmpty())
        }
        assertFalse(FocusSoundCategory.SILENCE in FocusSoundCatalog.browseCategories)
        assertFalse(FocusSoundCategory.CUSTOM in FocusSoundCatalog.browseCategories)
        assertEquals(FocusSoundCategory.NOISE, FocusSoundCatalog.browseCategories.first())
    }

    @Test
    fun `generated and bundled source metadata is unambiguous`() {
        FocusSoundCatalog.noiseEntries.forEach {
            assertNotNull(it.noiseColor)
            assertNull(it.bundledAssetName)
        }
        (FocusSoundCatalog.ambientEntries + FocusSoundCatalog.melodyEntries).forEach {
            assertNotNull(it.bundledAssetName)
            assertTrue(it.loops)
        }
    }

    @Test
    fun `removed id falls back to silence and search supports both languages`() {
        assertEquals(FocusSoundCatalog.SILENCE_ID, FocusSoundCatalog.resolve("removed_sound").id)
        assertEquals("pencil_on_paper", FocusSoundCatalog.search("карандаш", "ru").single().id)
        assertEquals("pencil_on_paper", FocusSoundCatalog.search("pencil", "en").single().id)
    }
}
