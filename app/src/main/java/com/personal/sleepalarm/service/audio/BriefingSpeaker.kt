package com.personal.sleepalarm.service.audio

import com.personal.sleepalarm.data.preferences.BriefingVoiceSettings

/**
 * Абстракция движка озвучки брифинга.
 * Реализация: SystemTtsSpeaker (системный TTS Android).
 */
interface BriefingSpeaker {

    /** Готов ли движок говорить прямо сейчас. */
    fun isAvailable(): Boolean

    /**
     * Озвучивает текст. onFinished вызывается ОДИН раз после завершения
     * (или немедленно при ошибке/остановке).
     */
    fun speak(text: String, settings: BriefingVoiceSettings, onFinished: () -> Unit)

    /** Останавливает воспроизведение. true — если реально остановили. */
    fun stop(): Boolean

    /** Освобождение ресурсов. */
    fun shutdown()
}
