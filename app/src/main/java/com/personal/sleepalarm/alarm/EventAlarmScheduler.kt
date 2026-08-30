package com.personal.sleepalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.personal.sleepalarm.data.db.entity.CalendarEventEntity
import com.personal.sleepalarm.domain.calculator.CalendarOccurrence
import com.personal.sleepalarm.domain.calculator.CalendarRecurrenceCalculator
import java.time.ZoneId

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
        const val EXTRA_OCCURRENCE_START = "extra_occurrence_start"
        const val EXTRA_OCCURRENCE_END = "extra_occurrence_end"
        const val EXTRA_OCCURRENCE_TRIGGER = "extra_occurrence_trigger"
        const val EXTRA_MASTER_START = "extra_event_master_start"
        const val EXTRA_MASTER_END = "extra_event_master_end"
        const val EXTRA_MASTER_REMINDER = "extra_event_master_reminder"
        const val EXTRA_MASTER_REPEAT = "extra_event_master_repeat"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms()
        else true

    /** Ставит alarm для ближайшего будущего occurrence, не изменяя master в БД. */
    fun schedule(
        event: CalendarEventEntity,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ) {
        val reminder = event.reminderMinutes
        if (reminder == null) {
            Log.d(TAG, "id=${event.id}: reminderMinutes=null, skip")
            cancel(event.id)
            return
        }

        val occurrence = CalendarRecurrenceCalculator.nextOccurrence(
            startMillis = event.startMillis,
            endMillis = event.endMillis,
            repeatRule = event.repeatRule,
            reminderMinutes = reminder,
            nowMillis = nowMillis,
            zoneId = zoneId
        )
        if (occurrence == null) {
            Log.d(TAG, "id=${event.id}: future occurrence not found, skip")
            cancel(event.id)
            return
        }
        val triggerAt = occurrence.triggerAtMillis

        if (!canScheduleExact()) {
            Log.w(TAG, "canScheduleExact=false, fallback to inexact")
            setInexact(event, occurrence)
            return
        }

        val pending = PendingIntent.getBroadcast(
            context, event.id, fireIntent(event, occurrence),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            Log.d(TAG, "OK id=${event.id} occurrence=${occurrence.startMillis} at $triggerAt (+${triggerAt - nowMillis}ms)")
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException, fallback", se)
            setInexact(event, occurrence)
        }
    }

    private fun setInexact(event: CalendarEventEntity, occurrence: CalendarOccurrence) {
        val pending = PendingIntent.getBroadcast(
            context, event.id, fireIntent(event, occurrence),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            occurrence.triggerAtMillis,
            pending
        )
    }

    private fun fireIntent(event: CalendarEventEntity, occurrence: CalendarOccurrence) =
        Intent(context, EventAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_EVENT_ID, event.id)
            putExtra(EXTRA_OCCURRENCE_START, occurrence.startMillis)
            putExtra(EXTRA_OCCURRENCE_END, occurrence.endMillis)
            putExtra(EXTRA_OCCURRENCE_TRIGGER, occurrence.triggerAtMillis)
            putExtra(EXTRA_MASTER_START, event.startMillis)
            putExtra(EXTRA_MASTER_END, event.endMillis)
            putExtra(EXTRA_MASTER_REMINDER, event.reminderMinutes ?: Int.MIN_VALUE)
            putExtra(EXTRA_MASTER_REPEAT, event.repeatRule)
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
