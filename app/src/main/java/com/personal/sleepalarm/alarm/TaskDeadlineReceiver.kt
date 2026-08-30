package com.personal.sleepalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.data.preferences.AppSignalPreferences
import com.personal.sleepalarm.data.preferences.AppSignalType
import com.personal.sleepalarm.service.audio.AppNotificationSoundPlayer
import com.personal.sleepalarm.service.AppNotificationChannelIds
import com.personal.sleepalarm.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Показывает дедлайн только для актуальной, ещё не выполненной задачи. */
class TaskDeadlineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(TaskDeadlineScheduler.EXTRA_TASK_ID, 0)
        if (taskId <= 0) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val appContext = context.applicationContext
                val task = AppDatabase.getInstance(appContext).taskDao().getById(taskId)
                if (task == null || task.isDone) return@launch
                val expectedDueAt = intent.getLongExtra(
                    TaskDeadlineScheduler.EXTRA_EXPECTED_DUE_AT,
                    Long.MIN_VALUE
                )
                if (expectedDueAt != Long.MIN_VALUE && task.dueAtMillis != expectedDueAt) {
                    // A broadcast already queued by Android can arrive after
                    // the task deadline was edited. Its durable task id still
                    // exists, but this particular occurrence is stale.
                    return@launch
                }
                ensureChannel(appContext)
                val openIntent = TaskDeadlinePendingIntentFactory.openTasks(appContext, taskId)
                val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(appContext.getString(R.string.task_deadline_notification_title))
                    .setContentText(task.primaryLabel())
                    .setSubText(appContext.getString(R.string.task_deadline_notification_subtitle))
                    .setContentIntent(openIntent)
                    .setAutoCancel(true)
                    .setTimeoutAfter(24L * 60L * 60L * 1000L)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setSilent(true)
                    .setOnlyAlertOnce(true)
                    .build()
                val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        appContext,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                if (!canNotify) return@launch
                NotificationManagerCompat.from(appContext)
                    .notify(NOTIFICATION_BASE + taskId, notification)
                AppNotificationSoundPlayer.play(
                    context = appContext,
                    settings = AppSignalPreferences(appContext).get(AppSignalType.REMINDER),
                    dedupeKey = "task-deadline-$taskId-${task.dueAtMillis}"
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.task_deadline_notification_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(true)
                setBypassDnd(true)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = AppNotificationChannelIds.TASK_DEADLINE
        private const val NOTIFICATION_BASE = 115_000

        fun cancelNotification(context: Context, taskId: Int) {
            context.getSystemService(NotificationManager::class.java)
                .cancel(NOTIFICATION_BASE + taskId)
        }
    }
}

private object TaskDeadlinePendingIntentFactory {
    fun openTasks(context: Context, taskId: Int): android.app.PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_TASKS)
            putExtra(MainActivity.EXTRA_TASK_ID, taskId)
        }
        return android.app.PendingIntent.getActivity(
            context,
            420_000 + taskId,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }
}
