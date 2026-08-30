package com.personal.sleepalarm.domain.english

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishSpacedRepetitionSchedulerTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `new good answer returns tomorrow`() {
        val result = EnglishSpacedRepetitionScheduler.schedule(
            previous = null,
            grade = EnglishReviewGrade.GOOD,
            nowMillis = now
        )

        assertEquals(24L * 60L, result.intervalMinutes)
        assertEquals(now + 24L * 60L * 60_000L, result.dueAtMillis)
        assertEquals(1, result.repetitions)
        assertEquals(0, result.lapses)
    }

    @Test
    fun `again resets repetitions and schedules short relearning`() {
        val result = EnglishSpacedRepetitionScheduler.schedule(
            previous = EnglishReviewState(
                intervalMinutes = 14L * 24L * 60L,
                easePermille = 2_400,
                repetitions = 5,
                lapses = 1
            ),
            grade = EnglishReviewGrade.AGAIN,
            nowMillis = now
        )

        assertEquals(10L, result.intervalMinutes)
        assertEquals(0, result.repetitions)
        assertEquals(2, result.lapses)
        assertEquals(2_200, result.easePermille)
    }

    @Test
    fun `easy grows interval more than good`() {
        val state = EnglishReviewState(
            intervalMinutes = 3L * 24L * 60L,
            easePermille = 2_500,
            repetitions = 2
        )
        val good = EnglishSpacedRepetitionScheduler.schedule(state, EnglishReviewGrade.GOOD, now)
        val easy = EnglishSpacedRepetitionScheduler.schedule(state, EnglishReviewGrade.EASY, now)

        assertTrue(easy.intervalMinutes > good.intervalMinutes)
        assertTrue(easy.easePermille > good.easePermille)
    }

    @Test
    fun `ease never drops below safety floor`() {
        var state = EnglishReviewState(easePermille = EnglishSpacedRepetitionScheduler.MIN_EASE_PERMILLE)
        repeat(20) {
            val result = EnglishSpacedRepetitionScheduler.schedule(state, EnglishReviewGrade.AGAIN, now)
            state = EnglishReviewState(
                intervalMinutes = result.intervalMinutes,
                easePermille = result.easePermille,
                repetitions = result.repetitions,
                lapses = result.lapses
            )
        }
        assertEquals(EnglishSpacedRepetitionScheduler.MIN_EASE_PERMILLE, state.easePermille)
    }
}
