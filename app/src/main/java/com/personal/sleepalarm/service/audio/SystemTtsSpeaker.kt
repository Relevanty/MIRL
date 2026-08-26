// SystemTtsSpeaker.kt (обновлённый)
package com.personal.sleepalarm.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.personal.sleepalarm.data.preferences.BriefingVoiceSettings
import java.util.Locale

class SystemTtsSpeaker(
    private val context: Context
) : BriefingSpeaker {

    companion object {
        private const val TAG = "SystemTtsSpeaker"
    }

    private var tts: TextToSpeech? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(speechAttributes)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                stop()
            }
        }
        .build()

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
                var offlineReady = false
                tts?.apply {
                    setAudioAttributes(speechAttributes)
                    val offlineVoices = voices.orEmpty().filterNot { it.isNetworkConnectionRequired }
                    val offlineVoice = offlineVoices.firstOrNull { it.locale.language == "ru" }
                        ?: offlineVoices.firstOrNull { it.locale.language == Locale.getDefault().language }
                        ?: offlineVoices.firstOrNull()
                    if (offlineVoice == null) {
                        ready = false
                        Log.e(TAG, "Установленные офлайн-голоса TTS не найдены")
                    } else {
                        voice = offlineVoice
                        setOnUtteranceProgressListener(utteranceListener)
                        offlineReady = true
                    }
                }
                if (offlineReady) {
                    ready = true
                    Log.d(TAG, "системный офлайн TTS готов")
                } else {
                    ready = false
                }
            } else {
                ready = false
                Log.e(TAG, "системный TTS недоступен, status=$status")
            }
        }
    }

    override fun isAvailable(): Boolean = ready

    override fun speak(text: String, settings: BriefingVoiceSettings, onFinished: () -> Unit) {
        val engine = tts
        if (!ready || engine == null || text.isBlank()) {
            onFinished()
            return
        }

        val focusResult = audioManager.requestAudioFocus(audioFocusRequest)
        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            onFinished()
            return
        }

        // QUEUE_FLUSH останавливает прошлую фразу — завершаем её callback сами,
        // потому что некоторые TTS-движки не вызывают onStop().
        completeAllUtterances()

        val offlineVoices = engine.voices.orEmpty().filterNot { it.isNetworkConnectionRequired }
        val requestedLocale = Locale.forLanguageTag(settings.languageTag)
        val requestedVoice = offlineVoices.firstOrNull { it.name == settings.voiceName }
            ?: offlineVoices.firstOrNull { it.locale.toLanguageTag() == requestedLocale.toLanguageTag() }
            ?: offlineVoices.firstOrNull { it.locale.language == requestedLocale.language }
        if (requestedVoice == null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
            onFinished()
            return
        }
        engine.voice = requestedVoice

        engine.setSpeechRate(settings.ratePercent / 100f)
        engine.setPitch(settings.pitchPercent / 100f)

        val utteranceId = "briefing_${System.currentTimeMillis()}"
        synchronized(callbackLock) {
            callbacks[utteranceId] = onFinished
        }

        val result = engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.volumePercent / 100f)
            },
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
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        return result == TextToSpeech.SUCCESS
    }

    override fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        completeAllUtterances()
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
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
