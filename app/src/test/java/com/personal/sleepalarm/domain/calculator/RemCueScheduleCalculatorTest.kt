package com.personal.sleepalarm.domain.calculator

import com.personal.sleepalarm.domain.model.CueWarning
import com.personal.sleepalarm.domain.model.SleepWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Тесты REM-привязанного калькулятора подсказок (F7).
 *
 * Стратегия: проверяем ИНВАРИАНТЫ модели (независимо пересчитывая REM-окна),
 * плюс несколько детерминированных точек для короткого и длинного REM,
 * плюс граничные случаи (короткий сон, невалидное окно, невалидные настройки).
 *
 * Целочисленная арифметика в helper'ах теста намеренно совпадает с продакшн-кодом,
 * чтобы contains-проверки по минутам не расходились из-за округления.
 */
class RemCueScheduleCalculatorTest {

    private val zone = ZoneOffset.UTC

    private fun zdt(date: LocalDate, time: LocalTime): ZonedDateTime =
        LocalDateTime.of(date, time).atZone(zone)

    /** Описание одного REM-окна, пересчитанное независимо от продакшна. */
    private data class RemWindow(
        val cycleNumber: Int,
        val cycleStart: ZonedDateTime,
        val cycleEnd: ZonedDateTime,
        val remStart: ZonedDateTime,
        val remDurationMinutes: Long
    )

    /**
     * Независимый пересчёт REM-окон по той же формуле, что в калькуляторе.
     * Используется только для проверки инвариантов в тестах.
     */
    private fun recomputeWindows(
        sleepStart: ZonedDateTime,
        cycleLength: Int,
        cycles: Int
    ): List<RemWindow> {
        val fractions = RemCueScheduleCalculator.REM_FRACTION_BY_CYCLE
        return (1..cycles).map { i ->
            val cycleStart = sleepStart.plusMinutes((i - 1).toLong() * cycleLength)
            val cycleEnd = cycleStart.plusMinutes(cycleLength.toLong())
            val fraction = fractions.getOrElse(i - 1) { fractions.last() }
            val remDuration = (cycleLength * fraction).toLong()
            val remStart = cycleEnd.minusMinutes(remDuration)
            RemWindow(i, cycleStart, cycleEnd, remStart, remDuration)
        }
    }

    // =================================================================
    // Инварианты: сигналы внутри REM-окон и не в финальном цикле
    // =================================================================

    @Test
    fun `every cue falls inside its REM window and before final cycle`() {
        val sleepStart = zdt(LocalDate.of(2026, 8, 2), LocalTime.of(23, 15))
        val cycleLength = 90
        val cycles = 5
        val wake = sleepStart.plusMinutes(cycleLength.toLong() * cycles)
        val finalCycleStart = wake.minusMinutes(cycleLength.toLong())

        val window = SleepWindow(sleepStart = sleepStart, wake = wake)
        val schedule = RemCueScheduleCalculator.calculate(
            window = window,
            cycleLengthMinutes = cycleLength,
            cycles = cycles,
            remCueOffsetPercent = 40,
            stopCuesOneCycleBeforeWake = true
        )

        assertTrue("валидный сон должен дать сигналы", schedule.cues.isNotEmpty())
        assertTrue(schedule.warnings.isEmpty())

        val remWindows = recomputeWindows(sleepStart, cycleLength, cycles)

        schedule.cues.forEach { cue ->
            // Инвариант 1: cue строго до финального цикла.
            assertTrue(
                "cue ${cue.time} должен быть раньше finalCycleStart $finalCycleStart",
                cue.time.isBefore(finalCycleStart)
            )

            // Инвариант 2: cue лежит внутри некоторого REM-окна [remStart, cycleEnd).
            val inside = remWindows.any { w ->
                !cue.time.isBefore(w.remStart) && cue.time.isBefore(w.cycleEnd)
            }
            assertTrue("cue ${cue.time} должен лежать внутри REM-окна", inside)

            // Инвариант 3: cue не раньше засыпания и не позже пробуждения.
            assertTrue(!cue.time.isBefore(sleepStart))
            assertTrue(cue.time.isBefore(wake))
        }

        // Инвариант 4: индексы последовательны 0..N-1.
        assertEquals(
            (0 until schedule.cues.size).toList(),
            schedule.cues.map { it.index }
        )
    }

