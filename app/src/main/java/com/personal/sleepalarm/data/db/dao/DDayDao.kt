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
        SELECT dday_events.* FROM dday_events
        LEFT JOIN tasks ON tasks.id = dday_events.taskId
        WHERE targetDate >= :today
          AND (dday_events.taskId IS NULL OR tasks.isDone = 0)
        ORDER BY targetDate ASC
        LIMIT 1
        """
    )
    fun observeNearest(today: String): Flow<DDayEntity?>

    /** Ближайшее будущее событие (для брифинга). */
    @Query(
        """
        SELECT dday_events.* FROM dday_events
        LEFT JOIN tasks ON tasks.id = dday_events.taskId
        WHERE targetDate >= :today
          AND (dday_events.taskId IS NULL OR tasks.isDone = 0)
        ORDER BY targetDate ASC
        LIMIT 1
        """
    )
    suspend fun getNearest(today: String): DDayEntity?

    @Query("SELECT * FROM dday_events WHERE id = :id")
    suspend fun getById(id: Int): DDayEntity?

    @Query(
        """
        UPDATE dday_events
        SET title = CASE
            WHEN TRIM(title) = '' OR title = :oldTaskLabel THEN :newTaskLabel
            ELSE title
        END
        WHERE taskId = :taskId
        """
    )
    suspend fun syncTaskTitle(taskId: Int, oldTaskLabel: String, newTaskLabel: String)

    @Query(
        """
        UPDATE dday_events
        SET targetDate = :targetDate
        WHERE taskId = :taskId AND :targetDate IS NOT NULL
        """
    )
    suspend fun syncTaskDeadline(taskId: Int, targetDate: String?)

    @Query("UPDATE dday_events SET taskId = NULL WHERE taskId = :taskId")
    suspend fun detachTask(taskId: Int)

    @Query(
        """
        UPDATE dday_events SET taskId = NULL
        WHERE taskId IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM tasks WHERE tasks.id = dday_events.taskId)
        """
    )
    suspend fun detachMissingTasks()
}
