package com.personal.sleepalarm.service

import android.app.NotificationManager
import android.content.Context

/** Stable registry used by channel factories and the DND reconciliation pass. */
object AppNotificationChannelIds {
    const val SLEEP_SESSION = "sleep_session_channel"
    const val ALARM = "alarm_channel"
    const val ALARM_SET = "alarm_set_channel"
    const val REMINDER_PRE = "reminder_pre_channel"
    const val REMINDER_FIRE = "reminder_fire_channel_app_volume_dnd_v3"
    const val CALENDAR_EVENT = "calendar_event_channel_app_volume_v2"
    const val DAILY_PLAN = "daily_plan_nudge_silent_v1"
    const val FOCUS_PROTOCOL = "focus_protocol_channel_app_volume_v3"
    const val POMODORO = "pomodoro_channel_app_volume_dnd_v4"
    const val TASK_DEADLINE = "task_deadline_channel_v1"

    /**
     * Channels whose event can produce an app sound. The channel sound itself
     * stays null so the separately volume-controlled player remains the sole
     * audio source.
     */
    val SOUND_CHANNELS: Set<String> = linkedSetOf(
        ALARM,
        REMINDER_FIRE,
        CALENDAR_EVENT,
        DAILY_PLAN,
        FOCUS_PROTOCOL,
        POMODORO,
        TASK_DEADLINE
    )
}

data class DndBypassStatus(
    val policyAccessGranted: Boolean,
    val configuredChannelIds: Set<String>,
    val missingChannelIds: Set<String>,
    val blockedChannelIds: Set<String>
) {
    val fullyConfigured: Boolean
        get() = policyAccessGranted &&
            blockedChannelIds.isEmpty() &&
            missingChannelIds.isEmpty()
}

/**
 * Reconciles real Android channel state every time the app returns from
 * settings. It never deletes channels: deleting/recreating the same ID can
 * remove active notifications and Android may restore the old user lock.
 */
object DndBypassCoordinator {
    fun reconcile(context: Context): DndBypassStatus {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.isNotificationPolicyAccessGranted) {
            return DndBypassStatus(
                policyAccessGranted = false,
                configuredChannelIds = emptySet(),
                missingChannelIds = AppNotificationChannelIds.SOUND_CHANNELS,
                blockedChannelIds = emptySet()
            )
        }

        val missing = linkedSetOf<String>()
        AppNotificationChannelIds.SOUND_CHANNELS.forEach { channelId ->
            val channel = manager.getNotificationChannel(channelId)
            if (channel == null) {
                missing += channelId
            } else if (!channel.canBypassDnd()) {
                channel.setBypassDnd(true)
                manager.createNotificationChannel(channel)
            }
        }

        val configured = linkedSetOf<String>()
        val blocked = linkedSetOf<String>()
        AppNotificationChannelIds.SOUND_CHANNELS.forEach { channelId ->
            val channel = manager.getNotificationChannel(channelId) ?: return@forEach
            if (channel.canBypassDnd()) configured += channelId else blocked += channelId
        }
        return DndBypassStatus(
            policyAccessGranted = true,
            configuredChannelIds = configured,
            missingChannelIds = missing,
            blockedChannelIds = blocked
        )
    }
}
