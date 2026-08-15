package com.personal.sleepalarm.ui.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Состояние экрана задач.
 *
 * today — yyyy-MM-dd для визуального сброса утренней рутины.
 * morningRoutine / general — списки задач.
 * pendingReminderTaskId — ID задачи, для которой надо создать напоминание
 * (состояние, которое будет прочитано из MainActivity для открытия ReminderEditScreen).
 */
data class TasksUiState(
    val today: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val morningRoutine: List<TaskEntity> = emptyList(),
    val generalTasks: List<TaskEntity> = emptyList(),
    val pendingReminderTaskId: Int? = null,
    val draftTitle: String = "",
    val draftIsMorning: Boolean = false
)

/**
 * ViewModel экрана задач.
 *
 * Отдельно наблюдает утреннюю рутину и общие задачи; применяет
 * визуальный сброс isDone для утренней рутины при наступлении нового дня.
 */
class TasksViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application.applicationContext)
    private val repository = TaskRepository(database.taskDao())

    private val _draftTitle = MutableStateFlow("")
    private val _draftIsMorning = MutableStateFlow(false)
    private val _pendingReminderTaskId = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<TasksUiState> = combine(
        repository.observeMorningRoutine(),
        repository.observeGeneralTasks(),
        _draftTitle,
        _draftIsMorning,
        _pendingReminderTaskId
    ) { morning, general, draft, isMorningDraft, pending ->
        TasksUiState(
            today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
            morningRoutine = morning,
            generalTasks = general,
            pendingReminderTaskId = pending,
            draftTitle = draft,
            draftIsMorning = isMorningDraft
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TasksUiState()
    )

    // =====================================================================
    // Черновик новой задачи
    // =====================================================================

    fun setDraftTitle(value: String) {
        _draftTitle.value = value
    }

    fun setDraftIsMorning(value: Boolean) {
        _draftIsMorning.value = value
    }

    fun addDraftTask() {
        val title = _draftTitle.value.trim()
        if (title.isBlank()) return

        viewModelScope.launch {
            repository.addTask(
                title = title,
                isMorningRoutine = _draftIsMorning.value
            )
            _draftTitle.value = ""
        }
    }

    // =====================================================================
    // Отметка выполнения / удаление
    // =====================================================================

    fun toggleDone(task: TaskEntity) {
        viewModelScope.launch {
            if (task.isMorningRoutine) {
                // Утренняя рутина — только отметка вперёд (markDone внутри проверит).
                repository.markDone(task.id)
            } else {
                // Обычная задача — toggle (через тот же markDone).
                repository.markDone(task.id)
            }
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch { repository.delete(task) }
    }

    // =====================================================================
    // Связь с напоминаниями
    // =====================================================================

    /**
     * Запрашивает создание напоминания для задачи.
     * MainActivity прочитает pendingReminderTaskId и откроет ReminderEditScreen.
     * После создания напоминания MainActivity вызовет clearPendingReminder().
     */
    fun requestCreateReminder(taskId: Int) {
        _pendingReminderTaskId.value = taskId
    }

    fun clearPendingReminder() {
        _pendingReminderTaskId.value = null
    }

    /**
     * Связывает задачу с только что созданным напоминанием.
     * Вызывается из MainActivity после успешного сохранения ReminderEntity.
     */
    fun linkReminder(taskId: Int, reminderId: Int) {
        viewModelScope.launch { repository.setReminderId(taskId, reminderId) }
    }

    // =====================================================================
    // Утилита для UI
    // =====================================================================

    /**
     * Актуальное isDone для отображения (утренняя рутина сбрасывается
     * визуально при наступлении нового дня).
     */
    fun isDoneToday(task: TaskEntity): Boolean =
        repository.isDoneToday(task, uiState.value.today)
}