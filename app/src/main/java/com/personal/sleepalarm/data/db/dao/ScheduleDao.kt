package com.personal.sleepalarm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.personal.sleepalarm.data.db.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO расписания.
 */
@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedule WHERE id = 1")
    fun observe(): Flow<ScheduleEntity?>

    @Query("DELETE FROM schedule")
    suspend fun deleteAll()
    @Query("SELECT * FROM schedule WHERE id = 1")
    suspend fun get(): ScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScheduleEntity)
}