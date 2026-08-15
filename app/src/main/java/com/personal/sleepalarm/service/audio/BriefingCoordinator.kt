package com.personal.sleepalarm.service.audio

import android.content.Context
import android.util.Log
import com.personal.sleepalarm.data.preferences.BriefingPreference
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicLong

/**
 * Координатор голосового брифинга.
 *
 * Упрощён: использует только системный TTS.
 * SileroSpeaker удалён — модель ONNX больше не нужна.
 */
class BriefingCoordinator(
    private val context: Context,
    private val preference: BriefingPreference
) {
    companion object {
        private const val TAG = "BriefingCoordinator"
    }

    private var speaker: SystemTtsSpeaker? = null
    private val generation = AtomicLong(0)

    suspend fun speak(text: String, onFinished: () -> Unit) {
        val requestGeneration = generation.incrementAndGet()

        if (!preference.isEnabled()) {
            Log.d(TAG, "брифинг выключен в настройках")
            finishIfCurrent(requestGeneration, onFinished)
            return
        }
        if (text.isBlank()) {
            Log.d(TAG, "пустой текст брифинга")
            finishIfCurrent(requestGeneration, onFinished)
            return
        }

        val engine = speaker ?: SystemTtsSpeaker(context).also { speaker = it }

        // Даём движку время на инициализацию (до 2 секунд)
        var waited = 0
        while (
            !engine.isAvailable() &&
            waited < 4000 &&
            generation.get() == requestGeneration
        ) {
            kotlinx.coroutines.delay(100)
            waited += 100
        }

        // stop() мог быть вызван, пока TextToSpeech инициализировался.
        if (generation.get() != requestGeneration) {
            return
        }

        if (!engine.isAvailable()) {
            Log.w(TAG, "TTS не готов после 4 секунд ожидания")
            finishIfCurrent(requestGeneration, onFinished)
            return
        }

        engine.speak(text) {
            finishIfCurrent(requestGeneration, onFinished)
        }
    }

    fun stop() {
        generation.incrementAndGet()
        speaker?.stop()
    }

    fun shutdown() {
        speaker?.shutdown()
        speaker = null
    }

    private fun finishIfCurrent(requestGeneration: Long, onFinished: () -> Unit) {
        if (generation.compareAndSet(requestGeneration, requestGeneration + 1)) {
            onFinished()
        }
    }
}
