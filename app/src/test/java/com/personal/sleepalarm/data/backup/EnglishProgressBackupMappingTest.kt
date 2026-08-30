package com.personal.sleepalarm.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnglishProgressBackupMappingTest {
    @Test
    fun currentBackupMapsByStableHeadwordInsteadOfOldPosition() {
        assertEquals(
            742,
            resolveEnglishProgressWordId(
                savedWord = "garden",
                legacyWordId = 14,
                currentIdsByWord = mapOf("garden" to 742)
            )
        )
    }

    @Test
    fun removedHeadwordIsDroppedRatherThanAppliedToAnotherWord() {
        assertNull(
            resolveEnglishProgressWordId(
                savedWord = "removed",
                legacyWordId = 14,
                currentIdsByWord = mapOf("garden" to 14)
            )
        )
    }

    @Test
    fun legacyBackupKeepsItsValidatedPosition() {
        assertEquals(
            14,
            resolveEnglishProgressWordId(
                savedWord = null,
                legacyWordId = 14,
                currentIdsByWord = emptyMap()
            )
        )
        assertNull(resolveEnglishProgressWordId(null, 10_001, emptyMap()))
    }
}
