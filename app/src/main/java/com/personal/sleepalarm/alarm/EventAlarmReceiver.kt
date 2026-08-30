package com.personal.sleepalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.service.EventNotificationBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Приёмник срабатывания напоминания о событии календаря.
 * Показывает уведомление и для повторяющихся событий — переставляет alarm на следующий раз.
 */
class EventAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "EventReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getIntExtra(EventAlarmScheduler.EXTRA_EVENT_ID, -1)
        Log.d(TAG, "onReceive id=$eventId")
        if (eventId < 0) return

        val appContext = context.applicationContext
        val database = AppDatabase.getInstance(appContext)
        val eventDao = database.calendarEventDao()
        val scheduler = EventAlarmScheduler(appContext)
        val notifier = EventNotificationBuilder(appContext)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val all = eventDao.observeAllOnce()
                val event = all.firstOrNull { it.id == eventId }
                if (event == null) {
                    Log.w(TAG, "event $eventId not found")
                    notifier.cancel(eventId)
                    return@launch
                }
                val linkedTaskDone = event.taskId
                    ?.let { taskId -> database.taskDao().getById(taskId)?.isDone }
                    ?: false
                if (linkedTaskDone) {
                    notifier.cancel(eventId)
                    scheduler.cancel(eventId)
                    Log.d(TAG, "linked task completed, occurrence skipped id=$eventId")
                    return@launch
                }

                val expectedMasterStart = intent.getLongExtra(
                    EventAlarmScheduler.EXTRA_MASTER_START,
                    Long.MIN_VALUE
                )
                if (expectedMasterStart != Long.MIN_VALUE &&
                    (event.startMillis != expectedMasterStart ||
                        event.endMillis != intent.getLongExtra(
                            EventAlarmScheduler.EXTRA_MASTER_END,
                            Long.MIN_VALUE
                        ) ||
                        event.reminderMinutes != intent.getIntExtra(
                            EventAlarmScheduler.EXTRA_MASTER_REMINDER,
                            Int.MIN_VALUE
                        ).takeUnless { it == Int.MIN_VALUE } ||
                        event.repeatRule != intent.getStringExtra(
                            EventAlarmScheduler.EXTRA_MASTER_REPEAT
                        ))
                ) {
                    // Android may already have queued the previous PendingIntent
                    // while the user edits this series. Never present that old
                    // occurrence as if it belonged to the updated event.
                    notifier.cancel(eventId)
                    scheduler.schedule(event)
                    Log.d(TAG, "stale occurrence skipped id=$eventId")
                    return@launch
                }

                val occurrenceStart = intent.getLongExtra(
                    EventAlarmScheduler.EXTRA_OCCURRENCE_START,
                    event.startMillis
                )
                val occurrenceEnd = intent.getLongExtra(
                    EventAlarmScheduler.EXTRA_OCCURRENCE_END,
                    event.endMillis
                )
                val firedTrigger = intent.getLongExtra(
                    EventAlarmScheduler.EXTRA_OCCURRENCE_TRIGGER,
                    System.currentTimeMillis()
                )

                // Показываем конкретный occurrence, но recurrence master в БД
                // остаётся неизменным и продолжает отображать всю серию.
                notifier.show(
                    event.copy(
                        startMillis = occurrenceStart,
                        endMillis = occurrenceEnd
                    )
                )

                if (event.repeatRule == "daily" || event.repeatRule == "weekly") {
                    scheduler.schedule(
                        event = event,
                        nowMillis = maxOf(System.currentTimeMillis(), firedTrigger)
                    )
                    Log.d(TAG, "scheduled next occurrence id=$eventId rule=${event.repeatRule}")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "error in onReceive", e)
            } finally {
                pending.finish()
            }
        }
    }
}
