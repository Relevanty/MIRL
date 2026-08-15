package com.personal.sleepalarm.ui.reminders

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.alarm.ReminderScheduler
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.RepeatMode
import com.personal.sleepalarm.data.repository.ReminderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Черновик напоминания (создание / редактирование).
 */
data class ReminderEditState(
    val reminderId: Int? = null,
    val linkedTaskId: Int? = null,
    val title: String = "",
    val timeHour: Int = 8,
    val timeMinute: Int = 0,
    val repeatMode: RepeatMode = RepeatMode.ONCE,
    val daysOfWeek: Int = 0,
    val intervalDays: Int = 1
)

/**
 * ViewModel формы напоминания.
 *
 * При сохранении:
 * - создаёт или обновляет ReminderEntity (nextTriggerTime считается в репозитории);
 * - если linkedTaskId != null — привязывает напоминание к задаче;
 * - ставит оба alarm'а через ReminderScheduler.
 */
class ReminderEditViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = AppDatabase.getInstance(context)
    private val repository = ReminderRepository(database.reminderDao(), database.taskDao())
    private val scheduler = ReminderScheduler(context)

    private val _state = MutableStateFlow(ReminderEditState())
    val state: StateFlow<ReminderEditState> = _state

    /** Вызывается один раз из LaunchedEffect экрана. */
    fun init(editReminderId: Int?, linkedTaskId: Int?) {
        _state.update { it.copy(reminderId = editReminderId, linkedTaskId = linkedTaskId) }

        if (editReminderId != null) {
            viewModelScope.launch {
                val r = repository.getById(editReminderId) ?: return@launch
                _state.update {
                    it.copy(
                        title = r.title,
                        timeHour = r.timeHour,
                        timeMinute = r.timeMinute,
                        repeatMode = r.repeatMode,
                        daysOfWeek = r.daysOfWeek,
                        intervalDays = r.intervalDays
                    )
                }
            }
        }
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun setTimeHour(v: Int) = _state.update { it.copy(timeHour = v.coerceIn(0, 23)) }
    fun setTimeMinute(v: Int) = _state.update { it.copy(timeMinute = v.coerceIn(0, 59)) }
    fun setRepeatMode(v: RepeatMode) = _state.update { it.copy(repeatMode = v) }
    fun setIntervalDays(v: Int) = _state.update { it.copy(intervalDays = v.coerceIn(1, 365)) }

    /** Переключает бит дня недели (Пн=1..Вс=7 → бит). */
    fun toggleDay(dayOfWeekValue: Int) {
        _state.update { s ->
            val bit = 1 shl (dayOfWeekValue - 1)
            s.copy(daysOfWeek = s.daysOfWeek xor bit)
        }
    }

    /** Сохраняет. Возвращает true при успехе. */
    fun save(): Boolean {
        val s = _state.value
        if (s.title.isBlank()) return false

        viewModelScope.launch {
            val id: Long = if (s.reminderId == null) {
                repository.create(
                    title = s.title,
                    timeHour = s.timeHour,
                    timeMinute = s.timeMinute,
                    repeatMode = s.repeatMode,
                    daysOfWeek = if (s.repeatMode == RepeatMode.WEEKLY) s.daysOfWeek else 0,
                    intervalDays = if (s.repeatMode == RepeatMode.INTERVAL) s.intervalDays else 1
                )
            } else {
                val existing = repository.getById(s.reminderId)
                if (existing != null) {
                    repository.update(
                        existing.copy(
                            title = s.title.trim(),
                            timeHour = s.timeHour,
                            timeMinute = s.timeMinute,
                            repeatMode = s.repeatMode,
                            daysOfWeek = if (s.repeatMode == RepeatMode.WEEKLY) s.daysOfWeek else 0,
                            intervalDays = if (s.repeatMode == RepeatMode.INTERVAL) s.intervalDays else 1,
                            isEnabled = true
                        )
                    )
                }
                s.reminderId.toLong()
            }

            // Привязка к задаче.
            s.linkedTaskId?.let { taskId ->
                database.taskDao().setReminderId(taskId, id.toInt())
            }

            // Планирование обоих alarm'ов.
            val saved = repository.getById(id.toInt())
            if (saved != null) scheduler.schedule(saved)
        }
        return true
    }
}