package com.personal.sleepalarm.ui.assistant

import com.personal.sleepalarm.data.db.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantDailyPlanChangeTest {
    private val pending = PendingDailyPlanChange.TaskChange(
        taskId = 7,
        taskTitle = "Диплом",
        expectedUpdatedAt = 1_000L,
        field = AssistantTaskPlanField.DAILY_TARGET,
        oldIntValue = 60,
        newIntValue = 120
    )

    @Test
    fun confirmationAcceptsOnlyTheSnapshotThatWasShown() {
        val task = TaskEntity(id = 7, title = "Диплом", updatedAt = 1_000L)

        assertEquals(task, pending.resolveCurrentTask(task))
        assertNull(pending.resolveCurrentTask(task.copy(updatedAt = 1_001L)))
        assertNull(pending.resolveCurrentTask(task.copy(id = 8)))
        assertNull(pending.resolveCurrentTask(task.copy(isDone = true)))
        assertNull(pending.resolveCurrentTask(task.copy(isMorningRoutine = true)))
        assertNull(pending.resolveCurrentTask(null))
    }
}
