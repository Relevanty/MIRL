package com.personal.sleepalarm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personal.sleepalarm.data.db.entity.FocusProtocolSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusProtocolDao {
    @Insert
    suspend fun insert(session: FocusProtocolSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<FocusProtocolSessionEntity>)

    @Update
    suspend fun update(session: FocusProtocolSessionEntity)

    @Query("SELECT * FROM focus_protocol_sessions WHERE id = :id")
    suspend fun getById(id: Int): FocusProtocolSessionEntity?

    @Query(
        """
        SELECT * FROM focus_protocol_sessions
        WHERE phase NOT IN ('COMPLETE', 'CANCELLED')
        ORDER BY createdAt DESC
        LIMIT 1
        """
    )
    fun observeActive(): Flow<FocusProtocolSessionEntity?>

    @Query("SELECT * FROM focus_protocol_sessions ORDER BY createdAt DESC LIMIT 1")
    fun observeLatest(): Flow<FocusProtocolSessionEntity?>

    @Query(
        """
        SELECT * FROM focus_protocol_sessions
        WHERE phase = 'COMPLETE'
        ORDER BY completedAt DESC
        LIMIT :limit
        """
    )
    fun observeRecentCompleted(limit: Int): Flow<List<FocusProtocolSessionEntity>>

    @Query(
        """
        SELECT * FROM focus_protocol_sessions
        WHERE phase NOT IN ('COMPLETE', 'CANCELLED')
        ORDER BY createdAt DESC
        """
    )
    suspend fun getActive(): List<FocusProtocolSessionEntity>

    @Query(
        """
        SELECT * FROM focus_protocol_sessions
        WHERE itemId = :itemId AND phase NOT IN ('COMPLETE', 'CANCELLED')
        """
    )
    suspend fun getActiveForItem(itemId: Int): List<FocusProtocolSessionEntity>

    @Query("SELECT * FROM focus_protocol_sessions ORDER BY createdAt ASC")
    suspend fun getAll(): List<FocusProtocolSessionEntity>

    @Query("UPDATE focus_protocol_sessions SET distractionCount = distractionCount + 1 WHERE id = :id")
    suspend fun incrementDistractions(id: Int)

    @Query(
        """
        UPDATE focus_protocol_sessions
        SET soundscapeId = :primaryId,
            soundscapeCustomUri = :customUri,
            soundscapeCustomName = :customName,
            soundscapeVolume = :primaryVolume,
            soundscapeSecondaryId = :secondaryId,
            soundscapeSecondaryVolume = :secondaryVolume,
            soundscapePlayDuringRecovery = :playDuringRecovery
        WHERE id = :id AND phase NOT IN ('COMPLETE', 'CANCELLED')
        """
    )
    suspend fun updateSoundscape(
        id: Int,
        primaryId: String,
        customUri: String?,
        customName: String?,
        primaryVolume: Int,
        secondaryId: String?,
        secondaryVolume: Int,
        playDuringRecovery: Boolean
    ): Int

    @Query("DELETE FROM focus_protocol_sessions")
    suspend fun deleteAll()

    @Query(
        """
        UPDATE focus_protocol_sessions
        SET itemName = :itemName, activityType = :activityType
        WHERE itemId = :itemId AND phase NOT IN ('COMPLETE', 'CANCELLED')
        """
    )
    suspend fun syncActiveTaskTarget(
        itemId: Int,
        itemName: String,
        activityType: com.personal.sleepalarm.domain.model.FocusActivityType
    )

    @Query(
        """
        UPDATE focus_protocol_sessions
        SET itemId = :canonicalItemId, itemName = :itemName, activityType = :activityType
        WHERE itemId = :legacyTaskId AND activityType = 'WORK'
          AND phase NOT IN ('COMPLETE', 'CANCELLED')
        """
    )
    suspend fun migrateAndSyncLegacyTaskTarget(
        legacyTaskId: Int,
        canonicalItemId: Int,
        itemName: String,
        activityType: com.personal.sleepalarm.domain.model.FocusActivityType
    )

    @Query(
        """
        UPDATE focus_protocol_sessions
        SET phase = 'CANCELLED', phaseEndsAt = NULL,
            completedAt = :completedAt, cancelReason = 'TASK_DELETED'
        WHERE itemId = :itemId AND phase NOT IN ('COMPLETE', 'CANCELLED')
        """
    )
    suspend fun cancelActiveTaskTarget(itemId: Int, completedAt: Long)

    @Query(
        """
        UPDATE focus_protocol_sessions
        SET phase = 'CANCELLED', phaseEndsAt = NULL,
            completedAt = :completedAt, cancelReason = 'MISSING_TASK'
        WHERE itemId < 0 AND phase NOT IN ('COMPLETE', 'CANCELLED')
          AND NOT EXISTS (SELECT 1 FROM tasks WHERE tasks.id = -focus_protocol_sessions.itemId)
        """
    )
    suspend fun cancelMissingTaskTargets(completedAt: Long)

    @Query(
        """
        UPDATE focus_protocol_sessions
        SET phase = 'CANCELLED', phaseEndsAt = NULL,
            completedAt = :completedAt, cancelReason = 'MISSING_LEGACY_TASK'
        WHERE itemId > 0 AND activityType = 'WORK'
          AND phase NOT IN ('COMPLETE', 'CANCELLED')
          AND NOT EXISTS (SELECT 1 FROM tasks WHERE tasks.id = focus_protocol_sessions.itemId)
        """
    )
    suspend fun cancelMissingLegacyTaskTargets(completedAt: Long)
}
