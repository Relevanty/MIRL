package com.personal.sleepalarm.domain.model

/** Текущая фаза устойчивого протокола глубокой работы. */
enum class FocusProtocolPhase {
    RESET,
    ACTIVATE,
    FOCUS,
    FOCUS_PAUSED,
    RECOVERY,
    CYCLE_READY,
    REVIEW,
    COMPLETE,
    CANCELLED;

    val isTerminal: Boolean
        get() = this == COMPLETE || this == CANCELLED

    val hasCountdown: Boolean
        get() = this == RESET || this == FOCUS || this == RECOVERY
}
