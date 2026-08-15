package com.personal.sleepalarm.domain.calculator

import com.personal.sleepalarm.domain.model.CueOccurrence
import com.personal.sleepalarm.domain.model.CueSchedule
import com.personal.sleepalarm.domain.model.CueWarning
import com.personal.sleepalarm.domain.model.SleepWindow

/**
 * Калькулятор lucid-подсказок, привязанных к теоретическим REM-окнам (F7).
 *
 * Модель основана на усреднённой гипнограмме:
 * - цикл сна ≈ N1 → N2 → N3 → N2 → REM;
 * - в первых циклах глубокий сон длинный, REM короткий (~10 мин) и стоит в конце цикла;
 * - во второй половине ночи глубокий сон почти исчезает, REM длинный (до 30–40 мин);
 * - REM-доля цикла растёт с номером цикла.
 *
 * Сигналы ставятся ВНУТРИ REM-окна каждого цикла:
 * - для коротких REM (< 25 мин) — 1 сигнал на позиции remCueOffsetPercent%;
 * - для длинных REM (≥ 25 мин) — 2 сигнала на 35% и 70% окна.
 *
 * Сигналы НЕ ставятся в финальный цикл перед пробуждением
 * (если stopCuesOneCycleBeforeWake = true).
 *
 * Источники модели (справка в UI):
 * - S. LaBerge — MILD / WBTB;
 * - T. Stumbrys et al. (2013) «Induction of lucid dreams» — внешняя стимуляция во время REM;
 * - K. Konkoly et al. (2021, Current Biology) «Real-time dialogue between experimenters
 *   and dreamers during REM sleep» — cueing именно в REM;
 * - D. Aspy et al. — работы по MILD / оптимальному времени.
 *
 * Дефолтные числа — СТРОГО из REM_FRACTION_BY_CYCLE.
 */
object RemCueScheduleCalculator {

    /**
     * Доля цикла, занятая REM, по номеру цикла (1-based).
     * Индекс 0 = цикл 1, индекс 6 = цикл 7.
     * Если циклов больше 7 — берётся последнее значение (0.42).
     *
     * Основано на усреднённой гипнограмме взрослого человека:
     * - цикл 1: REM ≈ 10 мин из 90 ≈ 11%
     * - цикл 2: REM ≈ 15 мин из 90 ≈ 17%
     * - цикл 3: REM ≈ 20 мин из 90 ≈ 22%
     * - цикл 4: REM ≈ 25 мин из 90 ≈ 28%
     * - цикл 5: REM ≈ 30 мин из 90 ≈ 33%
     * - цикл 6: REM ≈ 34 мин из 90 ≈ 38%
     * - цикл 7: REM ≈ 38 мин из 90 ≈ 42%
     */
    val REM_FRACTION_BY_CYCLE: List<Double> = listOf(
        0.11,  // цикл 1
        0.17,  // цикл 2
        0.22,  // цикл 3
        0.28,  // цикл 4
        0.33,  // цикл 5
        0.38,  // цикл 6
        0.42   // цикл 7
    )

    /**
     * Порог «длинного» REM (в минутах).
     * Если REM-окно ≥ этого порога — ставим 2 сигнала вместо 1.
     */
    const val LONG_REM_THRESHOLD_MINUTES = 25L

    /**
     * Позиции двух сигналов внутри длинного REM-окна (в процентах от начала REM).
     */
    private const val LONG_REM_CUE_1_PERCENT = 35L
    private const val LONG_REM_CUE_2_PERCENT = 70L

    /**
     * Рассчитывает расписание подсказок, привязанных к REM-окнам.
     *
     * @param window окно сна (sleepStart → wake).
     * @param cycleLengthMinutes длина одного цикла в минутах.
     * @param cycles количество циклов.
     * @param remCueOffsetPercent позиция сигнала внутри короткого REM-окна (10..90).
     * @param stopCuesOneCycleBeforeWake если true — не ставить сигналы в финальный цикл.
     * @return CueSchedule с последовательными индексами 0..N.
     */
    fun calculate(
        window: SleepWindow,
        cycleLengthMinutes: Int,
        cycles: Int,
        remCueOffsetPercent: Int,
        stopCuesOneCycleBeforeWake: Boolean = true
    ): CueSchedule {
        val warnings = mutableSetOf<CueWarning>()

        // === Валидация окна ===
        if (!window.isValid) {
            return CueSchedule(
                cues = emptyList(),
                warnings = setOf(CueWarning.INVALID_WINDOW)
            )
        }

        // === Валидация параметров ===
        if (cycleLengthMinutes <= 0 || cycles <= 0) {
            return CueSchedule(
                cues = emptyList(),
                warnings = setOf(CueWarning.INVALID_SETTINGS)
            )
        }

        val safeOffset = remCueOffsetPercent.coerceIn(10, 90)
        if (remCueOffsetPercent != safeOffset) {
            warnings += CueWarning.INVALID_SETTINGS
        }

        // === Граница финального цикла ===
        val finalCycleStart = if (stopCuesOneCycleBeforeWake) {
            window.wake.minusMinutes(cycleLengthMinutes.toLong())
        } else {
            window.wake
        }

        val cues = mutableListOf<CueOccurrence>()
        var index = 0

        for (cycleNumber in 1..cycles) {
            // Начало и конец текущего цикла
            val cycleStart = window.sleepStart.plusMinutes(
                (cycleNumber - 1).toLong() * cycleLengthMinutes
            )
            val cycleEnd = cycleStart.plusMinutes(cycleLengthMinutes.toLong())

            // Доля REM для этого цикла (если циклов > 7 — берём последнюю)
            val fraction = REM_FRACTION_BY_CYCLE.getOrElse(cycleNumber - 1) {
                REM_FRACTION_BY_CYCLE.last()
            }

            // Длительность REM-окна в минутах
            val remDurationMinutes = (cycleLengthMinutes * fraction).toLong()

            if (remDurationMinutes <= 0) continue

            // REM-окно: [remStart, cycleEnd]
            val remStart = cycleEnd.minusMinutes(remDurationMinutes)

            // Пропускаем, если REM-окно целиком в финальном цикле
            if (!remStart.isBefore(finalCycleStart)) continue

            if (remDurationMinutes >= LONG_REM_THRESHOLD_MINUTES) {
                // Длинный REM → 2 сигнала (на 35% и 70% окна)
                val cue1Time = remStart.plusMinutes(
                    remDurationMinutes * LONG_REM_CUE_1_PERCENT / 100
                )
                val cue2Time = remStart.plusMinutes(
                    remDurationMinutes * LONG_REM_CUE_2_PERCENT / 100
                )

                if (cue1Time.isBefore(finalCycleStart)) {
                    cues += CueOccurrence(index = index++, time = cue1Time)
                }
                if (cue2Time.isBefore(finalCycleStart)) {
                    cues += CueOccurrence(index = index++, time = cue2Time)
                }
            } else {
                // Короткий REM → 1 сигнал на safeOffset%
                val cueTime = remStart.plusMinutes(
                    remDurationMinutes * safeOffset / 100
                )

                if (cueTime.isBefore(finalCycleStart)) {
                    cues += CueOccurrence(index = index++, time = cueTime)
                }
            }
        }

        // === Предупреждение, если сигналов не получилось ===
        if (cues.isEmpty()) {
            warnings += CueWarning.TOO_SHORT_FOR_CUES
        }

        return CueSchedule(
            cues = cues,
            warnings = warnings
        )
    }
}