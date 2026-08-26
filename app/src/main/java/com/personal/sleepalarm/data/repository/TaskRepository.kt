package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.dao.TaskDao
import com.personal.sleepalarm.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Репозиторий задач.
 *
 * Логика стриков:
 * - При отметке утренней рутины сегодня:
 *     * если doneDate == вчера → streak + 1
 *     * если doneDate == сегодня → ничего не менять (уже отмечено)
 *     * иначе → streak = 1 (новый стрик)
 * - Устанавливается doneDate = сегодня, isDone = true, completedAt = now.
 * - Визуальный сброс isDone при наступлении нового дня — на уровне UI
 *   (через map в Flow).
 *
 * Связь с напоминаниями:
 * - При удалении напоминания вызывается clearReminderLink, чтобы
 *   задача не держала мёртвую ссылку.
 */
class TaskRepository(
    private val dao: TaskDao
) {
    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()

    fun observeMorningRoutine(): Flow<List<TaskEntity>> =
        dao.observeByRoutineFlag(isMorningRoutine = true)

    fun observeGeneralTasks(): Flow<List<TaskEntity>> =
        dao.observeByRoutineFlag(isMorningRoutine = false)

    suspend fun getById(id: Int): TaskEntity? = dao.getById(id)

    suspend fun addTask(title: String, isMorningRoutine: Boolean): Long {
        val quadrant = 2
        return dao.insert(
            TaskEntity(
                title = title.trim(),
                isMorningRoutine = isMorningRoutine,
                matrixQuadrant = quadrant,
                sortOrder = if (isMorningRoutine) 0 else dao.maxActiveSortOrder(quadrant) + 1
            )
        )
    }

    suspend fun update(task: TaskEntity) = dao.update(task)

    suspend fun save(task: TaskEntity): Long {
        return if (task.id == 0) dao.insert(task) else {
            dao.update(task)
            task.id.toLong()
        }
    }

    suspend fun delete(task: TaskEntity) = dao.delete(task)

    suspend fun deleteById(id: Int) = dao.deleteById(id)

    /**
     * Отмечает задачу как выполненную.
     * Для утренней рутины ведёт стрик. Для обычной задачи — просто toggle.
     */
    suspend fun markDone(taskId: Int) {
        val task = dao.getById(taskId) ?: return
        val today = LocalDate.now().format(dateFormat)

        if (task.isMorningRoutine) {
            // Уже отмечено сегодня — ничего не делаем.
            if (task.doneDate == today) return

            val yesterday = LocalDate.now().minusDays(1).format(dateFormat)
            val newStreak = if (task.doneDate == yesterday) {
                task.streakCount + 1
            } else {
                1
            }

            dao.updateCompletion(
                id = taskId,
                isDone = true,
                doneDate = today,
                completedAt = System.currentTimeMillis(),
                streakCount = newStreak
            )
        } else {
            // Обычная задача — просто toggle.
            dao.updateCompletion(
                id = taskId,
                isDone = !task.isDone,
                doneDate = if (!task.isDone) today else null,
                completedAt = if (!task.isDone) System.currentTimeMillis() else null,
                streakCount = 0
            )
        }
    }

    /**
     * Возвращает актуальное isDone для отображения:
     * для утренней рутины — true только если doneDate == сегодня.
     */
    fun isDoneToday(task: TaskEntity, today: String): Boolean {
        return if (task.isMorningRoutine) {
            task.doneDate == today
        } else {
            task.isDone
        }
    }

    suspend fun setReminderId(taskId: Int, reminderId: Int?) =
        dao.setReminderId(taskId, reminderId)

    suspend fun addSpentMillis(taskId: Int, durationMillis: Long) {
        if (durationMillis <= 0L) return
        dao.addSpentMillis(taskId, durationMillis)
    }

    suspend fun updateSortOrder(taskId: Int, sortOrder: Int) =
        dao.updateSortOrder(taskId, sortOrder)

    suspend fun nextSortOrder(quadrant: Int): Int = dao.maxActiveSortOrder(quadrant) + 1

    suspend fun getActiveInQuadrant(quadrant: Int): List<TaskEntity> =
        dao.getActiveInQuadrant(quadrant)

    /**
     * Вызывается из ReminderRepository при удалении напоминания —
     * сбрасывает reminderId у связанной задачи.
     */
    suspend fun clearReminderLink(reminderId: Int) =
        dao.clearReminderLink(reminderId)

    suspend fun countDoneOnDate(date: String): Int =
        dao.countDoneOnDate(date)
}
