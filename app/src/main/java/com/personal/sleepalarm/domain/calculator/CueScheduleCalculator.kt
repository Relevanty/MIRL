package com.personal.sleepalarm.domain.calculator

import com.personal.sleepalarm.domain.model.CueOccurrence
import com.personal.sleepalarm.domain.model.CueSchedule
import com.personal.sleepalarm.domain.model.CueScheduleMode
import com.personal.sleepalarm.domain.model.CueWarning
import com.personal.sleepalarm.domain.model.SleepWindow

/**
 * Калькулятор lucid-подсказок.
 *
 * ДОБАВЛЕНО: фабрика buildCueSchedule (F7) — выбирает калькулятор
 * по режиму (PERIODIC / REM_TARGETED).
 *
 * Существующий метод calculate(...) НЕ изменён.
 */
object CueScheduleCalculator {

    // === Существующие константы (НЕ менять) ===

    const val DEFAULT_FIRST_CUE_DELAY_MINUTES = 60
    const val MIN_FIRST_CUE_DELAY_MINUTES = 20
    const val MAX_FIRST_CUE_DELAY_MINUTES = 120

    const val DEFAULT_CUE_INTERVAL_MINUTES = 30
    val ALLOWED_CUE_INTERVALS: List<Int> = listOf(20, 30, 45, 60)

    const val DEFAULT_CUE_VOLUME_PERCENT = 10
    const val MIN_CUE_VOLUME_PERCENT = 5
    const val MAX_CUE_VOLUME_PERCENT = 100
    const val HARD_MAX_CUE_VOLUME_PERCENT = 100

    // === Существующий метод calculate (НЕ менять) ===

    /**
     * Периодическое расписание подсказок (классический режим).
     */
    fun calculate(
        window: SleepWindow,
        firstCueDelayMinutes: Int,
        cueIntervalMinutes: Int,
        cycleLengthMinutes: Int,
        stopCuesOneCycleBeforeWake: Boolean = true
    ): CueSchedule {
        val warnings = mutableSetOf<CueWarning>()

        if (!window.isValid) {
            return CueSchedule(
                cues = emptyList(),
                warnings = setOf(CueWarning.INVALID_WINDOW)
            )
        }

        if (cycleLengthMinutes <= 0) {
            return CueSchedule(
                cues = emptyList(),
                warnings = setOf(CueWarning.INVALID_SETTINGS)
            )
        }

        if (firstCueDelayMinutes !in MIN_FIRST_CUE_DELAY_MINUTES..MAX_FIRST_CUE_DELAY_MINUTES) {
            warnings += CueWarning.INVALID_SETTINGS
        }

        if (cueIntervalMinutes !in ALLOWED_CUE_INTERVALS) {
            warnings += CueWarning.INVALID_SETTINGS
        }

        if (cueIntervalMinutes in 1 until MIN_FIRST_CUE_DELAY_MINUTES) {
            warnings += CueWarning.CUES_TOO_FREQUENT
        }

        val safeFirstDelay = normalizeFirstCueDelay(firstCueDelayMinutes)
        val safeInterval = normalizeCueInterval(cueIntervalMinutes)

        val cutoff: java.time.ZonedDateTime = if (stopCuesOneCycleBeforeWake) {
            window.wake.minusMinutes(cycleLengthMinutes.toLong())
        } else {
            window.wake
        }

        if (!cutoff.isAfter(window.sleepStart)) {
            warnings += CueWarning.TOO_SHORT_FOR_CUES
            return CueSchedule(
                cues = emptyList(),
                warnings = warnings
            )
        }

        val cues = mutableListOf<CueOccurrence>()
        var nextCueTime = window.sleepStart.plusMinutes(safeFirstDelay.toLong())
        var index = 0

        while (nextCueTime.isBefore(cutoff)) {
            cues += CueOccurrence(
                index = index,
                time = nextCueTime
            )
            index++
            nextCueTime = nextCueTime.plusMinutes(safeInterval.toLong())
        }

        if (cues.isEmpty()) {
            warnings += CueWarning.TOO_SHORT_FOR_CUES
        }

        return CueSchedule(
            cues = cues,
            warnings = warnings
        )
    }

    // === ДОБАВЛЕНО: F7 — фабрика выбора калькулятора ===

    /**
     * Строит расписание подсказок по выбранному режиму.
     *
     * PERIODIC → классический calculate(...).
     * REM_TARGETED → RemCueScheduleCalculator.calculate(...).
     *
     * Метод принимает отдельные параметры (НЕ entity),
     * чтобы домен не зависел от data-слоя.
     *
     * @param window окно сна (sleepStart → wake).
     * @param cueScheduleMode режим расписания.
     * @param cycleLengthMinutes длина цикла.
     * @param cycles количество циклов.
     * @param firstCueDelayMinutes задержка первого cue (только PERIODIC).
     * @param cueIntervalMinutes интервал cue (только PERIODIC).
     * @param remCueOffsetPercent позиция cue в REM-окне (только REM_TARGETED).
     * @param stopCuesOneCycleBeforeWake не ставить cue в финальный цикл.
     */
    fun buildCueSchedule(
        window: SleepWindow,
        cueScheduleMode: CueScheduleMode,
        cycleLengthMinutes: Int,
        cycles: Int,
        firstCueDelayMinutes: Int,
        cueIntervalMinutes: Int,
        remCueOffsetPercent: Int,
        stopCuesOneCycleBeforeWake: Boolean = true
    ): CueSchedule {
        return when (cueScheduleMode) {
            CueScheduleMode.PERIODIC -> calculate(
                window = window,
                firstCueDelayMinutes = firstCueDelayMinutes,
                cueIntervalMinutes = cueIntervalMinutes,
                cycleLengthMinutes = cycleLengthMinutes,
                stopCuesOneCycleBeforeWake = stopCuesOneCycleBeforeWake
            )

            CueScheduleMode.REM_TARGETED -> RemCueScheduleCalculator.calculate(
                window = window,
                cycleLengthMinutes = cycleLengthMinutes,
                cycles = cycles,
                remCueOffsetPercent = remCueOffsetPercent,
                stopCuesOneCycleBeforeWake = stopCuesOneCycleBeforeWake
            )
        }
    }

    // === Существующие методы нормализации (НЕ менять) ===

    fun normalizeFirstCueDelay(value: Int): Int {
        return value.coerceIn(
            MIN_FIRST_CUE_DELAY_MINUTES,
            MAX_FIRST_CUE_DELAY_MINUTES
        )
    }

    fun normalizeCueInterval(value: Int): Int {
        return if (value in ALLOWED_CUE_INTERVALS) {
            value
        } else {
            DEFAULT_CUE_INTERVAL_MINUTES
        }
    }

    fun normalizeCueVolume(value: Int): Int {
        return value.coerceIn(
            MIN_CUE_VOLUME_PERCENT,
            MAX_CUE_VOLUME_PERCENT
        )
    }

    fun isVolumeAboveHardLimit(value: Int): Boolean {
        return value > HARD_MAX_CUE_VOLUME_PERCENT
    }
}