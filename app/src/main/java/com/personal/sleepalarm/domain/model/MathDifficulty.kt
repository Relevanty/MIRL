package com.personal.sleepalarm.domain.model

/**
 * Сложность математической задачи для выключения будильника.
 */
enum class MathDifficulty {
    EASY,
    MEDIUM,
    HARD,
    /** Linear equations, proportions, systems, roots and simple quadratics. */
    EXPERT,
    /** Advanced algebra, powers, logarithms and basic trigonometry. */
    EXTREME
}
