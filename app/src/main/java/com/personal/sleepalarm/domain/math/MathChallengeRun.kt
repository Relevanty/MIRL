package com.personal.sleepalarm.domain.math

import com.personal.sleepalarm.domain.model.MathAnswerParseError
import com.personal.sleepalarm.domain.model.MathAnswerParser
import com.personal.sleepalarm.domain.model.MathAnswerValidation
import com.personal.sleepalarm.domain.model.MathChallenge
import com.personal.sleepalarm.domain.model.MathDifficulty
import com.personal.sleepalarm.util.MathChallengeGenerator
import kotlin.random.Random

const val MIN_MATH_CHALLENGE_COUNT = 1
const val MAX_MATH_CHALLENGE_COUNT = 10

/**
 * Pure state of one deterministic maths run. The same state machine is used by the alarm and by
 * free practice, so accepted answer formats and progression rules cannot drift apart.
 */
data class MathChallengeRunState(
    val difficulty: MathDifficulty,
    val challengeCount: Int,
    val seed: Int,
    val challenges: List<MathChallenge>,
    val currentIndex: Int = 0,
    val completedCount: Int = 0,
    val userInput: String = "",
    val wrongAttempts: Int = 0,
    val totalWrongAttempts: Int = 0,
    val totalAttempts: Int = 0,
    val isTransitioning: Boolean = false,
    val isComplete: Boolean = false
) {
    val currentChallenge: MathChallenge
        get() = challenges[currentIndex]

    val currentNumber: Int
        get() = currentIndex + 1
}

sealed interface MathChallengeRunEffect {
    data class Invalid(val reason: MathAnswerParseError) : MathChallengeRunEffect
    data object Incorrect : MathChallengeRunEffect
    data object Advance : MathChallengeRunEffect
    data object Completed : MathChallengeRunEffect
    data object Ignored : MathChallengeRunEffect
}

data class MathChallengeRunUpdate(
    val state: MathChallengeRunState,
    val effect: MathChallengeRunEffect
)

object MathChallengeRunEngine {

    fun start(
        difficulty: MathDifficulty,
        challengeCount: Int,
        seed: Int = Random.nextInt()
    ): MathChallengeRunState {
        val count = challengeCount.coerceIn(
            MIN_MATH_CHALLENGE_COUNT,
            MAX_MATH_CHALLENGE_COUNT
        )
        return MathChallengeRunState(
            difficulty = difficulty,
            challengeCount = count,
            seed = seed,
            challenges = generateSequence(difficulty, count, seed)
        )
    }

    /** Rebuilds challenges from [seed], keeping only bounded, internally consistent progress. */
    fun restore(
        difficulty: MathDifficulty,
        challengeCount: Int,
        seed: Int,
        currentIndex: Int,
        completedCount: Int,
        userInput: String,
        wrongAttempts: Int,
        totalWrongAttempts: Int,
        totalAttempts: Int,
        isTransitioning: Boolean,
        isComplete: Boolean
    ): MathChallengeRunState {
        val fresh = start(difficulty, challengeCount, seed)
        val complete = isComplete || completedCount >= fresh.challengeCount
        val safeIndex = if (complete) {
            fresh.challengeCount - 1
        } else {
            currentIndex.coerceIn(0, fresh.challengeCount - 1)
        }
        val safeCompleted = if (complete) {
            fresh.challengeCount
        } else {
            completedCount.coerceIn(0, safeIndex + 1)
        }
        return fresh.copy(
            currentIndex = safeIndex,
            completedCount = safeCompleted,
            userInput = MathAnswerParser.sanitizeInput(userInput),
            wrongAttempts = wrongAttempts.coerceAtLeast(0),
            totalWrongAttempts = totalWrongAttempts.coerceAtLeast(0),
            totalAttempts = totalAttempts.coerceAtLeast(0),
            isTransitioning = isTransitioning && !complete && safeIndex < fresh.challengeCount - 1,
            isComplete = complete
        )
    }

    fun updateInput(state: MathChallengeRunState, text: String): MathChallengeRunState {
        if (state.isComplete || state.isTransitioning) return state
        return state.copy(userInput = MathAnswerParser.sanitizeInput(text))
    }

    fun check(state: MathChallengeRunState): MathChallengeRunUpdate {
        if (state.isComplete || state.isTransitioning) {
            return MathChallengeRunUpdate(state, MathChallengeRunEffect.Ignored)
        }

        return when (
            val validation = MathAnswerParser.validate(
                state.currentChallenge.answerSpec,
                state.userInput
            )
        ) {
            is MathAnswerValidation.Invalid -> MathChallengeRunUpdate(
                state = state.copy(totalAttempts = state.totalAttempts + 1),
                effect = MathChallengeRunEffect.Invalid(validation.reason)
            )

            is MathAnswerValidation.Incorrect -> MathChallengeRunUpdate(
                state = state.copy(
                    userInput = validation.canonicalInput,
                    wrongAttempts = state.wrongAttempts + 1,
                    totalWrongAttempts = state.totalWrongAttempts + 1,
                    totalAttempts = state.totalAttempts + 1
                ),
                effect = MathChallengeRunEffect.Incorrect
            )

            is MathAnswerValidation.Correct -> {
                val completed = state.currentIndex + 1
                val isFinal = completed >= state.challengeCount
                MathChallengeRunUpdate(
                    state = state.copy(
                        userInput = validation.canonicalInput,
                        completedCount = completed,
                        totalAttempts = state.totalAttempts + 1,
                        isTransitioning = !isFinal,
                        isComplete = isFinal
                    ),
                    effect = if (isFinal) {
                        MathChallengeRunEffect.Completed
                    } else {
                        MathChallengeRunEffect.Advance
                    }
                )
            }
        }
    }

    fun advance(state: MathChallengeRunState): MathChallengeRunState {
        if (!state.isTransitioning || state.isComplete) return state
        return state.copy(
            currentIndex = (state.currentIndex + 1).coerceAtMost(state.challengeCount - 1),
            userInput = "",
            wrongAttempts = 0,
            isTransitioning = false
        )
    }

    private fun generateSequence(
        difficulty: MathDifficulty,
        count: Int,
        seed: Int
    ): List<MathChallenge> {
        val random = Random(seed)
        val result = ArrayList<MathChallenge>(count)

        repeat(count) {
            val previous = result.lastOrNull()
            var candidate = MathChallengeGenerator.generate(difficulty, random)
            var retry = 0
            while (previous != null && retry < MAX_GENERATION_RETRIES) {
                val exactRepeat = candidate.question == previous.question
                val sameAdvancedKind = difficulty.isAdvanced() && candidate.kind == previous.kind
                if (!exactRepeat && !sameAdvancedKind) break
                candidate = MathChallengeGenerator.generate(difficulty, random)
                retry += 1
            }
            result += candidate
        }

        return result
    }

    private fun MathDifficulty.isAdvanced(): Boolean =
        this == MathDifficulty.EXPERT || this == MathDifficulty.EXTREME

    private const val MAX_GENERATION_RETRIES = 8
}
