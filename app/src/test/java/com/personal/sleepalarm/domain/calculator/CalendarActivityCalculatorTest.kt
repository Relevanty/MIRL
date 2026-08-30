package com.personal.sleepalarm.domain.calculator

import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CalendarActivityCalculatorTest {
    private val zone = ZoneId.of("UTC")

    @Test
    fun crossMidnightActivityIsClippedIntoBothCalendarDays() {
        val first = LocalDate.of(2026, 8, 1)
        val start = first.atTime(23, 30).atZone(zone).toInstant().toEpochMilli()
        val end = first.plusDays(1).atTime(0, 30).atZone(zone).toInstant().toEpochMilli()

        val totals = CalendarActivityCalculator.millisByDate(
            listOf(record(1, start, end, end - start)),
            zone
        )

        assertEquals(30L * 60_000L, totals[first])
        assertEquals(30L * 60_000L, totals[first.plusDays(1)])
    }

    @Test
    fun parallelHistoryRowsAreVisibleButNotDoubleCounted() {
        val date = LocalDate.of(2026, 8, 1)
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val totals = CalendarActivityCalculator.millisByDate(
            listOf(
                record(1, start, start + 60_000L, 60_000L),
                record(2, start + 30_000L, start + 90_000L, 60_000L, counts = false)
            ),
            zone
        )

        assertEquals(90_000L, totals[date])
    }

    private fun record(
        id: Int,
        start: Long,
        end: Long,
        duration: Long,
        counts: Boolean = true
    ) = ActivityRecordEntity(
        id = id,
        taskId = 1,
        title = "Activity",
        startedAt = start,
        endedAt = end,
        durationMillis = duration,
        source = "MANUAL",
        countsTowardProgress = counts
    )
}
