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
import com.personal.sleepalarm.data.preferences.PomodoroSoundPreference
import com.personal.sleepalarm.service.audio.AppNotificationSoundPlayer
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
                ensureChannel(appContext)
                val openIntent = TaskDeadlinePendingIntentFactory.openTasks(appContext, taskId)
                val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(appContext.getString(R.string.task_deadline_notification_title))
                    .setContentText(
                        task.title.ifBlank {
                            task.description.ifBlank {
                                task.nextAction.ifBlank { appContext.getString(R.string.task_untitled) }
                            }
                        }
                    )
                    .setSubText(appContext.getString(R.string.task_deadline_notification_subtitle))
                    .setContentIntent(openIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
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
                    soundUri = PomodoroSoundPreference(appContext).getUri()
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
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "task_deadline_channel_v1"
        private const val NOTIFICATION_BASE = 115_000
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
