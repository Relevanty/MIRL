package com.personal.sleepalarm.domain.english

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishStudyContentTest {
    @Test
    fun `set defaults to mixed and normalizes title`() {
        val draft = EnglishStudySetDraft(title = "  Travel   words ")
        val result = EnglishStudyContentValidator.validateSet(draft)

        assertTrue(result.isValid)
        assertEquals("Travel words", result.normalizedSetTitle)
        assertEquals(EnglishStudyDirection.MIXED, draft.defaultDirection)
    }

    @Test
    fun `card requires both language sides`() {
        val result = EnglishStudyContentValidator.validateCard(
            EnglishStudyCardDraft(term = "sleep", translation = "  ")
        )

        assertFalse(result.isValid)
        assertTrue(EnglishStudyContentError.EMPTY_TRANSLATION in result.errors)
    }

    @Test
    fun `prompt factory swaps front and accepted answers`() {
        val enToRu = EnglishStudyPromptFactory.create(
            term = "house",
            translation = "дом; здание",
            definition = "a building for living",
            example = "This is my house.",
            exampleTranslation = "Это мой дом.",
            notes = "",
            direction = EnglishStudyDirection.EN_TO_RU
        )
        val ruToEn = EnglishStudyPromptFactory.create(
            term = "house",
            translation = "дом; здание",
            definition = "a building for living",
            example = "This is my house.",
            exampleTranslation = "Это мой дом.",
            notes = "",
            direction = EnglishStudyDirection.RU_TO_EN
        )

        assertEquals("house", enToRu.prompt)
        assertEquals(listOf("дом", "здание"), enToRu.expectedAnswers)
        assertEquals("дом", ruToEn.prompt)
        assertEquals(listOf("house"), ruToEn.expectedAnswers)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mixed cannot be persisted as review direction`() {
        EnglishStudyDirection.MIXED.requireConcrete()
    }

    @Test
    fun `russian evaluator treats yo and ye as equivalent`() {
        val result = BilingualAnswerEvaluator.evaluate(
            actual = "ёлка",
            expectedAnswers = listOf("елка"),
            direction = EnglishStudyDirection.EN_TO_RU
        )

        assertTrue(result.isCorrect)
        assertFalse(result.isMinorTypo)
        assertEquals("елка", result.matchedExpected)
    }

    @Test
    fun `english evaluator accepts one adjacent transposition as minor typo`() {
        val result = BilingualAnswerEvaluator.evaluate(
            actual = "langauge",
            expectedAnswers = listOf("language"),
            direction = EnglishStudyDirection.RU_TO_EN
        )

        assertTrue(result.isCorrect)
        assertTrue(result.isMinorTypo)
        assertEquals("language", result.matchedExpected)
    }

    @Test
    fun `unknown stored direction falls back safely`() {
        assertEquals(
            EnglishStudyDirection.MIXED,
            EnglishStudyDirection.fromStorage("REMOVED_VALUE")
        )
        assertNull(
            EnglishStudyDirection.entries.firstOrNull { it.name == "REMOVED_VALUE" }
        )
    }
}
