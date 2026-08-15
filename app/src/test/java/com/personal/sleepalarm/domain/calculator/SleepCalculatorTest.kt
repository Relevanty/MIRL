package com.personal.sleepalarm.domain.calculator

import com.personal.sleepalarm.domain.model.SleepPlanWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SleepCalculatorTest {

    private val zone = ZoneOffset.UTC

    private fun zdt(
        date: LocalDate,
        time: LocalTime
    ): ZonedDateTime {
        return LocalDateTime.of(date, time).atZone(zone)
    }

    @Test
    fun `forward calculation across midnight`() {
        // Пример из ТЗ:
        // bedTime = 23:00
        // onsetLatency = 15 min
        // cycleLength = 90 min
        // cycles = 5
        // expected wake = 06:45
        val bedTime = zdt(
            LocalDate.of(2026, 8, 2),
            LocalTime.of(23, 0)
        )

        val plan = SleepCalculator.calculateFromBedTime(
            bedTime = bedTime,
            onsetLatencyMinutes = 15,
            cycleLengthMinutes = 90,
            cycles = 5
        )

        assertEquals(
            zdt(
                LocalDate.of(2026, 8, 2),
                LocalTime.of(23, 15)
            ),
            plan.estimatedSleepStart
        )

        assertEquals(
            zdt(
                LocalDate.of(2026, 8, 3),
                LocalTime.of(6, 45)
            ),
            plan.estimatedWake
        )

        assertEquals(450L, plan.totalSleepMinutes)
        assertEquals(465L, plan.totalInBedMinutes)
        assertTrue(plan.crossesMidnight)
    }

    @Test
    fun `reverse calculation uses stated formula`() {
        // В ТЗ был пример:
        // desiredWake = 07:00
        // onsetLatency = 15
        // cycleLength = 90
        // cycles = 5
        // expected bedTime = 22:45
        //
        // Но по формуле:
        // 07:00 - 5 * 90 - 15 = 23:15
        //
        // Поэтому тест проверяет корректную формулу.
        val desiredWake = zdt(
            LocalDate.of(2026, 8, 3),
            LocalTime.of(7, 0)
        )

        val plan = SleepCalculator.calculateFromDesiredWake(
            desiredWake = desiredWake,
            onsetLatencyMinutes = 15,
            cycleLengthMinutes = 90,
            cycles = 5
        )

        assertEquals(
            zdt(
                LocalDate.of(2026, 8, 2),
                LocalTime.of(23, 15)
            ),
            plan.bedTime
        )

        assertEquals(
            zdt(
                LocalDate.of(2026, 8, 2),
                LocalTime.of(23, 30)
            ),
            plan.estimatedSleepStart
        )

        assertEquals(
            zdt(
                LocalDate.of(2026, 8, 3),
                LocalTime.of(7, 0)
            ),
            plan.estimatedWake
        )
    }

    @Test
    fun `reverse calculation with onset 45 gives bedtime 22_45`() {
        // Этот тест показывает, как получить 22:45 из ТЗ-примера.
        // Для wake = 07:00, cycles = 5, cycleLength = 90
        // bedTime = 22:45 только если onsetLatency = 45.
        val desiredWake = zdt(
            LocalDate.of(2026, 8, 3),
            LocalTime.of(7, 0)
        )

        val plan = SleepCalculator.calculateFromDesiredWake(
            desiredWake = desiredWake,
            onsetLatencyMinutes = 45,
            cycleLengthMinutes = 90,
            cycles = 5
        )

        assertEquals(
            zdt(
                LocalDate.of(2026, 8, 2),
                LocalTime.of(22, 45)
            ),
            plan.bedTime
        )

        assertEquals(
            zdt(
                LocalDate.of(2026, 8, 2),
                LocalTime.of(23, 30)
            ),
            plan.estimatedSleepStart
        )
    }

    @Test
    fun `forward calculation crossing midnight with corrected arithmetic`() {
        // Пример из ТЗ:
        // bedTime = 01:20
        // onsetLatency = 20
        // cycleLength = 100
        // cycles = 4
        //
        // По формуле:
        // 01:20 + 20 = 01:40
        // 01:40 + 4 * 100 = 08:20
        val bedTime = zdt(
            LocalDate.of(2026, 8, 3),
            LocalTime.of(1, 20)
        )

        val plan = SleepCalculator.calculateFromBedTime(
            bedTime = bedTime,
            onsetLatencyMinutes = 20,
            cycleLengthMinutes = 100,
            cycles = 4
        )

        assertEquals(
            zdt(
                LocalDate.of(2026, 8, 3),
                LocalTime.of(1, 40)
            ),
            plan.estimatedSleepStart
        )

        assertEquals(
            zdt(
                LocalDate.of(2026, 8, 3),
                LocalTime.of(8, 20)
            ),
            plan.estimatedWake
        )

        assertEquals(400L, plan.totalSleepMinutes)
        assertEquals(420L, plan.totalInBedMinutes)
    }

    @Test
    fun `resolve wake time to next day if wake time already passed`() {
        val now = zdt(
            LocalDate.of(2026, 8, 2),
            LocalTime.of(8, 0)
        )

        val resolvedWake = SleepCalculator.resolveFutureWakeTime(
            wakeTime = LocalTime.of(7, 0),
            referenceDate = now.toLocalDate(),
            zone = zone,
            now = now
        )

        assertEquals(
            zdt(
                LocalDate.of(2026, 8, 3),
                LocalTime.of(7, 0)
            ),
            resolvedWake
        )
    }

    @Test
    fun `resolve wake time keeps same day if wake time is in future`() {
        val now = zdt(
            LocalDate.of(2026, 8, 2),
            LocalTime.of(6, 0)
        )

        val resolvedWake = SleepCalculator.resolveFutureWakeTime(
            wakeTime = LocalTime.of(7, 0),
            referenceDate = now.toLocalDate(),
            zone = zone,
            now = now
        )

        assertEquals(
            zdt(
                LocalDate.of(2026, 8, 2),
                LocalTime.of(7, 0)
            ),
            resolvedWake
        )
    }

    @Test
    fun `resolve bed time to next day if bed time already passed`() {
        val now = zdt(
            LocalDate.of(2026, 8, 2),
            LocalTime.of(23, 30)
        )

        val resolvedBedTime = SleepCalculator.resolveFutureBedTime(
            bedTime = LocalTime.of(23, 0),
            referenceDate = now.toLocalDate(),
            zone = zone,
            now = now
        )

        assertEquals(
            zdt(
                LocalDate.of(2026, 8, 3),
                LocalTime.of(23, 0)
            ),
            resolvedBedTime
        )
    }

    @Test
    fun `recommend cycles chooses feasible closest bedtime`() {
        // now = 23:00
        // desiredWake = 07:00 next day
        // onset = 15
        // cycle = 90
        //
        // cycles = 6 -> bedTime = 21:45, уже прошло
        // cycles = 5 -> bedTime = 23:15, через 15 минут
        // cycles = 4 -> bedTime = 00:45, через 1ч45м
        //
        // Ожидаем 5 циклов.
        val now = zdt(
            LocalDate.of(2026, 8, 2),
            LocalTime.of(23, 0)
        )

        val desiredWake = zdt(
            LocalDate.of(2026, 8, 3),
            LocalTime.of(7, 0)
        )

        val recommendedCycles = SleepCalculator.recommendCyclesForWake(
            desiredWake = desiredWake,
            now = now,
            onsetLatencyMinutes = 15,
            cycleLengthMinutes = 90
        )

        assertEquals(5, recommendedCycles)
    }

    @Test
    fun `recommend cycles chooses fewer cycles when longer plan is already in past`() {
        // now = 00:30
        // desiredWake = 07:00 same day
        //
        // cycles = 5 -> bedTime previous day 23:15, past
        // cycles = 4 -> bedTime 00:45, future через 15 минут
        // cycles = 3 -> bedTime 02:15, future через 1ч45м
        //
        // Ожидаем 4 цикла.
        val now = zdt(
            LocalDate.of(2026, 8, 3),
            LocalTime.of(0, 30)
        )

        val desiredWake = zdt(
            LocalDate.of(2026, 8, 3),
            LocalTime.of(7, 0)
        )

        val recommendedCycles = SleepCalculator.recommendCyclesForWake(
            desiredWake = desiredWake,
            now = now,
            onsetLatencyMinutes = 15,
            cycleLengthMinutes = 90
        )

        assertEquals(4, recommendedCycles)
    }

    @Test
    fun `recommend cycles returns null when fewer than minimum cycles fit`() {
        val now = zdt(
            LocalDate.of(2026, 8, 3),
            LocalTime.of(4, 30)
        )
        val desiredWake = zdt(
            LocalDate.of(2026, 8, 3),
            LocalTime.of(7, 0)
        )

        val recommendedCycles = SleepCalculator.recommendCyclesForWake(
            desiredWake = desiredWake,
            now = now,
            onsetLatencyMinutes = 15,
            cycleLengthMinutes = 90
        )
        val recommendedPlan = SleepCalculator.calculateRecommendedFromDesiredWake(
            desiredWake = desiredWake,
            now = now,
            onsetLatencyMinutes = 15,
            cycleLengthMinutes = 90
        )

        assertNull(recommendedCycles)
        assertNull(recommendedPlan)
    }

    @Test
    fun `warnings include bed time in past`() {
        val now = zdt(
            LocalDate.of(2026, 8, 2),
            LocalTime.of(23, 30)
        )

        val desiredWake = zdt(
            LocalDate.of(2026, 8, 3),
            LocalTime.of(7, 0)
        )

        val plan = SleepCalculator.calculateFromDesiredWake(
            desiredWake = desiredWake,
            onsetLatencyMinutes = 15,
            cycleLengthMinutes = 90,
            cycles = 6
        )

        // cycles = 6, wake = 07:00, bedTime = 21:45 previous day
        val warnings = SleepCalculator.warningsFor(plan, now)

        assertTrue(SleepPlanWarning.BED_TIME_IN_PAST in warnings)
    }

    @Test
    fun `current cycle is calculated correctly`() {
        val bedTime = zdt(
            LocalDate.of(2026, 8, 2),
            LocalTime.of(23, 0)
        )

        val plan = SleepCalculator.calculateFromBedTime(
            bedTime = bedTime,
            onsetLatencyMinutes = 15,
            cycleLengthMinutes = 90,
            cycles = 5
        )

        // sleepStart = 23:15
        assertEquals(
            1,
            plan.currentCycleAt(
                zdt(
                    LocalDate.of(2026, 8, 2),
                    LocalTime.of(23, 30)
                )
            )
        )

        // 00:45 — прошло 90 минут от sleepStart, значит начался второй цикл
        assertEquals(
            2,
            plan.currentCycleAt(
                zdt(
                    LocalDate.of(2026, 8, 3),
                    LocalTime.of(0, 45)
                )
            )
        )

        // 06:45 — время пробуждения, считаем последний цикл
        assertEquals(
            5,
            plan.currentCycleAt(
                zdt(
                    LocalDate.of(2026, 8, 3),
                    LocalTime.of(6, 45)
                )
            )
        )
    }
}
