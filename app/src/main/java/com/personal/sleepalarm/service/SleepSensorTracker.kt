package com.personal.sleepalarm.service

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Детектор засыпания по акселерометру (F9).
 *
 * Алгоритм:
 * 1. Читаем TYPE_ACCELEROMETER с SENSOR_DELAY_NORMAL (низкое энергопотребление).
 * 2. Оцениваем гравитацию экспоненциальным скользящим средним (low-pass).
 * 3. Линейное ускорение = |magnitude - gravity| (движение без гравитации).
 * 4. В скользящем окне WINDOW_MS считаем средний уровень движения.
 * 5. Если экран выключен, а уровень движения ниже STILLNESS_THRESHOLD НЕПРЕРЫВНО в течение
 *    STILLNESS_WINDOW_MS после bedTime — фиксируем засыпание.
 *
 * Уточнение модели относительно спецификации:
 * detectedSleepOnset берётся как МОМЕНТ НАЧАЛА непрерывной неподвижности
 * (stillnessStart), а не как момент срабатывания детекта. Обоснование:
 * начало устойчивой неподвижности лучше аппроксимирует момент, когда
 * человек лёг и затих, а окно STILLNESS_WINDOW служит лишь фильтром,
 * отсекающим случайные паузы в движении. Это уменьшает систематическое
 * завышение sleepOnsetLatency на величину окна.
 *
 * Компромиссы (честно):
 * - в doze доставка событий сенсора может batch-иться → точность ±1–2 мин;
 * - акселерометр НЕ требует разрешения BODY_SENSORS
 *   (оно нужно только для пульса/температуры тела) → разрешение НЕ добавляем;
 * - это эвристика, а не измерение фаз сна.
 */
