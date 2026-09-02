package com.personal.sleepalarm.ui.calendar

import com.personal.sleepalarm.data.db.entity.CalendarEventEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlannedActivityValidationTest {
    private fun event() = CalendarEventEntity(
        title = "Plan", startMillis = 1_000L, endMillis = 61_000L, allDay = false,
        repeatRule = "none", reminderMinutes = null, createdAt = 123L
    )

    @Test fun futurePlansAreAllowedButMustHavePositiveDuration() {
        assertNull(validatePlannedActivity(event().copy(startMillis = 9_000_000_000_000L, endMillis = 9_000_000_060_000L)))
        assertEquals(PlannedActivityValidationError.TIME_RANGE, validatePlannedActivity(event().copy(endMillis = 1_000L)))
        assertEquals(PlannedActivityValidationError.TIME_RANGE, validatePlannedActivity(event().copy(endMillis = 999L)))
    }

    @Test fun invalidTitlesRepeatsAndReminderOffsetsAreRejected() {
        assertEquals(PlannedActivityValidationError.TITLE, validatePlannedActivity(event().copy(title = "  ")))
        assertEquals(PlannedActivityValidationError.REPEAT, validatePlannedActivity(event().copy(repeatRule = "unexpected")))
        assertEquals(PlannedActivityValidationError.REMINDER, validatePlannedActivity(event().copy(reminderMinutes = -1)))
        assertEquals(PlannedActivityValidationError.INVALID_ID, validatePlannedActivity(event().copy(id = -1)))
    }

    @Test fun existingLongLeadRemindersAndSupportedRepeatsRemainValid() {
        listOf("none", "daily", "weekly").forEach { repeat ->
            assertNull(validatePlannedActivity(event().copy(repeatRule = repeat, reminderMinutes = 10_080)))
        }
        assertNull(validatePlannedActivity(event().copy(reminderMinutes = 0)))
    }

    @Test fun normalizationIsAlwaysPlannedAndPreservesExistingIdentity() {
        val current = event().copy(id = 7)
        val edited = current.copy(title = "  Updated  ", eventKind = "ACTUAL", createdAt = 999L)
        val saved = normalizePlannedActivity(edited, current, null)
        assertEquals(7, saved.id)
        assertEquals(123L, saved.createdAt)
        assertEquals("Updated", saved.title)
        assertEquals("PLANNED", saved.eventKind)
    }

    @Test fun taskAssociationUsesCurrentTaskProjectEvenWhenCleared() {
        val task = TaskEntity(id = 9, title = "Task", projectId = null)
        val saved = normalizePlannedActivity(event().copy(taskId = 9, projectId = 50), null, task)
        assertEquals(9, saved.taskId)
        assertNull(saved.projectId)
        assertEquals(0L, task.spentMillis)
    }
}
