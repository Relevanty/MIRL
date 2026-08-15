package com.personal.sleepalarm.data.db.dao

import androidx.room.Update
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.personal.sleepalarm.data.db.entity.CalendarEventEntity
import kotlinx.coroutines.flow.Flow
import androidx.room.OnConflictStrategy
@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM events ORDER BY startMillis ASC")
    fun observeAll(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM events")
    suspend fun getAll(): List<CalendarEventEntity>

    @Query("DELETE FROM events")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<CalendarEventEntity>)

    @Insert
    suspend fun insert(event: CalendarEventEntity): Long

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM events")
    suspend fun observeAllOnce(): List<CalendarEventEntity>

    @Update
    suspend fun update(event: CalendarEventEntity)


}