package com.personal.sleepalarm.domain.calculator

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Единая системная граница статистического дня: 04:00 → 04:00. */
object ActivityDayBoundary {
    fun dateFor(millis: Long, zone: ZoneId): LocalDate {
        val local = Instant.ofEpochMilli(millis).atZone(zone)
        return if (local.hour < START_HOUR) {
            local.toLocalDate().minusDays(1)
        } else {
            local.toLocalDate()
        }
    }

    fun boundsFor(startDate: LocalDate, endDateExclusive: LocalDate, zone: ZoneId): Pair<Long, Long> =
        startDate.atTime(START_HOUR, 0).atZone(zone).toInstant().toEpochMilli() to
            endDateExclusive.atTime(START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()

    fun currentBounds(nowMillis: Long, zone: ZoneId): Pair<Long, Long> {
        val date = dateFor(nowMillis, zone)
        return boundsFor(date, date.plusDays(1), zone)
    }

    const val START_HOUR = 4
}
