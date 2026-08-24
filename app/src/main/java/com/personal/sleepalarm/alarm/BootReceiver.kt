package com.personal.sleepalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.repository.SleepSessionRepository
import com.personal.sleepalarm.domain.model.CueEventState
import com.personal.sleepalarm.domain.model.DismissType
import com.personal.sleepalarm.service.SleepForegroundService
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
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
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
                val enabledReminders = app.serviceLocator.reminderRepository.getEnabled()
                app.serviceLocator.reminderScheduler.rescheduleAll(enabledReminders)
                app.serviceLocator.focusProtocolManager.reconcileActiveSessions()

                val database = AppDatabase.getInstance(applicationContext)

                val repository = SleepSessionRepository(
                    database = database,
                    sessionDao = database.sleepSessionDao(),
                    cueEventDao = database.cueEventDao()
                )

                // События календаря
                val eventDao = database.calendarEventDao()
                val allEvents = eventDao.observeAllOnce()
                EventAlarmScheduler(context).rescheduleAll(allEvents)

                val scheduler = AlarmScheduler.create(
                    context = applicationContext,
                    sessionRepository = repository
                )

                val session = repository.getActiveSession()

                if (session == null) {
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
                    firstCueTime = firstCueTime
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
        private const val TAG = "BootReceiver"
        private const val MINUTE_MS = 60L * 1000L
        private const val BOOT_WAKE_LOCK_TIMEOUT_MS = 20_000L
        private const val BOOT_ALARM_GRACE_MS = 10L * MINUTE_MS
    }
}
