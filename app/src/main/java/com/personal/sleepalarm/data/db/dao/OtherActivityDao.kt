package com.personal.sleepalarm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personal.sleepalarm.data.db.entity.OtherActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OtherActivityDao {
    @Query("SELECT * FROM other_activities ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<OtherActivityEntity>>

    @Query("SELECT * FROM other_activities ORDER BY createdAt ASC")
    suspend fun getAll(): List<OtherActivityEntity>

    @Insert
    suspend fun insert(activity: OtherActivityEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(activities: List<OtherActivityEntity>)

    @Update
    suspend fun update(activity: OtherActivityEntity)

    @Query("DELETE FROM other_activities WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM other_activities")
    suspend fun deleteAll()
}
