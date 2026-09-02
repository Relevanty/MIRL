package com.personal.sleepalarm.ui.dday

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.data.db.entity.ProjectEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.data.repository.DDayRepository
import com.personal.sleepalarm.data.repository.TaskEcosystemRepository
import com.personal.sleepalarm.domain.coordinator.TaskLifecycleCoordinator
import com.personal.sleepalarm.R
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.domain.model.calendarDeadlineItems
import com.personal.sleepalarm.domain.calculator.TaskDeadlinePlan
import com.personal.sleepalarm.domain.calculator.TaskDeadlinePlanCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

/**
 * Ближайшее событие + сколько дней осталось.
 */
data class NearestDDay(
    val event: DDayEntity,
    val days: Int
)

/**
 * Состояние экрана D-Day.
 */
data class DDayUiState(
    val events: List<DDayEntity> = emptyList(),
    val nearest: NearestDDay? = null,
    val projects: List<ProjectEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val plans: Map<Int, DDayPlanInfo> = emptyMap(),
    val isLoaded: Boolean = false,
    val metadata: List<DDayEntity> = emptyList()
)

data class DDayPlanInfo(
    val linkedTitle: String,
    val remainingMinutes: Int,
    val minutesPerDay: Int,
    val readinessPercent: Int,
    val isOnTrack: Boolean,
    val hasWorkBudget: Boolean = false,
    val taskPlan: TaskDeadlinePlan? = null
)

data class DeadlineMutationResult(val id: Int, val targetDate: String? = null)

/**
 * ViewModel D-Day: список, CRUD, ближайшее событие для бейджей и брифинга.
 *
 * Бейдж показывается только при 0 <= days <= 30 (чтобы не мусорить
 * событиями «через год»).
 */
class DDayViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application.applicationContext)
    private val repository = DDayRepository(database.ddayDao())
    private val ecosystem = TaskEcosystemRepository(database)
    private val taskLifecycle = TaskLifecycleCoordinator(application.applicationContext, database)
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000L)
        }
    }

    /** Только ближайшее событие с днями — для бейджей и брифинга. */
    val nearest: StateFlow<NearestDDay?> = repository.observeNearest()
        .combine(MutableStateFlow(Unit)) { event, _ ->
            event?.let { NearestDDay(it, repository.daysUntil(it)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val uiState: StateFlow<DDayUiState> = combine(
        repository.observeMetadata(),
        nearest,
        database.projectDao().observeAll(),
        database.taskDao().observeAll(),
        clock
    ) { metadata, nearest, projects, tasks, nowMillis ->
        val events = calendarDeadlineItems(metadata, tasks)
        val projectById = projects.associateBy { it.id }
        val taskById = tasks.associateBy { it.id }
        val plans = events.mapNotNull { event ->
            val task = event.taskId?.let(taskById::get)
            val project = event.projectId?.let(projectById::get)
            val title = task?.primaryLabel() ?: project?.title ?: return@mapNotNull null
            val budgetMinutes = task?.workBudgetMinutes ?: project?.workBudgetMinutes ?: 0
            val spentMillis = task?.spentMillis ?: project?.spentMillis ?: 0L
            val spentMinutes = (spentMillis / 60_000L).toInt()
            val remaining = (budgetMinutes - spentMinutes).coerceAtLeast(0)
            val taskPlan = task?.let {
                TaskDeadlinePlanCalculator.calculate(it, nowMillis, java.time.ZoneId.systemDefault())
            }
            val progress = if (budgetMinutes > 0) spentMinutes.toFloat() / budgetMinutes else 0f
            event.id to DDayPlanInfo(
                linkedTitle = title,
                remainingMinutes = remaining,
                minutesPerDay = taskPlan?.requiredMinutesPerDay ?: 0,
                readinessPercent = (progress.coerceIn(0f, 1f) * 100).toInt(),
                isOnTrack = taskPlan?.isManualDailyGoalSufficient == true,
                hasWorkBudget = budgetMinutes > 0,
                taskPlan = taskPlan
            )
        }.toMap()
        DDayUiState(
            events = events,
            nearest = nearest,
            projects = projects.filterNot { it.isArchived },
            tasks = tasks.filterNot { it.isMorningRoutine },
            plans = plans,
            isLoaded = true,
            metadata = metadata
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DDayUiState()
    )

    // =====================================================================
    // CRUD
    // =====================================================================

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError
    private val _mutationResult = MutableStateFlow<DeadlineMutationResult?>(null)
    val mutationResult: StateFlow<DeadlineMutationResult?> = _mutationResult

    fun consumeMutationResult(result: DeadlineMutationResult) {
        if (_mutationResult.value == result) _mutationResult.value = null
    }

    fun clearSaveError() { _saveError.value = null }

    /** Keep the editor open until the database confirms the change. */
    fun saveEvent(event: DDayEntity, taskDueAtMillis: Long? = null) {
        if (_saving.value || _mutationResult.value != null) return
        if (event.title.isBlank() || !repository.isValidDate(event.targetDate)) {
            _saveError.value = getApplication<Application>().getString(R.string.calendar_deadline_invalid)
            return
        }
        _saving.value = true
        _saveError.value = null
        viewModelScope.launch {
            try {
                val saved = ecosystem.saveDeadline(event, taskDueAtMillis)
                if (saved == null) {
                    _saveError.value = getApplication<Application>().getString(R.string.calendar_deadline_stale)
                    return@launch
                }
                saved.taskId?.let { taskId ->
                    try {
                        taskLifecycle.synchronize(taskId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // The write has committed. Do not invite a second insert on retry.
                        Toast.makeText(
                            getApplication(), R.string.calendar_deadline_alarm_failed, Toast.LENGTH_LONG
                        ).show()
                    }
                }
                _mutationResult.value = DeadlineMutationResult(saved.id, saved.targetDate)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _saveError.value = getApplication<Application>().getString(R.string.calendar_deadline_save_failed)
            } finally {
                _saving.value = false
            }
        }
    }

    fun deleteEvent(id: Int) {
        if (_saving.value || _mutationResult.value != null) return
        _saving.value = true
        _saveError.value = null
        viewModelScope.launch {
            try {
                val taskId = ecosystem.deleteDeadline(id)
                taskId?.let {
                    try {
                        taskLifecycle.synchronize(it)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        Toast.makeText(getApplication(), R.string.calendar_deadline_alarm_failed, Toast.LENGTH_LONG).show()
                    }
                }
                _mutationResult.value = DeadlineMutationResult(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _saveError.value = getApplication<Application>().getString(R.string.calendar_deadline_delete_failed)
            } finally {
                _saving.value = false
            }
        }
    }

    fun daysUntil(event: DDayEntity): Int = repository.daysUntil(event)

    /** true если событие стоит показывать в бейдже (0..30 дней). */
    fun isBadgeVisible(nearest: NearestDDay?): Boolean =
        nearest != null && nearest.days in 0..BADGE_MAX_DAYS

    companion object {
        const val BADGE_MAX_DAYS = 30
    }
}