    @Test
    fun `cues never land in the skipped final cycle window`() {
        val sleepStart = zdt(LocalDate.of(2026, 8, 2), LocalTime.of(23, 15))
        val cycleLength = 90
        val cycles = 5
        val wake = sleepStart.plusMinutes(cycleLength.toLong() * cycles)
        val finalCycleStart = wake.minusMinutes(cycleLength.toLong())

        val window = SleepWindow(sleepStart = sleepStart, wake = wake)
        val schedule = RemCueScheduleCalculator.calculate(
            window = window,
            cycleLengthMinutes = cycleLength,
            cycles = cycles,
            remCueOffsetPercent = 40,
            stopCuesOneCycleBeforeWake = true
        )

        // Финальный цикл = [05:15, 06:45]. Ни один cue не должен туда попасть.
        schedule.cues.forEach { cue ->
            assertTrue(
                "cue ${cue.time} не должен попадать в финальный цикл",
                cue.time.isBefore(finalCycleStart)
            )
        }
    }

    // =================================================================
    // Детерминированные точки: короткий REM (1 сигнал)
    // =================================================================

    @Test
    fun `short REM produces single cue at exact offset`() {
        // cycleLength=100, цикл 1: fraction .11 → remDuration=11 (<25 → 1 сигнал).
        // sleepStart=00:00 → cycleEnd=01:40, remStart=01:29.
        // offset=50 → cue = remStart + 11*50/100 = remStart + 5 = 01:34.
        val sleepStart = zdt(LocalDate.of(2026, 8, 3), LocalTime.of(0, 0))
        val cycleLength = 100
        val cycles = 3
        val wake = sleepStart.plusMinutes(cycleLength.toLong() * cycles)

        val window = SleepWindow(sleepStart = sleepStart, wake = wake)
        val schedule = RemCueScheduleCalculator.calculate(
            window = window,
            cycleLengthMinutes = cycleLength,
            cycles = cycles,
            remCueOffsetPercent = 50,
            stopCuesOneCycleBeforeWake = true
        )

        val times = schedule.cues.map { it.time.toLocalTime().toString() }
        assertTrue("ожидали 01:34 в $times", "01:34" in times)
    }

    // =================================================================
    // Детерминированные точки: длинный REM (2 сигнала)
    // =================================================================

    @Test
    fun `long REM produces two cues at 35 and 70 percent`() {
        // cycleLength=120, цикл 4: fraction .28 → remDuration=33 (>=25 → 2 сигнала).
        // cycleStart=06:00, cycleEnd=08:00, remStart=07:27.
        // cue1 = remStart + 33*35/100 = +11 = 07:38.
        // cue2 = remStart + 33*70/100 = +23 = 07:50.
        val sleepStart = zdt(LocalDate.of(2026, 8, 3), LocalTime.of(0, 0))
        val cycleLength = 120
        val cycles = 5
        val wake = sleepStart.plusMinutes(cycleLength.toLong() * cycles)

        val window = SleepWindow(sleepStart = sleepStart, wake = wake)
        val schedule = RemCueScheduleCalculator.calculate(
            window = window,
            cycleLengthMinutes = cycleLength,
            cycles = cycles,
            remCueOffsetPercent = 40, // для длинного REM offset не используется
            stopCuesOneCycleBeforeWake = true
        )

        val times = schedule.cues.map { it.time.toLocalTime().toString() }
        assertTrue("ожидали 07:38 в $times", "07:38" in times)
        assertTrue("ожидали 07:50 в $times", "07:50" in times)
    }

    // =================================================================
    // Рост числа сигналов при длинных REM
    // =================================================================

