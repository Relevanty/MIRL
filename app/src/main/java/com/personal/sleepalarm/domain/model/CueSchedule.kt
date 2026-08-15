package com.personal.sleepalarm.domain.model

import java.time.ZonedDateTime

/**
 * Тип звуковой подсказки для осознанных сновидений.
 */
enum class CueType {
    BEEP,
    BINAURAL,
    TTS
}

/**
 * Предупреждения расчёта cue-расписания.
 */
enum class CueWarning {
    INVALID_WINDOW,
    INVALID_SETTINGS,
    TOO_SHORT_FOR_CUES,
    CUES_TOO_FREQUENT
}

/**
 * Одна запланированная подсказка.
 *
 * index — порядковый номер подсказки внутри сессии.
 * Используется для unique request code и защиты от дублей.
 */
data class CueOccurrence(
    val index: Int,
    val time: ZonedDateTime
)

/**
 * Расписание lucid-подсказок.
 */
data class CueSchedule(
    val cues: List<CueOccurrence>,
    val warnings: Set<CueWarning>
) {
    val scheduledCount: Int
        get() = cues.size

    val lastCue: CueOccurrence?
        get() = cues.lastOrNull()

    val firstCue: CueOccurrence?
        get() = cues.firstOrNull()

    fun hasWarning(warning: CueWarning): Boolean {
        return warning in warnings
    }
}