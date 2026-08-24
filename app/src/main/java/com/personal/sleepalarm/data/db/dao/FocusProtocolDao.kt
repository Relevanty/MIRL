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

    @Query("SELECT * FROM focus_protocol_sessions ORDER BY createdAt ASC")
    suspend fun getAll(): List<FocusProtocolSessionEntity>

    @Query("UPDATE focus_protocol_sessions SET distractionCount = distractionCount + 1 WHERE id = :id")
    suspend fun incrementDistractions(id: Int)

    @Query("DELETE FROM focus_protocol_sessions")
    suspend fun deleteAll()
}
