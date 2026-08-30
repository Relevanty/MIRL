package com.personal.sleepalarm.domain.math

import com.personal.sleepalarm.domain.model.MathAnswerParser
import com.personal.sleepalarm.domain.model.MathDifficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathChallengeRunTest {

    @Test
    fun `challenge count is clamped to supported alarm bounds`() {
        assertEquals(1, MathChallengeRunEngine.start(MathDifficulty.EASY, -4, 1).challengeCount)
        assertEquals(10, MathChallengeRunEngine.start(MathDifficulty.EASY, 99, 1).challengeCount)
    }

    @Test
    fun `same seed produces the same structured sequence`() {
        val first = MathChallengeRunEngine.start(MathDifficulty.EXTREME, 10, 7123)
        val second = MathChallengeRunEngine.start(MathDifficulty.EXTREME, 10, 7123)

        assertEquals(first.challenges, second.challenges)
        first.challenges.zipWithNext().forEach { (left, right) ->
            assertNotEquals(left.question, right.question)
        }
    }

    @Test
    fun `canonical answers complete every supported difficulty`() {
        MathDifficulty.entries.forEachIndexed { difficultyIndex, difficulty ->
            var run = MathChallengeRunEngine.start(difficulty, 6, 900 + difficultyIndex)
            repeat(run.challengeCount) { index ->
                val canonical = MathAnswerParser.canonical(run.currentChallenge.answerSpec)
                run = MathChallengeRunEngine.updateInput(run, canonical)
                val update = MathChallengeRunEngine.check(run)
                if (index == run.challengeCount - 1) {
                    assertEquals(MathChallengeRunEffect.Completed, update.effect)
                    assertTrue(update.state.isComplete)
                    run = update.state
                } else {
                    assertEquals(MathChallengeRunEffect.Advance, update.effect)
                    assertFalse(update.state.isComplete)
                    assertTrue(update.state.isTransitioning)
                    run = MathChallengeRunEngine.advance(update.state)
                }
            }
            assertEquals(run.challengeCount, run.completedCount)
        }
    }

    @Test
    fun `intermediate correct answer advances but does not complete run`() {
        var run = MathChallengeRunEngine.start(MathDifficulty.MEDIUM, 3, 42)
        run = MathChallengeRunEngine.updateInput(
            run,
            MathAnswerParser.canonical(run.currentChallenge.answerSpec)
        )

        val solved = MathChallengeRunEngine.check(run)

        assertEquals(MathChallengeRunEffect.Advance, solved.effect)
        assertFalse(solved.state.isComplete)
        assertEquals(1, solved.state.completedCount)
        assertEquals(0, solved.state.currentIndex)

        val advanced = MathChallengeRunEngine.advance(solved.state)
        assertEquals(1, advanced.currentIndex)
        assertEquals("", advanced.userInput)
        assertFalse(advanced.isTransitioning)
    }

    @Test
    fun `single challenge preserves legacy final gate`() {
        var run = MathChallengeRunEngine.start(MathDifficulty.EASY, 1, 7)
        run = MathChallengeRunEngine.updateInput(
            run,
            MathAnswerParser.canonical(run.currentChallenge.answerSpec)
        )

        val update = MathChallengeRunEngine.check(run)

        assertEquals(MathChallengeRunEffect.Completed, update.effect)
        assertTrue(update.state.isComplete)
        assertEquals(1, update.state.completedCount)
        assertFalse(update.state.isTransitioning)
    }

    @Test
    fun `wrong and invalid inputs never advance and double submit is ignored`() {
        var run = MathChallengeRunEngine.start(MathDifficulty.EASY, 2, 81)

        val empty = MathChallengeRunEngine.check(run)
        assertTrue(empty.effect is MathChallengeRunEffect.Invalid)
        assertEquals(0, empty.state.currentIndex)

        run = MathChallengeRunEngine.updateInput(empty.state, "999999")
        val wrong = MathChallengeRunEngine.check(run)
        assertEquals(MathChallengeRunEffect.Incorrect, wrong.effect)
        assertEquals(0, wrong.state.currentIndex)
        assertEquals(1, wrong.state.totalWrongAttempts)

        run = MathChallengeRunEngine.updateInput(
            wrong.state,
            MathAnswerParser.canonical(wrong.state.currentChallenge.answerSpec)
        )
        val correct = MathChallengeRunEngine.check(run)
        val duplicate = MathChallengeRunEngine.check(correct.state)
        assertEquals(MathChallengeRunEffect.Advance, correct.effect)
        assertEquals(MathChallengeRunEffect.Ignored, duplicate.effect)
        assertEquals(correct.state, duplicate.state)
    }

    @Test
    fun `saved progress rebuilds identical challenge without storing parcelables`() {
        var run = MathChallengeRunEngine.start(MathDifficulty.EXPERT, 4, 333)
        run = MathChallengeRunEngine.updateInput(
            run,
            MathAnswerParser.canonical(run.currentChallenge.answerSpec)
        )
        run = MathChallengeRunEngine.advance(MathChallengeRunEngine.check(run).state)

        val restored = MathChallengeRunEngine.restore(
            difficulty = run.difficulty,
            challengeCount = run.challengeCount,
            seed = run.seed,
            currentIndex = run.currentIndex,
            completedCount = run.completedCount,
            userInput = run.userInput,
            wrongAttempts = run.wrongAttempts,
            totalWrongAttempts = run.totalWrongAttempts,
            totalAttempts = run.totalAttempts,
            isTransitioning = run.isTransitioning,
            isComplete = run.isComplete
        )

        assertEquals(run.challenges, restored.challenges)
        assertEquals(run.currentChallenge, restored.currentChallenge)
        assertEquals(run.completedCount, restored.completedCount)
    }
}
