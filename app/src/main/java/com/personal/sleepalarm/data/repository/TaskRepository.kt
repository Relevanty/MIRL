package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.dao.TaskDao
import com.personal.sleepalarm.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * Read-only task queries used by screens and focus target resolution.
 * All live TaskEntity mutations go through TaskEcosystemRepository via
 * TaskLifecycleCoordinator; keeping write methods out of this type makes an
 * accidental screen-specific source of truth harder to introduce.
 */
class TaskRepository(
    private val dao: TaskDao
) {
    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()

    fun observeMorningRoutine(): Flow<List<TaskEntity>> =
        dao.observeByRoutineFlag(isMorningRoutine = true)

    fun observeGeneralTasks(): Flow<List<TaskEntity>> =
        dao.observeByRoutineFlag(isMorningRoutine = false)

    suspend fun getById(id: Int): TaskEntity? = dao.getById(id)

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

    suspend fun nextSortOrder(quadrant: Int): Int = dao.maxActiveSortOrder(quadrant) + 1

    suspend fun getActiveInQuadrant(quadrant: Int): List<TaskEntity> =
        dao.getActiveInQuadrant(quadrant)

}
