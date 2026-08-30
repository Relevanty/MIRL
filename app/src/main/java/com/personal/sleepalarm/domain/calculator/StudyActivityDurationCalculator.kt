package com.personal.sleepalarm.domain.calculator

data class StudyTimeInterval(
    val startMillis: Long,
    val endMillis: Long
)

/**
 * ActivityRecord intervals are canonical. Legacy StudySession intervals are
 * only used when that canonical source has no study rows for the period.
 */
object StudyActivityDurationCalculator {
    fun calculate(
        periodStartMillis: Long,
        periodEndMillis: Long,
        canonicalIntervals: List<StudyTimeInterval>,
        legacyIntervals: List<StudyTimeInterval>
    ): Long {
        val selected = canonicalIntervals.ifEmpty { legacyIntervals }
        return ActivityPeriodCalculator.calculate(
            periodStartMillis = periodStartMillis,
            periodEndMillis = periodEndMillis,
            intervals = selected.map { interval ->
                TrackedInterval(
                    type = TrackedActivityType.STUDY,
                    startMillis = interval.startMillis,
                    endMillis = interval.endMillis
                )
            }
        ).studyMillis
    }
}
