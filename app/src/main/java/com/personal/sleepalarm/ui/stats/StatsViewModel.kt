package com.personal.sleepalarm.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.DailyCheckInEntity
import com.personal.sleepalarm.data.db.entity.EnergyObservationEntity
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.data.db.entity.TaskDemandProfileEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

data class StatsUiState(
    val allSessions: List<SleepSessionEntity> = emptyList(),
    val energyAnalytics: EnergyAnalytics = EnergyAnalytics.empty(),
    /** Stable while the screen is alive; statistics do not recalculate every minute. */
    val snapshotTimeMillis: Long = System.currentTimeMillis()
)

private data class AdaptiveStatsSource(
    val checkIns: List<DailyCheckInEntity>,
    val observations: List<EnergyObservationEntity>,
    val profiles: List<TaskDemandProfileEntity>,
    val tasks: List<TaskEntity>,
    val activities: List<ActivityRecordEntity>
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application.applicationContext)
    private val snapshotTime = System.currentTimeMillis()
    private val energyPeriodStart = snapshotTime - ENERGY_WINDOW_MILLIS
    private val adaptiveSource = combine(
        database.dailyCheckInDao().observeFrom(energyPeriodStart),
        database.energyObservationDao().observeFrom(energyPeriodStart),
        database.taskDemandProfileDao().observeAll(),
        database.taskDao().observeAll(),
        database.activityRecordDao().observeAll()
    ) { checkIns, observations, profiles, tasks, activities ->
        AdaptiveStatsSource(checkIns, observations, profiles, tasks, activities)
    }

    val uiState: StateFlow<StatsUiState> = combine(
        database.sleepSessionDao().observeAll(),
        database.focusProtocolDao().observeCompletedFrom(energyPeriodStart),
        adaptiveSource
    ) { sessions, focusSessions, adaptive ->
            StatsUiState(
                allSessions = sessions.filter(::isCountable),
                energyAnalytics = aggregateEnergyAnalytics(
                    checkIns = adaptive.checkIns,
                    observations = adaptive.observations,
                    profiles = adaptive.profiles,
                    tasks = adaptive.tasks,
                    activities = adaptive.activities,
                    focusSessions = focusSessions,
                    sleepSessions = sessions,
                    periodStartMillis = energyPeriodStart,
                    snapshotTimeMillis = snapshotTime,
                    zoneId = ZoneId.systemDefault()
                ),
                snapshotTimeMillis = snapshotTime
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            StatsUiState(snapshotTimeMillis = snapshotTime)
        )

    fun correctSleepDuration(session: SleepSessionEntity, durationMinutes: Long) {
        val wake = session.actualWakeTime ?: return
        val safeDuration = durationMinutes.coerceIn(1L, 24L * 60L)
        val correctedOnset = wake - safeDuration * 60_000L
        viewModelScope.launch {
            database.sleepSessionDao().update(
                session.copy(
                    detectedSleepOnsetTime = correctedOnset,
                    detectedOnsetLatencyMinutes = ((correctedOnset - session.bedTimePlanned) / 60_000L)
                        .toInt().coerceAtLeast(0),
                    detectedOnsetConfidencePercent = 100,
                    detectedOnsetSource = "MANUAL_CORRECTION",
                    detectedOnsetUncertaintyMinutes = 0,
                    onsetReviewState = "CORRECTED",
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun isCountable(session: SleepSessionEntity): Boolean {
        if (session.isActive) return true
        val end = session.actualWakeTime ?: return false
        val start = session.detectedSleepOnsetTime ?: session.estimatedSleepStartTime
        return end - start >= MIN_SESSION_DURATION_MS
    }

    private companion object {
        const val MIN_SESSION_DURATION_MS = 60L * 60L * 1000L
        const val ENERGY_WINDOW_MILLIS = 90L * 24L * 60L * 60L * 1000L
    }
}