class SleepSensorTracker(
    context: Context
) : SensorEventListener {

    private val appContext: Context = context.applicationContext

    private val sensorManager: SensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val powerManager: PowerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val audioManager: AudioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val buffer: ArrayDeque<Sample> = ArrayDeque()

    private var gravityEma: Float = 0f
    private var gravityInitialized = false

    private var lastEvalTs: Long = 0L
    private var stillnessStartMs: Long? = null
    private var screenOffSinceMs: Long? = null
    private var isCharging: Boolean = false
    private var orientationEma = FloatArray(3)
    private var orientationInitialized = false
    private var orientationDeltaEma = 1f
    private var receiverRegistered = false

    private val contextReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> screenOffSinceMs = System.currentTimeMillis()
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    screenOffSinceMs = null
                    stillnessStartMs = null
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                }
            }
        }
    }

    @Volatile
    private var detected = false

    @Volatile
    private var registered = false

    private var bedTimeMs: Long = 0L
    private var expectedLatencyMinutes: Int? = null
    private var onDetected: ((onsetEpochMs: Long, latencyMinutes: Int, confidencePercent: Int) -> Unit)? = null

    /**
     * Запускает отслеживание.
     *
     * @param bedTimeEpochMs запланированное время отхода ко сну.
     * @param onDetected вызывается ОДИН раз при детекции засыпания.
     */
    fun start(
        bedTimeEpochMs: Long,
        expectedLatencyMinutes: Int? = null,
        onDetected: (onsetEpochMs: Long, latencyMinutes: Int, confidencePercent: Int) -> Unit
    ) {
        val sensor = accelerometer
        if (sensor == null) {
            Log.w(TAG, "No accelerometer available, detection disabled")
            return
        }

        if (registered) {
            return
        }

        this.bedTimeMs = bedTimeEpochMs
        this.expectedLatencyMinutes = expectedLatencyMinutes
        this.onDetected = onDetected

        resetState()
        registerContextSignals()

        runCatching {
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            registered = true
            Log.i(TAG, "SleepSensorTracker started")
        }.onFailure {
            Log.e(TAG, "Failed to register accelerometer listener", it)
        }
    }

    /**
     * Останавливает отслеживание и освобождает слушатель.
     */
    fun stop() {
        if (registered) runCatching { sensorManager.unregisterListener(this) }
        unregisterContextSignals()

        registered = false
        onDetected = null
        resetState()

        Log.i(TAG, "SleepSensorTracker stopped")
    }

    // =================================================================
    // SensorEventListener
    // =================================================================

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (detected) return
        if (event.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)

        // Low-pass оценка гравитации.
        if (!gravityInitialized) {
            gravityEma = magnitude
            gravityInitialized = true
        } else {
            gravityEma = GRAVITY_EMA_ALPHA * magnitude +
                    (1f - GRAVITY_EMA_ALPHA) * gravityEma
        }

        val linearAccel = abs(magnitude - gravityEma)
        if (magnitude > 0.01f) updateOrientation(x / magnitude, y / magnitude, z / magnitude)
        val now = System.currentTimeMillis()

        // Пока экран включён, неподвижность не является достаточным признаком сна.
        if (powerManager.isInteractive) {
            buffer.clear()
            stillnessStartMs = null
            return
        }

        buffer.addLast(Sample(timestampMs = now, absLinearAccel = linearAccel))

        // Выбрасываем выборки старше окна.
        val cutoff = now - WINDOW_MS
        while (buffer.isNotEmpty() && buffer.first().timestampMs < cutoff) {
            buffer.removeFirst()
        }

        // Оцениваем уровень движения не чаще EVAL_INTERVAL_MS.
        if (now - lastEvalTs >= EVAL_INTERVAL_MS) {
            lastEvalTs = now
            evaluate(now)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Не используется.
    }

    // =================================================================
    // Оценка неподвижности
    // =================================================================

    private fun evaluate(now: Long) {
        // Нужен минимум выборок, чтобы движение было репрезентативным.
        if (buffer.size < MIN_SAMPLES) {
            return
        }

        var sum = 0f
        for (sample in buffer) {
            sum += sample.absLinearAccel
        }
        val movement = sum / buffer.size

        if (movement < STILLNESS_THRESHOLD) {
            // Неподвижность: начинаем или продолжаем отсчёт непрерывного окна.
            // Игнорируем неподвижность ДО bedTime.
            if (now < bedTimeMs + START_GRACE_MS) {
                stillnessStartMs = null
                return
            }

            if (stillnessStartMs == null) {
                stillnessStartMs = now
            }

            val start = stillnessStartMs ?: return

            if (now - start >= STILLNESS_WINDOW_MS) {
                // Засыпание зафиксировано.
                // onset = момент НАЧАЛА непрерывной неподвижности (см. комментарий к классу).
                val onset = start
                val latency = ((onset - bedTimeMs) / MINUTE_MS)
                    .toInt()
                    .coerceAtLeast(0)

                detected = true

                // Снимаем слушатель сразу, чтобы onSensorChanged больше не приходил.
                runCatching { sensorManager.unregisterListener(this) }
                registered = false

                Log.i(
                    TAG,
                    "Sleep onset detected: onset=$onset, latency=$latency min"
                )

                onDetected?.invoke(onset, latency, contextConfidence(now))
                onDetected = null
            }
        } else {
            // Движение — сбрасываем непрерывность.
            stillnessStartMs = null
        }
    }

    private fun resetState() {
        buffer.clear()
        gravityEma = 0f
        gravityInitialized = false
        lastEvalTs = 0L
        stillnessStartMs = null
        detected = false
        orientationInitialized = false
        orientationDeltaEma = 1f
        orientationEma.fill(0f)
    }

    private fun updateOrientation(x: Float, y: Float, z: Float) {
        if (!orientationInitialized) {
            orientationEma[0] = x; orientationEma[1] = y; orientationEma[2] = z
            orientationInitialized = true
            return
        }
        val delta = abs(x - orientationEma[0]) + abs(y - orientationEma[1]) + abs(z - orientationEma[2])
        orientationDeltaEma = 0.08f * delta + 0.92f * orientationDeltaEma
        orientationEma[0] = 0.08f * x + 0.92f * orientationEma[0]
        orientationEma[1] = 0.08f * y + 0.92f * orientationEma[1]
        orientationEma[2] = 0.08f * z + 0.92f * orientationEma[2]
    }

    private fun contextConfidence(now: Long): Int {
        var score = 45 // sustained accelerometer stillness
        val screenOffFor = now - (screenOffSinceMs ?: now)
        if (!powerManager.isInteractive && screenOffFor >= STILLNESS_WINDOW_MS) score += 20
        if (isCharging) score += 10
        if (!audioManager.isMusicActive) score += 10
        if (orientationInitialized && orientationDeltaEma < ORIENTATION_STABLE_THRESHOLD) score += 10
        if (now - bedTimeMs in 0..NEAR_BEDTIME_WINDOW_MS) score += 5
        expectedLatencyMinutes?.let { expected ->
            val actual = ((now - bedTimeMs) / MINUTE_MS).toInt()
            if (kotlin.math.abs(actual - expected) <= 30) score += 5
        }
        return score.coerceIn(55, 92)
    }

    private fun registerContextSignals() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        runCatching {
            appContext.registerReceiver(contextReceiver, filter)
            receiverRegistered = true
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.let { contextReceiver.onReceive(appContext, it) }
            screenOffSinceMs = if (powerManager.isInteractive) null else System.currentTimeMillis()
        }.onFailure { Log.w(TAG, "Context signals unavailable", it) }
    }

    private fun unregisterContextSignals() {
        if (!receiverRegistered) return
        runCatching { appContext.unregisterReceiver(contextReceiver) }
        receiverRegistered = false
    }

    private data class Sample(
        val timestampMs: Long,
        val absLinearAccel: Float
    )

    companion object {
        private const val TAG = "SleepSensorTracker"

        /** Скользящее окно выборок. */
        private const val WINDOW_MS = 30_000L

        /** Частота оценки уровня движения. */
        private const val EVAL_INTERVAL_MS = 2_000L

        /** Порог «неподвижности» (среднее |линейного ускорения|, м/с²). */
        private const val STILLNESS_THRESHOLD = 0.15f

        /** Устойчивое окно покоя; короткая пауза не считается сном. */
        private const val STILLNESS_WINDOW_MS = 20L * 60L * 1000L

        /** Даём пользователю время закончить подготовку после планового отбоя. */
        private const val START_GRACE_MS = 10L * 60L * 1000L

        /** Эвристическая оценка телефона, не медицинское измерение. */
        private const val ORIENTATION_STABLE_THRESHOLD = 0.035f
        private const val NEAR_BEDTIME_WINDOW_MS = 4L * 60L * 60L * 1000L

        /** Коэффициент low-pass для оценки гравитации. */
        private const val GRAVITY_EMA_ALPHA = 0.1f

        /** Минимум выборок в окне для репрезентативной оценки. */
        private const val MIN_SAMPLES = 5

        private const val MINUTE_MS = 60L * 1000L
    }
}
