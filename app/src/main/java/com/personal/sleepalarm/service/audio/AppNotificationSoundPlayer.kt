package com.personal.sleepalarm.service.audio

import android.content.Context
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.util.Log
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.preferences.AppSignalSettings
import com.personal.sleepalarm.data.preferences.AppSoundMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Проигрывает короткие сигналы приложения отдельно от звука NotificationChannel.
 *
 * Системное уведомление остаётся визуальным и беззвучным, а источник и
 * громкость берутся из отдельной настройки конкретного типа сигнала.
 * Старый общий уровень профиля используется только как миграционный fallback.
 * CueSoundPlayer uses MIRL's common USAGE_ALARM routing, so the signal is not
 * tied to notification volume and can pass DND modes that allow alarm audio.
 */
object AppNotificationSoundPlayer {
    private val playbackMutex = Mutex()
    private val recentPlaybackByKey = mutableMapOf<String, Long>()

    suspend fun play(
        context: Context,
        settings: AppSignalSettings = AppSignalSettings(),
        maxPlayMs: Long = DEFAULT_MAX_PLAY_MS,
        dedupeKey: String? = null,
        allowSystemFallback: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val appContext = context.applicationContext
            val sound = settings.sound.normalized()
            if (sound.mode == AppSoundMode.SILENT) {
                Log.d(TAG, "skip silent mode")
                return@withContext true
            }
            val profile = AppDatabase.getInstance(appContext)
                .alarmProfileDao()
                .getProfile()
            val volumePercent = settings.effectiveVolume(
                profile?.notificationVolumePercent ?: DEFAULT_VOLUME_PERCENT
            )
            if (volumePercent == 0) {
                Log.d(TAG, "skip volume=0%")
                return@withContext true
            }

            playbackMutex.withLock {
                val now = System.currentTimeMillis()
                if (dedupeKey != null) {
                    val lastPlaybackAt = recentPlaybackByKey[dedupeKey]
                    if (lastPlaybackAt != null && now - lastPlaybackAt < DEDUPE_WINDOW_MS) {
                        Log.d(TAG, "skip duplicate key=$dedupeKey")
                        return@withLock true
                    }
                    recentPlaybackByKey[dedupeKey] = now
                    recentPlaybackByKey.entries.removeAll { now - it.value >= DEDUPE_RETENTION_MS }
                }
                val safeMaxPlayMs = maxPlayMs.coerceIn(1L, MAX_PLAY_LIMIT_MS)
                val selectedUri = sound.uriString?.let { raw ->
                    runCatching { Uri.parse(raw) }.getOrNull()
                }
                val candidateUris = when {
                    selectedUri == null -> listOf(defaultNotificationUri())
                    allowSystemFallback -> listOf(
                        selectedUri,
                        defaultNotificationUri()
                    ).distinct()
                    else -> listOf(selectedUri)
                }
                val streamVolumeLease = AlarmStreamVolumeController.acquire(appContext)
                try {
                    var played = false
                    for (candidateUri in candidateUris) {
                        Log.d(TAG, "play volume=$volumePercent% uri=$candidateUri")
                        played = CueSoundPlayer.play(
                            context = appContext,
                            uriString = candidateUri.toString(),
                            volumePercent = volumePercent,
                            maxPlayMs = safeMaxPlayMs
                        )
                        if (played) break
                    }
                    if (!played && allowSystemFallback) {
                        Log.w(TAG, "Ringtone unavailable, using alarm-stream fallback tone")
                        played = playFallbackTone(volumePercent, safeMaxPlayMs)
                    }
                    Log.d(TAG, "complete played=$played")
                    played
                } finally {
                    streamVolumeLease.close()
                }
            }
        } catch (throwable: Throwable) {
            Log.e(TAG, "Unable to play app notification sound", throwable)
            false
        }
    }

    internal fun normalizeVolumePercent(value: Int): Int = value.coerceIn(0, 100)

    internal fun volumeFraction(value: Int): Float =
        AppVolumeScale.gainForPercent(normalizeVolumePercent(value))

    private fun defaultNotificationUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private fun playFallbackTone(volumePercent: Int, maxPlayMs: Long): Boolean {
        var toneGenerator: ToneGenerator? = null
        return try {
            val durationMs = maxPlayMs.coerceIn(MIN_FALLBACK_TONE_MS, MAX_FALLBACK_TONE_MS).toInt()
            val toneVolume = (volumeFraction(volumePercent) * 100f)
                .roundToInt()
                .coerceIn(1, 100)
            toneGenerator = ToneGenerator(AppAudioAttributes.LEGACY_STREAM, toneVolume)
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
    private const val DEDUPE_WINDOW_MS = 15_000L
    private const val DEDUPE_RETENTION_MS = 60_000L
    private const val TAG = "AppNotificationSound"
}
