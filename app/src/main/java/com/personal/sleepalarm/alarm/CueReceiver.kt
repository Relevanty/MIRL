package com.personal.sleepalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.repository.SleepSessionRepository
import com.personal.sleepalarm.service.audio.CueSoundPlayer
import com.personal.sleepalarm.util.IntentActions
import com.personal.sleepalarm.util.IntentExtras
import com.personal.sleepalarm.util.WakeLocks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Резервный receiver для lucid-подсказок.
 *
 * Основной механизм подсказок — SleepForegroundService.
 * CueReceiver нужен на случай, если сервис был убит,
 * но точный alarm для подсказки всё-таки сработал.
 *
 * Чтобы не было двойного звука:
 * 1. Сначала атомарно помечаем cue как PLAYED в Room.
 * 2. Если markCuePlayed вернул true, играем звук.
 * 3. Если cue уже был сыгран сервисом, ничего не играем.
 *
 * В receiver'е используем обычный beep, а не TTS/binaural,
 * потому что receiver должен отработать быстро и надёжно.
 */
class CueReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (intent.action != IntentActions.ACTION_CUE_ALARM) {
            return
        }

        val sessionId = intent.getIntExtra(IntentExtras.EXTRA_SESSION_ID, -1)
        val cueIndex = intent.getIntExtra(IntentExtras.EXTRA_CUE_INDEX, -1)

        if (sessionId < 0 || cueIndex < 0) {
            return
        }

        val pendingResult = goAsync()
        val wakeLock = WakeLocks.acquire(
            context = context,
            tag = "cueReceiver",
            timeoutMs = RECEIVER_WAKE_LOCK_TIMEOUT_MS
        )

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val repository = createRepository(context)
                val session = repository.getSession(sessionId)

                val now = System.currentTimeMillis()

                if (session == null || !session.isActive) {
                    Log.i(TAG, "Cue ignored: session is null or inactive. sessionId=$sessionId")
                    return@launch
                }

                if (!session.cuesEnabled) {
                    Log.i(TAG, "Cue ignored: cues disabled. sessionId=$sessionId")
                    repository.markCueSkipped(
                        sessionId = sessionId,
                        cueIndex = cueIndex
                    )
                    return@launch
                }

                val finalCycleStart = session.estimatedWakeTime -
                        session.cycleLengthMinutes * MINUTE_MS

                // Если уже начался финальный цикл или время пробуждения прошло,
                // подсказку играть нельзя.
                if (now >= finalCycleStart || now >= session.estimatedWakeTime) {
                    Log.i(TAG, "Cue skipped: final cycle or wake time. sessionId=$sessionId")
                    repository.markCueSkipped(
                        sessionId = sessionId,
                        cueIndex = cueIndex
                    )
                    return@launch
                }

                // Атомарно захватываем cue ДО воспроизведения. Сервис и
                // receiver больше не могут одновременно сыграть один сигнал.
                val claimed = repository.claimCuePlayback(
                    sessionId = sessionId,
                    cueIndex = cueIndex,
                    playedBy = PLAYED_BY_RECEIVER
                )

                if (!claimed) {
                    Log.i(
                        TAG,
                        "Cue already played or not scheduled. sessionId=$sessionId, cueIndex=$cueIndex"
                    )
                    return@launch
                }

                val played = session.cueRingtoneUri?.let { uri ->
                    CueSoundPlayer.play(
                        context = context.applicationContext,
                        uriString = uri,
                        volumePercent = session.cueVolumePercent,
                        maxPlayMs = MAX_RECEIVER_CUE_PLAY_MS
                    )
                } ?: false

                repository.completeCuePlayback(
                    sessionId = sessionId,
                    cueIndex = cueIndex,
                    played = played
                )


                Log.i(
                    TAG,
                    "Cue handled by receiver. played=$played, sessionId=$sessionId, cueIndex=$cueIndex"
                )
            } catch (throwable: Throwable) {
                Log.e(TAG, "Error in CueReceiver", throwable)
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
        private const val TAG = "CueReceiver"
        private const val MINUTE_MS = 60L * 1000L
        private const val MAX_RECEIVER_CUE_PLAY_MS = 8_000L
        private const val RECEIVER_WAKE_LOCK_TIMEOUT_MS = 10_000L
        private const val PLAYED_BY_RECEIVER = "RECEIVER"
    }
}
