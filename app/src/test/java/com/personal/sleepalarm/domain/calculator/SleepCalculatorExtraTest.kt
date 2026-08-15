package com.personal.sleepalarm.domain.calculator

import com.personal.sleepalarm.domain.model.CueWarning
import com.personal.sleepalarm.domain.model.SleepWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Дополнительные тесты:
 * - корректность расчёта при переходе на летнее время (DST);
 * - защита от невалидных настроек lucid-подсказок.
 */
class SleepCalculatorExtraTest {

    /**
     * DST spring-forward для America/New_York:
     * 8 марта 2026 в 02:00 часы переводятся на 03:00.
     *
     * Если лечь в 00:30 и спать 3 цикла по 90 минут + 15 минут засыпание,
     * то фактических минут сна = 15 + 270 = 285 минут реального времени.
     * Из-за пропуска часа локальное время пробуждения "сдвигается" вперёд.
     */
    @Test
    fun `wake time respects DST spring forward`() {
        val zone = ZoneId.of("America/New_York")

        val bedTime = LocalDateTime.of(
            LocalDate.of(2026, 3, 8),
            LocalTime.of(0, 30)
        ).atZone(zone)

        val plan = SleepCalculator.calculateFromBedTime(
            bedTime = bedTime,
            onsetLatencyMinutes = 15,
            cycleLengthMinutes = 90,
            cycles = 3
        )

        // sleepStart = 00:45 (локальное)
        assertEquals(
            LocalTime.of(0, 45),
            plan.estimatedSleepStart.toLocalTime()
        )

        // 00:45 EST + 270 реальных минут = 06:15 EDT
        // (час между 02:00 и 03:00 "выпадает")
        assertEquals(
            LocalTime.of(6, 15),
            plan.estimatedWake.toLocalTime()
        )

        // При этом общая продолжительность сна в минутах остаётся 270.
        assertEquals(270L, plan.totalSleepMinutes)
    }

    /**
     * Обратный расчёт тоже должен учитывать DST:
     * желаемое время пробуждения фиксируется, bedTime считается назад
     * по реальному времени.
     */
    @Test
    fun `reverse calculation respects DST`() {
        val zone = ZoneId.of("America/New_York")

        val desiredWake = LocalDateTime.of(
            LocalDate.of(2026, 3, 8),
            LocalTime.of(7, 0)
        ).atZone(zone)

        val plan = SleepCalculator.calculateFromDesiredWake(
            desiredWake = desiredWake,
            onsetLatencyMinutes = 15,
            cycleLengthMinutes = 90,
            cycles = 3
        )

        // 07:00 EDT - 270 минут сна - 15 минут засыпание
        // = 07:00 - 285 реальных минут = 01:15 EDT (ночь уже после перевода)
        assertEquals(
            desiredWake,
            plan.estimatedWake
        )

        assertEquals(
            LocalTime.of(1, 15),
            plan.bedTime.toLocalTime()
        )
    }

    /**
     * Невалидный интервал подсказок:
     * - помечается как INVALID_SETTINGS;
     * - слишком частый интервал помечается CUES_TOO_FREQUENT;
     * - значение нормализуется к 30 минутам.
     */
    @Test
    fun `invalid cue interval is normalized and warned`() {
        val zone = ZoneId.of("UTC")

        val sleepStart = LocalDateTime.of(
            LocalDate.of(2026, 8, 2),
            LocalTime.of(23, 15)
        ).atZone(zone)

        val wake = LocalDateTime.of(
            LocalDate.of(2026, 8, 3),
            LocalTime.of(6, 45)
        ).atZone(zone)

        val window = SleepWindow(sleepStart = sleepStart, wake = wake)

        val schedule = CueScheduleCalculator.calculate(
            window = window,
            firstCueDelayMinutes = 60,
            cueIntervalMinutes = 15,
            cycleLengthMinutes = 90,
            stopCuesOneCycleBeforeWake = true
        )

        assertTrue(schedule.hasWarning(CueWarning.INVALID_SETTINGS))
        assertTrue(schedule.hasWarning(CueWarning.CUES_TOO_FREQUENT))

        // Интервал нормализован к 30 минутам:
        // 00:15, 00:45, 01:15, ... до 04:45
        val times = schedule.cues.map { it.time.toLocalTime().toString() }

        assertTrue("00:15" in times)
        assertTrue("00:45" in times)
        assertTrue("04:45" in times)
        assertTrue("05:15" !in times)
    }

    /**
     * Громкость выше жёсткого предела детектируется.
     */
    @Test
    fun `volume above hard limit is detected`() {
        assertTrue(CueScheduleCalculator.isVolumeAboveHardLimit(135))
        assertEquals(100, CueScheduleCalculator.normalizeCueVolume(135))
    }
}
