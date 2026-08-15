package com.personal.sleepalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.personal.sleepalarm.data.db.entity.CalendarEventEntity

/**
 * Планировщик напоминаний для событий календаря.
 * Ставит точный alarm на startMillis - reminderMinutes*60_000.
 */
class EventAlarmScheduler(
    private val context: Context
) {
    companion object {
        private const val TAG = "EventScheduler"
        const val ACTION_FIRE = "com.personal.sleepalarm.event.FIRE"
        const val EXTRA_EVENT_ID = "extra_event_id"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms()
        else true

    /** Ставит alarm для события, если reminderMinutes задан. */
    fun schedule(event: CalendarEventEntity) {
        val reminder = event.reminderMinutes
        if (reminder == null) {
            Log.d(TAG, "id=${event.id}: reminderMinutes=null, skip")
            cancel(event.id)
            return
        }

        val triggerAt = event.startMillis - reminder * 60_000L
        val now = System.currentTimeMillis()

        if (triggerAt <= now) {
            Log.d(TAG, "id=${event.id}: triggerAt в прошлом (${triggerAt - now}ms), skip")
            cancel(event.id)
            return
        }

        if (!canScheduleExact()) {
            Log.w(TAG, "canScheduleExact=false, fallback to inexact")
            setInexact(triggerAt, event.id)
            return
        }

        val intent = Intent(context, EventAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_EVENT_ID, event.id)
        }
        val pending = PendingIntent.getBroadcast(
            context, event.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            Log.d(TAG, "OK id=${event.id} at $triggerAt (+${triggerAt - now}ms)")
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException, fallback", se)
            setInexact(triggerAt, event.id)
        }
    }

    private fun setInexact(time: Long, eventId: Int) {
        val intent = Intent(context, EventAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        val pending = PendingIntent.getBroadcast(
            context, eventId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
    }

    fun cancel(eventId: Int) {
        val intent = Intent(context, EventAlarmReceiver::class.java).apply { action = ACTION_FIRE }
        val pending = PendingIntent.getBroadcast(
            context, eventId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }

    /** Переставить все события (после загрузки устройства). */
    fun rescheduleAll(events: List<CalendarEventEntity>) {
        events.forEach { schedule(it) }
    }
}