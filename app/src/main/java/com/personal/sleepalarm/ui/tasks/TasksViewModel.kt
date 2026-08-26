package com.personal.sleepalarm.ui.tasks

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.ProjectEntity
import com.personal.sleepalarm.data.db.entity.LibraryItemEntity
import com.personal.sleepalarm.data.db.entity.TaskLibraryLinkEntity
import com.personal.sleepalarm.data.repository.TaskRepository
import com.personal.sleepalarm.alarm.TaskDeadlineScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.personal.sleepalarm.util.CoverHelper
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
) {
    val activeMatrixTasks: List<TaskEntity>
        get() = generalTasks.filterNot(TaskEntity::isDone)

    val completedMatrixTasks: List<TaskEntity>
        get() = generalTasks.filter(TaskEntity::isDone)
            .sortedByDescending { it.completedAt ?: 0L }
}

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
    private val deadlineScheduler = TaskDeadlineScheduler(application.applicationContext)

    private val _draftTitle = MutableStateFlow("")
    private val _draftIsMorning = MutableStateFlow(false)
    private val _pendingReminderTaskId = MutableStateFlow<Int?>(null)
    private data class MoveSnapshot(val taskId: Int, val quadrant: Int, val sortOrder: Int)
    private val _lastMove = MutableStateFlow<MoveSnapshot?>(null)
    val canUndoMove: StateFlow<Boolean> = _lastMove
        .combine(MutableStateFlow(Unit)) { move, _ -> move != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val activityRecords: StateFlow<List<ActivityRecordEntity>> = database.activityRecordDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val projects: StateFlow<List<ProjectEntity>> = database.projectDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val libraryItems: StateFlow<List<LibraryItemEntity>> = database.libraryDao().observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val libraryLinks: StateFlow<List<TaskLibraryLinkEntity>> = database.taskLibraryLinkDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
            val createdId = repository.addTask(
                title = title,
                isMorningRoutine = _draftIsMorning.value
            )
            database.taskDao().getById(createdId.toInt())?.let(deadlineScheduler::schedule)
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
            val updated = repository.getById(task.id)
            if (updated == null || updated.isDone) deadlineScheduler.cancel(task.id)
            else deadlineScheduler.schedule(updated)
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            deadlineScheduler.cancel(task.id)
            repository.delete(task)
            withContext(Dispatchers.IO) { CoverHelper.deleteCover(task.imagePath) }
        }
    }

    /** Создаёт или обновляет полную карточку матрицы. */
    fun saveTask(task: TaskEntity) {
        val normalized = task.copy(
            title = task.title.trim(),
            matrixQuadrant = task.matrixQuadrant.coerceIn(1, 4),
            estimatedMinutes = task.estimatedMinutes.coerceIn(5, 480),
            plannedFocusMinutes = task.plannedFocusMinutes.coerceIn(5, 480),
            workBudgetMinutes = task.workBudgetMinutes.coerceIn(0, 100_000),
            updatedAt = System.currentTimeMillis()
        )
        // Новые карточки идентифицируются фотографией. Старые текстовые задачи
        // остаются валидными и продолжают редактироваться без миграции данных.
        if (normalized.id == 0 && normalized.imagePath == null) return
        viewModelScope.launch {
            val previous = if (normalized.id != 0) repository.getById(normalized.id) else null
            val toSave = if (normalized.id == 0) {
                normalized.copy(sortOrder = repository.nextSortOrder(normalized.matrixQuadrant))
            } else {
                normalized
            }
            val savedId = repository.save(toSave)
            val saved = database.taskDao().getById(savedId.toInt())
            saved?.let(deadlineScheduler::schedule)
            if (previous?.imagePath != null && previous.imagePath != toSave.imagePath) {
                withContext(Dispatchers.IO) { CoverHelper.deleteCover(previous.imagePath) }
            }
        }
    }

    fun moveTask(task: TaskEntity, quadrant: TaskQuadrant) {
        if (task.matrixQuadrant == quadrant.storageValue || task.isDone) return
        _lastMove.value = MoveSnapshot(task.id, task.matrixQuadrant, task.sortOrder)
        viewModelScope.launch {
            val newOrder = repository.nextSortOrder(quadrant.storageValue)
            repository.update(
                task.copy(
                    matrixQuadrant = quadrant.storageValue,
                    sortOrder = newOrder,
                    updatedAt = System.currentTimeMillis()
                )
            )
            normalizeQuadrant(task.matrixQuadrant)
        }
    }

    /** Переставляет задачу внутри раскрытого квадранта. */
    fun moveTaskWithinQuadrant(task: TaskEntity, direction: Int) {
        if (task.isDone || direction == 0) return
        val siblings = uiState.value.activeMatrixTasks
            .filter { it.matrixQuadrant == task.matrixQuadrant }
            .sortedWith(compareBy<TaskEntity> { it.sortOrder }.thenByDescending { it.createdAt })
        val current = siblings.indexOfFirst { it.id == task.id }
        if (current < 0) return
        val target = (current + direction).coerceIn(0, siblings.lastIndex)
        if (current == target) return
        moveTaskToIndex(task, target)
    }

    /** Places a ball at the exact insertion index calculated by the matrix/list. */
    fun moveTaskToIndex(task: TaskEntity, targetIndex: Int) {
        if (task.isDone) return
        val siblings = uiState.value.activeMatrixTasks
            .filter { it.matrixQuadrant == task.matrixQuadrant }
            .sortedWith(compareBy<TaskEntity> { it.sortOrder }.thenByDescending { it.createdAt })
        val current = siblings.indexOfFirst { it.id == task.id }
        if (current < 0) return
        val target = targetIndex.coerceIn(0, siblings.lastIndex)
        if (current == target) return
        _lastMove.value = MoveSnapshot(task.id, task.matrixQuadrant, task.sortOrder)
        val reordered = siblings.toMutableList().apply { add(target, removeAt(current)) }
        viewModelScope.launch {
            reordered.forEachIndexed { index, item ->
                repository.updateSortOrder(item.id, index)
            }
        }
    }

    fun undoLastMove() {
        val snapshot = _lastMove.value ?: return
        _lastMove.value = null
        viewModelScope.launch {
            val task = repository.getById(snapshot.taskId) ?: return@launch
            val currentQuadrant = task.matrixQuadrant
            repository.update(
                task.copy(
                    matrixQuadrant = snapshot.quadrant,
                    sortOrder = snapshot.sortOrder,
                    updatedAt = System.currentTimeMillis()
                )
            )
            normalizeQuadrant(currentQuadrant)
            normalizeQuadrant(snapshot.quadrant)
        }
    }

    private suspend fun normalizeQuadrant(quadrant: Int) {
        repository.getActiveInQuadrant(quadrant).forEachIndexed { index, task ->
            repository.updateSortOrder(task.id, index)
        }
    }

    fun completeTask(task: TaskEntity) {
        if (!task.isDone) toggleDone(task)
    }

    fun restoreTask(task: TaskEntity) {
        if (task.isDone) toggleDone(task)
    }

    fun duplicateCompletedTask(task: TaskEntity) {
        viewModelScope.launch {
            val fresh = task.copy(
                id = 0,
                isDone = false,
                completedAt = null,
                doneDate = null,
                spentMillis = 0L,
                reminderId = null,
                sortOrder = repository.nextSortOrder(task.matrixQuadrant),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.save(fresh)
        }
    }

    fun toggleChecklistItem(task: TaskEntity, index: Int) {
        val items = parseTaskChecklist(task.checklist).toMutableList()
        val item = items.getOrNull(index) ?: return
        items[index] = item.copy(isDone = !item.isDone)
        saveTask(task.copy(checklist = serializeTaskChecklist(items)))
    }

    fun saveProject(project: ProjectEntity) {
        val normalized = project.copy(
            title = project.title.trim(),
            description = project.description.trim(),
            goal = project.goal.trim(),
            workBudgetMinutes = project.workBudgetMinutes.coerceIn(0, 100_000),
            updatedAt = System.currentTimeMillis()
        )
        if (normalized.title.isBlank()) return
        viewModelScope.launch {
            if (normalized.id == 0) database.projectDao().insert(normalized)
            else database.projectDao().update(normalized)
        }
    }

    fun archiveProject(project: ProjectEntity) {
        viewModelScope.launch {
            database.projectDao().update(
                project.copy(isArchived = !project.isArchived, updatedAt = System.currentTimeMillis())
            )
        }
    }

    fun toggleLibraryLink(taskId: Int, libraryItemId: Int) {
        viewModelScope.launch {
            val linked = libraryLinks.value.any { it.taskId == taskId && it.libraryItemId == libraryItemId }
            if (linked) database.taskLibraryLinkDao().delete(taskId, libraryItemId)
            else database.taskLibraryLinkDao().insert(TaskLibraryLinkEntity(taskId, libraryItemId))
        }
    }

    /** Копирует выбранное изображение в приватное хранилище приложения. */
    suspend fun importTaskImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        CoverHelper.copyCover(getApplication<Application>().applicationContext, uri)
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
