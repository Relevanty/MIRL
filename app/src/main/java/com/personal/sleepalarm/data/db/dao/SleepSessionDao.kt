package com.personal.sleepalarm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.domain.model.DismissType
import kotlinx.coroutines.flow.Flow
import androidx.room.OnConflictStrategy

/**
 * DAO для сессий сна.
 *
 * ДОБАВЛЕНО: updateDetectedOnset (F9).
 */
@Dao
interface SleepSessionDao {

    @Query("DELETE FROM sleep_sessions")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<SleepSessionEntity>)

    /** Последняя завершённая сессия (для «вчера вы спали…»). */
    @Query(
        """
        SELECT * FROM sleep_sessions
        WHERE actualWakeTime IS NOT NULL
        ORDER BY actualWakeTime DESC
        LIMIT 1
        """
    )
    suspend fun getLatestCompleted(): SleepSessionEntity?

    /** Последняя завершённая ночь в виде потока для утренней карточки. */
    @Query(
        """
        SELECT * FROM sleep_sessions
        WHERE actualWakeTime IS NOT NULL
        ORDER BY actualWakeTime DESC
        LIMIT 1
        """
    )
    fun observeLatestCompleted(): Flow<SleepSessionEntity?>
    @Query("SELECT * FROM sleep_sessions")
    suspend fun getAllSessions(): List<SleepSessionEntity>
    // === Существующие методы (НЕ менять) ===



    // В SleepSessionDao (если нет):
    @Query("SELECT * FROM sleep_sessions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SleepSessionEntity>>
    @Query("SELECT * FROM sleep_sessions WHERE id = :sessionId")
    suspend fun getById(sessionId: Int): SleepSessionEntity?

    @Query("SELECT * FROM sleep_sessions WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveSession(): SleepSessionEntity?

    @Query("SELECT * FROM sleep_sessions WHERE isActive = 1 LIMIT 1")
    fun observeActiveSession(): Flow<SleepSessionEntity?>

    @Query("SELECT * FROM sleep_sessions ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentSessions(limit: Int): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_sessions WHERE createdAt >= :sinceTimestamp ORDER BY createdAt DESC")
    fun observeSessionsSince(sinceTimestamp: Long): Flow<List<SleepSessionEntity>>

    @Insert
    suspend fun insert(session: SleepSessionEntity): Long

    @Update
    suspend fun update(session: SleepSessionEntity)

    @Query(
        """
        UPDATE sleep_sessions
        SET isActive = 0,
            actualWakeTime = :actualWakeTime,
            dismissType = :dismissType,
            cuesPlayedCount = :cuesPlayedCount,
            cuesSkippedCount = :cuesSkippedCount,
            updatedAt = :updatedAt
        WHERE id = :sessionId
        """
    )
    suspend fun finishSession(
        sessionId: Int,
        actualWakeTime: Long?,
        dismissType: DismissType,
        cuesPlayedCount: Int,
        cuesSkippedCount: Int,
        updatedAt: Long
    )

    @Query("DELETE FROM sleep_sessions WHERE id = :sessionId")
    suspend fun deleteById(sessionId: Int)

    @Query(
        """
        UPDATE sleep_sessions
        SET isActive = 0,
            dismissType = :dismissType,
            actualWakeTime = :cancelledAt,
            updatedAt = :updatedAt
        WHERE id = :sessionId
        """
    )
    suspend fun cancelSession(
        sessionId: Int,
        dismissType: DismissType,
        cancelledAt: Long,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE sleep_sessions
        SET cuesPlayedCount = cuesPlayedCount + 1,
            updatedAt = :updatedAt
        WHERE id = :sessionId
        """
    )
    suspend fun incrementPlayed(
        sessionId: Int,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE sleep_sessions
        SET cuesSkippedCount = cuesSkippedCount + 1,
            updatedAt = :updatedAt
        WHERE id = :sessionId
        """
    )
    suspend fun incrementSkipped(
        sessionId: Int,
        updatedAt: Long
    )

    @Query("SELECT COUNT(*) FROM sleep_sessions WHERE isActive = 1")
    suspend fun activeSessionCount(): Int

    // === ДОБАВЛЕНО: F9 — автоопределение засыпания ===

    /**
     * Записывает результат детекции засыпания по акселерометру.
     * Вызывается из SleepForegroundService через SleepSensorTracker.
     */
    @Query(
        """
        UPDATE sleep_sessions
        SET detectedSleepOnsetTime = :onsetTime,
            detectedOnsetLatencyMinutes = :latencyMinutes,
            detectedOnsetConfidencePercent = :confidencePercent,
            detectedOnsetSource = :source,
            detectedOnsetUncertaintyMinutes = :uncertaintyMinutes,
            onsetReviewState = 'PENDING',
            updatedAt = :updatedAt
        WHERE id = :sessionId
        """
    )
    suspend fun updateDetectedOnset(
        sessionId: Int,
        onsetTime: Long,
        latencyMinutes: Int,
        confidencePercent: Int,
        source: String,
        uncertaintyMinutes: Int,
        updatedAt: Long
    )
}
