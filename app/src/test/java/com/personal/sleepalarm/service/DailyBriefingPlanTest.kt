package com.personal.sleepalarm.service

import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyBriefingPlanTest {
    @Test
    fun includesOnlyRequiredActiveTasksAndUsesCivilDayProgress() {
        val dayStart = 1_000_000L
        val now = dayStart + 6L * 60L * 60_000L
        val midnight = dayStart + 24L * 60L * 60_000L
        val requiredStarted = task(1, "Диплом", target = 120, required = true)
        val requiredUnstarted = task(2, "Алгебра", target = 60, required = true)
        val optional = task(3, "Физика", target = 180, required = false)
        val completed = task(4, "Готово", target = 90, required = true).copy(isDone = true)
        val records = listOf(
            record(1, dayStart + 60_000L, 30L * 60_000L),
            // Before 00:00 must not count toward today's plan.
            record(2, dayStart - 20L * 60_000L, 10L * 60_000L)
        )

        val plan = calculateDailyBriefingPlan(
            tasks = listOf(requiredStarted, requiredUnstarted, optional, completed),
            records = records,
            nowMillis = now,
            dayStartMillis = dayStart,
            nextMidnightMillis = midnight,
            cutoffMillis = midnight,
            bufferMinutes = 60
        )

        assertEquals(listOf(1, 2), plan.snapshot.tasks.map { it.taskId })
        assertEquals(30, plan.snapshot.tasks.first { it.taskId == 1 }.todayProgressMinutes)
        assertEquals(listOf(2), plan.unstartedTasks.map { it.taskId })
        assertEquals(150, plan.snapshot.totalRemainingMinutes)
    }

    @Test
    fun anyExactProgressMeansTaskWasStartedEvenBeforeOneFullMinute() {
        val task = task(7, "Черновик", target = 25, required = true)
        val plan = calculateDailyBriefingPlan(
            tasks = listOf(task),
            records = listOf(record(7, 1_100L, 20_000L)),
            nowMillis = 30_000L,
            dayStartMillis = 1_000L,
            nextMidnightMillis = 100_000L,
            cutoffMillis = 90_000L,
            bufferMinutes = 60
        )

        assertTrue(plan.unstartedTasks.isEmpty())
        assertFalse(plan.snapshot.tasks.isEmpty())
    }

    @Test
    fun futureActivityDoesNotStartOrReduceTodaysTask() {
        val task = task(8, "Геометрия", target = 45, required = true)
        val now = 50_000L
        val plan = calculateDailyBriefingPlan(
            tasks = listOf(task),
            records = listOf(record(8, now + 1_000L, 20_000L)),
            nowMillis = now,
            dayStartMillis = 1_000L,
            nextMidnightMillis = 100_000L,
            cutoffMillis = 90_000L,
            bufferMinutes = 60
        )

        assertEquals(0, plan.snapshot.tasks.single().todayProgressMinutes)
        assertEquals(listOf(8), plan.unstartedTasks.map { it.taskId })
        assertEquals(45, plan.snapshot.totalRemainingMinutes)
    }

    @Test
    fun requiredTaskScheduledForTheFutureIsNotPartOfCurrentPlan() {
        val now = 50_000L
        val current = task(9, "Текущая", target = 30, required = true)
        val future = task(10, "Будущая", target = 90, required = true)
            .copy(startAtMillis = now + 1L)

        val plan = calculateDailyBriefingPlan(
            tasks = listOf(current, future),
            records = emptyList(),
            nowMillis = now,
            dayStartMillis = 1_000L,
            nextMidnightMillis = 100_000L,
            cutoffMillis = 90_000L,
            bufferMinutes = 60
        )

        assertEquals(listOf(9), plan.snapshot.tasks.map { it.taskId })
        assertEquals(30, plan.snapshot.totalRemainingMinutes)
        assertEquals(listOf(9), plan.unstartedTasks.map { it.taskId })
    }

    private fun task(
        id: Int,
        title: String,
        target: Int,
        required: Boolean
    ) = TaskEntity(
        id = id,
        title = title,
        plannedFocusMinutes = target,
        isDailyRequired = required,
        workBudgetMinutes = 0
    )

    private fun record(taskId: Int, startedAt: Long, duration: Long) =
        ActivityRecordEntity(
            taskId = taskId,
            title = "Работа",
            startedAt = startedAt,
            endedAt = startedAt + duration,
            durationMillis = duration,
            source = "TIMER",
            countsTowardProgress = true
        )
}
