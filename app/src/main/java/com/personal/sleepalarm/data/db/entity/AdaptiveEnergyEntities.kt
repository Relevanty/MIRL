package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A revisionable subjective state check-in. Several rows per local day are allowed. */
@Entity(
    tableName = "daily_check_ins",
    indices = [
        Index(value = ["localDate"]),
        Index(value = ["timestamp"]),
        Index(value = ["localDate", "timestamp"]),
        Index(value = ["source"])
    ]
)
data class DailyCheckInEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** ISO-8601 local date (yyyy-MM-dd) in [zoneId]. */
    val localDate: String,
    val timestamp: Long,
    val zoneId: String,
    /** Energy is 1..10; mood is 1..5; capacity dimensions are 0..4. */
    val energy: Int? = null,
    val mood: Int? = null,
    val clarity: Int? = null,
    val focus: Int? = null,
    val social: Int? = null,
    val physical: Int? = null,
    val stress: Int? = null,
    /** ALARM, MORNING_RECHECK or AD_HOC. Kept as text for forward compatibility. */
    val source: String = "AD_HOC",
    /** Stable comma-separated reason codes; free-form details belong in [unusualDayNote]. */
    val unusualDayFlags: String = "",
    val unusualDayNote: String = "",
    val excludedFromLearning: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** A point-in-time energy reading, optionally tied to the work that caused it. */
@Entity(
    tableName = "energy_observations",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ActivityRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityRecordId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = FocusProtocolSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["focusProtocolSessionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["taskId"]),
        Index(value = ["activityRecordId"]),
        Index(value = ["focusProtocolSessionId"]),
        Index(value = ["context"]),
        Index(value = ["legacyEnergySampleId"], unique = true)
    ]
)
data class EnergyObservationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,
    /** Absolute subjective energy on the existing 1..10 scale. */
    val absoluteEnergy: Int? = null,
    /** Quick relative response, normally in the -9..9 range. */
    val relativeDelta: Int? = null,
    /** MORNING, BEFORE_TASK, AFTER_TASK, AFTER_RECOVERY or AD_HOC. */
    val context: String,
    val taskId: Int? = null,
    val activityRecordId: Int? = null,
    val focusProtocolSessionId: Int? = null,
    val source: String = "USER",
    val quality: String = "EXACT",
    val confidence: Float = 1f,
    val excludedFromLearning: Boolean = false,
    /** Populated when this row was migrated from the append-only legacy table. */
    val legacyEnergySampleId: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** One adaptive demand profile per task. User-locked fields are represented by a bit mask. */
