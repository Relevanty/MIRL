package com.personal.sleepalarm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.personal.sleepalarm.data.db.entity.EnergySampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EnergySampleDao {
    @Insert
    suspend fun insert(sample: EnergySampleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<EnergySampleEntity>)

    @Query("SELECT * FROM energy_samples WHERE timestamp >= :from ORDER BY timestamp ASC")
    fun observeFrom(from: Long): Flow<List<EnergySampleEntity>>

    @Query("SELECT * FROM energy_samples ORDER BY timestamp ASC")
    suspend fun getAll(): List<EnergySampleEntity>

    @Query("DELETE FROM energy_samples")
    suspend fun deleteAll()
}
