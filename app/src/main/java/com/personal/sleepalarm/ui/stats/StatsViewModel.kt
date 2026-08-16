package com.personal.sleepalarm.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val allSessions: List<SleepSessionEntity> = emptyList(),
    /** Stable while the screen is alive; statistics do not recalculate every minute. */
    val snapshotTimeMillis: Long = System.currentTimeMillis()
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application.applicationContext)
    private val snapshotTime = System.currentTimeMillis()

    val uiState: StateFlow<StatsUiState> = database.sleepSessionDao()
        .observeAll()
        .map { sessions ->
            StatsUiState(
                allSessions = sessions.filter(::isCountable),
                snapshotTimeMillis = snapshotTime
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            StatsUiState(snapshotTimeMillis = snapshotTime)
        )

    private fun isCountable(session: SleepSessionEntity): Boolean {
        if (session.isActive) return true
        val end = session.actualWakeTime ?: return false
        val start = session.detectedSleepOnsetTime ?: session.estimatedSleepStartTime
        return end - start >= MIN_SESSION_DURATION_MS
    }

    private companion object {
        const val MIN_SESSION_DURATION_MS = 60L * 60L * 1000L
    }
}
