package com.personal.sleepalarm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personal.sleepalarm.data.db.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO напоминаний.
 */
@Dao
interface ReminderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<ReminderEntity>

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<ReminderEntity>)

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM reminders ORDER BY nextTriggerTime ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Int): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE isEnabled = 1")
    suspend fun getEnabled(): List<ReminderEntity>

    @Query("UPDATE reminders SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Int, enabled: Boolean)

    @Query("UPDATE reminders SET nextTriggerTime = :next WHERE id = :id")
    suspend fun setNextTriggerTime(id: Int, next: Long)

    @Query("SELECT * FROM reminders WHERE linkedType = 'TASK' AND linkedId = :taskId")
    suspend fun getLinkedToTask(taskId: Int): List<ReminderEntity>

    @Query(
        """
        UPDATE reminders
        SET title = CASE
            WHEN TRIM(title) = '' OR title = :oldTaskLabel THEN :newTaskLabel
            ELSE title
        END
        WHERE linkedType = 'TASK' AND linkedId = :taskId
        """
    )
    suspend fun syncTaskTitle(taskId: Int, oldTaskLabel: String, newTaskLabel: String)

    @Query("DELETE FROM reminders WHERE linkedType = 'TASK' AND linkedId = :taskId")
    suspend fun deleteLinkedToTask(taskId: Int)

    @Query(
        """
        UPDATE reminders
        SET isEnabled = 0, linkedType = '', linkedId = NULL
        WHERE linkedType = 'TASK' AND linkedId IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM tasks WHERE tasks.id = reminders.linkedId)
        """
    )
    suspend fun disableMissingTaskLinks()
}
