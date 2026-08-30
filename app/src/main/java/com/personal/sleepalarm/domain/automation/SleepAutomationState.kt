package com.personal.sleepalarm.domain.automation

import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val AUTOMATION_ARMED_SOURCE = "AUTOMATION_ARMED"
const val AUTOMATION_WINDOW_EXPIRED_SOURCE = "AUTOMATION_WINDOW_EXPIRED"
const val AUTOMATION_DETECTED_SOURCE = "AUTOMATION_PHONE_CONTEXT_HEURISTIC"
const val AUTOMATION_FOCUS_PAUSED_SOURCE = "AUTOMATION_FOCUS_PAUSED"

fun SleepSessionEntity.isAutomationArmed(): Boolean =
    isActive && detectedSleepOnsetTime == null && detectedOnsetSource == AUTOMATION_ARMED_SOURCE

fun SleepSessionEntity.isAutomaticSleepSession(): Boolean =
    detectedOnsetSource?.startsWith("AUTOMATION_") == true

fun SleepSessionEntity.isAutomationPausedForFocus(): Boolean =
    isActive && detectedOnsetSource == AUTOMATION_FOCUS_PAUSED_SOURCE

/**
 * Decides how an explicit awake action (starting focus, for example) should
 * coexist with the current sleep record. Automatic detection is only a
 * heuristic and yields to the user's action; manually started sleep is kept.
 */
enum class ExplicitAwakeSleepConflict {
    PROCEED,
    PAUSE_AUTOMATIC_SLEEP,
    BLOCKED_BY_MANUAL_SLEEP
}

fun SleepSessionEntity?.conflictForExplicitAwakeAction(): ExplicitAwakeSleepConflict = when {
    this == null || !isActive -> ExplicitAwakeSleepConflict.PROCEED
    isAutomaticSleepSession() -> ExplicitAwakeSleepConflict.PAUSE_AUTOMATIC_SLEEP
    else -> ExplicitAwakeSleepConflict.BLOCKED_BY_MANUAL_SLEEP
}

/**
 * Invalidates a possibly false onset and restores the immutable safety wake.
 * AlarmReceiver repairs an older early PendingIntent if the process dies
 * before AlarmManager receives the restored time.
 */
fun SleepSessionEntity.pauseAutomaticDetectionForFocus(): SleepSessionEntity {
    require(isAutomaticSleepSession())
    return copy(
        estimatedSleepStartTime = bedTimePlanned + sleepOnsetLatencyMinutes * 60_000L,
        estimatedWakeTime = automationSafetyWakeTime ?: estimatedWakeTime,
        cuesEnabled = false,
        cuesScheduledCount = 0,
        detectedSleepOnsetTime = null,
        detectedOnsetLatencyMinutes = null,
        detectedOnsetConfidencePercent = null,
        detectedOnsetSource = AUTOMATION_FOCUS_PAUSED_SOURCE,
        detectedOnsetUncertaintyMinutes = null,
        onsetReviewState = "PENDING"
    )
}

fun SleepSessionEntity.resumeAutomaticDetectionAfterFocus(): SleepSessionEntity {
    require(isAutomationPausedForFocus())
    return copy(
        estimatedSleepStartTime = bedTimePlanned + sleepOnsetLatencyMinutes * 60_000L,
        estimatedWakeTime = automationSafetyWakeTime ?: estimatedWakeTime,
        detectedOnsetSource = AUTOMATION_ARMED_SOURCE,
        detectedSleepOnsetTime = null,
        detectedOnsetLatencyMinutes = null,
        detectedOnsetConfidencePercent = null,
        detectedOnsetUncertaintyMinutes = null,
        onsetReviewState = "PENDING"
    )
}

/** Serializes focus/automatic-sleep ownership changes inside the app process. */
object FocusSleepTransitionGate {
    private val mutex = Mutex()

    suspend fun <T> serialized(block: suspend () -> T): T = mutex.withLock { block() }
}
