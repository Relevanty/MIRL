package com.personal.sleepalarm.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.CalendarEventEntity
import com.personal.sleepalarm.service.audio.AppNotificationSoundPlayer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Построитель уведомлений для событий календаря.
 */
class EventNotificationBuilder(
    private val context: Context
) {
    companion object {
        private const val TAG = "EventNotify"
        const val CHANNEL_ID = "calendar_event_channel_app_volume_v2"
        const val NOTIFICATION_ID_BASE = 100_000
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.calendar_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.calendar_notification_channel_description)
                enableVibration(true)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun build(event: CalendarEventEntity): android.app.Notification {
        val timeText = Instant.ofEpochMilli(event.startMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(event.title)
            .setContentText(context.getString(R.string.calendar_notification_event_time, timeText))
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
    }

    suspend fun show(event: CalendarEventEntity) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w(TAG, "skip: уведомления отключены пользователем")
            return
        }
        try {
            notificationManager.notify(NOTIFICATION_ID_BASE + event.id, build(event))
            AppNotificationSoundPlayer.play(context)
            Log.d(TAG, "shown id=${event.id} title=${event.title}")
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException (POST_NOTIFICATIONS)", se)
        } catch (e: Throwable) {
            Log.e(TAG, "show error", e)
        }
    }

    fun cancel(eventId: Int) = notificationManager.cancel(NOTIFICATION_ID_BASE + eventId)
}
