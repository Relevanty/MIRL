package com.personal.sleepalarm.service.audio

import android.content.Context
import android.util.Log
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.personal.sleepalarm.data.preferences.BriefingPreference
import java.util.concurrent.atomic.AtomicLong

enum class VoiceScenario(val priority: Int) {
    ASSISTANT(10), BRIEFING(20), REMINDER(30), FOCUS(40), NIGHT_CUE(50), ALARM(60), MORNING(20)
}

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
    private var currentPriority = 0
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    suspend fun speak(
        text: String,
        scenario: VoiceScenario = VoiceScenario.BRIEFING,
        onFinished: () -> Unit
    ) {
        if (!preference.isEnabled()) {
            Log.d(TAG, "брифинг выключен в настройках")
            onFinished()
            return
        }
        val settings = preference.getVoiceSettings()
        if (!scenarioEnabled(settings, scenario) || (settings.headphonesOnly && !hasPrivateAudioOutput())) {
            onFinished()
            return
        }
        val preparedText = prepareText(text, settings)
        if (preparedText.isBlank()) {
            Log.d(TAG, "пустой текст брифинга")
            onFinished()
            return
        }
        val requestGeneration = synchronized(this) {
            if (currentPriority > scenario.priority) return@synchronized -1L
            currentPriority = scenario.priority
            generation.incrementAndGet()
        }
        if (requestGeneration < 0L) {
            onFinished()
            return
        }
        speaker?.stop()

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

        engine.speak(preparedText, settings) {
            finishIfCurrent(requestGeneration, onFinished)
        }
    }

    fun stop() {
        generation.incrementAndGet()
        synchronized(this) { currentPriority = 0 }
        speaker?.stop()
    }

    fun shutdown() {
        speaker?.shutdown()
        speaker = null
    }

    private fun finishIfCurrent(requestGeneration: Long, onFinished: () -> Unit) {
        if (generation.compareAndSet(requestGeneration, requestGeneration + 1)) {
            synchronized(this) { currentPriority = 0 }
            onFinished()
        }
    }

    private fun scenarioEnabled(
        settings: com.personal.sleepalarm.data.preferences.BriefingVoiceSettings,
        scenario: VoiceScenario
    ): Boolean = when (scenario) {
        VoiceScenario.ALARM, VoiceScenario.NIGHT_CUE, VoiceScenario.MORNING -> settings.morningEnabled
        VoiceScenario.FOCUS -> settings.focusEnabled
        VoiceScenario.REMINDER -> settings.reminderEnabled
        VoiceScenario.ASSISTANT -> settings.assistantEnabled
        VoiceScenario.BRIEFING -> true
    }

    private fun prepareText(
        source: String,
        settings: com.personal.sleepalarm.data.preferences.BriefingVoiceSettings
    ): String {
        var value = source.trim()
        if (!settings.personalDataEnabled) {
            value = value.replace(Regex("[«\"].+?[»\"]"), "ваша задача")
        }
        val maxSentences = when {
            settings.brevityPercent >= 75 -> 2
            settings.brevityPercent >= 40 -> 4
            else -> Int.MAX_VALUE
        }
        if (maxSentences != Int.MAX_VALUE) {
            value = value.split(Regex("(?<=[.!?])\\s+"))
                .take(maxSentences).joinToString(" ")
        }
        return value
    }

    private fun hasPrivateAudioOutput(): Boolean = audioManager
        .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .any { device ->
            device.type in setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_USB_HEADSET
            )
        }
}
