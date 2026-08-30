package com.personal.sleepalarm.domain.calculator

import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityProgressCalculatorTest {
    @Test
    fun overallTimeMergesOverlapAndIgnoresNonProgressRows() {
        val total = ActivityProgressCalculator.uniqueCountedMillis(
            listOf(
                record(1, 1_000L, 6_000L, 5_000L),
                record(2, 4_000L, 9_000L, 5_000L),
                record(3, 0L, 10_000L, 10_000L, counts = false)
            )
        )

        assertEquals(8_000L, total)
    }

    @Test
    fun progressUsesCanonicalDurationRatherThanWallClockSpan() {
        val records = listOf(record(1, 1_000L, 9_000L, 2_000L))

        assertEquals(2_000L, ActivityProgressCalculator.countedMillis(records))
        assertEquals(2_000L, ActivityProgressCalculator.uniqueCountedMillis(records))
    }

    @Test
    fun recordedTimeIncludesNonProgressHistoryWithoutDoubleCountingIt() {
        val records = listOf(
            record(1, 1_000L, 3_000L, 2_000L),
            record(2, 2_000L, 4_000L, 2_000L, counts = false)
        )

        assertEquals(
            3_000L,
            ActivityProgressCalculator.uniqueRecordedMillis(records, 1_000L, 4_000L)
        )
    }

    private fun record(
        id: Int,
        start: Long,
        end: Long,
        duration: Long,
        counts: Boolean = true
    ) = ActivityRecordEntity(
        id = id,
        taskId = id,
        title = "Task $id",
        startedAt = start,
        endedAt = end,
        durationMillis = duration,
        source = "MANUAL",
        countsTowardProgress = counts
    )
}
