package com.personal.sleepalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.personal.sleepalarm.data.db.entity.TaskEntity
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Планирует одно системное событие на конец выбранного дня задачи. */
class TaskDeadlineScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(task: TaskEntity) {
        cancel(task.id)
        if (task.id <= 0 || task.isDone || task.dueAtMillis == null) return

        val triggerAt = endOfDeadlineDay(task.dueAtMillis)
        if (triggerAt <= System.currentTimeMillis()) return
        val pending = pendingIntent(task.id)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (securityException: SecurityException) {
            Log.w(TAG, "Exact deadline alarm unavailable, using inexact", securityException)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(taskId: Int) {
        if (taskId <= 0) return
        alarmManager.cancel(pendingIntent(taskId))
    }

    fun rescheduleAll(tasks: List<TaskEntity>) = tasks.forEach(::schedule)

    private fun pendingIntent(taskId: Int): PendingIntent {
        val intent = Intent(context, TaskDeadlineReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + taskId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun endOfDeadlineDay(dueAtMillis: Long): Long {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(dueAtMillis).atZone(zone).toLocalDate()
        return date.atTime(LocalTime.of(18, 0)).atZone(zone).toInstant().toEpochMilli()
    }

    companion object {
        const val ACTION_FIRE = "com.personal.sleepalarm.task.DEADLINE"
        const val EXTRA_TASK_ID = "extra_task_deadline_id"
        private const val REQUEST_BASE = 320_000
        private const val TAG = "TaskDeadlineScheduler"
    }
}
