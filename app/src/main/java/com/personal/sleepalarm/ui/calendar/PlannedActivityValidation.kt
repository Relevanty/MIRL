package com.personal.sleepalarm.ui.calendar

import com.personal.sleepalarm.data.db.entity.CalendarEventEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity

internal enum class PlannedActivityValidationError { TITLE, TIME_RANGE, REPEAT, REMINDER, INVALID_ID }

/** Plans may be future or historical, but they are never actual-work records. */
internal fun validatePlannedActivity(event: CalendarEventEntity): PlannedActivityValidationError? = when {
    event.id < 0 -> PlannedActivityValidationError.INVALID_ID
    event.title.isBlank() -> PlannedActivityValidationError.TITLE
    event.startMillis >= event.endMillis -> PlannedActivityValidationError.TIME_RANGE
    event.repeatRule !in setOf("none", "daily", "weekly") -> PlannedActivityValidationError.REPEAT
    event.reminderMinutes != null && event.reminderMinutes < 0 -> PlannedActivityValidationError.REMINDER
    else -> null
}

internal fun normalizePlannedActivity(
    edited: CalendarEventEntity,
    current: CalendarEventEntity?,
    linkedTask: TaskEntity?
): CalendarEventEntity = edited.copy(
    id = current?.id ?: edited.id,
    title = edited.title.trim(),
    eventKind = "PLANNED",
    taskId = linkedTask?.id,
    projectId = if (linkedTask != null) linkedTask.projectId else edited.projectId,
    createdAt = current?.createdAt ?: edited.createdAt
)
