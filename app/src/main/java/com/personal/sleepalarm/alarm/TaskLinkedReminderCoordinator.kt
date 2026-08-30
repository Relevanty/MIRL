package com.personal.sleepalarm.alarm

import android.content.Context
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.repository.ReminderRepository
import com.personal.sleepalarm.service.ReminderNotificationBuilder

/** Re-evaluates live reminders whenever their canonical task changes. */
class TaskLinkedReminderCoordinator(
    context: Context,
    private val database: AppDatabase
) {
    private val repository = ReminderRepository(
        database.reminderDao(),
        database.taskDao(),
        database.activityRecordDao()
    )
    private val scheduler = ReminderScheduler(context.applicationContext)
    private val notifications = ReminderNotificationBuilder(context.applicationContext)

    suspend fun taskChanged(taskId: Int) {
        val task = database.taskDao().getById(taskId)
        database.reminderDao().getLinkedToTask(taskId).forEach { reminder ->
            // A visible PRE/FIRE row is a snapshot (title, deadline and task
            // actions). Remove it before deriving the new projection so an
            // edit cannot leave an old task name or obsolete deadline in the
            // shade.
            notifications.cancelPre(reminder.id)
            notifications.cancelFire(reminder.id)
            val schedulable = task != null && repository.isSchedulable(reminder)
            if (!schedulable) {
                // Suspend it without changing the user's enabled switch. A restored
                // task can be scheduled again, while a manually disabled reminder
                // remains disabled for its own reason.
                scheduler.cancel(reminder.id)
            } else {
                scheduler.schedule(repository.refreshDynamic(reminder))
            }
        }
    }
}
