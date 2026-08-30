package com.personal.sleepalarm.domain.calculator

import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.nextFocusDurationMinutes
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyTaskFocusCalculatorTest {
    @Test
    fun localDayUsesMidnightAndHandlesSpringDst() {
        val zone = ZoneId.of("America/New_York")
        val now = LocalDate.of(2024, 3, 10).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        val bounds = DailyTaskFocusCalculator.localDayBounds(now, zone)

        assertEquals(LocalTime.MIDNIGHT, instantLocalTime(bounds.startMillis, zone))
        assertEquals(23L * 60L * 60_000L, bounds.endMillis - bounds.startMillis)
    }

    @Test
    fun localDayHandlesFallDstWithoutAssumingTwentyFourHours() {
        val zone = ZoneId.of("America/New_York")
        val now = LocalDate.of(2024, 11, 3).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        val bounds = DailyTaskFocusCalculator.localDayBounds(now, zone)

        assertEquals(25L * 60L * 60_000L, bounds.endMillis - bounds.startMillis)
    }

    @Test
    fun todayProgressClipsMergesAndIncludesManualAndTimer() {
        val zone = ZoneId.of("Europe/Moscow")
        val date = LocalDate.of(2026, 8, 29)
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val now = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val task = TaskEntity(id = 7, title = "Task", plannedFocusMinutes = 120)
        val records = listOf(
            record(1, 7, dayStart - 10 * 60_000L, dayStart + 10 * 60_000L, "MANUAL"),
            record(2, 7, dayStart + 5 * 60_000L, dayStart + 20 * 60_000L, "TIMER"),
            record(3, 7, dayStart, dayStart + 90 * 60_000L, "MANUAL", counts = false),
            record(4, 8, dayStart, dayStart + 60 * 60_000L, "TIMER")
        )

        val progress = DailyTaskFocusCalculator.calculate(
            task = task,
            records = records,
            nowMillis = now,
            zoneId = zone,
            liveIntervals = listOf(
                TaskFocusInterval(7, dayStart + 15 * 60_000L, dayStart + 30 * 60_000L)
            )
        )

        assertEquals(30L * 60_000L, progress.spentMillis)
        assertEquals(20L * 60_000L, progress.persistedSpentMillis)
        assertEquals(10L * 60_000L, progress.liveAddedMillis)
        assertEquals(90L * 60_000L, progress.remainingMillis)
    }

    @Test
    fun overlapsAreMergedPerTaskNotAcrossTasks() {
        val records = listOf(
            record(1, 1, 0L, 60_000L, "TIMER"),
            record(2, 1, 30_000L, 90_000L, "MANUAL"),
            record(3, 2, 30_000L, 90_000L, "TIMER")
        )

        val totals = DailyTaskFocusCalculator.countedMillisByTask(records, 0L, 120_000L)

        assertEquals(90_000L, totals[1])
        assertEquals(60_000L, totals[2])
    }

    @Test
    fun progressMayExceedGoalButRemainingStopsAtZero() {
        val zone = ZoneId.of("UTC")
        val date = LocalDate.of(2026, 8, 29)
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val now = start + 10L * 60L * 60_000L
        val task = TaskEntity(id = 1, title = "Task", plannedFocusMinutes = 120, estimatedMinutes = 40)
        val progress = DailyTaskFocusCalculator.calculate(
            task,
            listOf(record(1, 1, start, start + 145L * 60_000L, "TIMER")),
            now,
            zone
        )

        assertEquals(145, progress.spentMinutes)
        assertEquals(0L, progress.remainingMillis)
        assertEquals(1f, progress.progressFraction, 0f)
        assertEquals(40, task.nextFocusDurationMinutes())
    }

    @Test
    fun effectiveEndUsesCanonicalDurationAndCannotLeakPastNow() {
        val zone = ZoneId.of("UTC")
        val start = LocalDate.of(2026, 8, 29).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = start + 60_000L
        val malformed = ActivityRecordEntity(
            id = 1,
            taskId = 1,
            title = "Task",
            startedAt = start,
            endedAt = start + 10 * 60_000L,
            durationMillis = 2 * 60_000L,
            source = "MANUAL"
        )

        val progress = DailyTaskFocusCalculator.calculate(
            TaskEntity(id = 1, title = "Task"),
            listOf(malformed),
            now,
            zone
        )

        assertEquals(60_000L, progress.spentMillis)
        assertTrue(progress.spentMillis <= now - start)
    }

    private fun instantLocalTime(millis: Long, zone: ZoneId): LocalTime =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()

    private fun record(
        id: Int,
        taskId: Int,
        start: Long,
        end: Long,
        source: String,
        counts: Boolean = true
    ) = ActivityRecordEntity(
        id = id,
        taskId = taskId,
        title = "Task $taskId",
        startedAt = start,
        endedAt = end,
        durationMillis = (end - start).coerceAtLeast(0L),
        source = source,
        countsTowardProgress = counts
    )
}
