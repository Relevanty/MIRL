package com.personal.sleepalarm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personal.sleepalarm.data.db.entity.PomodoroSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO помодоро-сессий.
 */
@Dao
interface PomodoroDao {

    @Insert
    suspend fun insert(session: PomodoroSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<PomodoroSessionEntity>)

    @Query("SELECT * FROM pomodoro_sessions ORDER BY startedAt ASC")
    suspend fun getAll(): List<PomodoroSessionEntity>

    @Query("DELETE FROM pomodoro_sessions")
    suspend fun deleteAll()

    @Update
    suspend fun update(session: PomodoroSessionEntity)

    /** Завершить сессию: поставить completedAt и isCompleted. */
    @Query(
        """
        UPDATE pomodoro_sessions
        SET completedAt = :completedAt, isCompleted = :isCompleted
        WHERE id = :id
        """
    )
    suspend fun markFinished(id: Int, completedAt: Long, isCompleted: Boolean)

    /** Все завершённые фокус-сессии (не перерывы) за период. */
    @Query(
        """
        SELECT * FROM pomodoro_sessions
        WHERE isBreak = 0 AND isCompleted = 1
          AND startedAt >= :from AND startedAt < :to
        ORDER BY startedAt DESC
        """
    )
    fun observeCompletedFocusBetween(from: Long, to: Long): Flow<List<PomodoroSessionEntity>>

    /** Фокус-сессии, пересекающие точный временной интервал. */
    @Query(
        """
        SELECT * FROM pomodoro_sessions
        WHERE isBreak = 0
          AND completedAt IS NOT NULL
          AND actualDurationMillis > 0
          AND completedAt > :from AND startedAt < :to
        ORDER BY startedAt ASC
        """
    )
    fun observeFocusOverlapping(from: Long, to: Long): Flow<List<PomodoroSessionEntity>>

    @Query(
        """
        SELECT * FROM pomodoro_sessions
        WHERE isBreak = 0 AND completedAt IS NOT NULL AND actualDurationMillis > 0
        ORDER BY startedAt ASC
        """
    )
    fun observeAllRecordedFocus(): Flow<List<PomodoroSessionEntity>>

    /** Количество завершённых фокусов за период. */
    @Query(
        """
        SELECT COUNT(*) FROM pomodoro_sessions
        WHERE isBreak = 0 AND isCompleted = 1
          AND startedAt >= :from AND startedAt < :to
        """
    )
    suspend fun countCompletedFocusBetween(from: Long, to: Long): Int

    /** Суммарные минуты фокуса за период. */
    @Query(
        """
        SELECT COALESCE(SUM(durationMinutes), 0) FROM pomodoro_sessions
        WHERE isBreak = 0 AND isCompleted = 1
          AND startedAt >= :from AND startedAt < :to
        """
    )
    suspend fun sumFocusMinutesBetween(from: Long, to: Long): Int
}
