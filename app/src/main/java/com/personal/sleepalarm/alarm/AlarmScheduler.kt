package com.personal.sleepalarm.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.os.Build
import com.personal.sleepalarm.data.db.entity.CueEventEntity
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.data.repository.SleepSessionRepository

/**
 * Планировщик будильников и lucid-подсказок.
 *
 * Основной будильник:
 * - AlarmManager.setAlarmClock()
 *
 * Lucid-подсказки:
 * - AlarmManager.setExactAndAllowWhileIdle()
 *
 * Если точные alarm'ы запрещены, планировщик возвращает false/0.
 * UI должен заранее проверять canScheduleExactAlarms() и показывать баннер.
 */
class AlarmScheduler(
    private val alarmManager: AlarmManager,
    private val pendingIntentFactory: PendingIntentFactory,
    private val sessionRepository: SleepSessionRepository
) {

    /**
     * Проверка разрешения на точные alarm'ы.
     *
     * На Android 12+ пользователь может отключить точные будильники.
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Ставит основной будильник через setAlarmClock().
     *
     * Возвращает true, если alarm был поставлен.
     */
    @SuppressLint("MissingPermission")
    fun scheduleMainAlarm(session: SleepSessionEntity): Boolean {
        if (!canScheduleExactAlarms()) {
            return false
        }

        val triggerTime = session.estimatedWakeTime
        val now = System.currentTimeMillis()

        // Если время пробуждения уже прошло, не ставим будильник.
        // Восстановлением просроченных сессий занимается BootReceiver.
        if (triggerTime <= now) {
            return false
        }

        val operation = pendingIntentFactory.mainAlarmPendingIntent(session.id)
        val showIntent = pendingIntentFactory.mainAlarmShowPendingIntent(session.id)

        val alarmClockInfo = AlarmManager.AlarmClockInfo(
            triggerTime,
            showIntent
        )

        alarmManager.setAlarmClock(
            alarmClockInfo,
            operation
        )

        return true
    }

    /**
     * Ставит резервные точные alarm'ы для lucid-подсказок.
     *
     * Возвращает количество поставленных cue-alarm'ов.
     */
    @SuppressLint("MissingPermission")
    fun scheduleCueAlarms(
        sessionId: Int,
        cues: List<CueEventEntity>
    ): Int {
        if (!canScheduleExactAlarms()) {
            return 0
        }

        val now = System.currentTimeMillis()
        var scheduledCount = 0

        cues.forEach { cue ->
            // Ставим только будущие события.
            // Небольшой запас 1 секунда нужен, чтобы не поставить alarm
            // в уже прошедший момент.
            if (cue.scheduledTime > now + 1_000L) {
                val pendingIntent = pendingIntentFactory.cuePendingIntent(
                    sessionId = sessionId,
                    cueIndex = cue.cueIndex
                )

                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    cue.scheduledTime,
                    pendingIntent
                )

                scheduledCount++
            }
        }

        return scheduledCount
    }

    /**
     * Отменяет основной будильник и show intent.
     */
    fun cancelMainAlarm(sessionId: Int) {
        val alarmIntent = pendingIntentFactory.mainAlarmPendingIntent(sessionId)
        alarmManager.cancel(alarmIntent)
        alarmIntent.cancel()
        pendingIntentFactory.mainAlarmShowPendingIntent(sessionId).cancel()
    }

    /**
     * Отменяет один cue-alarm.
     */
    fun cancelCueAlarm(
        sessionId: Int,
        cueIndex: Int
    ) {
        val cueIntent = pendingIntentFactory.cuePendingIntent(
            sessionId = sessionId,
            cueIndex = cueIndex
        )
        alarmManager.cancel(cueIntent)
        cueIntent.cancel()
    }

    /**
     * Отменяет все alarm'ы сессии:
     * основной будильник и все cue-события, сохранённые в базе.
     */
    suspend fun cancelAllAlarmsForSession(sessionId: Int) {
        cancelMainAlarm(sessionId)

        val cues = sessionRepository.getCuesForSession(sessionId)
        cues.forEach { cue ->
            cancelCueAlarm(
                sessionId = sessionId,
                cueIndex = cue.cueIndex
            )
        }
    }

    /**
     * Полностью переставляет alarm'ы для сессии.
     *
     * Используется после boot или после восстановления сервиса.
     */
    suspend fun rescheduleAllForSession(session: SleepSessionEntity) {
        cancelAllAlarmsForSession(session.id)

        scheduleMainAlarm(session)

        if (session.cuesEnabled) {
            val scheduledCues = sessionRepository.getScheduledCues(session.id)
            scheduleCueAlarms(
                sessionId = session.id,
                cues = scheduledCues
            )
        }
    }

    companion object {

        /**
         * Удобная фабрика для receiver'ов.
         */
        fun create(
            context: Context,
            sessionRepository: SleepSessionRepository
        ): AlarmScheduler {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntentFactory = PendingIntentFactory(context)

            return AlarmScheduler(
                alarmManager = alarmManager,
                pendingIntentFactory = pendingIntentFactory,
                sessionRepository = sessionRepository
            )
        }
    }
}
