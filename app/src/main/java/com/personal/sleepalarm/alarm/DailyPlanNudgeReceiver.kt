package com.personal.sleepalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.personal.sleepalarm.data.preferences.DailyPlanNudgePreferences
import com.personal.sleepalarm.service.DailyPlanNotificationBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class DailyPlanNudgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        val appContext = context.applicationContext
        if (intent.action == ACTION_SNOOZE || intent.action == ACTION_DISMISS) {
            DailyPlanNotificationBuilder(appContext).cancel()
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val now = System.currentTimeMillis()
                val localDate = Instant.ofEpochMilli(now)
                    .atZone(ZoneId.systemDefault()).toLocalDate().toString()
                val preferences = DailyPlanNudgePreferences(appContext)
                val scheduler = DailyPlanNudgeScheduler(appContext, preferences = preferences)
                when (intent.action) {
                    ACTION_EVALUATE -> scheduler.refreshNow(nowMillis = now, playSoundIfDue = true)
                    ACTION_DISMISS -> {
                        preferences.dismissForDate(localDate)
                        scheduler.reschedule(now)
                    }
                    ACTION_SNOOZE -> {
                        val settings = preferences.get()
                        val snapshot = scheduler.buildSnapshot(now)
                        val requestedUntil = now +
                            settings.repeatIntervalMinutes.coerceIn(5, 120) * MINUTE_MILLIS
                        val until = minOf(
                            requestedUntil,
                            snapshot?.cutoffMillis ?: requestedUntil,
                            snapshot?.nextMidnightMillis ?: requestedUntil
                        )
                        if (until > now) preferences.snooze(localDate, until)
                        else preferences.dismissForDate(localDate)
                        scheduler.reschedule(now)
                    }
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Daily-plan action failed: ${intent.action}", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_EVALUATE = "com.personal.sleepalarm.dailyplan.EVALUATE"
        const val ACTION_SNOOZE = "com.personal.sleepalarm.dailyplan.SNOOZE"
        const val ACTION_DISMISS = "com.personal.sleepalarm.dailyplan.DISMISS"
        const val EXTRA_EXPECTED_AT = "extra_daily_plan_expected_at"
        private const val MINUTE_MILLIS = 60_000L
        private const val TAG = "DailyPlanReceiver"
        private val SUPPORTED_ACTIONS = setOf(ACTION_EVALUATE, ACTION_SNOOZE, ACTION_DISMISS)
    }
}
