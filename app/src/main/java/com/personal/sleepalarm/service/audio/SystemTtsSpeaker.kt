// SystemTtsSpeaker.kt (обновлённый)
package com.personal.sleepalarm.service.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class SystemTtsSpeaker(
    private val context: Context
) : BriefingSpeaker {

    companion object {
        private const val TAG = "SystemTtsSpeaker"
    }

    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false

    private val callbackLock = Any()
    private val callbacks = mutableMapOf<String, () -> Unit>()

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = completeUtterance(utteranceId)
        override fun onError(utteranceId: String?) = completeUtterance(utteranceId)
        override fun onStop(utteranceId: String?, interrupted: Boolean) =
            completeUtterance(utteranceId)
    }

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.apply {
                    language = Locale("ru")
                    setSpeechRate(1.0f)
                    setPitch(1.0f)
                    setOnUtteranceProgressListener(utteranceListener)
                }
                ready = true
                Log.d(TAG, "системный TTS готов")
            } else {
                ready = false
                Log.e(TAG, "системный TTS недоступен, status=$status")
            }
        }
    }

    override fun isAvailable(): Boolean = ready

    override fun speak(text: String, onFinished: () -> Unit) {
        val engine = tts
        if (!ready || engine == null || text.isBlank()) {
            onFinished()
            return
        }

        // QUEUE_FLUSH останавливает прошлую фразу — завершаем её callback сами,
        // потому что некоторые TTS-движки не вызывают onStop().
        completeAllUtterances()

        val utteranceId = "briefing_${System.currentTimeMillis()}"
        synchronized(callbackLock) {
            callbacks[utteranceId] = onFinished
        }

        val result = engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            utteranceId
        )
        if (result != TextToSpeech.SUCCESS) {
            Log.w(TAG, "speak() вернул ошибку: $result")
            completeUtterance(utteranceId)
        }
    }

    override fun stop(): Boolean {
        val result = runCatching { tts?.stop() ?: TextToSpeech.ERROR }
            .getOrDefault(TextToSpeech.ERROR)
        completeAllUtterances()
        return result == TextToSpeech.SUCCESS
    }

    override fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        completeAllUtterances()
        tts = null
        ready = false
    }

    private fun completeUtterance(utteranceId: String?) {
        if (utteranceId == null) return
        val callback = synchronized(callbackLock) {
            callbacks.remove(utteranceId)
        }
        callback?.let { runCatching(it) }
    }

    private fun completeAllUtterances() {
        val pending = synchronized(callbackLock) {
            callbacks.values.toList().also { callbacks.clear() }
        }
        pending.forEach { runCatching(it) }
    }
}
