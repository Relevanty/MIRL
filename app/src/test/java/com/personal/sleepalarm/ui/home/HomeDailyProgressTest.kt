package com.personal.sleepalarm.ui.home

import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.calculator.DailyTaskFocusProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDailyProgressTest {

    @Test
    fun `home remaining time uses today's target instead of whole task budget`() {
        val task = TaskEntity(
            id = 7,
            title = "Daily work",
            plannedFocusMinutes = 120,
            workBudgetMinutes = 900,
            spentMillis = 300L * MINUTE
        )
        val progress = progress(taskId = task.id, targetMinutes = 120, spentMinutes = 35)

        assertEquals(85, homeDailyRemainingMinutes(task, progress))
    }

    @Test
    fun `next home focus is capped by remaining whole task budget`() {
        val task = TaskEntity(
            id = 8,
            title = "Nearly complete",
            estimatedMinutes = 25,
            plannedFocusMinutes = 120,
            workBudgetMinutes = 60,
            spentMillis = 50L * MINUTE
        )
        val progress = progress(taskId = task.id, targetMinutes = 120, spentMinutes = 35)

        assertEquals(10, homeNextFocusMinutes(task, progress))
    }

    @Test
    fun `completed daily target removes task from today's focus`() {
        val task = TaskEntity(
            id = 9,
            title = "Done today",
            estimatedMinutes = 25,
            plannedFocusMinutes = 45
        )
        val progress = progress(taskId = task.id, targetMinutes = 45, spentMinutes = 50)

        assertEquals(0, homeDailyRemainingMinutes(task, progress))
        assertEquals(0, homeNextFocusMinutes(task, progress))
    }

    @Test
    fun `live focus also reduces remaining whole task budget`() {
        val task = TaskEntity(
            id = 10,
            title = "Active final cycle",
            estimatedMinutes = 25,
            plannedFocusMinutes = 120,
            workBudgetMinutes = 60,
            spentMillis = 50L * MINUTE
        )
        val progress = progress(
            taskId = task.id,
            targetMinutes = 120,
            spentMinutes = 39,
            liveAddedMinutes = 4
        )

        assertEquals(6, homeNextFocusMinutes(task, progress))
    }

    @Test
    fun `daylight progress reports elapsed and remaining sunlight`() {
        val sunrise = 6L * 60L * MINUTE
        val sunset = 18L * 60L * MINUTE

        val result = calculateHomeDaylightProgress(
            nowMillis = 12L * 60L * MINUTE,
            sunriseMillis = sunrise,
            sunsetMillis = sunset,
            daylightMinutes = 12 * 60
        )

        assertEquals(HomeDaylightPhase.DAYLIGHT, result.phase)
        assertEquals(6 * 60, result.elapsedMinutes)
        assertEquals(6 * 60, result.remainingMinutes)
        assertEquals(0.5f, result.fraction, 0.0001f)
    }

    @Test
    fun `before sunrise reports countdown without consuming daylight`() {
        val result = calculateHomeDaylightProgress(
            nowMillis = 5L * 60L * MINUTE + 30L * MINUTE,
            sunriseMillis = 6L * 60L * MINUTE,
            sunsetMillis = 18L * 60L * MINUTE,
            daylightMinutes = 12 * 60
        )

        assertEquals(HomeDaylightPhase.BEFORE_SUNRISE, result.phase)
        assertEquals(30, result.minutesUntilSunrise)
        assertEquals(0, result.elapsedMinutes)
        assertEquals(12 * 60, result.remainingMinutes)
    }

    private fun progress(
        taskId: Int,
        targetMinutes: Int,
        spentMinutes: Int,
        liveAddedMinutes: Int = 0
    ): DailyTaskFocusProgress = DailyTaskFocusProgress(
        taskId = taskId,
        dayStartMillis = 0L,
        dayEndMillis = 24L * 60L * MINUTE,
        spentMillis = spentMinutes * MINUTE,
        persistedSpentMillis = (spentMinutes - liveAddedMinutes).coerceAtLeast(0) * MINUTE,
        liveAddedMillis = liveAddedMinutes * MINUTE,
        targetMinutes = targetMinutes
    )

    private companion object {
        const val MINUTE = 60_000L
    }
}
