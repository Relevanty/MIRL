package com.personal.sleepalarm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.personal.sleepalarm.data.db.entity.MoodEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO настроения.
 *
 * UNIQUE INDEX на date + REPLACE = одна запись в день:
 * повторное сохранение за тот же день перезаписывает предыдущее.
 */
@Dao
interface MoodEntryDao {

    @Query("SELECT * FROM mood_entries")
    suspend fun getAll(): List<MoodEntryEntity>

    @Query("DELETE FROM mood_entries")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<MoodEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MoodEntryEntity): Long

    @Query("SELECT * FROM mood_entries ORDER BY date DESC")
    fun observeAll(): Flow<List<MoodEntryEntity>>

    @Query("SELECT * FROM mood_entries WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): MoodEntryEntity?

    @Query("SELECT * FROM mood_entries WHERE date >= :from ORDER BY date ASC")
    fun observeFrom(from: String): Flow<List<MoodEntryEntity>>
}