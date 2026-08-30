package com.personal.sleepalarm.domain.model

import com.personal.sleepalarm.data.db.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Assert.assertThrows

class FocusTaskTargetTest {

    @Test
    fun mixedPickerId_roundTripsWithoutCollidingWithNativeItems() {
        assertEquals(-42, taskFocusItemId(42))
        assertEquals(42, focusItemTaskId(-42))
        assertNull(focusItemTaskId(42))
        assertNull(focusItemTaskId(null))
        assertNull(focusItemTaskId(Int.MIN_VALUE))
        assertThrows(IllegalArgumentException::class.java) { taskFocusItemId(0) }
    }

    @Test
    fun taskUsesItsExplicitFocusActivity() {
        assertEquals(
            FocusActivityType.STUDY,
            TaskEntity(title = "", category = "STUDY").focusActivityType()
        )
        assertEquals(
            FocusActivityType.OTHER,
            TaskEntity(title = "", category = "other").focusActivityType()
        )
    }

    @Test
    fun legacyOrInvalidCategoryFallsBackToWork() {
        assertEquals(
            FocusActivityType.WORK,
            TaskEntity(title = "", category = "").focusActivityType()
        )
        assertEquals(
            FocusActivityType.WORK,
            TaskEntity(title = "", category = "UNKNOWN").focusActivityType()
        )
    }

    @Test
    fun nextFocus_isCappedByRemainingTaskBudget() {
        val task = TaskEntity(
            title = "",
            workBudgetMinutes = 48,
            plannedFocusMinutes = 120,
            estimatedMinutes = 25,
            spentMillis = 30L * 60_000L
        )

        assertEquals(48, task.effectiveWorkBudgetMinutes())
        assertEquals(18L * 60_000L, task.remainingWorkMillisOrNull())
        assertEquals(18, task.nextFocusDurationMinutes())
    }

    @Test
    fun nextFocus_allowsShortFinalCycleAndStopsAtExhaustedBudget() {
        val finalMinutes = TaskEntity(
            title = "",
            workBudgetMinutes = 25,
            plannedFocusMinutes = 120,
            estimatedMinutes = 15,
            spentMillis = 23L * 60_000L + 1L
        )
        val exhausted = finalMinutes.copy(spentMillis = 25L * 60_000L)

        assertEquals(2, finalMinutes.nextFocusDurationMinutes())
        assertEquals(0, exhausted.nextFocusDurationMinutes())
    }

    @Test
    fun noWholeBudget_doesNotFallBackToBoutOrCapAtDailyTarget() {
        val task = TaskEntity(
            title = "",
            workBudgetMinutes = 0,
            plannedFocusMinutes = 120,
            estimatedMinutes = 40,
            spentMillis = 400L * 60_000L
        )

        assertEquals(0, task.effectiveWorkBudgetMinutes())
        assertNull(task.remainingWorkMillisOrNull())
        assertNull(task.remainingWorkMinutesOrNull())
        assertEquals(40, task.nextFocusDurationMinutes())
    }

    @Test
    fun dailyTargetIsNotTheBoutDuration() {
        val task = TaskEntity(
            title = "",
            plannedFocusMinutes = 120,
            estimatedMinutes = 40
        )

        assertEquals(40, task.nextFocusDurationMinutes())
    }
}
