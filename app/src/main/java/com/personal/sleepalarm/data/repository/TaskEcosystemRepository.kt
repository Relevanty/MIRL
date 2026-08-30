package com.personal.sleepalarm.data.repository

import androidx.room.withTransaction
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.ReminderEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.focusActivityType
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.domain.model.taskFocusItemId
import java.time.Instant
import java.time.LocalDate
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
 * live projection (active focus, reminder, calendar plan and D-Day) follows
 * the canonical TaskEntity. This prevents screen-specific DAO writes from
 * creating several conflicting versions of the same task.
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
        milestones.detachTask(taskId)
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
    }

    private suspend fun synchronizeLiveProjections(previous: TaskEntity?, saved: TaskEntity) {
        val oldLabel = previous?.primaryLabel() ?: saved.primaryLabel()
        val newLabel = saved.primaryLabel()

        reminders.syncTaskTitle(saved.id, oldLabel, newLabel)
        events.syncTaskProjection(saved.id, oldLabel, newLabel, saved.projectId)
        milestones.syncTaskTitle(saved.id, oldLabel, newLabel)
        milestones.syncTaskDeadline(
            saved.id,
            saved.dueAtMillis?.let { due ->
                Instant.ofEpochMilli(due)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
            }
        )
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
}
