package com.personal.sleepalarm.domain.calculator

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** One concrete occurrence of an immutable calendar recurrence master. */
data class CalendarOccurrence(
    val startMillis: Long,
    val endMillis: Long,
    val triggerAtMillis: Long
)

/**
 * Resolves the next alarmable occurrence while preserving local wall-clock time.
 *
 * The stored calendar row remains the recurrence master. Daily and weekly
 * occurrences are derived with ZonedDateTime rather than fixed 24/168-hour
 * offsets, so a daylight-saving transition does not move an 08:00 event.
 */
object CalendarRecurrenceCalculator {

    fun nextOccurrence(
        startMillis: Long,
        endMillis: Long,
        repeatRule: String,
        reminderMinutes: Int,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CalendarOccurrence? {
        val leadMillis = reminderMinutes.coerceAtLeast(0).toLong() * MILLIS_PER_MINUTE
        val baseStart = Instant.ofEpochMilli(startMillis).atZone(zoneId)
        val baseEnd = Instant.ofEpochMilli(endMillis).atZone(zoneId)

        if (repeatRule != DAILY && repeatRule != WEEKLY) {
            val triggerAt = startMillis - leadMillis
            return if (triggerAt > nowMillis) {
                CalendarOccurrence(startMillis, endMillis, triggerAt)
            } else {
                null
            }
        }

        val threshold = Instant.ofEpochMilli(saturatedAdd(nowMillis, leadMillis)).atZone(zoneId)
        var steps = estimatedSteps(baseStart, threshold, repeatRule)
        var occurrenceStart = advance(baseStart, repeatRule, steps)
        var occurrenceEnd = advance(baseEnd, repeatRule, steps)
        var triggerAt = occurrenceStart.toInstant().toEpochMilli() - leadMillis

        // The estimate is intentionally conservative around time-zone changes.
        // Usually this executes zero or one iteration.
        while (triggerAt <= nowMillis) {
            steps += 1L
            occurrenceStart = advance(baseStart, repeatRule, steps)
            occurrenceEnd = advance(baseEnd, repeatRule, steps)
            triggerAt = occurrenceStart.toInstant().toEpochMilli() - leadMillis
        }

        return CalendarOccurrence(
            startMillis = occurrenceStart.toInstant().toEpochMilli(),
            endMillis = occurrenceEnd.toInstant().toEpochMilli(),
            triggerAtMillis = triggerAt
        )
    }

    private fun estimatedSteps(
        base: ZonedDateTime,
        threshold: ZonedDateTime,
        repeatRule: String
    ): Long {
        if (!threshold.isAfter(base)) return 0L
        val days = ChronoUnit.DAYS.between(base.toLocalDate(), threshold.toLocalDate())
            .coerceAtLeast(0L)
        return if (repeatRule == WEEKLY) days / 7L else days
    }

    private fun advance(base: ZonedDateTime, repeatRule: String, steps: Long): ZonedDateTime =
        if (repeatRule == WEEKLY) base.plusWeeks(steps) else base.plusDays(steps)

    private fun saturatedAdd(left: Long, right: Long): Long =
        if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    private const val DAILY = "daily"
    private const val WEEKLY = "weekly"
    private const val MILLIS_PER_MINUTE = 60_000L
}
