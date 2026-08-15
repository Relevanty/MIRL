package com.personal.sleepalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.repository.SleepSessionRepository
import com.personal.sleepalarm.service.SleepForegroundService
import com.personal.sleepalarm.util.IntentActions
import com.personal.sleepalarm.util.IntentExtras
import com.personal.sleepalarm.util.WakeLocks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receiver основного будильника.
 *
 * Получает событие от AlarmManager.setAlarmClock() и поднимает AlarmActivity
 * через полноэкранное уведомление (fullScreenIntent) — надёжный способ
 * на Android 14, где прямой startActivity из фона блокируется.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (intent.action != IntentActions.ACTION_MAIN_ALARM) {
            return
        }

        val sessionId = intent.getIntExtra(IntentExtras.EXTRA_SESSION_ID, -1)

        val pendingResult = goAsync()
        val wakeLock = WakeLocks.acquire(
            context = context,
            tag = "alarmReceiver"
        )

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val repository = createRepository(context)

                val session = if (sessionId >= 0) {
                    repository.getSession(sessionId)
                } else {
                    repository.getActiveSession()
                }

                val now = System.currentTimeMillis()

                if (session != null &&
                    session.isActive &&
                    session.estimatedWakeTime <= now + FIVE_MINUTES_MS
                ) {
                    // Звук запускает foreground service: он не зависит от того,
                    // разрешила ли прошивка открыть полноэкранный Activity.
                    SleepForegroundService.triggerAlarm(
                        context = context.applicationContext,
                        sessionId = session.id
                    )
                } else {
                    Log.i(
                        TAG,
                        "Alarm received but session is not valid. sessionId=$sessionId"
                    )
                }
            } catch (throwable: Throwable) {
                Log.e(TAG, "Error in AlarmReceiver", throwable)
            } finally {
                WakeLocks.release(wakeLock)
                pendingResult.finish()
            }
        }
    }

    private fun createRepository(context: Context): SleepSessionRepository {
        val database = AppDatabase.getInstance(context.applicationContext)

        return SleepSessionRepository(
            database = database,
            sessionDao = database.sleepSessionDao(),
            cueEventDao = database.cueEventDao()
        )
    }

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val FIVE_MINUTES_MS = 5L * 60L * 1000L
    }
}
