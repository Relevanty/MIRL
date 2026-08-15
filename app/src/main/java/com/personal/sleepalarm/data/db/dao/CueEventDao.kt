package com.personal.sleepalarm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.personal.sleepalarm.data.db.entity.CueEventEntity

/**
 * DAO для lucid-подсказок.
 */
@Dao
interface CueEventDao {

    @Query("SELECT * FROM cue_events")
    suspend fun getAll(): List<CueEventEntity>

    @Query("DELETE FROM cue_events")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cues: List<CueEventEntity>)

    /**
     * Массовая вставка cue-событий при старте сессии.
     *
     * IGNORE используется, чтобы защитить базу от дублей,
     * например при повторном запуске сессии или восстановлении после reboot.
     */


    @Query("DELETE FROM cue_events WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Int)

    @Query("SELECT * FROM cue_events WHERE sessionId = :sessionId ORDER BY cueIndex ASC")
    suspend fun getCuesForSession(sessionId: Int): List<CueEventEntity>

    @Query("SELECT * FROM cue_events WHERE sessionId = :sessionId AND cueIndex = :cueIndex LIMIT 1")
    suspend fun getCue(sessionId: Int, cueIndex: Int): CueEventEntity?

    /**
     * Все запланированные cue для восстановления alarm'ов.
     */
    @Query("SELECT * FROM cue_events WHERE sessionId = :sessionId AND state = 'SCHEDULED' ORDER BY scheduledTime ASC")
    suspend fun getScheduledCues(sessionId: Int): List<CueEventEntity>

    @Query(
        """
        UPDATE cue_events
        SET state = 'PLAYING',
            playedAt = :claimedAt,
            playedBy = :playedBy,
            updatedAt = :claimedAt
        WHERE sessionId = :sessionId
          AND cueIndex = :cueIndex
          AND state = 'SCHEDULED'
        """
    )
    suspend fun claimForPlayback(
        sessionId: Int,
        cueIndex: Int,
        claimedAt: Long,
        playedBy: String
    ): Int

    @Query(
        """
        UPDATE cue_events
        SET state = 'PLAYED', updatedAt = :updatedAt
        WHERE sessionId = :sessionId
          AND cueIndex = :cueIndex
          AND state = 'PLAYING'
        """
    )
    suspend fun completePlaybackAsPlayed(
        sessionId: Int,
        cueIndex: Int,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE cue_events
        SET state = 'SKIPPED', updatedAt = :updatedAt
        WHERE sessionId = :sessionId
          AND cueIndex = :cueIndex
          AND state = 'PLAYING'
        """
    )
    suspend fun completePlaybackAsSkipped(
        sessionId: Int,
        cueIndex: Int,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE cue_events
        SET state = 'SKIPPED', updatedAt = :updatedAt
        WHERE sessionId = :sessionId
          AND state = 'PLAYING'
          AND playedAt < :claimedBefore
        """
    )
    suspend fun recoverInterruptedPlaybacks(
        sessionId: Int,
        claimedBefore: Long,
        updatedAt: Long
    ): Int

    /**
     * Атомарно помечает cue как SKIPPED.
     */
    @Query(
        """
        UPDATE cue_events
        SET state = 'SKIPPED',
            updatedAt = :updatedAt
        WHERE sessionId = :sessionId
          AND cueIndex = :cueIndex
          AND state = 'SCHEDULED'
        """
    )
    suspend fun markSkipped(
        sessionId: Int,
        cueIndex: Int,
        updatedAt: Long
    ): Int

    @Query("SELECT COUNT(*) FROM cue_events WHERE sessionId = :sessionId AND state = 'PLAYED'")
    suspend fun playedCount(sessionId: Int): Int

    @Query("SELECT COUNT(*) FROM cue_events WHERE sessionId = :sessionId AND state = 'SKIPPED'")
    suspend fun skippedCount(sessionId: Int): Int

    /**
     * Отменяет все ещё не проигранные cue.
     * Используется при отмене сессии, dismiss, snooze, reboot cleanup.
     */
    @Query(
        """
        UPDATE cue_events
        SET state = 'CANCELLED',
            updatedAt = :updatedAt
        WHERE sessionId = :sessionId
          AND state IN ('SCHEDULED', 'PLAYING')
        """
    )
    suspend fun cancelScheduled(
        sessionId: Int,
        updatedAt: Long
    )
}
