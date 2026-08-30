package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.dao.ReminderDao
import com.personal.sleepalarm.data.db.dao.TaskDao
import com.personal.sleepalarm.data.db.dao.ActivityRecordDao
import com.personal.sleepalarm.data.db.entity.ReminderEntity
import com.personal.sleepalarm.data.db.entity.RepeatMode
import com.personal.sleepalarm.domain.calculator.ReminderTimeCalculator
import com.personal.sleepalarm.domain.model.ReminderSchedulingPolicy
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Репозиторий напоминаний.
 *
 * Отвечает за расчёт nextTriggerTime и пересчёт после срабатывания.
 * При удалении напоминания сбрасывает reminderId у связанной задачи.
 */
class ReminderRepository(
    private val reminderDao: ReminderDao,
    private val taskDao: TaskDao,
    private val activityRecordDao: ActivityRecordDao? = null
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
        intervalDays: Int,
        linkedType: String = "",
        linkedId: Int? = null,
        triggerRule: String = "AT_TIME",
        offsetMinutes: Int = 5,
        inactivityHours: Int = 24
    ): Long {
        val next = calculateNext(
            triggerRule, linkedId, offsetMinutes, inactivityHours,
            repeatMode, timeHour, timeMinute, daysOfWeek, intervalDays
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
                isEnabled = true,
                linkedType = linkedType,
                linkedId = linkedId,
                triggerRule = triggerRule,
                offsetMinutes = offsetMinutes,
                inactivityHours = inactivityHours
            )
        )
    }

    /** Обновляет напоминание и пересчитывает nextTriggerTime. */
    suspend fun update(reminder: ReminderEntity) {
        val next = calculateNext(
            reminder.triggerRule, reminder.linkedId, reminder.offsetMinutes, reminder.inactivityHours,
            reminder.repeatMode, reminder.timeHour, reminder.timeMinute,
            reminder.daysOfWeek, reminder.intervalDays
        )
        reminderDao.update(reminder.copy(nextTriggerTime = next))
    }

    /** Удаляет напоминание и сбрасывает ссылку у связанной задачи. */
    suspend fun delete(id: Int) {
        taskDao.clearReminderLink(id)
        reminderDao.deleteById(id)
    }

    /**
     * Changes the switch and, when enabling, derives a fresh trigger from the
     * current task/time state. Reusing a stale nextTriggerTime used to make a
     * re-enabled reminder look active while no alarm was actually scheduled.
     */
    suspend fun setEnabled(id: Int, enabled: Boolean): ReminderEntity? {
        val current = reminderDao.getById(id) ?: return null
        if (!enabled) {
            reminderDao.setEnabled(id, false)
            return current.copy(isEnabled = false)
        }
        update(current.copy(isEnabled = true))
        return reminderDao.getById(id)
    }

    /** Whether the reminder still has a live source from which it can fire. */
    suspend fun isSchedulable(reminder: ReminderEntity): Boolean {
        if (!reminder.isEnabled) return false
        val task = reminder.linkedId
            ?.takeIf { reminder.linkedType == "TASK" }
            ?.let { taskDao.getById(it) }
        return ReminderSchedulingPolicy.canSchedule(
            triggerRule = reminder.triggerRule,
            linkedType = reminder.linkedType,
            linkedId = reminder.linkedId,
            taskExists = task != null,
            taskDone = task?.isDone == true,
            taskDueAtMillis = task?.dueAtMillis,
            taskStartAtMillis = task?.startAtMillis
        )
    }

    /**
     * Reconciles wall-clock reminders after reboot or a clock/time-zone change.
     * A missed one-shot reminder stays one-shot and is disabled instead of
     * silently turning into a reminder for the following day.
     */
    suspend fun reconcileForScheduling(
        reminder: ReminderEntity,
        now: ZonedDateTime = ZonedDateTime.now()
    ): ReminderEntity? {
        if (!isSchedulable(reminder)) return null
        if (reminder.triggerRule != "AT_TIME" && reminder.triggerRule != "BEFORE_SLEEP") {
            val refreshed = refreshDynamic(reminder)
            return refreshed.takeIf { it.nextTriggerTime > now.toInstant().toEpochMilli() }
        }

        val nowMillis = now.toInstant().toEpochMilli()
        val next = when (reminder.repeatMode) {
            RepeatMode.ONCE -> {
                if (reminder.nextTriggerTime <= nowMillis) {
                    reminderDao.setEnabled(reminder.id, false)
                    return null
                }
                Instant.ofEpochMilli(reminder.nextTriggerTime)
                    .atZone(now.zone)
                    .withHour(reminder.timeHour)
                    .withMinute(reminder.timeMinute)
                    .withSecond(0)
                    .withNano(0)
                    .takeIf { it.isAfter(now) }
                    ?.toInstant()
                    ?.toEpochMilli()
                    // A one-shot does not store its original ZoneId. If moving
                    // the wall time would put it in the past, preserving the
                    // still-future instant is safer than losing the reminder.
                    ?: reminder.nextTriggerTime
            }
            RepeatMode.INTERVAL -> {
                var candidate = Instant.ofEpochMilli(reminder.nextTriggerTime)
                    .atZone(now.zone)
                    .withHour(reminder.timeHour)
                    .withMinute(reminder.timeMinute)
                    .withSecond(0)
                    .withNano(0)
                val step = reminder.intervalDays.coerceAtLeast(1).toLong()
                while (!candidate.isAfter(now)) candidate = candidate.plusDays(step)
                candidate.toInstant().toEpochMilli()
            }
            else -> ReminderTimeCalculator.nextTrigger(
                mode = reminder.repeatMode,
                hour = reminder.timeHour,
                minute = reminder.timeMinute,
                daysOfWeek = reminder.daysOfWeek,
                intervalDays = reminder.intervalDays,
                now = now
            )
        }
        reminderDao.setNextTriggerTime(reminder.id, next)
        return reminder.copy(nextTriggerTime = next)
    }

    /**
     * Пересчёт после срабатывания (ACTION_FIRE).
     * ONCE → отключается. Остальные → следующий триггер по режиму.
     */
    suspend fun rescheduleAfterFire(id: Int) {
        val reminder = reminderDao.getById(id) ?: return

        if (reminder.repeatMode == RepeatMode.ONCE ||
            (reminder.triggerRule != "AT_TIME" && reminder.triggerRule != "BEFORE_SLEEP")
        ) {
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

    /** Перепроверяет условие, если после планирования появился прогресс или сдвинулся дедлайн. */
    suspend fun refreshDynamic(reminder: ReminderEntity): ReminderEntity {
        if (reminder.triggerRule == "AT_TIME" || reminder.triggerRule == "BEFORE_SLEEP") return reminder
        val next = calculateNext(
            reminder.triggerRule, reminder.linkedId, reminder.offsetMinutes, reminder.inactivityHours,
            reminder.repeatMode, reminder.timeHour, reminder.timeMinute,
            reminder.daysOfWeek, reminder.intervalDays
        )
        if (kotlin.math.abs(next - reminder.nextTriggerTime) < 30_000L) return reminder
        reminderDao.setNextTriggerTime(reminder.id, next)
        return reminder.copy(nextTriggerTime = next)
    }

    private suspend fun calculateNext(
        triggerRule: String,
        linkedTaskId: Int?,
        offsetMinutes: Int,
        inactivityHours: Int,
        repeatMode: RepeatMode,
        hour: Int,
        minute: Int,
        daysOfWeek: Int,
        intervalDays: Int
    ): Long {
        val now = System.currentTimeMillis()
        val task = if (linkedTaskId != null) taskDao.getById(linkedTaskId) else null
        return when (triggerRule) {
            // Keep the canonical trigger even when it is already in the past.
            // The reconciliation boundary will then skip it instead of moving
            // an expired deadline/focus warning one minute into the future on
            // every app start.
            "BEFORE_DEADLINE", "BECOMES_URGENT" -> task?.dueAtMillis
                ?.minus(offsetMinutes.coerceAtLeast(0) * 60_000L)
                ?: Long.MIN_VALUE
            "BEFORE_FOCUS" -> task?.startAtMillis
                ?.minus(offsetMinutes.coerceAtLeast(0) * 60_000L)
                ?: Long.MIN_VALUE
            "NO_PROGRESS" -> {
                val lastProgress = linkedTaskId?.let { activityRecordDao?.getLatestEndForTask(it) }
                    ?: task?.createdAt
                    ?: now
                (lastProgress + inactivityHours.coerceAtLeast(1) * 3_600_000L)
                    .coerceAtLeast(now + 60_000L)
            }
            else -> ReminderTimeCalculator.nextTrigger(
                mode = repeatMode,
                hour = hour,
                minute = minute,
                daysOfWeek = daysOfWeek,
                intervalDays = intervalDays
            )
        }
    }
}
