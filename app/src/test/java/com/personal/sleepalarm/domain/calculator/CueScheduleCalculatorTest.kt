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

class CueScheduleCalculatorTest {

    private val zone = ZoneOffset.UTC

    private fun zdt(
        date: LocalDate,
        time: LocalTime
    ): ZonedDateTime {
        return LocalDateTime.of(date, time).atZone(zone)
    }

    @Test
    fun `cues stop one cycle before wake`() {
        // sleepStart = 23:15
        // wake = 06:45
        // firstCueDelay = 60
        // cueInterval = 30
        // cycleLength = 90
        //
        // finalCycleStart = 05:15
        // cues должны быть строго раньше 05:15.
        val sleepStart = zdt(
            LocalDate.of(2026, 8, 2),
            LocalTime.of(23, 15)
        )

        val wake = zdt(
            LocalDate.of(2026, 8, 3),
            LocalTime.of(6, 45)
        )

        val window = SleepWindow(
            sleepStart = sleepStart,
            wake = wake
        )

        val schedule = CueScheduleCalculator.calculate(
            window = window,
            firstCueDelayMinutes = 60,
            cueIntervalMinutes = 30,
            cycleLengthMinutes = 90,
            stopCuesOneCycleBeforeWake = true
        )

        val expectedTimes = listOf(
            "00:15",
            "00:45",
            "01:15",
            "01:45",
            "02:15",
            "02:45",
            "03:15",
            "03:45",
            "04:15",
            "04:45"
        )

        val actualTimes = schedule.cues.map { cue ->
            cue.time.toLocalTime().toString()
        }

        assertEquals(expectedTimes, actualTimes)
        assertTrue(schedule.warnings.isEmpty())
        assertEquals(10, schedule.scheduledCount)
        assertEquals("04:45", schedule.lastCue?.time?.toLocalTime().toString())
    }

    @Test
    fun `cue exactly at final cycle start is skipped`() {
        // sleepStart = 23:15
        // wake = 06:45
        // cycleLength = 90
        // finalCycleStart = 05:15
        //
        // Подберём firstDelay и interval так,
        // чтобы один из cue попал ровно в 05:15.
        // first = 60, interval = 60:
        // 00:15, 01:15, 02:15, 03:15, 04:15, 05:15
        // 05:15 должен быть пропущен.
        val sleepStart = zdt(
            LocalDate.of(2026, 8, 2),
            LocalTime.of(23, 15)
        )

        val wake = zdt(
            LocalDate.of(2026, 8, 3),
            LocalTime.of(6, 45)
        )

        val window = SleepWindow(
            sleepStart = sleepStart,
            wake = wake
        )

        val schedule = CueScheduleCalculator.calculate(
            window = window,
            firstCueDelayMinutes = 60,
            cueIntervalMinutes = 60,
            cycleLengthMinutes = 90,
            stopCuesOneCycleBeforeWake = true
        )

        val actualTimes = schedule.cues.map { cue ->
            cue.time.toLocalTime().toString()
        }

        val expectedTimes = listOf(
            "00:15",
            "01:15",
            "02:15",
            "03:15",
            "04:15"
        )

        assertEquals(expectedTimes, actualTimes)
    }

    @Test
    fun `too short sleep produces no cues and warning when stopping one cycle before wake`() {
        // sleepStart = 05:00
        // wake = 06:30
        // cycleLength = 90
        // finalCycleStart = 05:00
        //
        // Окно сна равно одному циклу.
        // При stopCuesOneCycleBeforeWake места для cues нет.
        val sleepStart = zdt(
            LocalDate.of(2026, 8, 3),
            LocalTime.of(5, 0)
        )

        val wake = zdt(
            LocalDate.of(2026, 8, 3),
            LocalTime.of(6, 30)
        )

        val window = SleepWindow(
            sleepStart = sleepStart,
            wake = wake
        )

        val schedule = CueScheduleCalculator.calculate(
            window = window,
            firstCueDelayMinutes = 60,
            cueIntervalMinutes = 30,
            cycleLengthMinutes = 90,
            stopCuesOneCycleBeforeWake = true
        )

        assertTrue(schedule.cues.isEmpty())
        assertTrue(schedule.hasWarning(CueWarning.TOO_SHORT_FOR_CUES))
    }

    @Test
    fun `without one cycle protection short sleep may contain one cue`() {
        // sleepStart = 05:00
        // wake = 06:30
        // firstCueDelay = 60
        // cue = 06:00
        //
        // Если не защищать финальный цикл,
        // один cue помещается до wake.
        val sleepStart = zdt(
            LocalDate.of(2026, 8, 3),
            LocalTime.of(5, 0)
        )

        val wake = zdt(
            LocalDate.of(2026, 8, 3),
            LocalTime.of(6, 30)
        )

        val window = SleepWindow(
            sleepStart = sleepStart,
            wake = wake
        )

        val schedule = CueScheduleCalculator.calculate(
            window = window,
            firstCueDelayMinutes = 60,
            cueIntervalMinutes = 30,
            cycleLengthMinutes = 90,
            stopCuesOneCycleBeforeWake = false
        )

        assertEquals(1, schedule.scheduledCount)
        assertEquals(
            "06:00",
            schedule.firstCue?.time?.toLocalTime().toString()
        )
    }

    @Test
    fun `invalid window returns invalid warning`() {
        val sleepStart = zdt(
            LocalDate.of(2026, 8, 3),
            LocalTime.of(7, 0)
        )

        val wake = zdt(
            LocalDate.of(2026, 8, 3),
            LocalTime.of(6, 0)
        )

        val window = SleepWindow(
            sleepStart = sleepStart,
            wake = wake
        )

        val schedule = CueScheduleCalculator.calculate(
            window = window,
            firstCueDelayMinutes = 60,
            cueIntervalMinutes = 30,
            cycleLengthMinutes = 90,
            stopCuesOneCycleBeforeWake = true
        )

        assertTrue(schedule.cues.isEmpty())
        assertTrue(schedule.hasWarning(CueWarning.INVALID_WINDOW))
    }

    @Test
    fun `volume normalization keeps safe range`() {
        assertEquals(
            CueScheduleCalculator.MIN_CUE_VOLUME_PERCENT,
            CueScheduleCalculator.normalizeCueVolume(1)
        )

        assertEquals(
            CueScheduleCalculator.DEFAULT_CUE_VOLUME_PERCENT,
            CueScheduleCalculator.normalizeCueVolume(10)
        )

        assertEquals(
            CueScheduleCalculator.MAX_CUE_VOLUME_PERCENT,
            CueScheduleCalculator.normalizeCueVolume(125)
        )
    }

    @Test
    fun `invalid interval normalizes to default`() {
        assertEquals(
            CueScheduleCalculator.DEFAULT_CUE_INTERVAL_MINUTES,
            CueScheduleCalculator.normalizeCueInterval(17)
        )

        assertEquals(
            30,
            CueScheduleCalculator.normalizeCueInterval(30)
        )

        assertEquals(
            45,
            CueScheduleCalculator.normalizeCueInterval(45)
        )
    }
}
