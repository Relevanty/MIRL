package com.personal.sleepalarm.domain.calculator

import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.FocusProtocolSessionEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.domain.model.FocusProtocolPhase
import com.personal.sleepalarm.domain.model.focusItemTaskId
import java.time.Instant
import java.time.ZoneId

private const val MINUTE_MILLIS = 60_000L

data class LocalDayBounds(
    val startMillis: Long,
    val endMillis: Long
)

/** An in-flight focus leg that has not reached activity_records yet. */
data class TaskFocusInterval(
    val taskId: Int,
    val startMillis: Long,
    val endMillis: Long
)

data class DailyTaskFocusProgress(
    val taskId: Int,
    val dayStartMillis: Long,
    val dayEndMillis: Long,
    /** Persisted + live, with overlaps merged. */
    val spentMillis: Long,
    /** Persisted portion of [spentMillis]. */
    val persistedSpentMillis: Long,
    /** Additional unique live time not already represented by a record. */
    val liveAddedMillis: Long,
    val targetMinutes: Int
) {
    val targetMillis: Long = targetMinutes.coerceAtLeast(0).toLong() * MINUTE_MILLIS
    val remainingMillis: Long = (targetMillis - spentMillis).coerceAtLeast(0L)
    val spentMinutes: Int = (spentMillis / MINUTE_MILLIS).toInt()
    val progressFraction: Float = if (targetMillis <= 0L) 0f
    else (spentMillis.toDouble() / targetMillis).coerceIn(0.0, 1.0).toFloat()
}

/**
 * Canonical daily task progress. A task day is always local 00:00..00:00;
 * this deliberately does not use the separate 04:00 analytics boundary.
 */
object DailyTaskFocusCalculator {
    fun localDayBounds(nowMillis: Long, zoneId: ZoneId): LocalDayBounds {
        val date = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        return LocalDayBounds(
            startMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            endMillis = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        )
    }

    /**
     * Returns exact elapsed milliseconds per task. Intersections are merged per
     * task, so a parallel/manual duplicate never inflates that task's progress.
     */
    fun countedMillisByTask(
        records: Iterable<ActivityRecordEntity>,
        periodStartMillis: Long,
        periodEndMillis: Long,
        liveIntervals: Iterable<TaskFocusInterval> = emptyList()
    ): Map<Int, Long> {
        require(periodEndMillis >= periodStartMillis)
        val intervalsByTask = linkedMapOf<Int, MutableList<TrackedInterval>>()
        records.forEach { record ->
            val taskId = record.taskId ?: return@forEach
            if (!record.countsTowardProgress) return@forEach
            val end = record.effectiveActivityEndMillis()
            if (end <= record.startedAt) return@forEach
            intervalsByTask.getOrPut(taskId, ::mutableListOf).add(
                TrackedInterval(TrackedActivityType.WORK, record.startedAt, end)
            )
        }
        liveIntervals.forEach { interval ->
            if (interval.endMillis <= interval.startMillis) return@forEach
            intervalsByTask.getOrPut(interval.taskId, ::mutableListOf).add(
                TrackedInterval(
                    TrackedActivityType.WORK,
                    interval.startMillis,
                    interval.endMillis
                )
            )
        }
        return intervalsByTask.mapValues { (_, intervals) ->
            ActivityPeriodCalculator.uniqueActiveMillis(
                periodStartMillis = periodStartMillis,
                periodEndMillis = periodEndMillis,
                intervals = intervals
            )
        }
    }

