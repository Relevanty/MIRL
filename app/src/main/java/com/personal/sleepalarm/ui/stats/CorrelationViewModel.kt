package com.personal.sleepalarm.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.repository.MoodRepository
import com.personal.sleepalarm.data.repository.SleepSessionRepository
import com.personal.sleepalarm.domain.calculator.CorrelationCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Точка данных за день (для графика и корреляций).
 */
data class DayPoint(
    val date: String,
    val sleepMinutes: Double? = null,
    val tasksDone: Double? = null,
    val mood: Double? = null
)

/**
 * Состояние корреляционной секции.
 */
data class CorrelationState(
    val rSleepMood: Double? = null,
    val rTasksMood: Double? = null,
    val rSleepTasks: Double? = null,
    val points: List<DayPoint> = emptyList(),
    val enoughData: Boolean = false
)

/**
 * ViewModel корреляционной аналитики «сон vs задачи vs настроение».
 *
 * Собирает дневные агрегаты за последние [WINDOW_DAYS] дней:
 * - sleepMinutes: из завершённых сессий (actualWakeTime != null)
 * - tasksDone: из tasks.doneDate (GROUP BY)
 * - mood: из mood_entries
 *
 * Считает Пирсона для трёх пар. Минимум 5 точек, иначе «недостаточно данных».
 */
class CorrelationViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application.applicationContext)
    private val sessionRepository = SleepSessionRepository(
        database = database,
        sessionDao = database.sleepSessionDao(),
        cueEventDao = database.cueEventDao()
    )
    private val moodRepository = MoodRepository(database.moodEntryDao())
    private val taskDao = database.taskDao()

    private val zone = ZoneId.systemDefault()
    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE

    private val _state = MutableStateFlow(CorrelationState())
    val state: StateFlow<CorrelationState> = _state

    init {
        // Наблюдаем сессии и настроение; задачи дёргаем разово при каждом пересчёте.
        combine(
            sessionRepository.observeAllSessions(),
            moodRepository.observeAll()
        ) { sessions, moods ->
            rebuild(sessions, moods)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Unit
        )
    }

    private fun rebuild(
        sessions: List<com.personal.sleepalarm.data.db.entity.SleepSessionEntity>,
        moods: List<com.personal.sleepalarm.data.db.entity.MoodEntryEntity>
    ) {
        viewModelScope.launch {
            val cutoff = LocalDate.now().minusDays(WINDOW_DAYS.toLong() - 1)
                .format(dateFormat)

            // Сон: сумма минут по дате фактического пробуждения.
            val sleepByDate = mutableMapOf<String, Double>()
            sessions.forEach { s ->
                val wake = s.actualWakeTime ?: return@forEach
                val date = Instant.ofEpochMilli(wake).atZone(zone)
                    .toLocalDate().format(dateFormat)
                if (date < cutoff) return@forEach
                val start = s.estimatedSleepStartTime
                val minutes = (wake - start) / 60_000.0
                sleepByDate[date] = (sleepByDate[date] ?: 0.0) + minutes
            }

            // Задачи: GROUP BY doneDate.
            val tasksByDate = taskDao.getDoneCountsByDate()
                .filter { it.date >= cutoff }
                .associate { it.date to it.count.toDouble() }

            // Настроение.
            val moodByDate = moods
                .filter { it.date >= cutoff }
                .associate { it.date to it.mood.toDouble() }

            // Объединяем в точки.
            val dates = (sleepByDate.keys + tasksByDate.keys + moodByDate.keys)
                .toSortedSet()
            val points = dates.map { d ->
                DayPoint(
                    date = d,
                    sleepMinutes = sleepByDate[d],
                    tasksDone = tasksByDate[d],
                    mood = moodByDate[d]
                )
            }

            // Пары для Пирсона (только дни, где есть оба значения).
            val sleepMood = points.filter { it.sleepMinutes != null && it.mood != null }
                .map { it.sleepMinutes!! to it.mood!! }
            val tasksMood = points.filter { it.tasksDone != null && it.mood != null }
                .map { it.tasksDone!! to it.mood!! }
            val sleepTasks = points.filter { it.sleepMinutes != null && it.tasksDone != null }
                .map { it.sleepMinutes!! to it.tasksDone!! }

            _state.value = CorrelationState(
                rSleepMood = CorrelationCalculator.pearson(sleepMood),
                rTasksMood = CorrelationCalculator.pearson(tasksMood),
                rSleepTasks = CorrelationCalculator.pearson(sleepTasks),
                points = points,
                enoughData = sleepMood.size >= CorrelationCalculator.MIN_POINTS ||
                        tasksMood.size >= CorrelationCalculator.MIN_POINTS
            )
        }
    }

    companion object {
        private const val WINDOW_DAYS = 30
    }
}