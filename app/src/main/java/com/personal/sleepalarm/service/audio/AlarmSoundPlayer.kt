package com.personal.sleepalarm.service.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Плеер основного будильника.
 *
 * ДОБАВЛЕНО (F2): customRingtoneUri — пользовательская мелодия ставится
 * ПЕРВОЙ в список кандидатов перед системным TYPE_ALARM.
 *
 * ДОБАВЛЕНО (F10): setVolumeFraction — внешнее управление громкостью
 * для импульсов smart-repeat (отменяет текущий ramp и фиксирует уровень);
 * isPlaying — диагностика.
 *
 * USAGE_ALARM, нарастание 10%→100% за 60 с, looping и fallback URI
 * сохраняются. На время звонка системный alarm stream открывается до максимума,
 * чтобы ramp покрывал полный диапазон, а после остановки возвращается обратно.
 */
class AlarmSoundPlayer(
    private val context: Context
) : AutoCloseable {

    private var mediaPlayer: MediaPlayer? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var rampJob: Job? = null
    private var fallbackToneJob: Job? = null
    private var toneGenerator: ToneGenerator? = null
    private var streamVolumeLease: AutoCloseable? = null

    @Volatile
    private var fallbackToneActive = false

    @Volatile
    private var released = false

    /**
     * Синхронный запуск.
     *
     * @param quietMode тихий режим (ниже стартовая громкость).
     * @param customRingtoneUri пользовательская мелодия (null = системная).
     */
    fun start(
        quietMode: Boolean = false,
        customRingtoneUri: Uri? = null
    ) {
        if (released) {
            return
        }

        stopInternal()
        streamVolumeLease = AlarmStreamVolumeController.acquire(context)

        // ДОБАВЛЕНО: пользовательский URI — первый кандидат (F2).
        val uris = resolveAlarmUris(customRingtoneUri)

        for (uri in uris) {
            if (tryStartWithUri(uri, quietMode)) {
                return
            }
        }

        Log.e(TAG, "Failed to start alarm sound with all candidate URIs; using tone fallback")
        val fallbackStarted = startFallbackTone(
            if (quietMode) QUIET_START_VOLUME else NORMAL_START_VOLUME
        )
        if (!fallbackStarted) releaseStreamVolumeLease()
    }

    /**
     * Suspending-версия запуска.
     */
    suspend fun startSuspend(
        quietMode: Boolean = false,
        customRingtoneUri: Uri? = null
    ) {
        withContext(Dispatchers.IO) {
            start(quietMode = quietMode, customRingtoneUri = customRingtoneUri)
        }
    }

    /**
     * ДОБАВЛЕНО (F10): внешняя установка громкости.
     *
     * Отменяет текущий ramp, чтобы он не перебил заданный уровень,
     * и фиксирует громкость на mediaPlayer.
     *
     * Используется smart-repeat: с каждым импульсом громкость
     * поднимается ближе к 100%.
     */
    fun setVolumeFraction(fraction: Float) {
        if (released) return

        // Останавливаем ramp — иначе он перезапишет громкость.
        rampJob?.cancel()
        rampJob = null

        val safe = fraction.coerceIn(0f, 1f)

        if (fallbackToneActive) {
            if (!startFallbackTone(safe)) releaseStreamVolumeLease()
            return
        }

        runCatching {
            mediaPlayer?.setVolume(safe, safe)
        }.onFailure {
            Log.e(TAG, "Failed to set volume fraction", it)
        }
    }

    /**
     * ДОБАВЛЕНО (F10): играет ли звук сейчас.
     */
    fun isPlaying(): Boolean {
        return fallbackToneActive ||
                runCatching { mediaPlayer?.isPlaying ?: false }.getOrDefault(false)
    }

    private fun tryStartWithUri(
        uri: Uri,
        quietMode: Boolean
    ): Boolean {
        var player: MediaPlayer? = null

        return try {
            player = MediaPlayer()

            player.setAudioAttributes(AppAudioAttributes.sonification)

            player.setDataSource(context, uri)
            player.isLooping = true

            runCatching {
                player.setWakeMode(
                    context.applicationContext,
                    PowerManager.PARTIAL_WAKE_LOCK
                )
            }

            player.setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                if (mediaPlayer === mp) mediaPlayer = null
                runCatching { mp.reset() }
                runCatching { mp.release() }
                if (!startFallbackTone(NORMAL_START_VOLUME)) releaseStreamVolumeLease()
                true
            }

            player.prepare()

            val startVolume = if (quietMode) {
                QUIET_START_VOLUME
            } else {
                NORMAL_START_VOLUME
            }

            player.setVolume(startVolume, startVolume)
            player.start()

            mediaPlayer = player

            startVolumeRamp(
                startVolume = startVolume,
                endVolume = END_VOLUME,
                rampSeconds = RAMP_SECONDS
            )

            Log.i(TAG, "Alarm sound started with uri=$uri")
            true
        } catch (throwable: Throwable) {
            Log.e(TAG, "Failed to start alarm sound with uri=$uri", throwable)

            runCatching {
                player?.release()
            }

            if (mediaPlayer === player) {
                mediaPlayer = null
            }

            false
        }
    }

    private fun startVolumeRamp(
        startVolume: Float,
        endVolume: Float,
        rampSeconds: Int
    ) {
        rampJob?.cancel()

        rampJob = scope.launch {
            val totalSteps = rampSeconds * STEPS_PER_SECOND
            val stepDelayMs = 1000L / STEPS_PER_SECOND

            for (step in 0..totalSteps) {
                if (!isActive) {
                    break
                }

                val player = mediaPlayer ?: break

                val progress = step.toFloat() / totalSteps.toFloat()
                val volume = startVolume + (endVolume - startVolume) * progress

                val safeVolume = volume.coerceIn(0f, 1f)

                runCatching {
                    player.setVolume(safeVolume, safeVolume)
                }

                delay(stepDelayMs)
            }
        }
    }

    /**
     * Список URI-кандидатов.
     *
     * ДОБАВЛЕНО (F2): customRingtoneUri ставится ПЕРВЫМ.
     * Порядок fallback остаётся прежним.
     */
    private fun resolveAlarmUris(customRingtoneUri: Uri?): List<Uri> {
        val candidates = mutableListOf<Uri>()

        // ДОБАВЛЕНО: пользовательская мелодия — высший приоритет.
        if (customRingtoneUri != null) {
            candidates.add(customRingtoneUri)
        }

        runCatching {
            RingtoneManager.getActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_ALARM
            )?.let { candidates.add(it) }
        }

        runCatching {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let {
                candidates.add(it)
            }
        }

        runCatching {
            Settings.System.DEFAULT_ALARM_ALERT_URI?.let {
                candidates.add(it)
            }
        }

        // Резервные варианты, если системный alarm-звук недоступен.
        runCatching {
            RingtoneManager.getActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_NOTIFICATION
            )?.let { candidates.add(it) }
        }

        runCatching {
            Settings.System.DEFAULT_NOTIFICATION_URI?.let {
                candidates.add(it)
            }
        }

        return candidates.distinct()
    }

    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        rampJob?.cancel()
        rampJob = null
        fallbackToneJob?.cancel()
        fallbackToneJob = null
        fallbackToneActive = false
        runCatching { toneGenerator?.stopTone() }
        runCatching { toneGenerator?.release() }
        toneGenerator = null

        val player = mediaPlayer
        mediaPlayer = null

        if (player != null) {
            runCatching {
                if (player.isPlaying) {
                    player.stop()
                }
            }

            runCatching {
                player.reset()
            }

            runCatching {
                player.release()
            }
        }
        releaseStreamVolumeLease()
    }

    /** Последний аварийный вариант, не зависящий от URI мелодий. */
    private fun startFallbackTone(volumeFraction: Float): Boolean {
        fallbackToneJob?.cancel()
        runCatching { toneGenerator?.stopTone() }
        runCatching { toneGenerator?.release() }

        val generator = runCatching {
            ToneGenerator(
                AppAudioAttributes.LEGACY_STREAM,
                (volumeFraction.coerceIn(0f, 1f) * 100).toInt().coerceAtLeast(1)
            )
        }.onFailure {
            Log.e(TAG, "Failed to create alarm tone fallback", it)
        }.getOrNull()

        toneGenerator = generator
        fallbackToneActive = generator != null
        if (generator == null) return false

        fallbackToneJob = scope.launch {
            while (isActive && toneGenerator === generator) {
                runCatching {
                    generator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, FALLBACK_TONE_MS)
                }
                delay(FALLBACK_REPEAT_MS)
            }
        }
        return true
    }

    private fun releaseStreamVolumeLease() {
        val lease = streamVolumeLease
        streamVolumeLease = null
        runCatching { lease?.close() }
    }

    override fun close() {
        released = true
        stopInternal()
        scope.cancel()
    }

    companion object {
        private const val TAG = "AlarmSoundPlayer"

        private const val NORMAL_START_VOLUME = 0.10f
        private const val QUIET_START_VOLUME = 0.05f
        private const val END_VOLUME = 1.0f

        private const val RAMP_SECONDS = 60
        private const val STEPS_PER_SECOND = 2
        private const val FALLBACK_TONE_MS = 900
        private const val FALLBACK_REPEAT_MS = 1_200L
    }
}
