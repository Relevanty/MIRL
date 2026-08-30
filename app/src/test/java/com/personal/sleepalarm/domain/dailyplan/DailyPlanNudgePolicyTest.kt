package com.personal.sleepalarm.domain.dailyplan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyPlanNudgePolicyTest {
    @Test
    fun `daily and whole-budget remainders are independent before cap`() {
        val currentWholeRemaining = DailyPlanNudgePolicy.currentWholeBudgetRemainingMinutes(
            workBudgetMinutes = 180,
            persistedAllTimeSpentMillis = 120L * 60_000L,
            liveElapsedMillis = 0L
        )
        val capped = DailyPlanNudgePolicy.calculateTask(
            task(
                target = 120,
                today = 20,
                wholeRemaining = currentWholeRemaining
            )
        )
        val unlimited = DailyPlanNudgePolicy.calculateTask(
            task(
                target = 120,
                today = 20,
                wholeRemaining = null
            )
        )

        assertEquals(60, capped?.remainingMinutes)
        assertEquals(100, unlimited?.remainingMinutes)
        assertEquals(
            null,
            DailyPlanNudgePolicy.currentWholeBudgetRemainingMinutes(
                workBudgetMinutes = 0,
                persistedAllTimeSpentMillis = 120L * 60_000L,
                liveElapsedMillis = 0L
            )
        )
    }

    @Test
    fun `slack triggers inside buffer and overload is negative slack`() {
        val snapshot = DailyPlanNudgePolicy.calculate(
            tasks = listOf(task(target = 90, today = 15, wholeRemaining = null)),
            nowMillis = 0L,
            dayStartMillis = 0L,
            nextMidnightMillis = 24L * 60L * 60_000L,
            cutoffMillis = 100L * 60_000L,
            bufferMinutes = 30
        )

        assertEquals(75, snapshot.totalRemainingMinutes)
        assertEquals(25, snapshot.slackMinutes)
        assertTrue(snapshot.shouldNudge)
        assertFalse(snapshot.isOverloaded)

        val overloaded = snapshot.copy(availableMinutes = 50, slackMinutes = -25)
        assertTrue(overloaded.shouldNudge)
        assertTrue(overloaded.isOverloaded)
    }

    @Test
    fun `exhausted target is removed from actionable snapshot`() {
        val snapshot = DailyPlanNudgePolicy.calculate(
            tasks = listOf(task(target = 25, today = 25, wholeRemaining = null)),
            nowMillis = 0L,
            dayStartMillis = 0L,
            nextMidnightMillis = 1_000_000L,
            cutoffMillis = 1_000_000L,
            bufferMinutes = 60
        )

        assertTrue(snapshot.tasks.isEmpty())
        assertFalse(snapshot.hasRequiredTasks)
        assertFalse(snapshot.shouldNudge)
    }

    @Test
    fun `future-start and optional tasks are not eligible`() {
        assertFalse(
            DailyPlanTaskEligibility.isEligible(
                isDailyRequired = true,
                isDone = false,
                isMorningRoutine = false,
                startAtMillis = 2_000L,
                dailyTargetMinutes = 25,
                nowMillis = 1_000L
            )
        )
        assertFalse(
            DailyPlanTaskEligibility.isEligible(
                isDailyRequired = false,
                isDone = false,
                isMorningRoutine = false,
                startAtMillis = null,
                dailyTargetMinutes = 25,
                nowMillis = 1_000L
            )
        )
        assertTrue(
            DailyPlanTaskEligibility.isEligible(
                isDailyRequired = true,
                isDone = false,
                isMorningRoutine = false,
                startAtMillis = 1_000L,
                dailyTargetMinutes = 25,
                nowMillis = 1_000L
            )
        )
    }

    private fun task(
        target: Int,
        today: Int,
        wholeRemaining: Int?
    ) = DailyPlanTaskInput(
        taskId = 1,
        title = "Task",
        dailyTargetMinutes = target,
        wholeBudgetRemainingMinutes = wholeRemaining,
        todayProgressMinutes = today,
        boutMinutes = 25
    )
}