    fun calculate(
        task: TaskEntity,
        records: Iterable<ActivityRecordEntity>,
        nowMillis: Long,
        zoneId: ZoneId,
        liveIntervals: Iterable<TaskFocusInterval> = emptyList()
    ): DailyTaskFocusProgress {
        val bounds = localDayBounds(nowMillis, zoneId)
        val observationEnd = minOf(nowMillis, bounds.endMillis).coerceAtLeast(bounds.startMillis)
        val persistedSpent = countedMillisByTask(
            records = records,
            periodStartMillis = bounds.startMillis,
            periodEndMillis = observationEnd
        )[task.id] ?: 0L
        val spent = countedMillisByTask(
            records = records,
            periodStartMillis = bounds.startMillis,
            periodEndMillis = observationEnd,
            liveIntervals = liveIntervals
        )[task.id] ?: 0L
        return DailyTaskFocusProgress(
            taskId = task.id,
            dayStartMillis = bounds.startMillis,
            dayEndMillis = bounds.endMillis,
            spentMillis = spent,
            persistedSpentMillis = persistedSpent,
            liveAddedMillis = (spent - persistedSpent).coerceAtLeast(0L),
            targetMinutes = task.plannedFocusMinutes.coerceAtLeast(0)
        )
    }

    fun calculateForTasks(
        tasks: Iterable<TaskEntity>,
        records: Iterable<ActivityRecordEntity>,
        nowMillis: Long,
        zoneId: ZoneId,
        liveIntervals: Iterable<TaskFocusInterval> = emptyList()
    ): Map<Int, DailyTaskFocusProgress> {
        val bounds = localDayBounds(nowMillis, zoneId)
        val observationEnd = minOf(nowMillis, bounds.endMillis).coerceAtLeast(bounds.startMillis)
        val persistedByTask = countedMillisByTask(
            records = records,
            periodStartMillis = bounds.startMillis,
            periodEndMillis = observationEnd
        )
        val spentByTask = countedMillisByTask(
            records = records,
            periodStartMillis = bounds.startMillis,
            periodEndMillis = observationEnd,
            liveIntervals = liveIntervals
        )
        return tasks.associate { task ->
            val persisted = persistedByTask[task.id] ?: 0L
            val spent = spentByTask[task.id] ?: 0L
            task.id to DailyTaskFocusProgress(
                taskId = task.id,
                dayStartMillis = bounds.startMillis,
                dayEndMillis = bounds.endMillis,
                spentMillis = spent,
                persistedSpentMillis = persisted,
                liveAddedMillis = (spent - persisted).coerceAtLeast(0L),
                targetMinutes = task.plannedFocusMinutes.coerceAtLeast(0)
            )
        }
    }
}

/**
 * Converts the currently unrecorded focus-protocol cycle into mergeable legs.
 * Completed cycles already live in activity_records and therefore return none.
 */
fun FocusProtocolSessionEntity.liveTaskFocusIntervals(
    nowMillis: Long
): List<TaskFocusInterval> {
    if (pomodoroRecorded || phase !in setOf(FocusProtocolPhase.FOCUS, FocusProtocolPhase.FOCUS_PAUSED)) {
        return emptyList()
    }
    val taskId = focusItemTaskId(itemId)
        ?: itemId.takeIf { activityType == FocusActivityType.WORK && it > 0 }
        ?: return emptyList()
    val cycleStart = focusStartedAt ?: return emptyList()
    val result = mutableListOf<TaskFocusInterval>()

    // Paused/resumed time is stored as accumulated active time. Keep it as a
    // compact leg ending before the current leg; it remains merge-safe with
    // records while never counting the pause itself.
    val accumulated = focusElapsedMillis.coerceAtLeast(0L)
    if (accumulated > 0L) {
        val accumulatedEnd = (cycleStart + accumulated).coerceAtMost(phaseStartedAt)
        if (accumulatedEnd > cycleStart) {
            result += TaskFocusInterval(taskId, cycleStart, accumulatedEnd)
        }
    }
    if (phase == FocusProtocolPhase.FOCUS) {
        val currentEnd = minOf(nowMillis, phaseEndsAt ?: nowMillis)
        if (currentEnd > phaseStartedAt) {
            result += TaskFocusInterval(taskId, phaseStartedAt, currentEnd)
        }
    }
    return result
}
