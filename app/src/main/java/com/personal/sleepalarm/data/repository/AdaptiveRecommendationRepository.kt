package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.adaptive.AdaptivePlanningBridge
import com.personal.sleepalarm.domain.adaptive.AdaptivePlanningInput
import com.personal.sleepalarm.domain.adaptive.AdaptivePlanningSnapshot

/** One-shot facade for non-Compose consumers of the same adaptive order used by Home. */
class AdaptiveRecommendationRepository(private val database: AppDatabase) {
    suspend fun rank(
        tasks: List<TaskEntity>? = null,
        nowMillis: Long = System.currentTimeMillis(),
        photoperiodMinutes: Int? = null,
        outdoorFeasible: Boolean? = null
    ): AdaptivePlanningSnapshot = AdaptivePlanningBridge.build(
        AdaptivePlanningInput(
            nowMillis = nowMillis,
            tasks = tasks ?: database.taskDao().getAll(),
            profiles = database.taskDemandProfileDao().getAll(),
            dependencies = database.taskDependencyDao().getAll(),
            latestCheckIn = database.dailyCheckInDao().getLatest(),
            latestSleep = database.sleepSessionDao().getLatestCompleted(),
            activities = database.activityRecordDao().getAll(),
            energyObservations = database.energyObservationDao().getAll(),
            calendarEvents = database.calendarEventDao().getAll(),
            photoperiodMinutes = photoperiodMinutes,
            outdoorFeasible = outdoorFeasible
        )
    )
}
