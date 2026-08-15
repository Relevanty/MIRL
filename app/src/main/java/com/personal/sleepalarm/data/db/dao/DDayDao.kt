package com.personal.sleepalarm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personal.sleepalarm.data.db.entity.DDayEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO обратных отсчётов (D-Day).
 *
 * Даты хранятся как yyyy-MM-dd — лексикографическая сортировка
 * совпадает с хронологической, поэтому ORDER BY targetDate корректен.
 */
@Dao
interface DDayDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: DDayEntity): Long

    @Update
    suspend fun update(event: DDayEntity)

    @Query("DELETE FROM dday_events WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM dday_events")
    suspend fun getAll(): List<DDayEntity>

    @Query("DELETE FROM dday_events")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<DDayEntity>)

    /** Все события, ближайшие сверху. */
    @Query("SELECT * FROM dday_events ORDER BY targetDate ASC")
    fun observeAll(): Flow<List<DDayEntity>>

    /** Ближайшее событие, которое ещё не прошло (targetDate >= сегодня). */
    @Query(
        """
        SELECT * FROM dday_events
        WHERE targetDate >= :today
        ORDER BY targetDate ASC
        LIMIT 1
        """
    )
    fun observeNearest(today: String): Flow<DDayEntity?>

    /** Ближайшее будущее событие (для брифинга). */
    @Query(
        """
        SELECT * FROM dday_events
        WHERE targetDate >= :today
        ORDER BY targetDate ASC
        LIMIT 1
        """
    )
    suspend fun getNearest(today: String): DDayEntity?

    @Query("SELECT * FROM dday_events WHERE id = :id")
    suspend fun getById(id: Int): DDayEntity?
}