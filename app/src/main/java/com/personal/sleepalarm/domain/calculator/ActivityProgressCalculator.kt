package com.personal.sleepalarm.domain.calculator

import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity

/** Canonical progress calculations shared by task/project analytics surfaces. */
object ActivityProgressCalculator {
    fun countedMillis(records: Iterable<ActivityRecordEntity>): Long = records
        .asSequence()
        .filter(ActivityRecordEntity::countsTowardProgress)
        .sumOf { it.durationMillis.coerceAtLeast(0L) }

    /** Overall active time counts overlapping records only once. */
    fun uniqueCountedMillis(records: Iterable<ActivityRecordEntity>): Long {
        return uniqueMillis(records, countedOnly = true)
    }

    /** Calendar/history totals include every real row but still merge overlap. */
    fun uniqueRecordedMillis(
        records: Iterable<ActivityRecordEntity>,
        periodStartMillis: Long,
        periodEndMillis: Long
    ): Long = uniqueMillis(
        records = records,
        countedOnly = false,
        periodStartMillis = periodStartMillis,
        periodEndMillis = periodEndMillis
    )

    private fun uniqueMillis(
        records: Iterable<ActivityRecordEntity>,
        countedOnly: Boolean,
        periodStartMillis: Long? = null,
        periodEndMillis: Long? = null
    ): Long {
        val intervals = records.asSequence()
            .filter { !countedOnly || it.countsTowardProgress }
            .mapNotNull { record ->
                val end = record.effectiveActivityEndMillis()
                if (end > record.startedAt) {
                    TrackedInterval(TrackedActivityType.WORK, record.startedAt, end)
                } else null
            }
            .toList()
        if (intervals.isEmpty()) return 0L
        val start = periodStartMillis ?: intervals.minOf(TrackedInterval::startMillis)
        val end = periodEndMillis ?: intervals.maxOf(TrackedInterval::endMillis)
        return ActivityPeriodCalculator.uniqueActiveMillis(
            periodStartMillis = start,
            periodEndMillis = end,
            intervals = intervals
        )
    }
}

fun ActivityRecordEntity.effectiveActivityEndMillis(): Long {
    val duration = durationMillis.coerceAtLeast(0L)
    val durationEnd = if (startedAt > Long.MAX_VALUE - duration) Long.MAX_VALUE
    else startedAt + duration
    return minOf(endedAt, durationEnd)
}
