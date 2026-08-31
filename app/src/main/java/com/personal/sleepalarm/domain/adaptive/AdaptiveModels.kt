package com.personal.sleepalarm.domain.adaptive

/** Inputs that may contribute to the estimated state for the current moment. */
enum class StateSignal {
    TIME_SINCE_WAKE,
    SLEEP_DURATION,
    CURRENT_ENERGY,
    RECENT_LOAD,
    SEASONAL_CONTEXT
}

enum class ConfidenceLevel {
    VERY_LOW,
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Confidence is intentionally separate from the capacity estimate. A plausible
 * number based on one weak signal must not silently drive the day plan.
 */
data class EstimateConfidence(
    val value: Double,
    val level: ConfidenceLevel,
    val observedSignals: Set<StateSignal>,
    val missingSignals: Set<StateSignal>
) {
    init {
        require(value.isFinite() && value in 0.0..1.0)
        require(observedSignals.intersect(missingSignals).isEmpty())
    }

    companion object {
        fun of(
            value: Double,
            observedSignals: Set<StateSignal>,
            consideredSignals: Set<StateSignal> = StateSignal.entries.toSet()
        ): EstimateConfidence {
            val safeValue = value.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 0.0
            val level = when {
                safeValue < 0.25 -> ConfidenceLevel.VERY_LOW
                safeValue < 0.45 -> ConfidenceLevel.LOW
                safeValue < 0.70 -> ConfidenceLevel.MEDIUM
                else -> ConfidenceLevel.HIGH
            }
            return EstimateConfidence(
                value = safeValue,
                level = level,
                observedSignals = observedSignals.intersect(consideredSignals),
                missingSignals = consideredSignals - observedSignals
            )
        }
    }
}

enum class ScoreFactor {
    BASE_CAPACITY,
    CURRENT_ENERGY,
    PROVISIONAL_WAKE_RATING,
    SLEEP_DURATION,
    SLEEP_INERTIA,
    TIME_AWAKE,
    RECENT_LOAD,
    SEASONAL_PRIOR,
    BOUNDS_CLAMP,
    FALLBACK_PRIORITY,
    ENERGY_FIT,
    COGNITIVE_FIT,
    FATIGUE_COST,
    DEADLINE_URGENCY,
    MANDATORY_TASK,
    DURATION_FIT,
    START_DELAY,
    HARD_CONSTRAINT
}

/** A machine-readable reason that can later be localized by a UI consumer. */
data class ScoreExplanation(
    val factor: ScoreFactor,
    val contribution: Double,
    val evidence: String
) {
    init {
        require(contribution.isFinite())
        require(evidence.isNotBlank())
    }
}

/** Estimated ability to take on work now. All normalized values are in 0..1. */
data class PersonalState(
    val capacity: Double,
    val estimatedEnergy: Double,
    val fatigue: Double,
    val minutesSinceWake: Int?,
    val sleepMinutes: Int?,
    val currentEnergy: Int?,
    val confidence: EstimateConfidence,
    val explanations: List<ScoreExplanation>
) {
    init {
        require(capacity.isFinite() && capacity in 0.0..1.0)
        require(estimatedEnergy.isFinite() && estimatedEnergy in 1.0..10.0)
        require(fatigue.isFinite() && fatigue in 0.0..1.0)
        require(minutesSinceWake == null || minutesSinceWake >= 0)
        require(sleepMinutes == null || sleepMinutes >= 0)
        require(currentEnergy == null || currentEnergy in 1..10)
    }
}

/** Raw observations accepted by [StateEstimator]. Invalid optional values are ignored. */
data class PersonalStateObservation(
    val nowMillis: Long,
    val wakeTimeMillis: Long? = null,
    val sleepMinutes: Int? = null,
    val targetSleepMinutes: Int = 8 * 60,
    val currentEnergy: Int? = null,
    val currentEnergyMeasuredAtMillis: Long? = null,
    /** Recent demand from 0 (rested) to 1 (sustained heavy load). */
    val recentLoad: Double? = null,
    /** Local day length; useful only as a very weak, opt-in prior. */
    val photoperiodMinutes: Int? = null,
    /** Personal learned sensitivity in -1..1. Zero disables the seasonal prior. */
    val personalSeasonSensitivity: Double = 0.0
)

/** A half-open interval [startMillis, endMillis). */
data class TimeWindow(
    val startMillis: Long,
    val endMillis: Long
) {
    init {
        require(endMillis > startMillis) { "A time window must have positive duration" }
    }

    val durationMillis: Long
        get() = endMillis - startMillis

    fun overlaps(other: TimeWindow): Boolean =
        startMillis < other.endMillis && other.startMillis < endMillis
}

/**
 * Persistence-independent work demand. [dueAtMillis] is an urgency boundary;
 * it is enforced as a finish constraint only when [deadlineIsHard] is true.
 */
data class TaskDemand<ID : Any>(
    val id: ID,
    val durationMinutes: Int,
    val energyDemand: Double = 5.5,
    val cognitiveDemand: Double = 0.5,
    val earliestStartMillis: Long? = null,
    val dueAtMillis: Long? = null,
    val deadlineIsHard: Boolean = false,
    val fixedStartMillis: Long? = null,
    val mandatory: Boolean = false,
    val completed: Boolean = false,
    val blocked: Boolean = false
) {
    internal fun isNumericallyValid(): Boolean =
        durationMinutes > 0 &&
            energyDemand.isFinite() && energyDemand in 1.0..10.0 &&
            cognitiveDemand.isFinite() && cognitiveDemand in 0.0..1.0
}

/** Runtime context shared by ranking and short-horizon planning. */
data class PlanningContext(
    val nowMillis: Long,
    val horizonEndMillis: Long,
    val personalState: PersonalState? = null,
    /** Calendar/sleep/other immutable commitments to subtract from the horizon. */
    val fixedCalendarWindows: List<TimeWindow> = emptyList(),
    val minimumAdaptiveConfidence: Double = 0.45,
    val bufferMinutes: Int = 5,
    val maxSequenceTasks: Int = 4,
    val candidateLimit: Int = 8
) {
    internal fun isValid(): Boolean =
        horizonEndMillis > nowMillis &&
            minimumAdaptiveConfidence.isFinite() && minimumAdaptiveConfidence in 0.0..1.0 &&
            bufferMinutes in 0..MAX_BUFFER_MINUTES &&
            maxSequenceTasks in 1..MAX_SEQUENCE_TASKS &&
            candidateLimit in maxSequenceTasks..MAX_CANDIDATES

    companion object {
        const val MAX_BUFFER_MINUTES = 12 * 60
        const val MAX_SEQUENCE_TASKS = 8
        const val MAX_CANDIDATES = 12
    }
}

enum class RankingMode {
    ADAPTIVE,
    FALLBACK_NO_STATE,
    FALLBACK_LOW_CONFIDENCE,
    FALLBACK_INVALID_CONTEXT,
    FALLBACK_INVALID_INPUT
}

data class RankedTask<ID : Any>(
    val demand: TaskDemand<ID>,
    val score: Double,
    /** Lower tiers are lexicographically protected from adaptive scoring. */
    val hardConstraintTier: Int,
    val fallbackIndex: Int,
    val explanations: List<ScoreExplanation>
) {
    init {
        require(score.isFinite())
        require(hardConstraintTier >= 0)
        require(fallbackIndex >= 0)
    }
}

data class AdaptiveRanking<ID : Any>(
    val tasks: List<RankedTask<ID>>,
    val mode: RankingMode,
    val fallbackReason: String? = null
) {
    val isAdaptive: Boolean
        get() = mode == RankingMode.ADAPTIVE
}

data class AvailabilityResult(
    val freeWindows: List<TimeWindow>,
    val mergedFixedWindows: List<TimeWindow>
) {
    val totalAvailableMinutes: Long
        get() = freeWindows.sumOf { it.durationMillis } / MILLIS_PER_MINUTE

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

data class PlannedTask<ID : Any>(
    val rankedTask: RankedTask<ID>,
    val startMillis: Long,
    val endMillis: Long
) {
    init {
        require(endMillis > startMillis)
    }
}

data class SequencePlan<ID : Any>(
    val tasks: List<PlannedTask<ID>>,
    val rankingMode: RankingMode,
    val freeWindows: List<TimeWindow>,
    val totalAdaptiveScore: Double
) {
    init {
        require(totalAdaptiveScore.isFinite())
    }
}
