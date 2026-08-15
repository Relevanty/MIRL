package com.personal.sleepalarm.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.data.repository.SleepSessionRepository
import com.personal.sleepalarm.domain.model.DismissType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Презентационная модель одного дня для графиков (F12).
 *
 * Сетка дней строится непрерывной (14 дней назад от сегодня),
 * поэтому дни без сессий идут с hasData = false — на графиках
 * они отображаются как пропуски, а не схлопывают ось X.
 */
data class DayStat(
    val date: LocalDate,
    val dayLabel: String,
    val durationMinutes: Long,
    val wakeMinutesOfDay: Int?,
    val hasData: Boolean
)

/**
 * Состояние экрана статистики.
 *
 * ДОБАВЛЕНО (F5, F9, F12):
 * - chartDays — непрерывная сетка 14 дней для графиков;
 * - allSessions — расширенная выборка (до 60) для экспорта CSV;
 * - длительности (lastSessionSleepMinutes / avgSleepMinutes7d) теперь
 *   уточняются по detectedSleepOnsetTime, если автодетект сработал (F9).
 */
data class StatsUiState(
    val sessions: List<SleepSessionEntity> = emptyList(),
    val allSessions: List<SleepSessionEntity> = emptyList(),
    val lastSession: SleepSessionEntity? = null,
    val lastSessionSleepMinutes: Long? = null,
    val avgSleepMinutes7d: Long? = null,
    val sessionsCount7d: Int = 0,
    val noSnoozePercent: Int? = null,
    val finishedCount: Int = 0,
    val lastNightCuesPlayed: Int = 0,
    val lastNightCuesScheduled: Int = 0,
    val chartDays: List<DayStat> = emptyList()
)

/**
 * ViewModel экрана статистики.
 *
 * Один источник данных — observeRecentSessions(60). Из него:
 * - sessions (14) — история;
 * - allSessions (60) — экспорт CSV;
 * - chartDays (14 дней) — графики;
 * - агрегаты за 7 дней и последняя ночь.
 */
class StatsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application.applicationContext)

    private val sessionRepository = SleepSessionRepository(
        database = database,
        sessionDao = database.sleepSessionDao(),
        cueEventDao = database.cueEventDao()
    )

    val uiState: StateFlow<StatsUiState> = sessionRepository
        .observeRecentSessions(RECENT_LIMIT)
        .map { sessions -> buildState(sessions) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState()
        )

    private fun buildState(sessions: List<SleepSessionEntity>): StatsUiState {
        // ДОБАВЛЕНО: отбрасываем сессии короче часа.
        val sessions = sessions.filter { isCountable(it) }

        val now = System.currentTimeMillis()
        val weekAgo = now - WEEK_MS

        val last7Days = sessions.filter { it.createdAt >= weekAgo }
        val lastSession = sessions.firstOrNull()

        val finished = sessions.filter { session ->
            session.dismissType != null && session.dismissType != DismissType.CANCELLED
        }

        val normalCount = finished.count { it.dismissType == DismissType.NORMAL }

        val noSnoozePercent = if (finished.isNotEmpty()) {
            (normalCount * 100) / finished.size
        } else {
            null
        }




        // ДОБАВЛЕНО (F9): старт сна уточняется по автодетекту.
        val durations = last7Days.mapNotNull { session ->
            val end = session.actualWakeTime ?: session.estimatedWakeTime
            val start = session.detectedSleepOnsetTime ?: session.estimatedSleepStartTime
            val minutes = (end - start) / MINUTE_MS
            minutes.takeIf { it > 0 }
        }

        val avgSleepMinutes = if (durations.isNotEmpty()) {
            durations.average().toLong()
        } else {
            null
        }

        val lastSessionSleepMinutes = lastSession?.let { session ->
            val end = session.actualWakeTime ?: session.estimatedWakeTime
            val start = session.detectedSleepOnsetTime ?: session.estimatedSleepStartTime
            val minutes = (end - start) / MINUTE_MS
            minutes.takeIf { it > 0 }
        }

        return StatsUiState(
            sessions = sessions.take(HISTORY_LIMIT),
            allSessions = sessions, // ДОБАВЛЕНО (F5): всё, что есть, — в экспорт
            lastSession = lastSession,
            lastSessionSleepMinutes = lastSessionSleepMinutes,
            avgSleepMinutes7d = avgSleepMinutes,
            sessionsCount7d = last7Days.size,
            noSnoozePercent = noSnoozePercent,
            finishedCount = finished.size,
            lastNightCuesPlayed = lastSession?.cuesPlayedCount ?: 0,
            lastNightCuesScheduled = lastSession?.cuesScheduledCount ?: 0,
            chartDays = buildChartDays(sessions) // ДОБАВЛЕНО (F12)
        )
    }


    /**
     * Сессия учитывается, если она активна сейчас ИЛИ её фактическая
     * длительность >= 1 часа. Короткие (тестовые/отменённые) игнорируются.
     */
    private fun isCountable(session: SleepSessionEntity): Boolean {
        if (session.isActive) return true
        val end = session.actualWakeTime ?: return false
        val start = session.detectedSleepOnsetTime ?: session.estimatedSleepStartTime
        return (end - start) >= MIN_SESSION_DURATION_MS
    }


    /**
     * Строит непрерывную сетку из CHART_DAYS дней, заканчивающуюся сегодня.
     *
     * Для каждого дня берётся самая свежая сессия, чьё bedTimePlanned
     * (в системной зоне) попадает в этот день. Дни без сессий — hasData=false.
     */
    private fun buildChartDays(sessions: List<SleepSessionEntity>): List<DayStat> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dayLabelFormatter = DateTimeFormatter.ofPattern("EEE", Locale("ru"))

        // Индекс: дата отхода ко сну → самая свежая сессия этого дня.
        // sessions отсортированы по createdAt DESC, поэтому первая попавшаяся
        // при обходе = самая свежая для даты.
        val byDate = LinkedHashMap<LocalDate, SleepSessionEntity>()
        for (session in sessions) {
            val date = Instant.ofEpochMilli(session.bedTimePlanned)
                .atZone(zone)
                .toLocalDate()
            if (date !in byDate) {
                byDate[date] = session
            }
        }

        return (CHART_DAYS - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val session = byDate[date]

            if (session == null) {
                DayStat(
                    date = date,
                    dayLabel = dayLabelFormatter.format(date),
                    durationMinutes = 0L,
                    wakeMinutesOfDay = null,
                    hasData = false
                )
            } else {
                val end = session.actualWakeTime ?: session.estimatedWakeTime
                val start = session.detectedSleepOnsetTime ?: session.estimatedSleepStartTime
                val duration = ((end - start) / MINUTE_MS).coerceAtLeast(0)

                val wakeZone = Instant.ofEpochMilli(end).atZone(zone)
                val wakeMinutes = wakeZone.hour * 60 + wakeZone.minute

                DayStat(
                    date = date,
                    dayLabel = dayLabelFormatter.format(date),
                    durationMinutes = duration,
                    wakeMinutesOfDay = wakeMinutes,
                    hasData = true
                )
            }
        }
    }

    companion object {
        private const val RECENT_LIMIT = 60
        private const val HISTORY_LIMIT = 14
        private const val CHART_DAYS = 14
        private const val MINUTE_MS = 60L * 1000L
        private const val WEEK_MS = 7L * 24L * 60L * MINUTE_MS

        private const val MIN_SESSION_DURATION_MS = 60L * 60L * 1000L // 1 час
    }
}