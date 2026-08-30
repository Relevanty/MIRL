package com.personal.sleepalarm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedSoundImportTest {
    @Test
    fun slotName_isRestrictedToPrivateStoragePrefixCharacters() {
        assertEquals("daily_plan_1", ManagedSoundImport.sanitizeSlot("Daily plan/№1"))
        assertEquals("sound", ManagedSoundImport.sanitizeSlot("!!!"))
    }

    @Test
    fun displayName_keepsExtensionAndRemovesPathCharacters() {
        assertEquals(
            "Theme _ Aurora_.ogg",
            ManagedSoundImport.sanitizeDisplayName("Theme / Aurora?.ogg")
        )
    }

    @Test
    fun cleanupPolicy_keepsCommittedAndActivelyReferencedCopies() {
        val prefix = "cue_"

        assertFalse(
            ManagedSoundImport.shouldDeleteCopy(
                fileName = "cue_active.ogg",
                slotPrefix = prefix,
                keepFileName = "cue_new.ogg",
                protectedFileNames = setOf("cue_active.ogg")
            )
        )
        assertFalse(
            ManagedSoundImport.shouldDeleteCopy(
                fileName = "cue_new.ogg",
                slotPrefix = prefix,
                keepFileName = "cue_new.ogg",
                protectedFileNames = emptySet()
            )
        )
        assertTrue(
            ManagedSoundImport.shouldDeleteCopy(
                fileName = "cue_old.ogg",
                slotPrefix = prefix,
                keepFileName = "cue_new.ogg",
                protectedFileNames = setOf("cue_active.ogg")
            )
        )
    }
}
