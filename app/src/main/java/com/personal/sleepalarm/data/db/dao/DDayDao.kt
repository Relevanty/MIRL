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

    /** Raw optional metadata, including materials retained after a task due date is cleared. */
    @Query("SELECT * FROM dday_events ORDER BY targetDate ASC")
    fun observeMetadata(): Flow<List<DDayEntity>>

    /** Visible deadlines use the task's current local date, never the metadata cache. */
    @Query("""
        SELECT d.id, d.title,
            CASE WHEN d.taskId IS NULL THEN d.targetDate
                ELSE strftime('%Y-%m-%d', t.dueAtMillis / 1000, 'unixepoch', 'localtime') END AS targetDate,
            d.projectId, d.taskId, d.notes, d.linksJson, d.createdAt
        FROM dday_events d LEFT JOIN tasks t ON t.id = d.taskId
        WHERE d.taskId IS NULL OR t.dueAtMillis IS NOT NULL
        ORDER BY targetDate ASC
    """)
    fun observeAll(): Flow<List<DDayEntity>>

    /** Ближайшее событие, которое ещё не прошло (targetDate >= сегодня). */
    @Query(
        """
        SELECT d.id, d.title,
            CASE WHEN d.taskId IS NULL THEN d.targetDate
                ELSE strftime('%Y-%m-%d', t.dueAtMillis / 1000, 'unixepoch', 'localtime') END AS targetDate,
            d.projectId, d.taskId, d.notes, d.linksJson, d.createdAt
        FROM dday_events d LEFT JOIN tasks t ON t.id = d.taskId
        WHERE (d.taskId IS NULL AND d.targetDate >= :today)
           OR (d.taskId IS NOT NULL AND t.isDone = 0 AND t.dueAtMillis IS NOT NULL
               AND strftime('%Y-%m-%d', t.dueAtMillis / 1000, 'unixepoch', 'localtime') >= :today)
        ORDER BY targetDate ASC
        LIMIT 1
        """
    )
    fun observeNearest(today: String): Flow<DDayEntity?>

    /** Ближайшее будущее событие (для брифинга). */
    @Query(
        """
        SELECT d.id, d.title,
            CASE WHEN d.taskId IS NULL THEN d.targetDate
                ELSE strftime('%Y-%m-%d', t.dueAtMillis / 1000, 'unixepoch', 'localtime') END AS targetDate,
            d.projectId, d.taskId, d.notes, d.linksJson, d.createdAt
        FROM dday_events d LEFT JOIN tasks t ON t.id = d.taskId
        WHERE (d.taskId IS NULL AND d.targetDate >= :today)
           OR (d.taskId IS NOT NULL AND t.isDone = 0 AND t.dueAtMillis IS NOT NULL
               AND strftime('%Y-%m-%d', t.dueAtMillis / 1000, 'unixepoch', 'localtime') >= :today)
        ORDER BY targetDate ASC
        LIMIT 1
        """
    )
    suspend fun getNearest(today: String): DDayEntity?

    @Query("SELECT * FROM dday_events WHERE id = :id")
    suspend fun getById(id: Int): DDayEntity?

    @Query("SELECT * FROM dday_events WHERE taskId = :taskId LIMIT 1")
    suspend fun getForTask(taskId: Int): DDayEntity?

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

    /** A linked row is always optional metadata of the one canonical task deadline. */
    @Query(
        """
        UPDATE dday_events
        SET targetDate = :targetDate
        WHERE taskId = :taskId
        """
    )
    suspend fun syncTaskDeadline(taskId: Int, targetDate: String)

    /** Deleting a task also deletes its optional deadline materials; clearing its date does not. */
    @Query("DELETE FROM dday_events WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Int)

    @Query(
        """
        UPDATE dday_events SET taskId = NULL
        WHERE taskId IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM tasks WHERE tasks.id = dday_events.taskId)
        """
    )
    suspend fun detachMissingTasks()
}