    @Test
    fun `longer cycles yield more cues because more REM windows are long`() {
        val sleepStart = zdt(LocalDate.of(2026, 8, 3), LocalTime.of(0, 0))
        val cycles = 5

        val wakeShort = sleepStart.plusMinutes(90L * cycles)
        val scheduleShort = RemCueScheduleCalculator.calculate(
            window = SleepWindow(sleepStart, wakeShort),
            cycleLengthMinutes = 90,
            cycles = cycles,
            remCueOffsetPercent = 40,
            stopCuesOneCycleBeforeWake = true
        )

        val wakeLong = sleepStart.plusMinutes(120L * cycles)
        val scheduleLong = RemCueScheduleCalculator.calculate(
            window = SleepWindow(sleepStart, wakeLong),
            cycleLengthMinutes = 120,
            cycles = cycles,
            remCueOffsetPercent = 40,
            stopCuesOneCycleBeforeWake = true
        )

        // При 120 мин/цикл больше REM-окон пересекают порог 25 мин →
        // по 2 сигнала вместо 1 → суммарно больше.
        assertTrue(
            "при длинных циклах сигналов не меньше: long=${scheduleLong.scheduledCount}, short=${scheduleShort.scheduledCount}",
            scheduleLong.scheduledCount > scheduleShort.scheduledCount
        )
    }

    // =================================================================
    // Граничные случаи
    // =================================================================

    @Test
    fun `too short sleep yields no cues and warning`() {
        // 1 цикл = 90 мин, stop=true → finalCycleStart == sleepStart,
        // единственное REM-окно целиком в «финальном цикле» → пропуск.
        val sleepStart = zdt(LocalDate.of(2026, 8, 3), LocalTime.of(5, 0))
        val wake = sleepStart.plusMinutes(90)

        val schedule = RemCueScheduleCalculator.calculate(
            window = SleepWindow(sleepStart, wake),
            cycleLengthMinutes = 90,
            cycles = 1,
            remCueOffsetPercent = 40,
            stopCuesOneCycleBeforeWake = true
        )

        assertTrue(schedule.cues.isEmpty())
        assertTrue(schedule.hasWarning(CueWarning.TOO_SHORT_FOR_CUES))
    }

    @Test
    fun `invalid window returns invalid warning`() {
        val sleepStart = zdt(LocalDate.of(2026, 8, 3), LocalTime.of(7, 0))
        val wake = zdt(LocalDate.of(2026, 8, 3), LocalTime.of(6, 0))

        val schedule = RemCueScheduleCalculator.calculate(
            window = SleepWindow(sleepStart, wake),
            cycleLengthMinutes = 90,
            cycles = 5,
            remCueOffsetPercent = 40,
            stopCuesOneCycleBeforeWake = true
        )

        assertTrue(schedule.cues.isEmpty())
        assertTrue(schedule.hasWarning(CueWarning.INVALID_WINDOW))
    }

    @Test
    fun `invalid settings return invalid warning`() {
        val sleepStart = zdt(LocalDate.of(2026, 8, 3), LocalTime.of(23, 0))
        val wake = sleepStart.plusMinutes(450)

        val schedule = RemCueScheduleCalculator.calculate(
            window = SleepWindow(sleepStart, wake),
            cycleLengthMinutes = 0, // невалидно
            cycles = 5,
            remCueOffsetPercent = 40,
            stopCuesOneCycleBeforeWake = true
        )

        assertTrue(schedule.cues.isEmpty())
        assertTrue(schedule.hasWarning(CueWarning.INVALID_SETTINGS))
    }

    @Test
    fun `out of range offset is normalized and warned`() {
        val sleepStart = zdt(LocalDate.of(2026, 8, 3), LocalTime.of(0, 0))
        val cycleLength = 100
        val cycles = 3
        val wake = sleepStart.plusMinutes(cycleLength.toLong() * cycles)

        val schedule = RemCueScheduleCalculator.calculate(
            window = SleepWindow(sleepStart, wake),
            cycleLengthMinutes = cycleLength,
            cycles = cycles,
            remCueOffsetPercent = 5, // вне 10..90 → нормализуется к 10
            stopCuesOneCycleBeforeWake = true
        )

        assertTrue(schedule.hasWarning(CueWarning.INVALID_SETTINGS))

        // При offset=10 короткий REM цикла 1 (remDuration=11):
        // cue = remStart + 11*10/100 = remStart + 1 = 01:29 + 1 = 01:30.
        val times = schedule.cues.map { it.time.toLocalTime().toString() }
        assertTrue("ожидали нормализованный cue 01:30 в $times", "01:30" in times)
    }
}