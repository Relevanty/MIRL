package com.personal.sleepalarm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.ui.AlarmActivity
import com.personal.sleepalarm.ui.MainActivity
import com.personal.sleepalarm.util.IntentExtras
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


/**
 * Создаёт каналы уведомлений и уведомления:
 * - ongoing-уведомление активной сессии сна;
 * - тревожное уведомление будильника с fullScreenIntent.
 */
class SleepNotificationBuilder(
    private val context: Context
) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Создаёт каналы уведомлений.
     *
     * CHANNEL_SLEEP — низкий приоритет, ongoing-уведомление сна.
     * CHANNEL_ALARM — высокий приоритет, будильник, bypass DND.
     */
    fun createNotificationChannels() {
        val sleepChannel = NotificationChannel(
            CHANNEL_SLEEP,
            context.getString(R.string.notification_channel_sleep_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_sleep_description)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.notification_channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_alarm_description)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
            enableVibration(true)

            // Звук будильника управляется отдельно через AlarmSoundPlayer.
            setSound(null, null)
        }

        // ДОБАВЛЕНО: канал для всплывающего (heads-up) подтверждения установки будильника.
        val alarmSetChannel = NotificationChannel(
            CHANNEL_ALARM_SET,
            context.getString(R.string.notification_channel_alarm_set_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_alarm_set_description)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            // Без звука: подтверждение не должно пиликать, только всплывать.
            setSound(null, null)
        }

        notificationManager.createNotificationChannel(sleepChannel)
        notificationManager.createNotificationChannel(alarmChannel)
        notificationManager.createNotificationChannel(alarmSetChannel) // ДОБАВЛЕНО
    }

    /**
     * Канал мог быть создан до выдачи доступа «Не беспокоить». После выдачи
     * пересоздаём только канал будильника, чтобы setBypassDnd(true) применился.
     */
    fun ensureAlarmChannelCanBypassDnd() {
        if (!notificationManager.isNotificationPolicyAccessGranted) return

        val existing = notificationManager.getNotificationChannel(CHANNEL_ALARM)
        if (existing?.canBypassDnd() == true) return

        if (existing != null) {
            notificationManager.deleteNotificationChannel(CHANNEL_ALARM)
        }

        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.notification_channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_alarm_description)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
            enableVibration(true)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(alarmChannel)
    }



    /**
     * ДОБАВЛЕНО: уведомление-подтверждение сразу после установки будильника.
     * Показывает время подъёма и время ближайшей подсказки.
     */
    fun buildStartConfirmationNotification(
        wakeTime: Long,
        firstCueTime: Long?
    ): Notification {
        val wakeText = formatTime(wakeTime)

        val contentText = if (firstCueTime != null) {
            context.getString(
                R.string.sleep_notification_start_with_cue,
                wakeText,
                formatTime(firstCueTime)
            )
        } else {
            context.getString(R.string.sleep_notification_start_no_cue, wakeText)
        }

        return NotificationCompat.Builder(context, CHANNEL_SLEEP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.sleep_notification_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun formatTime(epochMillis: Long): String {
        val zonedDateTime = Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
        return DateTimeFormatter.ofPattern("HH:mm").format(zonedDateTime)
    }


    /**
     * Уведомление-заглушка, если сервис стартует,
     * но сессия ещё не успела прочитаться.
     */
    fun buildPlaceholderNotification(): Notification {
        return NotificationCompat.Builder(context, CHANNEL_SLEEP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.sleep_notification_title))
            .setContentText(context.getString(R.string.sleep_notification_starting))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Ongoing-уведомление активной сессии сна.
     *
     * Пример текста:
     * "Цикл 3/5 · следующий пиип через 12 мин · подъём в 06:45"
     */
    fun buildSleepNotification(
        session: SleepSessionEntity,
        nextCueTime: Long?,
        currentCycle: Int?
    ): Notification {
        val now = System.currentTimeMillis()

        val cyclePart = if (currentCycle != null) {
            context.getString(
                R.string.sleep_notification_cycle_format,
                currentCycle,
                session.cyclesPlanned
            )
        } else {
            context.getString(R.string.sleep_notification_no_cycle)
        }

        val cuePart = if (session.cuesEnabled && nextCueTime != null && nextCueTime > now) {
            val minutesUntilCue = minutesUntil(now, nextCueTime)
            context.getString(
                R.string.sleep_notification_next_cue_format,
                minutesUntilCue
            )
        } else {
            context.getString(R.string.sleep_notification_no_cues)
        }

        val wakePart = context.getString(
            R.string.sleep_notification_wake_format,
            formatTime(session.estimatedWakeTime, session.zoneId)
        )

        val contentText = context.getString(
            R.string.sleep_notification_status_format,
            cyclePart,
            cuePart,
            wakePart
        )

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val contentPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_MAIN,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, SleepForegroundService::class.java).apply {
            action = SleepForegroundService.ACTION_STOP_AND_CANCEL
        }

        val stopPendingIntent = PendingIntent.getService(
            context,
            REQUEST_CODE_STOP_SESSION,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_SLEEP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.sleep_notification_title))
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(contentText)
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)
            .addAction(
                0,
                context.getString(R.string.sleep_notification_action_cancel),
                stopPendingIntent
            )
            .build()
    }




    /**
     * Уведомление будильника.
     *
     * Использует fullScreenIntent, чтобы открыть AlarmActivity
     * поверх экрана блокировки.
     */
    fun buildAlarmNotification(
        sessionId: Int,
        fullScreenIntent: PendingIntent
    ): Notification {
        val contentIntent = Intent(context, AlarmActivity::class.java).apply {
            putExtra(IntentExtras.EXTRA_SESSION_ID, sessionId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }

        val contentPendingIntent = PendingIntent.getActivity(
            context,
            sessionId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(context.getString(R.string.alarm_notification_title))
            .setContentText(context.getString(R.string.alarm_notification_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.alarm_notification_text))
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(contentPendingIntent)
            .build()
    }

    /**
     * ДОБАВЛЕНО: всплывающее (heads-up) подтверждение установки будильника.
     * Показывается как push: высокий приоритет, категория ALARM.
     */
    fun buildAlarmSetNotification(
        wakeTime: Long,
        firstCueTime: Long?
    ): Notification {
        val wakeText = formatTime(wakeTime)

        val contentText = if (firstCueTime != null) {
            context.getString(
                R.string.sleep_notification_start_with_cue,
                wakeText,
                formatTime(firstCueTime)
            )
        } else {
            context.getString(R.string.sleep_notification_start_no_cue, wakeText)
        }

        return NotificationCompat.Builder(context, CHANNEL_ALARM_SET)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(context.getString(R.string.alarm_set_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(false)
            .setSilent(false)
            .build()
    }

    private fun formatTime(
        epochMillis: Long,
        zoneIdString: String
    ): String {
        val zone = runCatching { ZoneId.of(zoneIdString) }
            .getOrDefault(ZoneId.systemDefault())

        val zonedDateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)

        return DateTimeFormatter.ofPattern("HH:mm").format(zonedDateTime)
    }

    private fun minutesUntil(
        now: Long,
        future: Long
    ): Long {
        return Duration.ofMillis(future - now).toMinutes().coerceAtLeast(0)
    }

    companion object {
        const val CHANNEL_SLEEP = "sleep_session_channel"
        const val CHANNEL_ALARM = "alarm_channel"

        const val CHANNEL_ALARM_SET = "alarm_set_channel"
        const val ALARM_SET_NOTIFICATION_ID = 1003

        const val SLEEP_NOTIFICATION_ID = 1001
        const val ALARM_NOTIFICATION_ID = 1002

        private const val REQUEST_CODE_OPEN_MAIN = 10_001
        private const val REQUEST_CODE_STOP_SESSION = 10_002

        fun cancelAlarmNotification(context: Context) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(ALARM_NOTIFICATION_ID)
        }

        fun cancelSleepNotification(context: Context) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(SLEEP_NOTIFICATION_ID)
        }
    }
}
