package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.dao.PomodoroDao
import com.personal.sleepalarm.data.db.entity.PomodoroSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий помодоро-сессий. Тонкая обёртка над DAO.
 */
class PomodoroRepository(
    private val pomodoroDao: PomodoroDao
) {

    suspend fun startSession(session: PomodoroSessionEntity): Long =
        pomodoroDao.insert(session)

    suspend fun markFinished(id: Long, completedAt: Long, isCompleted: Boolean) =
        pomodoroDao.markFinished(id.toInt(), completedAt, isCompleted)

    fun observeCompletedFocusBetween(from: Long, to: Long): Flow<List<PomodoroSessionEntity>> =
        pomodoroDao.observeCompletedFocusBetween(from, to)

    suspend fun countCompletedFocusBetween(from: Long, to: Long): Int =
        pomodoroDao.countCompletedFocusBetween(from, to)

    suspend fun sumFocusMinutesBetween(from: Long, to: Long): Int =
        pomodoroDao.sumFocusMinutesBetween(from, to)
}