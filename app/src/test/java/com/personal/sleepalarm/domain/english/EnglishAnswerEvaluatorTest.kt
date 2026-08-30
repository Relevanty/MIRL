package com.personal.sleepalarm.domain.english

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishAnswerEvaluatorTest {
    @Test
    fun `typed answer ignores case punctuation and curly apostrophe`() {
        val result = EnglishAnswerEvaluator.evaluateTyped("don't", "  DON’T! ")

        assertTrue(result.isCorrect)
        assertEquals(EnglishReviewGrade.GOOD, result.grade)
    }

    @Test
    fun `one typo is hard but accepted as close`() {
        val result = EnglishAnswerEvaluator.evaluateTyped("language", "langauge")

        assertTrue(result.isCorrect)
        assertEquals(EnglishReviewGrade.HARD, result.grade)
        assertEquals(EnglishAnswerFeedback.MINOR_TYPO, result.feedback)
    }

    @Test
    fun `one replacement in longer word is a minor typo`() {
        val result = EnglishAnswerEvaluator.evaluateTyped("garden", "gardan")

        assertTrue(result.isCorrect)
        assertEquals(EnglishReviewGrade.HARD, result.grade)
        assertEquals(EnglishAnswerFeedback.MINOR_TYPO, result.feedback)
    }

    @Test
    fun `speech accepts target among on-device hypotheses`() {
        val result = EnglishAnswerEvaluator.evaluateSpeech(
            expected = "world",
            hypotheses = listOf("word", "world")
        )

        assertTrue(result.isCorrect)
        assertEquals(EnglishReviewGrade.GOOD, result.grade)
    }

    @Test
    fun `speech rejects a different word`() {
        val result = EnglishAnswerEvaluator.evaluateSpeech("ship", listOf("sheep", "cheap"))

        assertFalse(result.isCorrect)
        assertEquals(EnglishReviewGrade.AGAIN, result.grade)
    }
}
