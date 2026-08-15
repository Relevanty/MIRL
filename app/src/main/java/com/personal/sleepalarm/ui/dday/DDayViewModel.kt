package com.personal.sleepalarm.ui.dday

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.data.repository.DDayRepository
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
    val nearest: NearestDDay? = null
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
        nearest
    ) { events, nearest ->
        DDayUiState(events = events, nearest = nearest)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DDayUiState()
    )

    // =====================================================================
    // CRUD
    // =====================================================================

    fun addEvent(title: String, targetDate: String): Boolean {
        if (title.isBlank() || !repository.isValidDate(targetDate)) return false
        viewModelScope.launch { repository.addEvent(title, targetDate) }
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