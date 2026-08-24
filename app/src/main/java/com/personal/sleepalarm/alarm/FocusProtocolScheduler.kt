package com.personal.sleepalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.personal.sleepalarm.data.db.entity.FocusProtocolSessionEntity

/** Ставит один системный alarm на окончание текущей фазы протокола. */
class FocusProtocolScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(session: FocusProtocolSessionEntity) {
        cancel(session.id)
        val triggerAt = session.phaseEndsAt ?: return
        if (!session.phase.hasCountdown || triggerAt <= System.currentTimeMillis()) return

        val pending = phaseEndPendingIntent(session.id)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (securityException: SecurityException) {
            Log.w(TAG, "Exact alarm unavailable, using inexact alarm", securityException)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(sessionId: Int) {
        alarmManager.cancel(phaseEndPendingIntent(sessionId))
    }

    private fun phaseEndPendingIntent(sessionId: Int): PendingIntent {
        val intent = Intent(context, FocusProtocolReceiver::class.java).apply {
            action = FocusProtocolReceiver.ACTION_PHASE_END
            putExtra(FocusProtocolReceiver.EXTRA_SESSION_ID, sessionId)
        }
        return PendingIntent.getBroadcast(
            context,
            PHASE_REQUEST_BASE + sessionId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "FocusProtocolScheduler"
        private const val PHASE_REQUEST_BASE = 470_000
    }
}
