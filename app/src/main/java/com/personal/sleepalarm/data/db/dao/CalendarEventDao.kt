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

    @Query("SELECT * FROM events WHERE taskId = :taskId")
    suspend fun getLinkedToTask(taskId: Int): List<CalendarEventEntity>

    @Query(
        """
        SELECT events.* FROM events
        LEFT JOIN tasks ON tasks.id = events.taskId
        WHERE events.taskId IS NULL OR tasks.isDone = 0
        """
    )
    suspend fun getSchedulableForAlarms(): List<CalendarEventEntity>

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

    /** Keeps only task-owned projection fields in sync; custom event titles survive. */
    @Query(
        """
        UPDATE events
        SET title = CASE
                WHEN TRIM(title) = '' OR title = :oldTaskLabel THEN :newTaskLabel
                ELSE title
            END,
            projectId = :projectId
        WHERE taskId = :taskId
        """
    )
    suspend fun syncTaskProjection(
        taskId: Int,
        oldTaskLabel: String,
        newTaskLabel: String,
        projectId: Int?
    )

    @Query("UPDATE events SET taskId = NULL WHERE taskId = :taskId")
    suspend fun detachTask(taskId: Int)

    @Query(
        """
        UPDATE events SET taskId = NULL
        WHERE taskId IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM tasks WHERE tasks.id = events.taskId)
        """
    )
    suspend fun detachMissingTasks()


}
