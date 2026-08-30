package com.personal.sleepalarm.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSchedulingPolicyTest {
    @Test
    fun `clock reminder stays independent from an optional task link`() {
        assertTrue(
            ReminderSchedulingPolicy.canSchedule(
                triggerRule = "AT_TIME",
                linkedType = "TASK",
                linkedId = 4,
                taskExists = false,
                taskDone = false,
                taskDueAtMillis = null,
                taskStartAtMillis = null
            )
        )
    }

    @Test
    fun `deadline reminder requires a live unfinished task with deadline`() {
        assertFalse(deadline(taskExists = false, taskDone = false, dueAt = 1L))
        assertFalse(deadline(taskExists = true, taskDone = true, dueAt = 1L))
        assertFalse(deadline(taskExists = true, taskDone = false, dueAt = null))
        assertTrue(deadline(taskExists = true, taskDone = false, dueAt = 1L))
    }

    @Test
    fun `focus reminder requires start while no-progress only requires live task`() {
        assertFalse(
            ReminderSchedulingPolicy.canSchedule(
                "BEFORE_FOCUS", "TASK", 9, true, false, null, null
            )
        )
        assertTrue(
            ReminderSchedulingPolicy.canSchedule(
                "BEFORE_FOCUS", "TASK", 9, true, false, null, 2L
            )
        )
        assertTrue(
            ReminderSchedulingPolicy.canSchedule(
                "NO_PROGRESS", "TASK", 9, true, false, null, null
            )
        )
    }

    private fun deadline(taskExists: Boolean, taskDone: Boolean, dueAt: Long?) =
        ReminderSchedulingPolicy.canSchedule(
            triggerRule = "BEFORE_DEADLINE",
            linkedType = "TASK",
            linkedId = 3,
            taskExists = taskExists,
            taskDone = taskDone,
            taskDueAtMillis = dueAt,
            taskStartAtMillis = null
        )
}
