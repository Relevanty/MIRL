package com.personal.sleepalarm.ui.english

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishAudioLifecycleTest {

    @Test
    fun generationGuard_rejectsCallbacksFromOlderAndInvalidatedSessions() {
        val guard = EnglishAudioGenerationGuard()

        val first = guard.next()
        assertTrue(guard.isCurrent(first))

        val second = guard.next()
        assertFalse(guard.isCurrent(first))
        assertTrue(guard.isCurrent(second))

        guard.invalidate()
        assertFalse(guard.isCurrent(second))
    }

    @Test
    fun permissionResult_requiresCurrentActivePronunciationSession() {
        assertTrue(
            isRecognitionPermissionResultCurrent(
                hasRequestToken = true,
                tokenIsCurrent = true,
                pronunciationActive = true
            )
        )
        assertFalse(isRecognitionPermissionResultCurrent(false, true, true))
        assertFalse(isRecognitionPermissionResultCurrent(true, false, true))
        assertFalse(isRecognitionPermissionResultCurrent(true, true, false))
    }

    @Test
    fun modeGrid_switchesToOneColumnForNarrowOrLargeTextLayouts() {
        assertEquals(2, englishModeGridColumns(maxWidthDp = 420f, fontScale = 1f))
        assertEquals(1, englishModeGridColumns(maxWidthDp = 389f, fontScale = 1f))
        assertEquals(1, englishModeGridColumns(maxWidthDp = 420f, fontScale = 1.3f))
    }

    @Test
    fun pronunciationOffersManualFallbackWhenRecognizerOrPermissionIsUnavailable() {
        assertTrue(shouldOfferPronunciationSelfReport(false, false, null))
        assertTrue(shouldOfferPronunciationSelfReport(true, true, null))
        assertTrue(
            shouldOfferPronunciationSelfReport(
                true,
                false,
                OnDeviceRecognitionError.PERMISSION
            )
        )
        assertFalse(shouldOfferPronunciationSelfReport(true, false, null))
    }
}
