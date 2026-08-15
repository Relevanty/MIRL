package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.dao.ScheduleDao
import com.personal.sleepalarm.data.db.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий расписания. Тонкая обёртка над DAO.
 */
class ScheduleRepository(
    private val scheduleDao: ScheduleDao
) {

    fun observe(): Flow<ScheduleEntity?> = scheduleDao.observe()

    suspend fun get(): ScheduleEntity? = scheduleDao.get()

    /** Сохраняет текст расписания (одна запись, id = 1). */
    suspend fun save(content: String) {
        scheduleDao.upsert(
            ScheduleEntity(
                id = 1,
                content = content,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}