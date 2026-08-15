package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.dao.ReminderDao
import com.personal.sleepalarm.data.db.dao.TaskDao
import com.personal.sleepalarm.data.db.entity.ReminderEntity
import com.personal.sleepalarm.data.db.entity.RepeatMode
import com.personal.sleepalarm.domain.calculator.ReminderTimeCalculator
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий напоминаний.
 *
 * Отвечает за расчёт nextTriggerTime и пересчёт после срабатывания.
 * При удалении напоминания сбрасывает reminderId у связанной задачи.
 */
class ReminderRepository(
    private val reminderDao: ReminderDao,
    private val taskDao: TaskDao
) {

    fun observeAll(): Flow<List<ReminderEntity>> = reminderDao.observeAll()

    suspend fun getById(id: Int): ReminderEntity? = reminderDao.getById(id)

    /** Все включённые напоминания (для перепланирования после загрузки). */
    suspend fun getEnabled(): List<com.personal.sleepalarm.data.db.entity.ReminderEntity> =
        reminderDao.getEnabled()

    /**
     * Создаёт напоминание. nextTriggerTime рассчитывается сразу.
     * Возвращает id.
     */
    suspend fun create(
        title: String,
        timeHour: Int,
        timeMinute: Int,
        repeatMode: RepeatMode,
        daysOfWeek: Int,
        intervalDays: Int
    ): Long {
        val next = ReminderTimeCalculator.nextTrigger(
            mode = repeatMode,
            hour = timeHour,
            minute = timeMinute,
            daysOfWeek = daysOfWeek,
            intervalDays = intervalDays
        )

        return reminderDao.insert(
            ReminderEntity(
                title = title.trim(),
                timeHour = timeHour,
                timeMinute = timeMinute,
                repeatMode = repeatMode,
                daysOfWeek = daysOfWeek,
                intervalDays = intervalDays,
                nextTriggerTime = next,
                isEnabled = true
            )
        )
    }

    /** Обновляет напоминание и пересчитывает nextTriggerTime. */
    suspend fun update(reminder: ReminderEntity) {
        val next = ReminderTimeCalculator.nextTrigger(
            mode = reminder.repeatMode,
            hour = reminder.timeHour,
            minute = reminder.timeMinute,
            daysOfWeek = reminder.daysOfWeek,
            intervalDays = reminder.intervalDays
        )
        reminderDao.update(reminder.copy(nextTriggerTime = next))
    }

    /** Удаляет напоминание и сбрасывает ссылку у связанной задачи. */
    suspend fun delete(id: Int) {
        taskDao.clearReminderLink(id)
        reminderDao.deleteById(id)
    }

    suspend fun setEnabled(id: Int, enabled: Boolean) =
        reminderDao.setEnabled(id, enabled)

    /**
     * Пересчёт после срабатывания (ACTION_FIRE).
     * ONCE → отключается. Остальные → следующий триггер по режиму.
     */
    suspend fun rescheduleAfterFire(id: Int) {
        val reminder = reminderDao.getById(id) ?: return

        if (reminder.repeatMode == RepeatMode.ONCE) {
            reminderDao.setEnabled(id, false)
            return
        }

        val next = ReminderTimeCalculator.nextTrigger(
            mode = reminder.repeatMode,
            hour = reminder.timeHour,
            minute = reminder.timeMinute,
            daysOfWeek = reminder.daysOfWeek,
            intervalDays = reminder.intervalDays,
            lastTrigger = reminder.nextTriggerTime
        )
        reminderDao.setNextTriggerTime(id, next)
    }
}