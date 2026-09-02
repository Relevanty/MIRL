package com.personal.sleepalarm.data.repository

import androidx.room.withTransaction
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.data.db.entity.ReminderEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.focusActivityType
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.domain.model.taskFocusItemId
import com.personal.sleepalarm.util.DeadlineLinks
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TaskSaveResult(
    val task: TaskEntity,
    val remindersToReschedule: List<ReminderEntity>
)

data class TaskDeleteResult(
    val reminderIds: List<Int>,
    val attachmentPaths: List<String>
)

/**
 * The single write boundary for the live task graph.
 *
 * Historical Pomodoro/activity rows deliberately keep snapshots, but every
 * live task projection (active focus, reminder, calendar plan and task-deadline
 * D-Day) follows the canonical TaskEntity. A task-linked D-Day is only optional
 * deadline metadata; standalone events own their independent dates.
 */
class TaskEcosystemRepository(private val database: AppDatabase) {
    private val tasks = database.taskDao()
    private val reminders = database.reminderDao()
    private val events = database.calendarEventDao()
    private val milestones = database.ddayDao()
    private val protocols = database.focusProtocolDao()
    private val activities = database.activityRecordDao()
    private val projects = database.projectDao()
    private val subtasks = database.taskSubtaskDao()
    private val attachments = database.taskAttachmentDao()
    private val libraryLinks = database.taskLibraryLinkDao()

    suspend fun save(task: TaskEntity): TaskSaveResult? = database.withTransaction {
        val previous = task.id.takeIf { it != 0 }?.let { id -> tasks.getById(id) }
        val candidate = previous?.let { current ->
            // These fields belong to completion/activity/reminder commands, not
            // to a possibly stale editor snapshot.
            task.copy(
                isDone = current.isDone,
                completedAt = current.completedAt,
                doneDate = current.doneDate,
                streakCount = current.streakCount,
                // ReminderEntity(linkedType/linkId) is the one-to-many source
                // of truth; the legacy scalar reverse pointer is retired.
                reminderId = null,
                spentMillis = current.spentMillis,
                updatedAt = System.currentTimeMillis()
            )
        } ?: task
        val id = if (candidate.id == 0) tasks.insert(candidate).toInt() else {
            if (tasks.update(candidate) == 0) return@withTransaction null
            candidate.id
        }
        val saved = tasks.getById(id) ?: candidate.copy(id = id)
        synchronizeLiveProjections(previous, saved)
        TaskSaveResult(saved, reminders.getLinkedToTask(id).filter(ReminderEntity::isEnabled))
    }

    /** Null taskDueAtMillis means metadata-only; cached dates cannot resurrect a cleared due date. */
    suspend fun saveDeadline(event: DDayEntity, taskDueAtMillis: Long? = null): DDayEntity? = database.withTransaction {
        if ((event.id < 0 && event.taskId != -event.id) || event.title.isBlank() ||
            runCatching { LocalDate.parse(event.targetDate) }.isFailure
        ) return@withTransaction null
        val current = if (event.id <= 0) null else {
            milestones.getById(event.id) ?: return@withTransaction null
        }
        val linkedTask = event.taskId?.let { tasks.getById(it) }
        if (event.taskId != null && linkedTask == null) return@withTransaction null
        if (linkedTask == null && event.projectId != null && projects.getById(event.projectId) == null) {
            return@withTransaction null
        }
        val dueAt = taskDueAtMillis ?: linkedTask?.dueAtMillis
        var normalized = normalizeDeadlineForSave(event, current).copy(
            id = event.id.coerceAtLeast(0),
            targetDate = if (linkedTask != null && dueAt != null) taskDeadlineLocalDate(dueAt) else event.targetDate
        )
        val destination = linkedTask?.let { milestones.getForTask(it.id) }
        if (destination != null && destination.id != current?.id) {
            // A create/relink action opens the one metadata row rather than
            // replacing another row and losing its links or notes.
            normalized = mergeDeadlineMetadata(
                normalized.copy(id = destination.id, createdAt = destination.createdAt),
                listOf(destination)
            )
            current?.let { milestones.deleteById(it.id) }
        }
        if (linkedTask != null) normalized = canonicalTaskDeadlineDetails(normalized, linkedTask)
        val saved = if (normalized.id == 0) {
            normalized.copy(id = milestones.insert(normalized).toInt())
        } else {
            milestones.update(normalized)
            normalized
        }
        if (linkedTask != null && taskDueAtMillis != null && linkedTask.dueAtMillis != taskDueAtMillis) {
            checkNotNull(save(linkedTask.copy(dueAtMillis = taskDueAtMillis)))
        }
        current?.taskId?.takeIf { it != saved.taskId }?.let { oldTaskId ->
            tasks.getById(oldTaskId)?.let { synchronizeDeadlineMetadata(it) }
        }
        milestones.getById(saved.id) ?: saved
    }

