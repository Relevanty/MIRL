package com.personal.sleepalarm.domain.model

import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import java.time.Instant
import java.time.ZoneId

/** Task deadlines are projections, never a second independently editable date. */
fun calendarDeadlineItems(
    metadata: List<DDayEntity>,
    tasks: List<TaskEntity>,
    zone: ZoneId = ZoneId.systemDefault()
): List<DDayEntity> {
    val details = metadata.filter { it.taskId != null }.associateBy { it.taskId }
    val taskDeadlines = tasks.filter { !it.isMorningRoutine && it.dueAtMillis != null }.map { task ->
        val source = details[task.id] ?: DDayEntity(
            id = -task.id,
            title = task.primaryLabel(),
            targetDate = "",
            taskId = task.id,
            createdAt = task.createdAt
        )
        source.copy(
            title = task.primaryLabel(),
            targetDate = Instant.ofEpochMilli(task.dueAtMillis!!).atZone(zone).toLocalDate().toString()
        )
    }
    return (metadata.filter { it.taskId == null } + taskDeadlines).sortedBy { it.targetDate }
}
