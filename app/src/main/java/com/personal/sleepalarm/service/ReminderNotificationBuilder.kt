package com.personal.sleepalarm.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.personal.sleepalarm.R
import com.personal.sleepalarm.alarm.ReminderReceiver
import com.personal.sleepalarm.data.db.entity.ReminderEntity
import com.personal.sleepalarm.service.audio.AppNotificationSoundPlayer

/**
 * Построитель уведомлений напоминаний + каналы.
 *
 * CHANNEL_PRE  — IMPORTANCE_LOW, беззвучный, с обратным отсчётом (chronometer).
 * CHANNEL_FIRE — IMPORTANCE_HIGH, со звуком и действиями.
 */
class ReminderNotificationBuilder(
    private val context: Context
) {
    companion object {
        private const val TAG = "ReminderNotify"

        const val CHANNEL_PRE = "reminder_pre_channel"
        const val CHANNEL_FIRE = "reminder_fire_channel_app_volume_v2"

        const val PRE_NOTIFICATION_ID_BASE = 90_000
        const val FIRE_NOTIFICATION_ID_BASE = 95_000
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pre = NotificationChannel(
                CHANNEL_PRE,
                context.getString(R.string.channel_reminder_pre),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_reminder_pre_desc)
                setSound(null, null)
                enableVibration(false)
            }

            val fire = NotificationChannel(
                CHANNEL_FIRE,
                context.getString(R.string.channel_reminder_fire),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_reminder_fire_desc)
                enableLights(true)
                enableVibration(true)
                setSound(null, null)
            }

            notificationManager.createNotificationChannel(pre)
            notificationManager.createNotificationChannel(fire)
        }
    }

    /** Этап 1: беззвучное уведомление за 5 минут с живым обратным отсчётом. */
    fun buildPreNotification(reminder: ReminderEntity): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL_PRE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(reminder.title)
            .setContentText(context.getString(R.string.reminder_pre_text))
            .setWhen(reminder.nextTriggerTime)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setSilent(true)
            .setAutoCancel(false)
            .setOngoing(false)
            .build()
    }

    /** Этап 2: звуковое уведомление с действиями «Выполнено» / «Отложить». */
    fun buildFireNotification(reminder: ReminderEntity): android.app.Notification {
        val doneIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_DONE
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
        }
        val donePi = PendingIntent.getBroadcast(
            context, reminder.id * 4 + 3, doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_SNOOZE
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
        }
        val snoozePi = PendingIntent.getBroadcast(
            context, reminder.id * 4 + 4, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_FIRE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(reminder.title)
            .setContentText(context.getString(R.string.reminder_fire_text))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setSilent(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, context.getString(R.string.reminder_action_done), donePi)
            .addAction(0, context.getString(R.string.reminder_action_snooze), snoozePi)
            .build()
    }

    fun preNotificationId(reminderId: Int) = PRE_NOTIFICATION_ID_BASE + reminderId
    fun fireNotificationId(reminderId: Int) = FIRE_NOTIFICATION_ID_BASE + reminderId

    fun cancelPre(reminderId: Int) =
        notificationManager.cancel(preNotificationId(reminderId))

    fun cancelFire(reminderId: Int) =
        notificationManager.cancel(fireNotificationId(reminderId))

    /** Безопасный show PRE с диагностикой. */
    fun showPre(reminder: ReminderEntity) {
        if (!areNotificationsEnabled()) {
            Log.w(TAG, "PRE skip: уведомления отключены пользователем")
            return
        }
        try {
            notificationManager.notify(
                preNotificationId(reminder.id),
                buildPreNotification(reminder)
            )
            Log.d(TAG, "PRE shown id=${reminder.id}")
        } catch (se: SecurityException) {
            Log.e(TAG, "PRE SecurityException (POST_NOTIFICATIONS не выдан)", se)
        } catch (e: Throwable) {
            Log.e(TAG, "PRE unexpected error", e)
        }
    }

    /** Безопасный show FIRE с диагностикой. */
    suspend fun showFire(reminder: ReminderEntity) {
        cancelPre(reminder.id)
        if (!areNotificationsEnabled()) {
            Log.w(TAG, "FIRE skip: уведомления отключены пользователем")
            return
        }
        try {
            notificationManager.notify(
                fireNotificationId(reminder.id),
                buildFireNotification(reminder)
            )
            AppNotificationSoundPlayer.play(context)
            Log.d(TAG, "FIRE shown id=${reminder.id}")
        } catch (se: SecurityException) {
            Log.e(TAG, "FIRE SecurityException (POST_NOTIFICATIONS не выдан)", se)
        } catch (e: Throwable) {
            Log.e(TAG, "FIRE unexpected error", e)
        }
    }

    /** Проверяет runtime-разрешение на Android 13+ и каналы. */
    private fun areNotificationsEnabled(): Boolean {
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) {
            Log.w(TAG, "areNotificationsEnabled=false")
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val fireChannel = notificationManager.getNotificationChannel(CHANNEL_FIRE)
            if (fireChannel != null &&
                fireChannel.importance == NotificationManager.IMPORTANCE_NONE) {
                Log.w(TAG, "CHANNEL_FIRE disabled by user")
                return false
            }
        }
        return true
    }
}
