package com.personal.sleepalarm.util

import android.content.Context
import android.os.PowerManager

/**
 * Действия для BroadcastReceiver.
 */
object IntentActions {
    const val ACTION_MAIN_ALARM = "com.personal.sleepalarm.ACTION_MAIN_ALARM"
    const val ACTION_CUE_ALARM = "com.personal.sleepalarm.ACTION_CUE_ALARM"
}

/**
 * Ключи extras для Intent.
 */
object IntentExtras {
    const val EXTRA_SESSION_ID = "extra_session_id"
    const val EXTRA_CUE_INDEX = "extra_cue_index"
}

/**
 * Request codes для PendingIntent.
 *
 * Используем схему:
 * main alarm       = safeBase(sessionId) + 9999
 * main show intent = safeBase(sessionId) + 9998
 * cue              = safeBase(sessionId) + cueIndex
 *
 * cueIndex должен быть в диапазоне 0..9997,
 * чтобы не пересекаться с main show/main alarm.
 *
 * Для личного приложения ограничение в 200 000 сессий
 * и 9 998 событий на сессию более чем достаточно.
 */
object RequestCodes {
    const val MAIN_ALARM_OFFSET = 9999
    const val MAIN_SHOW_OFFSET = 9998
    const val MAX_CUE_INDEX = 9997

    private const val SESSION_MODULO = 200_000
    private const val CODES_PER_SESSION = 10_000

    fun forMain(sessionId: Int): Int {
        return safeBase(sessionId) + MAIN_ALARM_OFFSET
    }

    fun forMainShow(sessionId: Int): Int {
        return safeBase(sessionId) + MAIN_SHOW_OFFSET
    }

    fun forCue(sessionId: Int, cueIndex: Int): Int {
        require(cueIndex in 0..MAX_CUE_INDEX) {
            "cueIndex должен быть в диапазоне 0..$MAX_CUE_INDEX, получено: $cueIndex"
        }
        return safeBase(sessionId) + cueIndex
    }

    private fun safeBase(sessionId: Int): Int {
        // Гарантируем положительный остаток, даже если sessionId вдруг отрицательный.
        val safeSessionId = ((sessionId % SESSION_MODULO) + SESSION_MODULO) % SESSION_MODULO
        return safeSessionId * CODES_PER_SESSION
    }
}

/**
 * Небольшой helper для WakeLock в receiver'ах.
 *
 * Receiver должен работать быстро, поэтому WakeLock берётся
 * с таймаутом и освобождается в finally.
 */
object WakeLocks {

    private const val DEFAULT_TIMEOUT_MS = 10_000L

    fun acquire(
        context: Context,
        tag: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): PowerManager.WakeLock? {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        return powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "sleepalarm:$tag"
        ).apply {
            setReferenceCounted(false)
            acquire(timeoutMs)
        }
    }

    fun release(wakeLock: PowerManager.WakeLock?) {
        if (wakeLock?.isHeld == true) {
            wakeLock.release()
        }
    }
}