    /** Linked removal clears the task due date but keeps notes/links for future reuse. */
    suspend fun deleteDeadline(id: Int): Int? = database.withTransaction {
        val row = if (id > 0) milestones.getById(id) else null
        val taskId = row?.taskId ?: (-id).takeIf { id < 0 }
        if (taskId != null) {
            val task = tasks.getById(taskId) ?: return@withTransaction null
            checkNotNull(save(task.copy(dueAtMillis = null)))
            taskId
        } else {
            if (id > 0) milestones.deleteById(id)
            null
        }
    }

    suspend fun rename(taskId: Int, newTitle: String): TaskSaveResult? {
        return database.withTransaction {
            val previous = tasks.getById(taskId) ?: return@withTransaction null
            tasks.updateTitle(taskId, newTitle.trim())
            val saved = tasks.getById(taskId) ?: return@withTransaction null
            synchronizeLiveProjections(previous, saved)
            TaskSaveResult(saved, reminders.getLinkedToTask(taskId).filter(ReminderEntity::isEnabled))
        }
    }

    suspend fun toggleDone(taskId: Int): TaskSaveResult? = database.withTransaction {
        val previous = tasks.getById(taskId) ?: return@withTransaction null
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        if (previous.isMorningRoutine && previous.doneDate == today) {
            return@withTransaction TaskSaveResult(
                previous,
                reminders.getLinkedToTask(taskId).filter(ReminderEntity::isEnabled)
            )
        }
        if (previous.isMorningRoutine) {
            val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            tasks.updateCompletion(
                id = taskId,
                isDone = true,
                doneDate = today,
                completedAt = System.currentTimeMillis(),
                streakCount = if (previous.doneDate == yesterday) previous.streakCount + 1 else 1
            )
        } else {
            val completing = !previous.isDone
            tasks.updateCompletion(
                id = taskId,
                isDone = completing,
                doneDate = today.takeIf { completing },
                completedAt = System.currentTimeMillis().takeIf { completing },
                streakCount = 0
            )
        }
        val saved = tasks.getById(taskId) ?: return@withTransaction null
        synchronizeLiveProjections(previous, saved)
        TaskSaveResult(saved, reminders.getLinkedToTask(taskId).filter(ReminderEntity::isEnabled))
    }

    suspend fun move(taskId: Int, quadrant: Int, sortOrder: Int): TaskSaveResult? =
        database.withTransaction {
            val previous = tasks.getById(taskId) ?: return@withTransaction null
            if (tasks.updateMatrixPosition(taskId, quadrant.coerceIn(1, 4), sortOrder) == 0) {
                return@withTransaction null
            }
            val saved = tasks.getById(taskId) ?: return@withTransaction null
            synchronizeLiveProjections(previous, saved)
            TaskSaveResult(saved, reminders.getLinkedToTask(taskId).filter(ReminderEntity::isEnabled))
        }

    /** Atomically persists one quadrant's visual order without partial UI states. */
    suspend fun reorder(taskIdsInOrder: List<Int>) = database.withTransaction {
        taskIdsInOrder.distinct().forEachIndexed { index, taskId ->
            val task = tasks.getById(taskId) ?: return@forEachIndexed
            if (!task.isDone && !task.isMorningRoutine) {
                tasks.updateSortOrder(taskId, index)
            }
        }
    }

    suspend fun synchronize(taskId: Int): TaskSaveResult? {
        val current = tasks.getById(taskId) ?: return null
        return database.withTransaction {
            synchronizeLiveProjections(current, current)
            TaskSaveResult(current, reminders.getLinkedToTask(taskId).filter(ReminderEntity::isEnabled))
        }
    }

