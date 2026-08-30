package com.personal.sleepalarm.ui.assistant

import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.assistant.DailyPlanCommand

enum class AssistantTaskPlanField {
    DAILY_TARGET,
    BOUT_DURATION,
    DAILY_REQUIRED
}

sealed interface PendingDailyPlanChange {
    data class TaskChange(
        val taskId: Int,
        val taskTitle: String,
        val expectedUpdatedAt: Long,
        val field: AssistantTaskPlanField,
        val oldIntValue: Int? = null,
        val newIntValue: Int? = null,
        val oldBooleanValue: Boolean? = null,
        val newBooleanValue: Boolean? = null
    ) : PendingDailyPlanChange

    data class GlobalChange(
        val command: DailyPlanCommand,
        val oldValue: String,
        val newValue: String
    ) : PendingDailyPlanChange
}

/** A confirmation is valid only for the exact task snapshot shown to the user. */
internal fun PendingDailyPlanChange.TaskChange.resolveCurrentTask(
    current: TaskEntity?
): TaskEntity? = current?.takeIf { task ->
    task.id == taskId &&
        task.updatedAt == expectedUpdatedAt &&
        !task.isDone &&
        !task.isMorningRoutine
}
