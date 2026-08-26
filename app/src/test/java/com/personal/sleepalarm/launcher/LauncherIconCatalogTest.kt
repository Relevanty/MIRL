package com.personal.sleepalarm.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconCatalogTest {

    @Test
    fun catalog_hasOneHundredDistinctIcons() {
        assertEquals(100, LauncherIconCatalog.all.size)
        assertEquals(100, LauncherIconCatalog.all.map { it.id }.toSet().size)
        assertEquals(100, LauncherIconCatalog.all.map { it.aliasClassName }.toSet().size)
        assertEquals(1, LauncherIconCatalog.all.count { it.enabledByDefault })
        assertEquals(LauncherIconCatalog.DEFAULT_ID, LauncherIconCatalog.all.single { it.enabledByDefault }.id)
    }

    @Test
    fun everyPickerFilterHasContent() {
        LauncherIconFilter.entries
            .filterNot { it == LauncherIconFilter.ALL }
            .forEach { filter ->
                assertTrue(filter.name, LauncherIconCatalog.all.any { filter in it.filters })
            }
    }

    @Test
    fun unknownOrRemovedIconFallsBackToStandard() {
        assertEquals(LauncherIconCatalog.DEFAULT_ID, LauncherIconCatalog.byId("removed_icon").id)
        assertEquals(LauncherIconCatalog.DEFAULT_ID, LauncherIconCatalog.byId(null).id)
    }

    @Test
    fun automaticMatchingUsesThemeFamilies() {
        assertEquals("amoled", LauncherIconCatalog.forTheme("amoled", true).id)
        assertEquals("matrix_terminal", LauncherIconCatalog.forTheme("hacker", true).id)
        assertEquals("dieselpunk", LauncherIconCatalog.forTheme("factory", true).id)
        assertEquals("forest", LauncherIconCatalog.forTheme("pine_night", true).id)
        assertEquals("nebula", LauncherIconCatalog.forTheme("deep_space", true).id)
        assertEquals("paper_retro", LauncherIconCatalog.forTheme("parchment", false).id)
        assertEquals("pixel_cozy", LauncherIconCatalog.forTheme("pixel_night", true).id)
        assertEquals("holographic_glass", LauncherIconCatalog.forTheme("iridescent", false).id)
        assertEquals("ice_engraved", LauncherIconCatalog.forTheme("glacier", false).id)
        assertNotEquals(
            LauncherIconCatalog.forTheme("unknown_dark", true).id,
            LauncherIconCatalog.forTheme("unknown_light", false).id
        )
    }
}
