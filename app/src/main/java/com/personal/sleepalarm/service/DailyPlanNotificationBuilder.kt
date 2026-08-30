package com.personal.sleepalarm.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.personal.sleepalarm.R
import com.personal.sleepalarm.alarm.DailyPlanNudgeReceiver
import com.personal.sleepalarm.data.preferences.AppSignalPreferences
import com.personal.sleepalarm.data.preferences.AppSignalType
import com.personal.sleepalarm.domain.dailyplan.DailyPlanProgressSnapshot
import com.personal.sleepalarm.service.audio.AppNotificationSoundPlayer
import com.personal.sleepalarm.ui.MainActivity
import java.util.Date

enum class DailyPlanNotificationPhase {
    MORNING,
    URGENCY
}

/** A single replaceable, system-silent notification for the whole daily plan. */
class DailyPlanNotificationBuilder(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val signalPreferences = AppSignalPreferences(appContext)

    init {
        createChannel()
    }

    suspend fun show(
        snapshot: DailyPlanProgressSnapshot,
        phase: DailyPlanNotificationPhase,
        playSound: Boolean,
        dedupeKey: String
    ): Boolean {
        if (!canNotify()) {
            cancel()
            return false
        }
        val candidateTasks = when (phase) {
            DailyPlanNotificationPhase.MORNING -> snapshot.unstartedTasks
            DailyPlanNotificationPhase.URGENCY -> snapshot.tasks.filter { it.remainingMinutes > 0 }
        }
        if (candidateTasks.isEmpty()) {
            cancel()
            return false
        }
        return try {
            notificationManager.notify(
                NOTIFICATION_ID,
                build(snapshot, phase, candidateTasks.first().taskId)
            )
            if (playSound) {
                AppNotificationSoundPlayer.play(
                    context = appContext,
                    settings = signalPreferences.get(AppSignalType.DAILY_PLAN),
                    dedupeKey = dedupeKey
                )
            }
            true
        } catch (error: SecurityException) {
            Log.w(TAG, "Daily-plan notification permission denied", error)
            false
        } catch (error: Throwable) {
            Log.e(TAG, "Cannot show daily-plan notification", error)
            false
        }
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun build(
        snapshot: DailyPlanProgressSnapshot,
        phase: DailyPlanNotificationPhase,
        firstTaskId: Int
    ): android.app.Notification {
        val cutoffText = DateFormat.getTimeFormat(appContext).format(Date(snapshot.cutoffMillis))
        val title: String
        val body: String
        when (phase) {
            DailyPlanNotificationPhase.MORNING -> {
                title = appContext.getString(R.string.daily_plan_morning_title)
                val names = snapshot.unstartedTasks.take(MAX_MORNING_TASKS)
                    .joinToString(", ") { it.title }
                body = appContext.getString(R.string.daily_plan_morning_body, names)
            }
            DailyPlanNotificationPhase.URGENCY -> {
                title = appContext.getString(R.string.daily_plan_notification_title)
                body = if (snapshot.isOverloaded) {
                    appContext.getString(
                        R.string.daily_plan_overload_body,
                        cutoffText,
                        snapshot.totalRemainingMinutes,
                        -snapshot.slackMinutes
                    )
                } else {
                    appContext.getString(
                        R.string.daily_plan_urgency_body,
                        cutoffText,
                        snapshot.totalRemainingMinutes,
                        snapshot.slackMinutes
                    )
                }
            }
        }

        val openPlan = mainActivityIntent(
            requestCode = REQUEST_OPEN_PLAN,
            destination = MainActivity.DESTINATION_TASKS
        )
        val startTask = mainActivityIntent(
            requestCode = REQUEST_START_TASK,
            destination = MainActivity.DESTINATION_FOCUS_PROTOCOL,
            taskId = firstTaskId
        )
        val snooze = PendingIntent.getBroadcast(
            appContext,
            REQUEST_SNOOZE,
            Intent(appContext, DailyPlanNudgeReceiver::class.java)
                .setAction(DailyPlanNudgeReceiver.ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val delete = PendingIntent.getBroadcast(
            appContext,
            REQUEST_DISMISS,
            Intent(appContext, DailyPlanNudgeReceiver::class.java)
                .setAction(DailyPlanNudgeReceiver.ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setTimeoutAfter((snapshot.cutoffMillis - snapshot.nowMillis).coerceAtLeast(1L))
            .setContentIntent(openPlan)
            .setDeleteIntent(delete)
            .addAction(0, appContext.getString(R.string.daily_plan_action_start), startTask)
            .addAction(
                0,
                appContext.getString(
                    if (snapshot.isOverloaded) R.string.daily_plan_action_adjust_plan
                    else R.string.daily_plan_action_open_plan
                ),
                openPlan
            )
            .addAction(0, appContext.getString(R.string.daily_plan_action_snooze), snooze)
            .build()
    }

    private fun mainActivityIntent(
        requestCode: Int,
        destination: String,
        taskId: Int? = null
    ): PendingIntent = PendingIntent.getActivity(
        appContext,
        requestCode,
        Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DESTINATION, destination)
            taskId?.let { putExtra(MainActivity.EXTRA_TASK_ID, it) }
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.channel_daily_plan),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = appContext.getString(R.string.channel_daily_plan_description)
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
            }
        )
    }

    private fun canNotify(): Boolean {
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    companion object {
        const val CHANNEL_ID = AppNotificationChannelIds.DAILY_PLAN
        const val NOTIFICATION_ID = 118_000
        private const val MAX_MORNING_TASKS = 3
        private const val REQUEST_OPEN_PLAN = 118_001
        private const val REQUEST_START_TASK = 118_002
        private const val REQUEST_SNOOZE = 118_003
        private const val REQUEST_DISMISS = 118_004
        private const val TAG = "DailyPlanNotification"
    }
}
