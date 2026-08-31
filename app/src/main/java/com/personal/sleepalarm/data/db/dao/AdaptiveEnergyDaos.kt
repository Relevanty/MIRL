package com.personal.sleepalarm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personal.sleepalarm.data.db.entity.ContextSnapshotEntity
import com.personal.sleepalarm.data.db.entity.DailyCheckInEntity
import com.personal.sleepalarm.data.db.entity.EnergyObservationEntity
import com.personal.sleepalarm.data.db.entity.ExternalContextEntity
import com.personal.sleepalarm.data.db.entity.RecommendationDecisionEntity
import com.personal.sleepalarm.data.db.entity.TaskDemandProfileEntity
import com.personal.sleepalarm.data.db.entity.TaskDependencyEntity
import com.personal.sleepalarm.data.db.entity.WorkEpisodeAssessmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyCheckInDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkIn: DailyCheckInEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(checkIns: List<DailyCheckInEntity>)

    @Update
    suspend fun update(checkIn: DailyCheckInEntity)

    @Query("SELECT * FROM daily_check_ins WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): DailyCheckInEntity?

    @Query("SELECT * FROM daily_check_ins ORDER BY timestamp ASC, id ASC")
    suspend fun getAll(): List<DailyCheckInEntity>

    @Query("SELECT * FROM daily_check_ins WHERE timestamp >= :from ORDER BY timestamp ASC, id ASC")
    fun observeFrom(from: Long): Flow<List<DailyCheckInEntity>>

    @Query("SELECT * FROM daily_check_ins WHERE localDate = :localDate ORDER BY timestamp ASC, id ASC")
    fun observeForDate(localDate: String): Flow<List<DailyCheckInEntity>>

    @Query("SELECT * FROM daily_check_ins ORDER BY timestamp DESC, id DESC LIMIT 1")
    fun observeLatest(): Flow<DailyCheckInEntity?>

    @Query("SELECT * FROM daily_check_ins ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun getLatest(): DailyCheckInEntity?

    @Query("DELETE FROM daily_check_ins")
    suspend fun deleteAll()
}

@Dao
interface EnergyObservationDao {
    @Insert
    suspend fun insert(observation: EnergyObservationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(observations: List<EnergyObservationEntity>)

    @Update
    suspend fun update(observation: EnergyObservationEntity)

    @Query("SELECT * FROM energy_observations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): EnergyObservationEntity?

    @Query("SELECT * FROM energy_observations ORDER BY timestamp ASC, id ASC")
    suspend fun getAll(): List<EnergyObservationEntity>

    @Query("SELECT * FROM energy_observations WHERE timestamp >= :from ORDER BY timestamp ASC, id ASC")
    fun observeFrom(from: Long): Flow<List<EnergyObservationEntity>>

    @Query("SELECT * FROM energy_observations WHERE taskId = :taskId ORDER BY timestamp ASC, id ASC")
    fun observeForTask(taskId: Int): Flow<List<EnergyObservationEntity>>

    @Query("SELECT * FROM energy_observations WHERE focusProtocolSessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    suspend fun getForFocusSession(sessionId: Int): List<EnergyObservationEntity>

    @Query("SELECT * FROM energy_observations ORDER BY timestamp DESC, id DESC LIMIT 1")
    fun observeLatest(): Flow<EnergyObservationEntity?>

    @Query("DELETE FROM energy_observations")
    suspend fun deleteAll()
}

@Dao
interface TaskDemandProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: TaskDemandProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<TaskDemandProfileEntity>)

    @Query("SELECT * FROM task_demand_profiles WHERE taskId = :taskId LIMIT 1")
    suspend fun getForTask(taskId: Int): TaskDemandProfileEntity?

    @Query("SELECT * FROM task_demand_profiles ORDER BY taskId ASC")
    suspend fun getAll(): List<TaskDemandProfileEntity>

    @Query("SELECT * FROM task_demand_profiles ORDER BY taskId ASC")
    fun observeAll(): Flow<List<TaskDemandProfileEntity>>

    @Query("SELECT * FROM task_demand_profiles WHERE taskId = :taskId LIMIT 1")
    fun observeForTask(taskId: Int): Flow<TaskDemandProfileEntity?>

    @Query("DELETE FROM task_demand_profiles WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Int)

    @Query("DELETE FROM task_demand_profiles")
    suspend fun deleteAll()
}

@Dao
interface TaskDependencyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dependency: TaskDependencyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(dependencies: List<TaskDependencyEntity>)

    @Query("SELECT * FROM task_dependencies ORDER BY taskId ASC, dependsOnTaskId ASC")
    suspend fun getAll(): List<TaskDependencyEntity>

    @Query("SELECT * FROM task_dependencies ORDER BY taskId ASC, dependsOnTaskId ASC")
    fun observeAll(): Flow<List<TaskDependencyEntity>>

    @Query("SELECT * FROM task_dependencies WHERE taskId = :taskId ORDER BY dependsOnTaskId ASC")
    fun observeForTask(taskId: Int): Flow<List<TaskDependencyEntity>>

    @Query("SELECT * FROM task_dependencies WHERE dependsOnTaskId = :taskId ORDER BY taskId ASC")
    fun observeDependents(taskId: Int): Flow<List<TaskDependencyEntity>>

    @Query("DELETE FROM task_dependencies WHERE taskId = :taskId AND dependsOnTaskId = :dependsOnTaskId")
    suspend fun delete(taskId: Int, dependsOnTaskId: Int)

    @Query("DELETE FROM task_dependencies WHERE taskId = :taskId OR dependsOnTaskId = :taskId")
    suspend fun deleteForTask(taskId: Int)

    @Query("DELETE FROM task_dependencies")
    suspend fun deleteAll()
}

@Dao
interface WorkEpisodeAssessmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(assessment: WorkEpisodeAssessmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assessments: List<WorkEpisodeAssessmentEntity>)

    @Update
    suspend fun update(assessment: WorkEpisodeAssessmentEntity)

    @Query("SELECT * FROM work_episode_assessments WHERE activityRecordId = :activityRecordId LIMIT 1")
    suspend fun getForActivity(activityRecordId: Int): WorkEpisodeAssessmentEntity?

    @Query("SELECT * FROM work_episode_assessments ORDER BY activityRecordId ASC")
    suspend fun getAll(): List<WorkEpisodeAssessmentEntity>

    @Query("SELECT * FROM work_episode_assessments WHERE activityRecordId = :activityRecordId LIMIT 1")
    fun observeForActivity(activityRecordId: Int): Flow<WorkEpisodeAssessmentEntity?>

    @Query("DELETE FROM work_episode_assessments")
    suspend fun deleteAll()
}

@Dao
interface ExternalContextDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(context: ExternalContextEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contexts: List<ExternalContextEntity>)

    @Update
    suspend fun update(context: ExternalContextEntity)

    @Query("SELECT * FROM external_contexts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): ExternalContextEntity?

    @Query("SELECT * FROM external_contexts WHERE localDate = :localDate AND regionKey = :regionKey AND source = :source LIMIT 1")
    suspend fun getForKey(
        localDate: String,
        regionKey: String,
        source: String
    ): ExternalContextEntity?

    @Query("SELECT * FROM external_contexts ORDER BY localDate ASC, source ASC, id ASC")
    suspend fun getAll(): List<ExternalContextEntity>

    @Query("SELECT * FROM external_contexts WHERE localDate = :localDate AND regionKey = :regionKey ORDER BY fetchedAt DESC LIMIT 1")
    suspend fun getLatest(localDate: String, regionKey: String): ExternalContextEntity?

    @Query("SELECT * FROM external_contexts WHERE localDate = :localDate AND regionKey = :regionKey ORDER BY fetchedAt DESC LIMIT 1")
    fun observeLatest(localDate: String, regionKey: String): Flow<ExternalContextEntity?>

    @Query("DELETE FROM external_contexts")
    suspend fun deleteAll()
}

@Dao
interface ContextSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: ContextSnapshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<ContextSnapshotEntity>)

    @Query("SELECT * FROM context_snapshots WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): ContextSnapshotEntity?

    @Query("SELECT * FROM context_snapshots ORDER BY timestamp ASC, id ASC")
    suspend fun getAll(): List<ContextSnapshotEntity>

    @Query("SELECT * FROM context_snapshots WHERE timestamp >= :from ORDER BY timestamp ASC, id ASC")
    fun observeFrom(from: Long): Flow<List<ContextSnapshotEntity>>

    @Query("SELECT * FROM context_snapshots ORDER BY timestamp DESC, id DESC LIMIT 1")
    fun observeLatest(): Flow<ContextSnapshotEntity?>

    @Query("DELETE FROM context_snapshots")
    suspend fun deleteAll()
}

@Dao
interface RecommendationDecisionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(decision: RecommendationDecisionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(decisions: List<RecommendationDecisionEntity>)

    @Update
    suspend fun update(decision: RecommendationDecisionEntity)

    @Query("SELECT * FROM recommendation_decisions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): RecommendationDecisionEntity?

    @Query("SELECT * FROM recommendation_decisions ORDER BY generatedAt ASC, id ASC")
    suspend fun getAll(): List<RecommendationDecisionEntity>

    @Query("SELECT * FROM recommendation_decisions ORDER BY generatedAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecommendationDecisionEntity>>

    @Query("DELETE FROM recommendation_decisions")
    suspend fun deleteAll()
}
