package com.personal.sleepalarm.ui.dday

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.data.db.entity.ProjectEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.data.repository.DDayRepository
import com.personal.sleepalarm.domain.model.primaryLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val plans: Map<Int, DDayPlanInfo> = emptyMap()
)

data class DDayPlanInfo(
    val linkedTitle: String,
    val remainingMinutes: Int,
    val minutesPerDay: Int,
    val readinessPercent: Int,
    val isOnTrack: Boolean
)

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
        repository.observeAll(),
        nearest,
        database.projectDao().observeAll(),
        database.taskDao().observeAll()
    ) { events, nearest, projects, tasks ->
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
            val days = repository.daysUntil(event)
            val safeDays = (days + 1).coerceAtLeast(1)
            val createdDate = java.time.Instant.ofEpochMilli(event.createdAt)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val targetDate = runCatching { java.time.LocalDate.parse(event.targetDate) }
                .getOrDefault(java.time.LocalDate.now())
            val totalDays = java.time.temporal.ChronoUnit.DAYS
                .between(createdDate, targetDate).toInt().coerceAtLeast(1)
            val elapsedDays = java.time.temporal.ChronoUnit.DAYS
                .between(createdDate, java.time.LocalDate.now()).toInt().coerceAtLeast(0)
            val progress = if (budgetMinutes > 0) spentMinutes.toFloat() / budgetMinutes else 0f
            val expectedProgress = elapsedDays.toFloat() / totalDays
            event.id to DDayPlanInfo(
                linkedTitle = title,
                remainingMinutes = remaining,
                minutesPerDay = if (remaining == 0) 0 else kotlin.math.ceil(remaining / safeDays.toDouble()).toInt(),
                readinessPercent = (progress.coerceIn(0f, 1f) * 100).toInt(),
                isOnTrack = remaining == 0 || (days >= 0 && progress + 0.05f >= expectedProgress)
            )
        }.toMap()
        DDayUiState(
            events = events,
            nearest = nearest,
            projects = projects.filterNot { it.isArchived },
            tasks = tasks.filterNot { it.isDone || it.isMorningRoutine },
            plans = plans
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DDayUiState()
    )

    // =====================================================================
    // CRUD
    // =====================================================================

    fun addEvent(
        title: String,
        targetDate: String,
        projectId: Int? = null,
        taskId: Int? = null,
        notes: String = ""
    ): Boolean {
        if (title.isBlank() || !repository.isValidDate(targetDate)) return false
        viewModelScope.launch { repository.addEvent(title, targetDate, projectId, taskId, notes) }
        return true
    }

    fun updateEvent(event: DDayEntity) {
        viewModelScope.launch { repository.update(event) }
    }

    fun deleteEvent(id: Int) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun daysUntil(event: DDayEntity): Int = repository.daysUntil(event)

    /** true если событие стоит показывать в бейдже (0..30 дней). */
    fun isBadgeVisible(nearest: NearestDDay?): Boolean =
        nearest != null && nearest.days in 0..BADGE_MAX_DAYS

    companion object {
        const val BADGE_MAX_DAYS = 30
    }
}
