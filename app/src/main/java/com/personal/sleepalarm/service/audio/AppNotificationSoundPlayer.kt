package com.personal.sleepalarm.service.audio

import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.util.Log
import com.personal.sleepalarm.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Проигрывает короткие сигналы приложения отдельно от звука NotificationChannel.
 *
 * Системное уведомление остаётся визуальным и беззвучным, а громкость этого
 * проигрывателя берётся из профиля. CueSoundPlayer использует USAGE_ALARM,
 * поэтому сигнал не зависит от системного потока «Уведомления», но уважает
 * громкость будильника и ограничения режима «Не беспокоить».
 */
object AppNotificationSoundPlayer {
    private val playbackMutex = Mutex()

    suspend fun play(
        context: Context,
        soundUri: Uri? = null,
        maxPlayMs: Long = DEFAULT_MAX_PLAY_MS
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val appContext = context.applicationContext
            val profile = AppDatabase.getInstance(appContext)
                .alarmProfileDao()
                .getProfile()
            val volumePercent = normalizeVolumePercent(
                profile?.notificationVolumePercent ?: DEFAULT_VOLUME_PERCENT
            )
            if (volumePercent == 0) {
                Log.d(TAG, "skip volume=0%")
                return@withContext true
            }

            playbackMutex.withLock {
                val safeMaxPlayMs = maxPlayMs.coerceIn(1L, MAX_PLAY_LIMIT_MS)
                val candidateUris = listOfNotNull(
                    soundUri,
                    defaultNotificationUri().takeUnless { it == soundUri }
                )
                var played = false
                for (candidateUri in candidateUris) {
                    Log.d(TAG, "play volume=$volumePercent% uri=$candidateUri")
                    played = CueSoundPlayer.play(
                        context = appContext,
                        uriString = candidateUri.toString(),
                        volumeFraction = volumeFraction(volumePercent),
                        maxPlayMs = safeMaxPlayMs
                    )
                    if (played) break
                }
                if (!played) {
                    Log.w(TAG, "Ringtone unavailable, using alarm-stream fallback tone")
                    played = playFallbackTone(volumePercent, safeMaxPlayMs)
                }
                Log.d(TAG, "complete played=$played")
                played
            }
        } catch (throwable: Throwable) {
            Log.e(TAG, "Unable to play app notification sound", throwable)
            false
        }
    }

    internal fun normalizeVolumePercent(value: Int): Int = value.coerceIn(0, 100)

    internal fun volumeFraction(value: Int): Float = normalizeVolumePercent(value) / 100f

    private fun defaultNotificationUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private fun playFallbackTone(volumePercent: Int, maxPlayMs: Long): Boolean {
        var toneGenerator: ToneGenerator? = null
        return try {
            val durationMs = maxPlayMs.coerceIn(MIN_FALLBACK_TONE_MS, MAX_FALLBACK_TONE_MS).toInt()
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, volumePercent)
            val started = toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, durationMs) == true
            if (started) Thread.sleep(durationMs.toLong() + TONE_RELEASE_DELAY_MS)
            started
        } catch (throwable: Throwable) {
            Log.e(TAG, "Unable to play fallback tone", throwable)
            false
        } finally {
            runCatching { toneGenerator?.release() }
        }
    }

    internal const val DEFAULT_VOLUME_PERCENT = 50
    private const val DEFAULT_MAX_PLAY_MS = 8_000L
    private const val MAX_PLAY_LIMIT_MS = 15_000L
    private const val MIN_FALLBACK_TONE_MS = 250L
    private const val MAX_FALLBACK_TONE_MS = 700L
    private const val TONE_RELEASE_DELAY_MS = 75L
    private const val TAG = "AppNotificationSound"
}
