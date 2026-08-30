package com.personal.sleepalarm.ui.english

import com.personal.sleepalarm.domain.english.EnglishReviewGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnglishVocabularySwipeTest {
    @Test
    fun `swipe cannot grade a hidden answer`() {
        assertNull(englishSwipeGrade(offsetPx = 500f, thresholdPx = 100f, revealed = false))
        assertNull(englishSwipeGrade(offsetPx = -500f, thresholdPx = 100f, revealed = false))
    }

    @Test
    fun `short movement returns the card to center`() {
        assertNull(englishSwipeGrade(offsetPx = 99f, thresholdPx = 100f, revealed = true))
        assertNull(englishSwipeGrade(offsetPx = -99f, thresholdPx = 100f, revealed = true))
    }

    @Test
    fun `left means again and right means good at threshold`() {
        assertEquals(
            EnglishReviewGrade.AGAIN,
            englishSwipeGrade(offsetPx = -100f, thresholdPx = 100f, revealed = true)
        )
        assertEquals(
            EnglishReviewGrade.GOOD,
            englishSwipeGrade(offsetPx = 100f, thresholdPx = 100f, revealed = true)
        )
    }

    @Test
    fun `invalid threshold never commits a gesture`() {
        assertNull(englishSwipeGrade(offsetPx = 1_000f, thresholdPx = 0f, revealed = true))
    }
}
