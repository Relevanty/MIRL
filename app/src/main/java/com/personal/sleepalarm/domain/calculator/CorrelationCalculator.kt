package com.personal.sleepalarm.domain.calculator

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Локальный калькулятор корреляций (Пирсон).
 *
 * Минимум [MIN_POINTS] пар для расчёта — иначе null («недостаточно данных»).
 * Без внешних математических библиотек.
 */
object CorrelationCalculator {

    const val MIN_POINTS = 5

    /** Сила связи (по модулю r). */
    enum class Strength { NONE, WEAK, MODERATE, STRONG, VERY_STRONG }

    /**
     * Коэффициент корреляции Пирсона по списку пар (x, y).
     * Возвращает null, если пар меньше [MIN_POINTS] или знаменатель = 0.
     */
    fun pearson(pairs: List<Pair<Double, Double>>): Double? {
        if (pairs.size < MIN_POINTS) return null

        val n = pairs.size
        val xs = pairs.map { it.first }
        val ys = pairs.map { it.second }

        val mx = xs.sum() / n
        val my = ys.sum() / n

        var num = 0.0
        var dx = 0.0
        var dy = 0.0
        for (i in 0 until n) {
            val a = xs[i] - mx
            val b = ys[i] - my
            num += a * b
            dx += a * a
            dy += b * b
        }

        val den = sqrt(dx * dy)
        if (den == 0.0) return null

        return (num / den).coerceIn(-1.0, 1.0)
    }

    /** Категоризация силы связи по модулю r. */
    fun strength(r: Double): Strength = when {
        abs(r) < 0.2 -> Strength.NONE
        abs(r) < 0.4 -> Strength.WEAK
        abs(r) < 0.6 -> Strength.MODERATE
        abs(r) < 0.8 -> Strength.STRONG
        else -> Strength.VERY_STRONG
    }
}