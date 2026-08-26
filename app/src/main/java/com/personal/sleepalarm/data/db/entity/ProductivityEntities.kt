package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import com.personal.sleepalarm.domain.model.FocusActivityType

/** A local project/goal container. Projects never require a network account. */
@Entity(
    tableName = "projects",
    indices = [Index(value = ["isArchived"]), Index(value = ["dueAtMillis"])]
)
data class ProjectEntity(
    @androidx.room.PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val description: String = "",
    val goal: String = "",
    val color: Long = 0xFF6574CD,
    val workBudgetMinutes: Int = 0,
    val spentMillis: Long = 0L,
    val dueAtMillis: Long? = null,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Ordered, independently completable step inside a task. */
@Entity(
    tableName = "task_subtasks",
    indices = [Index(value = ["taskId"])]
)
data class TaskSubtaskEntity(
    @androidx.room.PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val taskId: Int,
    val title: String,
    val isDone: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

/** Additional local file attached to a task; TaskEntity.imagePath remains its cover. */
@Entity(
    tableName = "task_attachments",
    indices = [Index(value = ["taskId"])]
)
data class TaskAttachmentEntity(
    @androidx.room.PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val taskId: Int,
    val localPath: String,
    val mimeType: String = "application/octet-stream",
    val caption: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** Many-to-many connection between the local library and tasks. */
@Entity(
    tableName = "task_library_links",
    primaryKeys = ["taskId", "libraryItemId"],
    indices = [Index(value = ["libraryItemId"])]
)
data class TaskLibraryLinkEntity(
    val taskId: Int,
    val libraryItemId: Int,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Canonical history of real work. Calendar plans are deliberately absent here:
 * only a TIMER or MANUAL record can reduce a task/project work budget.
 */
@Entity(
    tableName = "activity_records",
    indices = [
        Index(value = ["startedAt"]),
        Index(value = ["taskId"]),
        Index(value = ["projectId"]),
        Index(value = ["pomodoroSessionId"], unique = true)
    ]
)
data class ActivityRecordEntity(
    @androidx.room.PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val taskId: Int? = null,
    val projectId: Int? = null,
    val activityType: FocusActivityType = FocusActivityType.WORK,
    val subjectId: Int? = null,
    val otherActivityId: Int? = null,
    /** Snapshot kept readable after a linked object is renamed/deleted. */
    val title: String,
    val category: String = "WORK",
    val startedAt: Long,
    val endedAt: Long,
    val durationMillis: Long,
    /** TIMER or MANUAL. Calendar plans are stored in events, never as this source. */
    val source: String,
    val result: String = "",
    val material: String = "",
    val note: String = "",
    val pomodoroSessionId: Int? = null,
    val countsTowardProgress: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
