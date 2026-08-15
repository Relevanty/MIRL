package com.personal.sleepalarm.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
 * 5. Если уровень движения ниже STILLNESS_THRESHOLD НЕПРЕРЫВНО в течение
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

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val buffer: ArrayDeque<Sample> = ArrayDeque()

    private var gravityEma: Float = 0f
    private var gravityInitialized = false

    private var lastEvalTs: Long = 0L
    private var stillnessStartMs: Long? = null

    @Volatile
    private var detected = false

    @Volatile
    private var registered = false

    private var bedTimeMs: Long = 0L
    private var onDetected: ((onsetEpochMs: Long, latencyMinutes: Int) -> Unit)? = null

    /**
     * Запускает отслеживание.
     *
     * @param bedTimeEpochMs запланированное время отхода ко сну.
     * @param onDetected вызывается ОДИН раз при детекции засыпания.
     */
    fun start(
        bedTimeEpochMs: Long,
        onDetected: (onsetEpochMs: Long, latencyMinutes: Int) -> Unit
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
        this.onDetected = onDetected

        resetState()

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
        if (!registered) {
            return
        }

        runCatching {
            sensorManager.unregisterListener(this)
        }

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
        val now = System.currentTimeMillis()

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
            if (now < bedTimeMs) {
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

                onDetected?.invoke(onset, latency)
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

        /** Непрерывная неподвижность, после которой фиксируем засыпание. */
        private const val STILLNESS_WINDOW_MS = 5L * 60L * 1000L

        /** Коэффициент low-pass для оценки гравитации. */
        private const val GRAVITY_EMA_ALPHA = 0.1f

        /** Минимум выборок в окне для репрезентативной оценки. */
        private const val MIN_SAMPLES = 5

        private const val MINUTE_MS = 60L * 1000L
    }
}