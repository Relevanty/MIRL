package com.personal.sleepalarm.domain.model

import com.personal.sleepalarm.data.db.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskIdentityTest {
    @Test
    fun primaryLabelUsesTheFirstMeaningfulTaskField() {
        val task = TaskEntity(
            id = 12,
            title = "  ",
            nextAction = "  Открыть конспект  ",
            description = "Описание"
        )

        assertEquals("Открыть конспект", task.primaryLabel())
    }

    @Test
    fun ordinaryTasksExcludeMorningRoutineRows() {
        val ordinary = TaskEntity(id = 1, title = "Отчёт")
        val routine = TaskEntity(id = 2, title = "Вода", isMorningRoutine = true)

        assertEquals(listOf(ordinary), listOf(routine, ordinary).ordinaryTasks())
    }
}
