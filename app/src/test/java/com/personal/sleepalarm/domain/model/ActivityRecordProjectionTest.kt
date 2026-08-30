package com.personal.sleepalarm.domain.model

import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityRecordProjectionTest {
    @Test
    fun linkedRecordsUseTheCurrentTaskLabelAndCategory() {
        val task = TaskEntity(
            id = 7,
            title = "",
            nextAction = "Повторить главу",
            category = "STUDY"
        )
        val records = listOf(
            record(id = 1, taskId = task.id, title = "Старое название", category = "WORK"),
            record(id = 2, taskId = task.id, title = "Другой снимок", category = "OTHER")
        )

        val projected = projectActivityRecords(records, listOf(task))

        assertEquals(listOf("Повторить главу", "Повторить главу"), projected.map { it.label })
        assertEquals(listOf(FocusActivityType.STUDY, FocusActivityType.STUDY), projected.map { it.activityType })
    }

    @Test
    fun deletedTaskFallsBackToTheRecordSnapshot() {
        val projected = projectActivityRecords(
            records = listOf(record(id = 1, taskId = 404, title = "Архивная задача", category = "УЧЁБА")),
            currentTasks = emptyList()
        ).single()

        assertEquals("Архивная задача", projected.label)
        assertEquals(FocusActivityType.STUDY, projected.activityType)
    }

    private fun record(
        id: Int,
        taskId: Int?,
        title: String,
        category: String
    ) = ActivityRecordEntity(
        id = id,
        taskId = taskId,
        title = title,
        category = category,
        startedAt = 1_000L,
        endedAt = 2_000L,
        durationMillis = 1_000L,
        source = "TIMER"
    )
}
