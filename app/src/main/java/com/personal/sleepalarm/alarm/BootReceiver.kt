package com.personal.sleepalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.repository.SleepSessionRepository
import com.personal.sleepalarm.domain.automation.isAutomationPausedForFocus
import com.personal.sleepalarm.domain.model.CueEventState
import com.personal.sleepalarm.domain.model.DismissType
import com.personal.sleepalarm.service.SleepForegroundService
import com.personal.sleepalarm.service.SleepNotificationBuilder
import com.personal.sleepalarm.util.WakeLocks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Восстанавливает состояние после перезагрузки устройства.
 *
 * Логика:
 * 1. Ищем активную сессию.
 * 2. Если активной сессии нет — ничего не делаем.
 * 3. Если время пробуждения уже прошло:
 *    - помечаем сессию как MISSED;
 *    - отменяем все alarm'ы;
 *    - не звоним.
 * 4. Если время пробуждения ещё в будущем:
 *    - помечаем прошедшие cue как SKIPPED;
 *    - переставляем основной будильник;
 *    - переставляем оставшиеся cue-alarm'ы;
 *    - запускаем SleepForegroundService.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (intent.action !in SUPPORTED_ACTIONS) {
            return
        }
        val pendingResult = goAsync()
        val wakeLock = WakeLocks.acquire(
            context = context,
            tag = "bootReceiver",
            timeoutMs = BOOT_WAKE_LOCK_TIMEOUT_MS
        )

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val applicationContext = context.applicationContext
                val app = applicationContext as com.personal.sleepalarm.app.App
                val reminderRepository = app.serviceLocator.reminderRepository
                val enabledReminders = reminderRepository.getEnabled()
                    .mapNotNull { reminder -> reminderRepository.reconcileForScheduling(reminder) }
                app.serviceLocator.reminderScheduler.rescheduleAll(enabledReminders)
                app.serviceLocator.focusProtocolManager.reconcileActiveSessions()

                val database = AppDatabase.getInstance(applicationContext)
                val deadlineScheduler = TaskDeadlineScheduler(applicationContext)
                val explicitDeadlineTaskIds = enabledReminders.asSequence()
                    .filter { it.linkedType == "TASK" }
                    .filter { it.triggerRule in setOf("BEFORE_DEADLINE", "BECOMES_URGENT") }
                    .mapNotNull { it.linkedId }
                    .toSet()
                database.taskDao().getAll().forEach { task ->
                    if (task.id in explicitDeadlineTaskIds) deadlineScheduler.cancel(task.id)
                    else deadlineScheduler.schedule(task)
                }

                val repository = SleepSessionRepository(
                    database = database,
                    sessionDao = database.sleepSessionDao(),
                    cueEventDao = database.cueEventDao()
                )

                // События календаря
                val eventDao = database.calendarEventDao()
                val allEvents = eventDao.getSchedulableForAlarms()
                EventAlarmScheduler(context).rescheduleAll(allEvents)

                // Восстанавливаем и следующую ночь, даже когда активной
                // сессии сна после перезагрузки нет.
                SleepAutomationScheduler(applicationContext).scheduleNext()
                DailyPlanNudgeScheduler(applicationContext, database = database).reschedule()

                val scheduler = AlarmScheduler.create(
                    context = applicationContext,
                    sessionRepository = repository
                )

                val session = repository.getActiveSession()

                if (session == null) {
                    SleepNotificationBuilder.cancelSleepNotification(applicationContext)
                    SleepNotificationBuilder.cancelAlarmNotification(applicationContext)
                    SleepNotificationBuilder.cancelTransientNotifications(applicationContext)
                    Log.i(TAG, "No active session after boot")
                    return@launch
                }

                val now = System.currentTimeMillis()

                if (session.estimatedWakeTime <= now) {
                    val lateness = now - session.estimatedWakeTime
                    if (lateness <= BOOT_ALARM_GRACE_MS) {
                        Log.i(
                            TAG,
                            "Wake time passed during reboot; ringing now. sessionId=${session.id}"
                        )
                        SleepForegroundService.triggerAlarm(
                            context = applicationContext,
                            sessionId = session.id
                        )
                        return@launch
                    }

                    Log.i(
                        TAG,
                        "Active session already passed after boot. Marking MISSED. sessionId=${session.id}"
                    )

                    scheduler.cancelAllAlarmsForSession(session.id)
                    repository.finishSession(
                        sessionId = session.id,
                        actualWakeTime = now,
                        dismissType = DismissType.MISSED
                    )
                    SleepNotificationBuilder.cancelSleepNotification(applicationContext)
                    SleepNotificationBuilder.cancelAlarmNotification(applicationContext)
                    SleepNotificationBuilder.cancelTransientNotifications(applicationContext)
                    return@launch
                }

                Log.i(
                    TAG,
                    "Restoring active session after boot. sessionId=${session.id}"
                )

                repository.recoverInterruptedCuePlaybacks(session.id)

                val finalCycleStart = session.estimatedWakeTime -
                        session.cycleLengthMinutes * MINUTE_MS

                // Помечаем прошедшие cue и cue из финального цикла как skipped.
                val cues = repository.getCuesForSession(session.id)

                cues.forEach { cue ->
                    if (cue.state == CueEventState.SCHEDULED) {
                        val shouldBeSkipped = cue.scheduledTime <= now ||
                                cue.scheduledTime >= finalCycleStart

                        if (shouldBeSkipped) {
                            repository.markCueSkipped(
                                sessionId = session.id,
                                cueIndex = cue.cueIndex
                            )
                        }
                    }
                }

                // Переставляем основной будильник и оставшиеся cue.
                scheduler.rescheduleAllForSession(session)

                val focusActive = database.focusProtocolDao().getActive().isNotEmpty()
                if (session.isAutomationPausedForFocus() || focusActive) {
                    // The morning AlarmManager entry remains restored, but a
                    // reboot during focus must not restart sleep detection.
                    SleepNotificationBuilder.cancelSleepNotification(applicationContext)
                    Log.i(TAG, "Sleep sensor runtime remains paused while focus is active")
                    return@launch
                }

                // Запускаем сервис, чтобы он держал сессию,
                // обновлял уведомление и мог играть cues.
// ДОБАВЛЕНО: передаём время подъёма и первого пиипа в сервис,
// чтобы после перезагрузки уведомление показывало правильные цифры.
                val firstCueTime = repository.getScheduledCues(session.id)
                    .minByOrNull { it.scheduledTime }
                    ?.scheduledTime

                SleepForegroundService.start(
                    context = context,
                    sessionId = session.id,
                    wakeTime = session.estimatedWakeTime,
                    firstCueTime = firstCueTime,
                    showStartConfirmation = false
                )
            } catch (throwable: Throwable) {
                Log.e(TAG, "Error in BootReceiver", throwable)
            } finally {
                WakeLocks.release(wakeLock)
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
        private const val TAG = "BootReceiver"
        private const val MINUTE_MS = 60L * 1000L
        private const val BOOT_WAKE_LOCK_TIMEOUT_MS = 20_000L
        private const val BOOT_ALARM_GRACE_MS = 10L * MINUTE_MS
    }
}