@Entity(
    tableName = "task_demand_profiles",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["domain"]), Index(value = ["workMode"])]
)
data class TaskDemandProfileEntity(
    @PrimaryKey
    val taskId: Int,
    val domain: String = "OTHER",
    val workMode: String = "OTHER",
    /** Demand dimensions use 0..4, where 0 means no special demand. */
    val difficulty: Int = 0,
    val concentrationDemand: Int = 0,
    val executiveDemand: Int = 0,
    val memoryDemand: Int = 0,
    val creativeDemand: Int = 0,
    val socialDemand: Int = 0,
    val physicalDemand: Int = 0,
    val emotionalDemand: Int = 0,
    val startFriction: Int = 0,
    val minimumBlockMinutes: Int = 5,
    val preferredBlockMinutes: Int = 25,
    val interruptibility: Int = 2,
    val placeContext: String = "ANY",
    val toolContext: String = "",
    val internetRequirement: String = "ANY",
    val peopleContext: String = "ANY",
    val canDoPartially: Boolean = true,
    val fixedTime: Boolean = false,
    val provenance: String = "USER",
    val confidence: Float = 1f,
    val userLockMask: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

/** Directed dependency edge between two tasks. */
@Entity(
    tableName = "task_dependencies",
    primaryKeys = ["taskId", "dependsOnTaskId"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["dependsOnTaskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["taskId"]), Index(value = ["dependsOnTaskId"])]
)
data class TaskDependencyEntity(
    val taskId: Int,
    val dependsOnTaskId: Int,
    val dependencyType: String = "FINISH_TO_START",
    val createdAt: Long = System.currentTimeMillis()
)

/** Outcome and perceived cost of one canonical activity record. */
@Entity(
    tableName = "work_episode_assessments",
    foreignKeys = [
        ForeignKey(
            entity = ActivityRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityRecordId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EnergyObservationEntity::class,
            parentColumns = ["id"],
            childColumns = ["beforeObservationId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = EnergyObservationEntity::class,
            parentColumns = ["id"],
            childColumns = ["afterObservationId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = EnergyObservationEntity::class,
            parentColumns = ["id"],
            childColumns = ["recoveryObservationId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["activityRecordId"], unique = true),
        Index(value = ["beforeObservationId"]),
        Index(value = ["afterObservationId"]),
        Index(value = ["recoveryObservationId"])
    ]
)
data class WorkEpisodeAssessmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val activityRecordId: Int,
    val beforeObservationId: Int? = null,
    val afterObservationId: Int? = null,
    val recoveryObservationId: Int? = null,
    /** COMPLETE, PARTIAL, MISSED or UNKNOWN. */
    val goalOutcome: String = "UNKNOWN",
    val perceivedDifficulty: Int? = null,
    val interruptionReason: String? = null,
    val profileMismatchFlags: String = "",
    val modelEligible: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Cacheable low-sensitivity external context. No raw provider payload is persisted. */
@Entity(
    tableName = "external_contexts",
    indices = [
        Index(value = ["localDate"]),
        Index(value = ["localDate", "regionKey", "source"], unique = true),
        Index(value = ["expiresAt"])
    ]
)
data class ExternalContextEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val localDate: String,
    /** Coarse region/cache key rather than precise coordinates. */
    val regionKey: String,
    val source: String,
    val daylightMinutes: Int? = null,
    val daylightChangeMinutes: Int? = null,
    val weatherCode: String? = null,
    val temperatureCelsius: Float? = null,
    val cloudCoverPercent: Int? = null,
    val precipitationProbability: Int? = null,
    val outdoorSuitability: Float? = null,
    val publicBackgroundSummary: String? = null,
    val fetchedAt: Long,
    val expiresAt: Long,
    val provenance: String,
    val rawPayloadHash: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** Immutable feature snapshot used to explain and reproduce a recommendation. */
@Entity(
    tableName = "context_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = DailyCheckInEntity::class,
            parentColumns = ["id"],
            childColumns = ["dailyCheckInId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ExternalContextEntity::class,
            parentColumns = ["id"],
            childColumns = ["externalContextId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["localDate"]),
        Index(value = ["dailyCheckInId"]),
        Index(value = ["externalContextId"])
    ]
)
data class ContextSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,
    val zoneId: String,
    val localDate: String,
    val minutesSinceWake: Int? = null,
    val hoursAwake: Float? = null,
    val sleepDurationMinutes: Int? = null,
    val sleepDeviationMinutes: Int? = null,
    val sleepDebtMinutes: Int? = null,
    val sleepRegularity: Float? = null,
    val dayOfWeek: Int,
    val isFreeDay: Boolean = false,
    val calendarWindowMinutes: Int? = null,
    val recentFocusMinutes: Int = 0,
    /** Compact ordered work-mode codes seen in the recent-load window. */
    val recentWorkModes: String = "",
    val recentBreakMinutes: Int = 0,
    val dailyCheckInId: Int? = null,
    val lastObservationAgeMinutes: Int? = null,
    val personalPeriodFlags: String = "",
    val externalContextId: Int? = null,
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)

/** Persisted planner decision and eventual user feedback for auditability and learning. */
@Entity(
    tableName = "recommendation_decisions",
    foreignKeys = [
        ForeignKey(
            entity = ContextSnapshotEntity::class,
            parentColumns = ["id"],
            childColumns = ["contextSnapshotId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["selectedTaskId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ActivityRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["resultingActivityRecordId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["generatedAt"]),
        Index(value = ["contextSnapshotId"]),
        Index(value = ["selectedTaskId"]),
        Index(value = ["resultingActivityRecordId"])
    ]
)
data class RecommendationDecisionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val generatedAt: Long,
    val modelVersion: String,
    val strategy: String,
    val contextSnapshotId: Int? = null,
    /** Compact JSON preserves model state without making it part of the operational schema. */
    val stateSnapshotJson: String = "{}",
    val selectedTaskId: Int? = null,
    val candidateTaskIds: String = "[]",
    val componentScores: String = "{}",
    val reasonCodes: String = "[]",
    val confidence: Float = 0f,
    val accepted: Boolean = false,
    val dismissed: Boolean = false,
    val reordered: Boolean = false,
    val feedbackReason: String? = null,
    val resultingActivityRecordId: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
