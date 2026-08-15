package com.personal.sleepalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.CalendarEventEntity
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
                    return@launch
                }

                notifier.show(event)

                // Если повторяющееся — переставляем на следующий день/неделю.
                val shiftMs = when (event.repeatRule) {
                    "daily" -> 24L * 60 * 60 * 1000
                    "weekly" -> 7L * 24 * 60 * 60 * 1000
                    else -> null
                }
                if (shiftMs != null) {
                    val updated = event.copy(
                        startMillis = event.startMillis + shiftMs,
                        endMillis = event.endMillis + shiftMs
                    )
                    eventDao.update(updated)
                    scheduler.schedule(updated)
                    Log.d(TAG, "rescheduled id=$eventId to next ${event.repeatRule}")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "error in onReceive", e)
            } finally {
                pending.finish()
            }
        }
    }
}