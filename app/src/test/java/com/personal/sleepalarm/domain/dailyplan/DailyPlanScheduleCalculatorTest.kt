package com.personal.sleepalarm.domain.dailyplan

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyPlanScheduleCalculatorTest {
    private val zone = ZoneId.of("Europe/Moscow")

    @Test
    fun `midnight fallback is always the next midnight`() {
        val atMidnight = ZonedDateTime.of(2026, 8, 29, 0, 0, 0, 0, zone)
        val cutoff = DailyPlanScheduleCalculator.nextFallbackCutoff(atMidnight, 0)

        assertEquals(atMidnight.plusDays(1), cutoff)
    }

    @Test
    fun `custom fallback already passed moves to next local day`() {
        val now = ZonedDateTime.of(2026, 8, 29, 20, 0, 0, 0, zone)
        val cutoff = DailyPlanScheduleCalculator.nextFallbackCutoff(now, 18 * 60 + 30)

        assertEquals(30, cutoff.minute)
        assertEquals(18, cutoff.hour)
        assertEquals(now.toLocalDate().plusDays(1), cutoff.toLocalDate())
    }

    @Test
    fun `live sleep window start replaces fallback`() {
        val now = ZonedDateTime.of(2026, 8, 29, 21, 0, 0, 0, zone)
        val result = DailyPlanScheduleCalculator.effectiveCutoff(
            now = now,
            fallbackCutoffMinutesOfDay = 0,
            sleepAutomation = DailyPlanSleepAutomationInput(
                enabled = true,
                windowStartMinutes = 22 * 60,
                windowEndMinutes = 2 * 60,
                skippedWindowStartEpochDay = null
            )
        )

        assertEquals(DailyPlanCutoffSource.SLEEP_AUTOMATION, result.source)
        assertEquals(22, result.at.hour)
        assertEquals(now.toLocalDate(), result.at.toLocalDate())
    }

    @Test
    fun `skipped automation window uses fallback`() {
        val now = ZonedDateTime.of(2026, 8, 29, 21, 0, 0, 0, zone)
        val result = DailyPlanScheduleCalculator.effectiveCutoff(
            now = now,
            fallbackCutoffMinutesOfDay = 0,
            sleepAutomation = DailyPlanSleepAutomationInput(
                enabled = true,
                windowStartMinutes = 22 * 60,
                windowEndMinutes = 2 * 60,
                skippedWindowStartEpochDay = now.toLocalDate().toEpochDay()
            )
        )

        assertEquals(DailyPlanCutoffSource.FALLBACK, result.source)
        assertEquals(now.toLocalDate().plusDays(1), result.at.toLocalDate())
        assertEquals(0, result.at.hour)
    }

    @Test
    fun `empty automation window uses fallback`() {
        val now = ZonedDateTime.of(2026, 8, 29, 21, 0, 0, 0, zone)
        val result = DailyPlanScheduleCalculator.effectiveCutoff(
            now = now,
            fallbackCutoffMinutesOfDay = 23 * 60,
            sleepAutomation = DailyPlanSleepAutomationInput(
                enabled = true,
                windowStartMinutes = 22 * 60,
                windowEndMinutes = 22 * 60,
                skippedWindowStartEpochDay = null
            )
        )

        assertEquals(DailyPlanCutoffSource.FALLBACK, result.source)
        assertEquals(23, result.at.hour)
    }

    @Test
    fun `urgency schedules threshold then repeat and stops at cutoff`() {
        val minute = 60_000L
        assertEquals(
            60L * minute,
            DailyPlanScheduleCalculator.nextUrgencyEvaluationMillis(
                nowMillis = 0L,
                cutoffMillis = 180L * minute,
                totalRemainingMinutes = 60,
                bufferMinutes = 60,
                repeatEnabled = true,
                repeatIntervalMinutes = 15,
                lastShownAtMillis = null
            )
        )
        assertEquals(
            75L * minute,
            DailyPlanScheduleCalculator.nextUrgencyEvaluationMillis(
                nowMillis = 60L * minute,
                cutoffMillis = 180L * minute,
                totalRemainingMinutes = 60,
                bufferMinutes = 60,
                repeatEnabled = true,
                repeatIntervalMinutes = 15,
                lastShownAtMillis = 60L * minute
            )
        )
        assertNull(
            DailyPlanScheduleCalculator.nextUrgencyEvaluationMillis(
                nowMillis = 180L * minute,
                cutoffMillis = 180L * minute,
                totalRemainingMinutes = 60,
                bufferMinutes = 60,
                repeatEnabled = true,
                repeatIntervalMinutes = 15,
                lastShownAtMillis = 165L * minute
            )
        )
    }
}