    suspend fun delete(taskId: Int): TaskDeleteResult = database.withTransaction {
        val linkedReminders = reminders.getLinkedToTask(taskId)
        val attachmentPaths = attachments.getForTask(taskId).map { it.localPath }

        reminders.deleteLinkedToTask(taskId)
        events.detachTask(taskId)
        // A deleted task has no independent deadline. Detaching retained hidden
        // metadata would incorrectly resurrect the previously cleared date.
        milestones.deleteForTask(taskId)
        subtasks.deleteForTask(taskId)
        attachments.deleteForTask(taskId)
        libraryLinks.deleteForTask(taskId)
        tasks.deleteById(taskId)

        TaskDeleteResult(linkedReminders.map(ReminderEntity::id), attachmentPaths)
    }

    suspend fun repairIntegrity() = database.withTransaction {
        val repairedAt = System.currentTimeMillis()
        tasks.clearMissingProjects()
        tasks.clearLegacyReminderLinks()
        events.detachMissingTasks()
        milestones.detachMissingTasks()
        reminders.disableMissingTaskLinks()
        protocols.cancelMissingTaskTargets(repairedAt)
        protocols.cancelMissingLegacyTaskTargets(repairedAt)
        subtasks.deleteOrphans()
        attachments.deleteOrphans()
        libraryLinks.deleteOrphans()
        activities.rebuildAllTaskTotals()
        activities.rebuildAllProjectTotals()
        tasks.getAll().forEach { synchronizeDeadlineMetadata(it) }
    }

    private suspend fun synchronizeLiveProjections(previous: TaskEntity?, saved: TaskEntity) {
        val oldLabel = previous?.primaryLabel() ?: saved.primaryLabel()
        val newLabel = saved.primaryLabel()

        reminders.syncTaskTitle(saved.id, oldLabel, newLabel)
        events.syncTaskProjection(saved.id, oldLabel, newLabel, saved.projectId)
        milestones.syncTaskTitle(saved.id, oldLabel, newLabel)
        synchronizeDeadlineMetadata(saved)
        protocols.syncActiveTaskTarget(
            itemId = taskFocusItemId(saved.id),
            itemName = newLabel,
            activityType = saved.focusActivityType()
        )
        protocols.migrateAndSyncLegacyTaskTarget(
            legacyTaskId = saved.id,
            canonicalItemId = taskFocusItemId(saved.id),
            itemName = newLabel,
            activityType = saved.focusActivityType()
        )

        if (previous?.projectId != saved.projectId) {
            activities.reassignProjectForTask(saved.id, saved.projectId)
        }
        tasks.setSpentMillis(saved.id, activities.sumForTask(saved.id))
        setOfNotNull(previous?.projectId, saved.projectId).forEach { projectId ->
            projects.setSpent(projectId, activities.sumForProject(projectId))
        }
    }

    private suspend fun synchronizeDeadlineMetadata(task: TaskEntity) {
        val dueAt = task.dueAtMillis ?: return
        val metadata = milestones.getForTask(task.id)
        if (metadata == null) {
            milestones.insert(taskDeadlineMetadata(task))
        } else {
            milestones.update(metadata.copy(
                title = task.primaryLabel(),
                targetDate = taskDeadlineLocalDate(dueAt),
                projectId = task.projectId
            ))
        }
    }
}

/** Normalization shared by both new and existing deadline saves. */
internal fun normalizeDeadlineForSave(edited: DDayEntity, current: DDayEntity?): DDayEntity =
    edited.copy(
        title = edited.title.trim(),
        notes = edited.notes.trim(),
        linksJson = DeadlineLinks.encode(DeadlineLinks.decode(edited.linksJson)),
        createdAt = current?.createdAt ?: edited.createdAt
    )

internal fun deadlineTaskDueAtMillis(
    targetDate: String,
    previousDueAtMillis: Long?,
    zone: ZoneId = ZoneId.systemDefault()
): Long {
    val previous = previousDueAtMillis?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val time = previous?.toLocalTime() ?: LocalTime.of(23, 59, 59, 999_000_000)
    return LocalDate.parse(targetDate).atTime(time).atZone(zone).toInstant().toEpochMilli()
}
