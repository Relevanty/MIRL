package com.personal.sleepalarm.ui.reminders

import android.app.Application
import androidx.room.withTransaction
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.alarm.ReminderScheduler
import com.personal.sleepalarm.alarm.TaskDeadlineScheduler
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.RepeatMode
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.data.repository.ReminderRepository
import com.personal.sleepalarm.domain.model.primaryLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
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
    val intervalDays: Int = 1,
    val triggerRule: String = "AT_TIME",
    val offsetMinutes: Int = 60,
    val inactivityHours: Int = 24
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
    private val repository = ReminderRepository(database.reminderDao(), database.taskDao(), database.activityRecordDao())
    private val scheduler = ReminderScheduler(context)
    private val deadlineScheduler = TaskDeadlineScheduler(context)

    private val _state = MutableStateFlow(ReminderEditState())
    val state: StateFlow<ReminderEditState> = _state
    val tasks: StateFlow<List<TaskEntity>> = database.taskDao().observeAll()
        .map { items -> items.filter { !it.isMorningRoutine && !it.isDone } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                        intervalDays = r.intervalDays,
                        triggerRule = r.triggerRule,
                        offsetMinutes = r.offsetMinutes,
                        inactivityHours = r.inactivityHours,
                        linkedTaskId = r.linkedId.takeIf { r.linkedType == "TASK" }
                    )
                }
            }
        } else if (linkedTaskId != null) {
            viewModelScope.launch {
                val task = database.taskDao().getById(linkedTaskId) ?: return@launch
                _state.update { current ->
                    if (current.reminderId == null &&
                        current.linkedTaskId == linkedTaskId &&
                        current.title.isBlank()
                    ) {
                        current.copy(title = task.primaryLabel())
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun setTimeHour(v: Int) = _state.update { it.copy(timeHour = v.coerceIn(0, 23)) }
    fun setTimeMinute(v: Int) = _state.update { it.copy(timeMinute = v.coerceIn(0, 59)) }
    fun setRepeatMode(v: RepeatMode) = _state.update { it.copy(repeatMode = v) }
    fun setIntervalDays(v: Int) = _state.update { it.copy(intervalDays = v.coerceIn(1, 365)) }
    fun setLinkedTask(id: Int?) = _state.update { current ->
        val oldLabel = tasks.value.firstOrNull { it.id == current.linkedTaskId }?.primaryLabel()
        val newLabel = tasks.value.firstOrNull { it.id == id }?.primaryLabel()
        val followsTaskTitle = current.title.isBlank() || current.title.trim() == oldLabel
        current.copy(
            linkedTaskId = id,
            title = if (id != null && followsTaskTitle && newLabel != null) newLabel else current.title
        )
    }
    fun setTriggerRule(v: String) = _state.update { it.copy(triggerRule = v) }
    fun setOffsetMinutes(v: Int) = _state.update { it.copy(offsetMinutes = v.coerceIn(0, 43_200)) }
    fun setInactivityHours(v: Int) = _state.update { it.copy(inactivityHours = v.coerceIn(1, 720)) }

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
        val requiresTask = s.triggerRule in setOf("BEFORE_DEADLINE", "NO_PROGRESS", "BECOMES_URGENT", "BEFORE_FOCUS")
        if (requiresTask && s.linkedTaskId == null) return false
        if (s.triggerRule in setOf("BEFORE_DEADLINE", "BECOMES_URGENT") &&
            tasks.value.firstOrNull { it.id == s.linkedTaskId }?.dueAtMillis == null
        ) return false
        if (s.triggerRule == "BEFORE_FOCUS" &&
            tasks.value.firstOrNull { it.id == s.linkedTaskId }?.startAtMillis == null
        ) return false

        viewModelScope.launch {
            val oldLinkedTaskId = s.reminderId
                ?.let { repository.getById(it) }
                ?.linkedId
            val id = database.withTransaction<Long?> {
                val savedId = if (s.reminderId == null) {
                    repository.create(
                        title = s.title,
                        timeHour = s.timeHour,
                        timeMinute = s.timeMinute,
                        repeatMode = s.repeatMode,
                        daysOfWeek = if (s.repeatMode == RepeatMode.WEEKLY) s.daysOfWeek else 0,
                        intervalDays = if (s.repeatMode == RepeatMode.INTERVAL) s.intervalDays else 1,
                        linkedType = if (s.linkedTaskId != null) "TASK" else "",
                        linkedId = s.linkedTaskId,
                        triggerRule = s.triggerRule,
                        offsetMinutes = s.offsetMinutes,
                        inactivityHours = s.inactivityHours
                    )
                } else {
                    val existing = repository.getById(s.reminderId)
                        ?: return@withTransaction null
                    repository.update(
                        existing.copy(
                            title = s.title.trim(),
                            timeHour = s.timeHour,
                            timeMinute = s.timeMinute,
                            repeatMode = s.repeatMode,
                            daysOfWeek = if (s.repeatMode == RepeatMode.WEEKLY) s.daysOfWeek else 0,
                            intervalDays = if (s.repeatMode == RepeatMode.INTERVAL) s.intervalDays else 1,
                            isEnabled = true,
                            linkedType = if (s.linkedTaskId != null) "TASK" else "",
                            linkedId = s.linkedTaskId,
                            triggerRule = s.triggerRule,
                            offsetMinutes = s.offsetMinutes,
                            inactivityHours = s.inactivityHours
                        )
                    )
                    s.reminderId.toLong()
                }

                // ReminderEntity is the only source of truth for task links.
                // Clear a legacy reverse pointer instead of creating another
                // conflicting one-to-many representation.
                database.taskDao().clearReminderLink(savedId.toInt())
                savedId
            } ?: return@launch

            // Планирование обоих alarm'ов.
            val saved = repository.getById(id.toInt())
            val reconciled = saved?.let { repository.reconcileForScheduling(it) }
            if (reconciled != null) scheduler.schedule(reconciled)
            else scheduler.cancel(id.toInt())
            setOfNotNull(oldLinkedTaskId, s.linkedTaskId).forEach { taskId ->
                reconcileDeadlineFallback(taskId)
            }
        }
        return true
    }

    private suspend fun reconcileDeadlineFallback(taskId: Int) {
        val task = database.taskDao().getById(taskId) ?: return
        val hasExplicit = task.dueAtMillis != null && database.reminderDao().getLinkedToTask(taskId).any {
            it.isEnabled && it.triggerRule in setOf("BEFORE_DEADLINE", "BECOMES_URGENT")
        }
        if (hasExplicit) deadlineScheduler.cancel(taskId) else deadlineScheduler.schedule(task)
    }
}
