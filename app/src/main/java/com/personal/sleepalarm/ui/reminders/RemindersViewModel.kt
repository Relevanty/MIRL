package com.personal.sleepalarm.ui.reminders

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.alarm.ReminderScheduler
import com.personal.sleepalarm.alarm.TaskDeadlineScheduler
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.ReminderEntity
import com.personal.sleepalarm.data.repository.ReminderRepository
import com.personal.sleepalarm.service.ReminderNotificationBuilder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel списка напоминаний.
 *
 * Отвечает за список, включение/выключение, удаление и перепланирование.
 */
class RemindersViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = AppDatabase.getInstance(context)
    private val repository = ReminderRepository(database.reminderDao(), database.taskDao(), database.activityRecordDao())
    private val scheduler = ReminderScheduler(context)
    private val notifications = ReminderNotificationBuilder(context)
    private val deadlineScheduler = TaskDeadlineScheduler(context)

    val uiState: StateFlow<List<ReminderEntity>> = repository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** Вкл/выкл напоминания + перепланирование alarm'ов. */
    fun setEnabled(reminder: ReminderEntity, enabled: Boolean) {
        viewModelScope.launch {
            val updated = repository.setEnabled(reminder.id, enabled)
            if (enabled) {
                val reconciled = updated?.let { repository.reconcileForScheduling(it) }
                if (reconciled != null) {
                    scheduler.schedule(reconciled)
                } else {
                    scheduler.cancel(reminder.id)
                    notifications.cancelPre(reminder.id)
                    notifications.cancelFire(reminder.id)
                }
            } else {
                scheduler.cancel(reminder.id)
                notifications.cancelPre(reminder.id)
                notifications.cancelFire(reminder.id)
            }
            reminder.linkedId?.takeIf { reminder.linkedType == "TASK" }?.let {
                reconcileDeadlineFallback(it)
            }
        }
    }

    /** Удаление: гасим alarm'ы, чистим ссылку у задачи. */
    fun delete(reminder: ReminderEntity) {
        viewModelScope.launch {
            scheduler.cancel(reminder.id)
            notifications.cancelPre(reminder.id)
            notifications.cancelFire(reminder.id)
            repository.delete(reminder.id)
            reminder.linkedId?.takeIf { reminder.linkedType == "TASK" }?.let {
                reconcileDeadlineFallback(it)
            }
        }
    }

    /** Перепланировать все активные (например, после правки точных alarm'ов). */
    fun rescheduleAll() {
        viewModelScope.launch {
            val all = uiState.value
            scheduler.rescheduleAll(all)
        }
    }

    private suspend fun reconcileDeadlineFallback(taskId: Int) {
        val task = database.taskDao().getById(taskId) ?: return
        val hasExplicit = task.dueAtMillis != null && database.reminderDao().getLinkedToTask(taskId).any {
            it.isEnabled && it.triggerRule in setOf("BEFORE_DEADLINE", "BECOMES_URGENT")
        }
        if (hasExplicit) deadlineScheduler.cancel(taskId) else deadlineScheduler.schedule(task)
    }
}
