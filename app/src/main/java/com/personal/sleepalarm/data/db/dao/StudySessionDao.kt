package com.personal.sleepalarm.data.db.dao

import androidx.room.OnConflictStrategy
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.personal.sleepalarm.data.db.entity.StudySessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudySessionDao {
    @Insert
    suspend fun insert(session: StudySessionEntity): Long

    @Query("SELECT * FROM study_sessions WHERE dateKey = :dateKey ORDER BY startMillis ASC")
    fun observeByDate(dateKey: String): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions")
    suspend fun getAll(): List<StudySessionEntity>

    @Query("DELETE FROM study_sessions")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<StudySessionEntity>)

    @Query("SELECT * FROM study_sessions WHERE startMillis >= :from AND startMillis < :to ORDER BY startMillis ASC")
    fun observeInRange(from: Long, to: Long): Flow<List<StudySessionEntity>>

    @Query("SELECT SUM(durationMillis) FROM study_sessions WHERE dateKey = :dateKey")
    suspend fun totalForDay(dateKey: String): Long?

    @Query("SELECT subjectId, SUM(durationMillis) AS total FROM study_sessions WHERE dateKey = :dateKey GROUP BY subjectId")
    suspend fun bySubjectForDay(dateKey: String): List<SubjectTotal>

    @Query("SELECT subjectId, SUM(durationMillis) AS total FROM study_sessions WHERE startMillis >= :from AND startMillis < :to GROUP BY subjectId")
    suspend fun bySubjectInRange(from: Long, to: Long): List<SubjectTotal>
}

data class SubjectTotal(val subjectId: Int, val total: Long)