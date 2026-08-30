package com.personal.sleepalarm.domain.model

import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import java.util.Locale

/**
 * A history row keeps its own snapshot, while a still-linked task supplies the
 * live label and category used by current analytics views.
 */
data class ActivityRecordLiveProjection(
    val record: ActivityRecordEntity,
    val label: String,
    val activityType: FocusActivityType
)

fun projectActivityRecords(
    records: List<ActivityRecordEntity>,
    currentTasks: List<TaskEntity>
): List<ActivityRecordLiveProjection> {
    val tasksById = currentTasks.associateBy(TaskEntity::id)
    return records.map { record ->
        val task = record.taskId?.let(tasksById::get)
        ActivityRecordLiveProjection(
            record = record,
            label = task?.primaryLabel() ?: record.title,
            activityType = task?.focusActivityType() ?: record.snapshotActivityType()
        )
    }
}

/** Resolves the immutable category snapshot when its task no longer exists. */
fun ActivityRecordEntity.snapshotActivityType(): FocusActivityType =
    when (category.trim().uppercase(Locale.ROOT)) {
        "STUDY", "УЧЁБА", "УЧЕБА" -> FocusActivityType.STUDY
        "WORK", "РАБОТА" -> FocusActivityType.WORK
        "OTHER", "ДРУГОЕ" -> FocusActivityType.OTHER
        else -> activityType
    }
