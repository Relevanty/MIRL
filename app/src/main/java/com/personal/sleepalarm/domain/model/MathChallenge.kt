package com.personal.sleepalarm.domain.model

/**
 * Математическая задача для экрана будильника.
 *
 * question — текст вида "12 + 17".
 * answer — правильный ответ.
 */
data class MathChallenge(
    val question: String,
    val answer: Int,
    val difficulty: MathDifficulty
)