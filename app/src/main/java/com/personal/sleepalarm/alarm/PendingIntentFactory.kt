package com.personal.sleepalarm.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.personal.sleepalarm.ui.AlarmActivity
import com.personal.sleepalarm.ui.MainActivity
import com.personal.sleepalarm.util.IntentActions
import com.personal.sleepalarm.util.IntentExtras
import com.personal.sleepalarm.util.RequestCodes

/**
 * Создаёт PendingIntent для:
 * - основного будильника;
 * - show intent будильника;
 * - резервных cue-событий.
 *
 * Все PendingIntent используют FLAG_IMMUTABLE,
 * потому что мы не планируем изменять их после создания.
 */
class PendingIntentFactory(
    private val context: Context
) {

    private val flags: Int
        get() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    /**
     * Operation PendingIntent для AlarmManager.setAlarmClock().
     *
     * Это broadcast, который получит событие пробуждения.
     */
    fun mainAlarmPendingIntent(sessionId: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = IntentActions.ACTION_MAIN_ALARM
            putExtra(IntentExtras.EXTRA_SESSION_ID, sessionId)
        }

        return PendingIntent.getBroadcast(
            context,
            RequestCodes.forMain(sessionId),
            intent,
            flags
        )
    }

    /**
     * Show intent для AlarmClockInfo.
     *
     * Система может открыть этот intent ДО срабатывания будильника
     * (например, по нажатию на значок следующего будильника). Поэтому здесь
     * открывается главный экран, а не AlarmActivity, иначе звук мог бы
     * запуститься раньше времени.
     */
    fun mainAlarmShowPendingIntent(sessionId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }

        return PendingIntent.getActivity(
            context,
            RequestCodes.forMainShow(sessionId),
            intent,
            flags
        )
    }

    /** Full-screen intent, создаваемый только в момент реального сигнала. */
    fun mainAlarmShowPendingIntentForTrigger(sessionId: Int): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java).apply {
            putExtra(IntentExtras.EXTRA_SESSION_ID, sessionId)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        }

        return PendingIntent.getActivity(
            context,
            RequestCodes.forMainShow(sessionId),
            intent,
            flags
        )
    }

    /**
     * PendingIntent для одной lucid-подсказки.
     */
    fun cuePendingIntent(
        sessionId: Int,
        cueIndex: Int
    ): PendingIntent {
        val intent = Intent(context, CueReceiver::class.java).apply {
            action = IntentActions.ACTION_CUE_ALARM
            putExtra(IntentExtras.EXTRA_SESSION_ID, sessionId)
            putExtra(IntentExtras.EXTRA_CUE_INDEX, cueIndex)
        }

        return PendingIntent.getBroadcast(
            context,
            RequestCodes.forCue(sessionId, cueIndex),
            intent,
            flags
        )
    }
}
