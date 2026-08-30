package com.personal.sleepalarm.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.PomodoroSessionEntity
import com.personal.sleepalarm.data.db.entity.FocusProtocolSessionEntity
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ActivityStatsSource(
    val focusSessions: List<PomodoroSessionEntity> = emptyList(),
    val sleepSessions: List<SleepSessionEntity> = emptyList(),
    val completedFocusBlocks: List<FocusProtocolSessionEntity> = emptyList(),
    val activityRecords: List<ActivityRecordEntity> = emptyList(),
    val currentTasks: List<TaskEntity> = emptyList(),
    /** A stable "now" value avoids repainting every minute while this screen is open. */
    val snapshotTimeMillis: Long = System.currentTimeMillis()
)

class ActivityStatsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application.applicationContext)
    private val snapshotTime = System.currentTimeMillis()

    private val activitySource = combine(
        database.pomodoroDao().observeAllRecordedFocus(),
        database.sleepSessionDao().observeAll(),
        database.focusProtocolDao().observeRecentCompleted(100),
        database.activityRecordDao().observeAll()
    ) { focusSessions, sleepSessions, completedFocusBlocks, activityRecords ->
        ActivityStatsSource(
            focusSessions = focusSessions,
            sleepSessions = sleepSessions,
            completedFocusBlocks = completedFocusBlocks,
            activityRecords = activityRecords,
            snapshotTimeMillis = snapshotTime
        )
    }

    val source: StateFlow<ActivityStatsSource> = combine(
        activitySource,
        database.taskDao().observeAll()
    ) { activity, tasks ->
        activity.copy(currentTasks = tasks)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ActivityStatsSource(snapshotTimeMillis = snapshotTime)
    )
}
