package com.personal.sleepalarm.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personal.sleepalarm.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow


// В конец файла TaskDao.kt (вне интерфейса):
/**
 * Проекция: количество выполненных задач по дням (для аналитики).
 */
data class TaskDoneCount(
    val date: String,
    val count: Int
)


/**
 * DAO задач.
 *
 * Задачи разделены на две секции:
 * - isMorningRoutine = true: утренняя рутина со стриками (сброс на уровне UI).
 * - isMorningRoutine = false: обычные (одноразовые/общие) задачи.
 */
@Dao
interface TaskDao {

    /** Все задачи (для брифинга). */
    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Int)

    /** Все задачи, отсортированные по времени создания (новые сверху). */
    @Query("SELECT * FROM tasks ORDER BY isDone ASC, matrixQuadrant ASC, sortOrder ASC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    /** Задачи по секции (утренняя рутина / общие). */
    @Query(
        """
        SELECT * FROM tasks
        WHERE isMorningRoutine = :isMorningRoutine
        ORDER BY isDone ASC, matrixQuadrant ASC, sortOrder ASC, createdAt DESC
        """
    )
    fun observeByRoutineFlag(isMorningRoutine: Boolean): Flow<List<TaskEntity>>

    /** Количество выполненных задач, сгруппированное по doneDate (для аналитики). */
    @Query(
        """
        SELECT doneDate AS date, COUNT(*) AS count
        FROM tasks
        WHERE doneDate IS NOT NULL AND isDone = 1
        GROUP BY doneDate
        """
    )
    suspend fun getDoneCountsByDate(): List<TaskDoneCount>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Int): TaskEntity?

    @Query("SELECT * FROM tasks WHERE reminderId = :reminderId LIMIT 1")
    suspend fun findByReminderId(reminderId: Int): TaskEntity?

    /**
     * Сбрасывает reminderId у задачи, если связанное напоминание удалено.
     * Вызывается из ReminderRepository при удалении напоминания.
     */
    @Query("UPDATE tasks SET reminderId = NULL WHERE reminderId = :reminderId")
    suspend fun clearReminderLink(reminderId: Int)

    /** Количество выполненных сегодня задач (для аналитики). */
    @Query(
        """
        SELECT COUNT(*) FROM tasks
        WHERE doneDate = :today AND isDone = 1
        """
    )
    suspend fun countDoneOnDate(today: String): Int

    /** Обновление полей выполнения (isDone, doneDate, completedAt, streakCount). */
    @Query(
        """
        UPDATE tasks
        SET isDone = :isDone,
            doneDate = :doneDate,
            completedAt = :completedAt,
            streakCount = :streakCount
        WHERE id = :id
        """
    )
    suspend fun updateCompletion(
        id: Int,
        isDone: Boolean,
        doneDate: String?,
        completedAt: Long?,
        streakCount: Int
    )

    /** Привязка напоминания к задаче. */
    @Query("UPDATE tasks SET reminderId = :reminderId WHERE id = :id")
    suspend fun setReminderId(id: Int, reminderId: Int?)

    /** Добавляет фактическое рабочее время, записанное Pomodoro или вручную. */
    @Query(
        """
        UPDATE tasks
        SET spentMillis = spentMillis + :durationMillis,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun addSpentMillis(id: Int, durationMillis: Long, updatedAt: Long = System.currentTimeMillis())

    /** Replaces the cached total after activity history is edited or deleted. */
    @Query("UPDATE tasks SET spentMillis = :spentMillis, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setSpentMillis(id: Int, spentMillis: Long, updatedAt: Long = System.currentTimeMillis())

    /** Обновляет порядок задач после перестановки шариков. */
    @Query("UPDATE tasks SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSortOrder(id: Int, sortOrder: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM tasks WHERE matrixQuadrant = :quadrant AND isMorningRoutine = 0 AND isDone = 0")
    suspend fun maxActiveSortOrder(quadrant: Int): Int

    @Query("SELECT * FROM tasks WHERE matrixQuadrant = :quadrant AND isMorningRoutine = 0 AND isDone = 0 ORDER BY sortOrder ASC, createdAt DESC")
    suspend fun getActiveInQuadrant(quadrant: Int): List<TaskEntity>
}
