package com.personal.sleepalarm.service.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Тип вибрации.
 *
 * ALARM_RAMP — нарастающий паттерн для основного будильника:
 *              вибрация «разгоняется» вместе со звуком.
 * REPEAT_BURST — короткий двойной толчок для импульсов smart-repeat (F10).
 */
enum class VibrationPattern {
    ALARM_RAMP,
    REPEAT_BURST
}

/**
 * Плеер вибрации будильника (F1).
 *
 * Использует VibratorManager на API 31+ и Vibrator на API <31.
 * Нарастающая интенсивность — через VibrationEffect.createWaveform
 * со ступенчатыми амплитудами.
 *
 * Граничные случаи обработаны без падений:
 * - нет вибратора → все операции no-op;
 * - нет amplitude control → fallback на waveform без амплитуд / createOneShot;
 * - любая системная ошибка → логируется, не пробрасывается.
 *
 * Вибрация помечена USAGE_ALARM, чтобы по возможности обходить DND.
 */
class AlarmVibrator(
    private val context: Context
) : AutoCloseable {

    private val vibrator: Vibrator? = resolveVibrator()

    private val alarmAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .build()

    @Volatile
    private var closed = false

    /**
     * Запускает вибрацию по паттерну.
     *
     * Для ALARM_RAMP — зацикленный нарастающий waveform (пока не stop()).
     * Для REPEAT_BURST — однократный короткий импульс.
     */
    fun start(pattern: VibrationPattern) {
        if (closed) return

        val v = vibrator ?: return

        if (!runCatching { v.hasVibrator() }.getOrDefault(false)) {
            return
        }

        val effect = buildEffect(v, pattern) ?: return

        runCatching {
            v.cancel()
            v.vibrate(effect, alarmAttributes)
        }.onFailure {
            Log.e(TAG, "Failed to start vibration", it)
        }
    }

    /**
     * Останавливает вибрацию. Синоним cancel() для читаемости.
     */
    fun stop() {
        cancel()
    }

    /**
     * Отменяет текущую вибрацию.
     */
    fun cancel() {
        val v = vibrator ?: return

        runCatching { v.cancel() }
    }

    /**
     * Есть ли физический вибратор.
     */
    fun hasVibrator(): Boolean {
        return runCatching { vibrator?.hasVibrator() ?: false }.getOrDefault(false)
    }

    override fun close() {
        closed = true
        cancel()
    }

    // =================================================================
    // Построение эффектов
    // =================================================================

    private fun buildEffect(
        vibrator: Vibrator,
        pattern: VibrationPattern
    ): VibrationEffect? {
        val hasAmplitude = runCatching { vibrator.hasAmplitudeControl() }
            .getOrDefault(false)

        return when (pattern) {
            VibrationPattern.ALARM_RAMP -> buildRampEffect(hasAmplitude)
            VibrationPattern.REPEAT_BURST -> buildBurstEffect(hasAmplitude)
        }
    }

    /**
     * Нарастающий зацикленный waveform.
     *
     * 6 сегментов по 800 мс, амплитуды [0, 60, 120, 180, 230, 255].
     * repeat = 0 → повтор с начала (ритмичная пульсация «разгон-пауза»),
     * пока будильник не выключат.
     *
     * Без amplitude control — двухаргументный waveform (амплитуды
     * игнорируются системой, вибрация равномерная).
     */
    private fun buildRampEffect(hasAmplitude: Boolean): VibrationEffect? {
        return runCatching {
            if (hasAmplitude) {
                VibrationEffect.createWaveform(
                    RAMP_TIMINGS,
                    RAMP_AMPLITUDES,
                    RAMP_REPEAT_INDEX
                )
            } else {
                @Suppress("DEPRECATION")
                VibrationEffect.createWaveform(RAMP_TIMINGS, RAMP_REPEAT_INDEX)
            }
        }.onFailure {
            Log.e(TAG, "Failed to build ramp effect", it)
        }.getOrNull()
    }

    /**
     * Короткий двойной толчок для smart-repeat.
     *
     * С amplitude control — waveform [пауза, толчок, пауза, толчок].
     * Без amplitude control — createOneShot (один короткий толчок).
     */
    private fun buildBurstEffect(hasAmplitude: Boolean): VibrationEffect? {
        return runCatching {
            if (hasAmplitude) {
                VibrationEffect.createWaveform(
                    BURST_TIMINGS,
                    BURST_AMPLITUDES,
                    -1 // -1 → без повтора (однократный импульс)
                )
            } else {
                VibrationEffect.createOneShot(
                    BURST_ONESHOT_MS,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            }
        }.onFailure {
            Log.e(TAG, "Failed to build burst effect", it)
        }.getOrNull()
    }

    // =================================================================
    // Резолв вибратора по версии API
    // =================================================================

    @SuppressLint("ServiceCast")
    private fun resolveVibrator(): Vibrator? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_SERVICE)
                        as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.onFailure {
            Log.e(TAG, "Failed to resolve vibrator", it)
        }.getOrNull()
    }

    companion object {
        private const val TAG = "AlarmVibrator"

        // Нарастающий паттерн: 6 сегментов по 800 мс.
        // Амплитуда 0 в первом сегменте = пауза перед разгоном.
        private val RAMP_TIMINGS: LongArray =
            longArrayOf(800, 800, 800, 800, 800, 800)
        private val RAMP_AMPLITUDES: IntArray =
            intArrayOf(0, 60, 120, 180, 230, 255)

        // Повтор с начала → ритмичная пульсация до stop().
        private const val RAMP_REPEAT_INDEX = 0

        // Короткий импульс: пауза-толчок-пауза-толчок.
        private val BURST_TIMINGS: LongArray =
            longArrayOf(0, 250, 150, 250)
        private val BURST_AMPLITUDES: IntArray =
            intArrayOf(0, 255, 0, 255)
        private const val BURST_ONESHOT_MS: Long = 300L
    }
}