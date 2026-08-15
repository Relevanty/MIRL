package com.personal.sleepalarm.ui.reminders

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.alarm.ReminderScheduler
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.ReminderEntity
import com.personal.sleepalarm.data.repository.ReminderRepository
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
    private val repository = ReminderRepository(database.reminderDao(), database.taskDao())
    private val scheduler = ReminderScheduler(context)

    val uiState: StateFlow<List<ReminderEntity>> = repository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** Вкл/выкл напоминания + перепланирование alarm'ов. */
    fun setEnabled(reminder: ReminderEntity, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(reminder.id, enabled)
            if (enabled) {
                val updated = repository.getById(reminder.id)
                if (updated != null) scheduler.schedule(updated)
            } else {
                scheduler.cancel(reminder.id)
            }
        }
    }

    /** Удаление: гасим alarm'ы, чистим ссылку у задачи. */
    fun delete(reminder: ReminderEntity) {
        viewModelScope.launch {
            scheduler.cancel(reminder.id)
            repository.delete(reminder.id)
        }
    }

    /** Перепланировать все активные (например, после правки точных alarm'ов). */
    fun rescheduleAll() {
        viewModelScope.launch {
            val enabled = repository.getById(-1) // no-op, чтобы не плодить методы
            val all = uiState.value
            scheduler.rescheduleAll(all)
        }
    }
}