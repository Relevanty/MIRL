package com.personal.sleepalarm.ui.assistant

import com.personal.sleepalarm.data.db.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantProposedActionTest {
    @Test
    fun refreshUsesCurrentTaskTitleAndFocusDuration() {
        val previous = AssistantProposedAction(7, "Старое название", 25)
        val renamed = TaskEntity(
            id = 7,
            title = "Новое название",
            workBudgetMinutes = 100,
            plannedFocusMinutes = 120,
            estimatedMinutes = 40
        )

        assertEquals(
            AssistantProposedAction(7, "Новое название", 40),
            previous.refreshFrom(listOf(renamed))
        )
    }

    @Test
    fun refreshRemovesCompletedMissingAndMorningTasks() {
        val previous = AssistantProposedAction(7, "Задача", 25)

        assertNull(previous.refreshFrom(emptyList()))
        assertNull(previous.refreshFrom(listOf(TaskEntity(id = 7, title = "Done", isDone = true))))
        assertNull(previous.refreshFrom(listOf(TaskEntity(id = 7, title = "Routine", isMorningRoutine = true))))
        assertNull(
            previous.refreshFrom(
                listOf(
                    TaskEntity(
                        id = 7,
                        title = "Exhausted",
                        workBudgetMinutes = 25,
                        spentMillis = 25 * 60_000L
                    )
                )
            )
        )
    }
}
