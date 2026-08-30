package com.personal.sleepalarm.domain.calculator

enum class TrackedActivityType {
    SLEEP,
    STUDY,
    WORK,
    OTHER
}

data class TrackedInterval(
    val type: TrackedActivityType,
    val startMillis: Long,
    val endMillis: Long
)

data class ActivityPeriodTotals(
    val periodStartMillis: Long,
    val periodEndMillis: Long,
    val sleepMillis: Long = 0L,
    val studyMillis: Long = 0L,
    val workMillis: Long = 0L,
    val otherMillis: Long = 0L
)

/** Считает объединённую длительность типов в заданных границах периода. */
object ActivityPeriodCalculator {
    fun calculate(
        periodStartMillis: Long,
        periodEndMillis: Long,
        intervals: List<TrackedInterval>
    ): ActivityPeriodTotals {
        require(periodEndMillis >= periodStartMillis)
        val durations = TrackedActivityType.entries.associateWith { type ->
            mergedDuration(
                intervals = intervals.filter { it.type == type },
                from = periodStartMillis,
                to = periodEndMillis
            )
        }
        return ActivityPeriodTotals(
            periodStartMillis = periodStartMillis,
            periodEndMillis = periodEndMillis,
            sleepMillis = durations.getValue(TrackedActivityType.SLEEP),
            studyMillis = durations.getValue(TrackedActivityType.STUDY),
            workMillis = durations.getValue(TrackedActivityType.WORK),
            otherMillis = durations.getValue(TrackedActivityType.OTHER)
        )
    }

    /** Unique active time across all categories; overlapping records count once. */
    fun uniqueActiveMillis(
        periodStartMillis: Long,
        periodEndMillis: Long,
        intervals: List<TrackedInterval>
    ): Long {
        require(periodEndMillis >= periodStartMillis)
        return mergedDuration(intervals, periodStartMillis, periodEndMillis)
    }

    private fun mergedDuration(
        intervals: List<TrackedInterval>,
        from: Long,
        to: Long
    ): Long {
        val clipped = intervals.mapNotNull { interval ->
            val start = maxOf(interval.startMillis, from)
            val end = minOf(interval.endMillis, to)
            if (end > start) start to end else null
        }.sortedBy { it.first }

        if (clipped.isEmpty()) return 0L
        var total = 0L
        var currentStart = clipped.first().first
        var currentEnd = clipped.first().second
        for ((start, end) in clipped.drop(1)) {
            if (start <= currentEnd) {
                currentEnd = maxOf(currentEnd, end)
            } else {
                total += currentEnd - currentStart
                currentStart = start
                currentEnd = end
            }
        }
        return total + (currentEnd - currentStart)
    }
}
