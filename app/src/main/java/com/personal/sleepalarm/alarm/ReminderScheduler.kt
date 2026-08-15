package com.personal.sleepalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.personal.sleepalarm.data.db.entity.ReminderEntity

class ReminderScheduler(
    private val context: Context
) {
    companion object {
        private const val TAG = "ReminderScheduler"
        const val ACTION_PRE = "com.personal.sleepalarm.reminder.PRE"
        const val ACTION_FIRE = "com.personal.sleepalarm.reminder.FIRE"
        const val ACTION_DONE = "com.personal.sleepalarm.reminder.DONE"
        const val ACTION_SNOOZE = "com.personal.sleepalarm.reminder.SNOOZE"
        const val ACTION_SNOOZE_FIRE = "com.personal.sleepalarm.reminder.SNOOZE_FIRE"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val PRE_LEAD_MS = 5 * 60_000L
        const val SNOOZE_MS = 5 * 60_000L
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms()
        else true

    fun schedule(reminder: ReminderEntity) {
        Log.d(TAG, "schedule id=${reminder.id} enabled=${reminder.isEnabled} next=${reminder.nextTriggerTime}")
        if (!reminder.isEnabled) {
            Log.d(TAG, "skip: disabled")
            return
        }
        cancel(reminder.id)

        val now = System.currentTimeMillis()
        val preTime = reminder.nextTriggerTime - PRE_LEAD_MS

        if (preTime > now) {
            Log.d(TAG, "set PRE at $preTime (+${preTime - now}ms)")
            setExact(preTime, reminder.id, ACTION_PRE)
        } else {
            Log.d(TAG, "PRE in past, skip")
        }

        if (reminder.nextTriggerTime > now) {
            Log.d(TAG, "set FIRE at ${reminder.nextTriggerTime} (+${reminder.nextTriggerTime - now}ms)")
            setExact(reminder.nextTriggerTime, reminder.id, ACTION_FIRE)
        } else {
            Log.d(TAG, "FIRE in past, skip")
        }
    }

    fun scheduleSnooze(reminderId: Int) {
        Log.d(TAG, "scheduleSnooze id=$reminderId")
        setExact(System.currentTimeMillis() + SNOOZE_MS, reminderId, ACTION_SNOOZE_FIRE)
    }

    fun cancel(reminderId: Int) {
        cancelPending(reminderId, ACTION_PRE)
        cancelPending(reminderId, ACTION_FIRE)
        cancelPending(reminderId, ACTION_SNOOZE_FIRE)
    }

    fun rescheduleAll(reminders: List<ReminderEntity>) {
        reminders.forEach { if (it.isEnabled) schedule(it) }
    }

    private fun setExact(time: Long, reminderId: Int, action: String) {
        // Проверка разрешения на точные alarm'ы.
        if (!canScheduleExact()) {
            Log.w(TAG, "canScheduleExact=false, falling back to inexact set()")
            setInexact(time, reminderId, action)
            return
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        val pending = PendingIntent.getBroadcast(
            context, requestCode(reminderId, action), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, time, pending)
            }
            Log.d(TAG, "OK setExact $action id=$reminderId at $time")
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException on setExact, fallback to inexact", se)
            setInexact(time, reminderId, action)
        }
    }

    /** Fallback для Android 12+ без разрешения на точные будильники. */
    private fun setInexact(time: Long, reminderId: Int, action: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        val pending = PendingIntent.getBroadcast(
            context, requestCode(reminderId, action), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
        Log.d(TAG, "set inexact $action id=$reminderId at $time")
    }

    private fun cancelPending(reminderId: Int, action: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply { this.action = action }
        val pending = PendingIntent.getBroadcast(
            context, requestCode(reminderId, action), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }

    private fun requestCode(reminderId: Int, action: String): Int =
        if (action == ACTION_PRE) reminderId * 2 else reminderId * 2 + 1
}
