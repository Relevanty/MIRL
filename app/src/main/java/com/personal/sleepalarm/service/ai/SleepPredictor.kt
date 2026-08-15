package com.personal.sleepalarm.service.ai

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Один обучающий пример: фичи дня + целевое настроение.
 */
data class TrainingSample(
    val features: DoubleArray,
    val target: Double
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrainingSample) return false
        return features.contentEquals(other.features) && target == other.target
    }
    override fun hashCode(): Int = features.contentHashCode()
}

/**
 * Локальная «нейронка»: однослойная линейная регрессия, обучаемая
 * градиентным спуском прямо на устройстве. Предсказывает настроение
 * (качество подъёма) по фичам ночи.
 *
 * Фичи (индексы):
 * 0 — sleepMinutes        (длительность сна)
 * 1 — onsetOffset         (минуты от 18:00 до засыпания)
 * 2 — cycles              (число циклов)
 * 3 — prevTasksDone       (задач выполнено накануне)
 * 4 — prevMood            (настроение накануне, 0 если нет)
 * 5 — dayOfWeek           (0..6)
 *
 * Цель: mood дня (1..5).
 *
 * Полностью оффлайн, без внешних ML-зависимостей.
 */
class SleepPredictor {

    companion object {
        const val NUM_FEATURES = 6
        private const val EPOCHS = 300
        private const val LEARNING_RATE = 0.05
        private const val MIN_SAMPLES = 3

        // Границы «тяжёлого утра».
        const val HEAVY_MORNING_THRESHOLD = 2.5
    }

    // Веса: NUM_FEATURES + 1 (bias).
    private var weights = DoubleArray(NUM_FEATURES + 1)

    // Параметры нормализации.
    private var means = DoubleArray(NUM_FEATURES)
    private var stds = DoubleArray(NUM_FEATURES) { 1.0 }

    var isTrained: Boolean = false
        private set

    var trainedOnSamples: Int = 0
        private set

    /** Обучает модель на истории. Вызывать из корутины (CPU). */
    fun train(samples: List<TrainingSample>) {
        trainedOnSamples = samples.size

        if (samples.size < MIN_SAMPLES) {
            isTrained = false
            return
        }

        computeNormalization(samples)

        // Инициализация весов нулями (bias = 3.0 — среднее настроение).
        weights = DoubleArray(NUM_FEATURES + 1)
        weights[NUM_FEATURES] = 3.0

        val n = samples.size
        val normX = Array(n) { i -> normalize(samples[i].features) }
        val y = DoubleArray(n) { i -> samples[i].target }

        repeat(EPOCHS) {
            for (i in 0 until n) {
                val pred = rawPredict(normX[i])
                val error = pred - y[i]
                // Градиентный шаг (MSE).
                for (j in 0 until NUM_FEATURES) {
                    weights[j] -= LEARNING_RATE * error * normX[i][j] / n
                }
                weights[NUM_FEATURES] -= LEARNING_RATE * error / n
            }
        }

        isTrained = true
    }

    /**
     * Предсказывает настроение (1..5) по фичам дня.
     * Если модель не обучена — возвращает null.
     */
    fun predict(features: DoubleArray): Double? {
        if (!isTrained) return null
        val norm = normalize(features)
        return rawPredict(norm).coerceIn(1.0, 5.0)
    }

    /** true если предсказанное утро «тяжёлое». */
    fun isHeavyMorning(predictedMood: Double): Boolean =
        predictedMood < HEAVY_MORNING_THRESHOLD

    /** Рекомендованный лимит snooze по предсказанному качеству. */
    fun recommendSnoozeLimit(predictedMood: Double): Int = when {
        predictedMood < 2.5 -> 0
        predictedMood < 3.5 -> 1
        else -> 2
    }

    // =====================================================================
    // Приват
    // =====================================================================

    private fun computeNormalization(samples: List<TrainingSample>) {
        for (j in 0 until NUM_FEATURES) {
            val values = samples.map { it.features[j] }
            val mean = values.average()
            val variance = values.map { (it - mean) * (it - mean) }.average()
            means[j] = mean
            stds[j] = if (variance > 1e-9) sqrt(variance) else 1.0
        }
    }

    private fun normalize(features: DoubleArray): DoubleArray =
        DoubleArray(NUM_FEATURES) { j ->
            (features.getOrElse(j) { 0.0 } - means[j]) / stds[j]
        }

    private fun rawPredict(norm: DoubleArray): Double {
        var sum = weights[NUM_FEATURES]
        for (j in 0 until NUM_FEATURES) {
            sum += weights[j] * norm[j]
        }
        return sum
    }

    /** Ограничивает значение (используется при предсказании). */
    private fun Double.coerce(lo: Double, hi: Double): Double = max(lo, min(hi, this))
}