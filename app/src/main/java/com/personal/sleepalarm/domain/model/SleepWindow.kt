package com.personal.sleepalarm.domain.model

import java.time.Duration
import java.time.ZonedDateTime

/**
 * Окно сна между фактическим засыпанием и расчётным пробуждением.
 *
 * sleepStart — это не время "лёг в кровать", а время фактического засыпания,
 * то есть bedTime + sleep onset latency.
 */
data class SleepWindow(
    val sleepStart: ZonedDateTime,
    val wake: ZonedDateTime
) {
    /**
     * Общая продолжительность окна сна.
     */
    val duration: Duration
        get() = Duration.between(sleepStart, wake)

    /**
     * Окно валидно, если время засыпания не позже времени пробуждения.
     */
    val isValid: Boolean
        get() = !sleepStart.isAfter(wake)

    /**
     * Проверяет, находится ли момент времени внутри окна сна.
     */
    fun contains(time: ZonedDateTime): Boolean {
        return !time.isBefore(sleepStart) && !time.isAfter(wake)
    }
}