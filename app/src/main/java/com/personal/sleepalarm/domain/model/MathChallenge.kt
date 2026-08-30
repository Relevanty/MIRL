package com.personal.sleepalarm.domain.model

/**
 * Математическая задача для экрана будильника.
 *
 * [answer] remains the source-compatible legacy integer answer. New challenge families should
 * pass [answerSpec]; [visual] is optional and rendered with code-native Canvas primitives.
 */
data class MathChallenge(
    val question: String,
    val answer: Int,
    val difficulty: MathDifficulty,
    val kind: MathChallengeKind = MathChallengeKind.ARITHMETIC,
    val answerSpec: MathAnswerSpec = MathAnswerSpec.Integer(answer.toLong()),
    val visual: ChallengeVisual? = null
) {
    /** Constructor for a structured answer without forcing generators to invent an integer. */
    constructor(
        question: String,
        answerSpec: MathAnswerSpec,
        difficulty: MathDifficulty,
        kind: MathChallengeKind = MathChallengeKind.ARITHMETIC,
        visual: ChallengeVisual? = null
    ) : this(
        question = question,
        answer = (answerSpec as? MathAnswerSpec.Integer)?.expected?.toIntExactOrNull() ?: 0,
        difficulty = difficulty,
        kind = kind,
        answerSpec = answerSpec,
        visual = visual
    )
}

private fun Long.toIntExactOrNull(): Int? = takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

/** Тип задачи нужен для понятной подписи и контроля разнообразия генератора. */
enum class MathChallengeKind {
    ARITHMETIC,
    LINEAR,
    FRACTION,
    PROPORTION,
    SYSTEM,
    ABSOLUTE,
    QUADRATIC,
    RADICAL,
    POWER,
    LOGARITHM,
    RATIONAL,
    TRIGONOMETRY,
    FACTORIAL,
    BIQUADRATIC,
    EXPONENTIAL,
    POLYNOMIAL,
    INEQUALITY,
    PARAMETER,
    NUMBER_SET,
    FUNCTION,
    COORDINATE,
    GEOMETRY,
    NUMBER_THEORY,
    DIVISORS,
    COMBINATORICS,
    SEQUENCE,
    ANALYSIS,
    DIGIT
}
