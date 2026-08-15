package com.personal.sleepalarm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.personal.sleepalarm.data.db.entity.DiaryEntryEntity
import kotlinx.coroutines.flow.Flow
import androidx.room.OnConflictStrategy
@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries ORDER BY dateKey DESC, createdAt DESC")
    fun observeAll(): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entries WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getByDate(dateKey: String): DiaryEntryEntity?

    @Query("SELECT * FROM diary_entries")
    suspend fun getAll(): List<DiaryEntryEntity>

    @Query("DELETE FROM diary_entries")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DiaryEntryEntity>)

    @Insert
    suspend fun insert(entry: DiaryEntryEntity): Long

    @Update
    suspend fun update(entry: DiaryEntryEntity)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteById(id: Int)
}