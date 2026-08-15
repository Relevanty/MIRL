package com.personal.sleepalarm.domain.model

import java.time.Duration
import java.time.ZonedDateTime

/**
 * Предупреждения, которые UI может показать пользователю.
 */
enum class SleepPlanWarning {
    TOO_FEW_CYCLES,
    TOO_MANY_CYCLES,
    BED_TIME_IN_PAST,
    WAKE_TIME_IN_PAST,
    TOTAL_SLEEP_TOO_SHORT,
    TOTAL_SLEEP_TOO_LONG
}

/**
 * Результат расчёта сна.
 *
 * bedTime — время, когда пользователь ложится в кровать.
 * estimatedSleepStart — предполагаемое время фактического засыпания.
 * estimatedWakeTime — расчётное время пробуждения между циклами.
 */
data class SleepPlan(
    val bedTime: ZonedDateTime,
    val estimatedSleepStart: ZonedDateTime,
    val estimatedWake: ZonedDateTime,
    val cycleLengthMinutes: Int,
    val cycles: Int,
    val onsetLatencyMinutes: Int,
    val totalInBed: Duration,
    val totalSleep: Duration,
    val crossesMidnight: Boolean,
    // === ДОБАВЛЕНО (новая логика) ===

    /** Ориентир «к какому времени нужно проснуться», который использовался в расчёте. */
    val preferredWake: ZonedDateTime? = null,

    /** Время пробуждения было ограничено preferredWake (не все циклы помещаются). */
    val isCutByPreferredWake: Boolean = false,

    /** Ни один полный цикл не поместился до preferredWake. */
    val cyclesDidNotFit: Boolean = false

) {
    /**
     * Чистое время сна в минутах.
     */
    val totalSleepMinutes: Long
        get() = totalSleep.toMinutes()

    /**
     * Общее время в постели в минутах.
     */
    val totalInBedMinutes: Long
        get() = totalInBed.toMinutes()

    /**
     * Начало последнего цикла сна.
     *
     * Например:
     * wake = 06:45
     * cycleLength = 90
     * finalCycleStart = 05:15
     */
    fun finalCycleStart(): ZonedDateTime {
        return estimatedWake.minusMinutes(cycleLengthMinutes.toLong())
    }

    /**
     * Возвращает номер текущего цикла для указанного момента времени.
     *
     * Нумерация циклов начинается с 1.
     *
     * Если время находится до засыпания или после пробуждения, возвращает null.
     */
    fun currentCycleAt(time: ZonedDateTime): Int? {
        if (cycleLengthMinutes <= 0) return null
        if (time.isBefore(estimatedSleepStart)) return null
        if (time.isAfter(estimatedWake)) return null

        val minutesFromSleepStart = Duration.between(estimatedSleepStart, time).toMinutes()

        // Если прошло 0 минут — это первый цикл.
        // Если прошло ровно totalSleepMinutes, время пробуждения, считаем последний цикл.
        val cycleIndexZeroBased = minutesFromSleepStart / cycleLengthMinutes
        val cycle = cycleIndexZeroBased.toInt() + 1

        return cycle.coerceIn(1, cycles)
    }

    /**
     * Создает SleepWindow для расчёта lucid-подсказок.
     */
    fun toSleepWindow(): SleepWindow {
        return SleepWindow(
            sleepStart = estimatedSleepStart,
            wake = estimatedWake
        )
    }
}