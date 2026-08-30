package com.personal.sleepalarm.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.personal.sleepalarm.data.preferences.SleepAutomationPreference
import com.personal.sleepalarm.domain.automation.SleepAutomationWindow
import java.time.Instant
import java.time.ZoneId

class SleepAutomationScheduler(
    private val context: Context,
    private val preference: SleepAutomationPreference = SleepAutomationPreference(context)
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @SuppressLint("MissingPermission")
    suspend fun scheduleNext(nowMillis: Long = System.currentTimeMillis()) {
        cancel()
        val settings = preference.get()
        if (!settings.enabled) return

        val now = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
        val current = SleepAutomationWindow.containing(
            now,
            settings.windowStartMinutes,
            settings.windowEndMinutes
        )
        val shouldArmCurrent = current != null &&
            settings.skippedWindowStartEpochDay != current.id &&
            settings.handledWindowStartEpochDay != current.id
        val triggerAt = if (shouldArmCurrent) {
            nowMillis + 1_000L
        } else {
            SleepAutomationWindow.nextStart(now, settings.windowStartMinutes)
                .toInstant().toEpochMilli()
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent())
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent())
        }
    }

    /** Retry a transient conflict inside the same window without a tight loop. */
    suspend fun scheduleRetry(
        delayMillis: Long = 15L * 60L * 1000L,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        cancel()
        val settings = preference.get()
        if (!settings.enabled) return
        val now = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
        val current = SleepAutomationWindow.containing(
            now,
            settings.windowStartMinutes,
            settings.windowEndMinutes
        ) ?: return scheduleNext(nowMillis)
        if (settings.skippedWindowStartEpochDay == current.id ||
            settings.handledWindowStartEpochDay == current.id
        ) return scheduleNext(nowMillis)
        val triggerAt = minOf(
            nowMillis + delayMillis.coerceAtLeast(60_000L),
            current.endExclusive.toInstant().toEpochMilli() - 1_000L
        )
        if (triggerAt <= nowMillis) return scheduleNext(nowMillis)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent())
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent())
        }
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, SleepAutomationReceiver::class.java).setAction(ACTION_ARM),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val ACTION_ARM = "com.personal.sleepalarm.ACTION_ARM_SLEEP_AUTOMATION"
        private const val REQUEST_CODE = 79_001
    }
}
