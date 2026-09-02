package com.personal.sleepalarm.domain.calculator

import com.personal.sleepalarm.data.db.entity.TaskEntity
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDeadlinePlanCalculatorTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val now = at("2026-09-02T10:00:00")

    @Test
    fun `pace divides whole-task remainder across inclusive calendar dates`() {
        val plan = calculate(task(
            budget = 300,
            spent = minutes(60),
            due = at("2026-09-04T18:00:00"),
            dailyGoal = 60,
            bout = 25
        ))

        assertTrue(plan.budgetConfigured)
        assertEquals(300, plan.totalMinutes)
        assertEquals(60, plan.spentMinutes)
        assertEquals(240, plan.remainingMinutes)
        assertEquals(3, plan.calendarDaysRemaining)
        assertEquals(80, plan.requiredMinutesPerDay)
        assertEquals(60, plan.manualDailyGoalMinutes)
        assertEquals(false, plan.isManualDailyGoalSufficient)
        assertFalse(plan.overdue)
        assertFalse(plan.cannotFitBeforeDeadline)
    }

    @Test
    fun `unknown total estimate has no fabricated pace or readiness`() {
        val plan = calculate(task(budget = 0, spent = minutes(40), due = at("2026-09-04T18:00:00")))

        assertFalse(plan.budgetConfigured)
        assertEquals(0, plan.totalMinutes)
        assertEquals(40, plan.spentMinutes)
        assertEquals(0, plan.remainingMinutes)
        assertNull(plan.requiredMinutesPerDay)
        assertNull(plan.isManualDailyGoalSufficient)
        assertFalse(plan.estimateExhaustedButTaskOpen)
        assertFalse(plan.cannotFitBeforeDeadline)
    }

    @Test
    fun `task without deadline retains workload but has no deadline recommendation`() {
        val plan = calculate(task(budget = 120, spent = minutes(30), due = null))

        assertEquals(90, plan.remainingMinutes)
        assertNull(plan.calendarDaysRemaining)
        assertNull(plan.wallClockMinutesRemaining)
        assertNull(plan.requiredMinutesPerDay)
        assertNull(plan.isManualDailyGoalSufficient)
        assertFalse(plan.overdue)
    }

    @Test
    fun `focus bout length does not alter whole-task pace or configured daily goal`() {
        val original = task(budget = 250, spent = minutes(10), due = at("2026-09-03T20:00:00"), dailyGoal = 75)

        val shortBout = calculate(original.copy(estimatedMinutes = 5))
        val longBout = calculate(original.copy(estimatedMinutes = 180))

        assertEquals(shortBout, longBout)
        assertEquals(120, shortBout.requiredMinutesPerDay)
        assertEquals(75, shortBout.manualDailyGoalMinutes)
    }

    @Test
    fun `same-day exact future deadline is not overdue`() {
        val plan = calculate(task(budget = 60, due = at("2026-09-02T12:00:00"), dailyGoal = 60))

        assertEquals(1, plan.calendarDaysRemaining)
        assertEquals(120L, plan.wallClockMinutesRemaining)
        assertEquals(60, plan.requiredMinutesPerDay)
        assertEquals(true, plan.isManualDailyGoalSufficient)
        assertFalse(plan.overdue)
        assertFalse(plan.cannotFitBeforeDeadline)
    }

    @Test
    fun `deadline already reached has no daily pace promise`() {
        listOf(now, now - 1L).forEach { due ->
            val plan = calculate(task(budget = 60, due = due))

            assertTrue(plan.overdue)
            assertEquals(0, plan.calendarDaysRemaining)
            assertEquals(0L, plan.wallClockMinutesRemaining)
            assertNull(plan.requiredMinutesPerDay)
            assertNull(plan.isManualDailyGoalSufficient)
            assertTrue(plan.cannotFitBeforeDeadline)
        }
    }

    @Test
    fun `exact remaining milliseconds catch impossible work despite rounded minutes`() {
        val plan = calculate(task(budget = 2, spent = 30_000L, due = now + 89_999L))

        assertEquals(2, plan.remainingMinutes)
        assertEquals(2L, plan.wallClockMinutesRemaining)
        assertTrue(plan.cannotFitBeforeDeadline)
    }

    @Test
    fun `exactly matching wall-clock remainder is not marked impossible`() {
        val plan = calculate(task(budget = 2, spent = 30_000L, due = now + 90_000L))

        assertFalse(plan.cannotFitBeforeDeadline)
    }

    @Test
    fun `adequate numeric daily goal does not guarantee wall-clock feasibility`() {
        val plan = calculate(task(budget = 60, due = now + minutes(15), dailyGoal = 60))

        assertEquals(true, plan.isManualDailyGoalSufficient)
        assertTrue(plan.cannotFitBeforeDeadline)
    }

    @Test
    fun `exhausted estimate is not a completed task or a zero-minute recommendation`() {
        val plan = calculate(task(budget = 60, spent = minutes(90), due = at("2026-09-03T18:00:00")))

        assertTrue(plan.estimateExhaustedButTaskOpen)
        assertEquals(90, plan.spentMinutes)
        assertEquals(0, plan.remainingMinutes)
        assertNull(plan.requiredMinutesPerDay)
        assertNull(plan.isManualDailyGoalSufficient)
        assertFalse(plan.cannotFitBeforeDeadline)
    }

    @Test
    fun `completed task does not recommend further work even below its estimate`() {
        val plan = calculate(task(budget = 120, spent = minutes(20), due = now - minutes(60)).copy(isDone = true))

        assertEquals(0, plan.remainingMinutes)
        assertNull(plan.requiredMinutesPerDay)
        assertNull(plan.isManualDailyGoalSufficient)
        assertFalse(plan.overdue)
        assertFalse(plan.estimateExhaustedButTaskOpen)
        assertFalse(plan.cannotFitBeforeDeadline)
    }

    @Test
    fun `partly completed minute is not discarded from remaining work`() {
        val plan = calculate(task(budget = 3, spent = 60_001L, due = at("2026-09-03T18:00:00")))

        assertEquals(1, plan.spentMinutes)
        assertEquals(2, plan.remainingMinutes)
        assertEquals(1, plan.requiredMinutesPerDay)
    }

    @Test
    fun `local calendar dates rather than utc dates determine pace`() {
        val beforeMidnight = at("2026-09-02T23:30:00")
        val afterMidnight = at("2026-09-03T00:30:00")
        val plan = TaskDeadlinePlanCalculator.calculate(task(budget = 30, due = afterMidnight), beforeMidnight, zone)

        assertEquals(2, plan.calendarDaysRemaining)
        assertEquals(60L, plan.wallClockMinutesRemaining)
        assertEquals(15, plan.requiredMinutesPerDay)
    }

    @Test
    fun `daylight saving changes wall-clock duration without changing inclusive date count`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val start = LocalDateTime.parse("2026-03-28T12:00:00").atZone(berlin).toInstant().toEpochMilli()
        val due = LocalDateTime.parse("2026-03-30T12:00:00").atZone(berlin).toInstant().toEpochMilli()
        val plan = TaskDeadlinePlanCalculator.calculate(task(budget = 150, due = due), start, berlin)

        assertEquals(3, plan.calendarDaysRemaining)
        assertEquals(47L * 60L, plan.wallClockMinutesRemaining)
        assertEquals(50, plan.requiredMinutesPerDay)
    }

    @Test
    fun `invalid negative workload values cannot manufacture progress or effort`() {
        val plan = calculate(task(budget = -10, spent = -50_000L, dailyGoal = -5, due = at("2026-09-03T18:00:00")))

        assertFalse(plan.budgetConfigured)
        assertEquals(0, plan.totalMinutes)
        assertEquals(0, plan.spentMinutes)
        assertEquals(0, plan.remainingMinutes)
        assertEquals(0, plan.manualDailyGoalMinutes)
        assertNull(plan.requiredMinutesPerDay)
    }

    @Test
    fun `extreme valid timestamps and workloads do not overflow`() {
        val plan = TaskDeadlinePlanCalculator.calculate(
            task(budget = Int.MAX_VALUE, due = Long.MAX_VALUE),
            Long.MIN_VALUE,
            ZoneId.of("UTC")
        )

        assertEquals(Int.MAX_VALUE, plan.remainingMinutes)
        assertEquals(Int.MAX_VALUE, plan.calendarDaysRemaining)
        assertEquals(1, plan.requiredMinutesPerDay)
        assertTrue(plan.wallClockMinutesRemaining!! > 0L)
        assertFalse(plan.cannotFitBeforeDeadline)
    }

    private fun calculate(task: TaskEntity) = TaskDeadlinePlanCalculator.calculate(task, now, zone)

    private fun task(
        budget: Int,
        spent: Long = 0L,
        due: Long?,
        dailyGoal: Int = 25,
        bout: Int = 25
    ) = TaskEntity(
        title = "Release",
        workBudgetMinutes = budget,
        spentMillis = spent,
        dueAtMillis = due,
        plannedFocusMinutes = dailyGoal,
        estimatedMinutes = bout
    )

    private fun minutes(value: Int): Long = value * 60_000L
    private fun at(value: String): Long = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
}
