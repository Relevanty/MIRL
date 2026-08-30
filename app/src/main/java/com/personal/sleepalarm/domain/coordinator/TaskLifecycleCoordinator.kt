package com.personal.sleepalarm.domain.coordinator

import android.content.Context
import com.personal.sleepalarm.alarm.ReminderScheduler
import com.personal.sleepalarm.alarm.EventAlarmScheduler
import com.personal.sleepalarm.alarm.TaskDeadlineScheduler
import com.personal.sleepalarm.alarm.TaskLinkedReminderCoordinator
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.data.repository.TaskDeleteResult
import com.personal.sleepalarm.data.repository.TaskEcosystemRepository
import com.personal.sleepalarm.data.repository.TaskSaveResult
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.domain.model.taskFocusItemId
import com.personal.sleepalarm.service.ReminderNotificationBuilder
import com.personal.sleepalarm.service.EventNotificationBuilder
import com.personal.sleepalarm.service.focus.FocusProtocolManager
import com.personal.sleepalarm.util.CoverHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single Android-side command boundary for the live task graph.
 *
 * Room owns the atomic data change; this coordinator owns every post-commit
 * alarm, notification and file side effect. Calling it from Focus and Tasks
 * therefore has identical behavior.
 */
class TaskLifecycleCoordinator(context: Context, private val database: AppDatabase) {
    private val appContext = context.applicationContext
    private val ecosystem = TaskEcosystemRepository(database)
    private val deadlineScheduler = TaskDeadlineScheduler(appContext)
    private val linkedReminders = TaskLinkedReminderCoordinator(appContext, database)
    private val reminderScheduler = ReminderScheduler(appContext)
    private val reminderNotifications = ReminderNotificationBuilder(appContext)
    private val eventScheduler = EventAlarmScheduler(appContext)
    private val eventNotifications = EventNotificationBuilder(appContext)
    private val focusManager = FocusProtocolManager(appContext)

    suspend fun save(task: TaskEntity): TaskSaveResult? {
        val result = ecosystem.save(task) ?: return null
        taskChanged(result.task)
        return result
    }

    suspend fun rename(taskId: Int, title: String): TaskSaveResult? {
        val result = ecosystem.rename(taskId, title) ?: return null
        taskChanged(result.task)
        return result
    }

    suspend fun synchronize(taskId: Int): TaskSaveResult? {
        val result = ecosystem.synchronize(taskId) ?: return null
        taskChanged(result.task)
        return result
    }

    suspend fun toggleDone(taskId: Int): TaskSaveResult? {
        val result = ecosystem.toggleDone(taskId) ?: return null
        if (result.task.isDone) {
            cancelActiveFocusForTask(taskId, "TASK_COMPLETED")
        }
        taskChanged(result.task)
        return result
    }

    suspend fun move(taskId: Int, quadrant: Int, sortOrder: Int): TaskSaveResult? {
        val result = ecosystem.move(taskId, quadrant, sortOrder) ?: return null
        taskChanged(result.task)
        return result
    }

    /** Reordering has no alarm side effects, but still crosses one task write boundary. */
    suspend fun reorder(taskIdsInOrder: List<Int>) {
        ecosystem.reorder(taskIdsInOrder)
    }

    suspend fun delete(taskId: Int): TaskDeleteResult? {
        val task = database.taskDao().getById(taskId) ?: return null
        val taskImageOwnedExclusively = task.imagePath != null &&
            database.taskDao().getAll().none { other ->
                other.id != task.id && other.imagePath == task.imagePath
            }

        // Cancel through the manager while the task still exists: it records
        // elapsed focus and also removes the alarm and ongoing notification.
        cancelActiveFocusForTask(taskId, "TASK_DELETED")

        deadlineScheduler.cancel(taskId)
        val result = ecosystem.delete(taskId)
        result.reminderIds.forEach { reminderId ->
            reminderScheduler.cancel(reminderId)
            reminderNotifications.cancelPre(reminderId)
            reminderNotifications.cancelFire(reminderId)
        }
        withContext(Dispatchers.IO) {
            if (taskImageOwnedExclusively) CoverHelper.deleteCover(task.imagePath)
            result.attachmentPaths.forEach(CoverHelper::deleteCover)
        }
        return result
    }

    private suspend fun cancelActiveFocusForTask(taskId: Int, reason: String) {
        database.focusProtocolDao().getActive()
            .filter { session ->
                session.itemId == taskFocusItemId(taskId) ||
                    (session.activityType == FocusActivityType.WORK && session.itemId == taskId)
            }
            .forEach { session -> focusManager.cancel(session.id, reason) }
    }

    private suspend fun taskChanged(task: TaskEntity) {
        val hasExplicitDeadlineReminder = task.dueAtMillis != null && database.reminderDao().getLinkedToTask(task.id).any {
            it.isEnabled && it.triggerRule in setOf("BEFORE_DEADLINE", "BECOMES_URGENT")
        }
        if (hasExplicitDeadlineReminder) deadlineScheduler.cancel(task.id)
        else deadlineScheduler.schedule(task)
        linkedReminders.taskChanged(task.id)
        database.calendarEventDao().getLinkedToTask(task.id).forEach { event ->
            // Event notifications are snapshots. Remove the old projection on
            // rename/category/completion and restore only a still-live plan.
            eventNotifications.cancel(event.id)
            if (task.isDone) eventScheduler.cancel(event.id)
            else eventScheduler.schedule(event)
        }
        // Room projections are already canonical at this point; refresh the
        // active protocol's alarm and ongoing notification as well so a rename
        // or category change is visible immediately outside the app UI.
        focusManager.reconcileActiveSessions()
    }
}
