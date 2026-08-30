package com.personal.sleepalarm.service

import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.calculator.ActivityProgressCalculator
import com.personal.sleepalarm.domain.dailyplan.DailyPlanNudgePolicy
import com.personal.sleepalarm.domain.dailyplan.DailyPlanProgressSnapshot
import com.personal.sleepalarm.domain.dailyplan.DailyPlanTaskInput
import com.personal.sleepalarm.domain.dailyplan.DailyPlanTaskProgress
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.domain.model.remainingWorkMinutesOrNull

internal data class DailyBriefingPlan(
    val snapshot: DailyPlanProgressSnapshot,
    /** Uses exact milliseconds, so 20 seconds of work is not called “unstarted”. */
    val unstartedTasks: List<DailyPlanTaskProgress>
)

/** Pure bridge from canonical activity history to the daily-plan policy. */
internal fun calculateDailyBriefingPlan(
    tasks: Iterable<TaskEntity>,
    records: Iterable<ActivityRecordEntity>,
    nowMillis: Long,
    dayStartMillis: Long,
    nextMidnightMillis: Long,
    cutoffMillis: Long,
    bufferMinutes: Int
): DailyBriefingPlan {
    val requiredTasks = tasks.filter { task ->
        !task.isDone &&
            !task.isMorningRoutine &&
            (task.startAtMillis == null || task.startAtMillis <= nowMillis) &&
            task.isDailyRequired &&
            task.plannedFocusMinutes > 0
    }
    val recordsByTask = records.asSequence()
        .filter(ActivityRecordEntity::countsTowardProgress)
        .filter { it.startedAt <= nowMillis }
        .mapNotNull { record -> record.taskId?.let { it to record } }
        .groupBy({ it.first }, { it.second })
    val exactProgress = requiredTasks.associate { task ->
        val taskRecords = recordsByTask[task.id].orEmpty()
        task.id to ActivityProgressCalculator.uniqueRecordedMillis(
            records = taskRecords,
            periodStartMillis = dayStartMillis,
            periodEndMillis = minOf(nowMillis, nextMidnightMillis).coerceAtLeast(dayStartMillis)
        )
    }
    val snapshot = DailyPlanNudgePolicy.calculate(
        tasks = requiredTasks.map { task ->
            DailyPlanTaskInput(
                taskId = task.id,
                title = task.primaryLabel(),
                dailyTargetMinutes = task.plannedFocusMinutes,
                wholeBudgetRemainingMinutes = task.remainingWorkMinutesOrNull(),
                todayProgressMinutes = (exactProgress[task.id].orEmptyMillis() / MINUTE_MILLIS)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt(),
                boutMinutes = task.estimatedMinutes
            )
        },
        nowMillis = nowMillis,
        dayStartMillis = dayStartMillis,
        nextMidnightMillis = nextMidnightMillis,
        cutoffMillis = cutoffMillis,
        bufferMinutes = bufferMinutes
    )
    val unstartedIds = exactProgress.filterValues { it <= 0L }.keys
    return DailyBriefingPlan(
        snapshot = snapshot,
        unstartedTasks = snapshot.tasks.filter { it.taskId in unstartedIds && it.remainingMinutes > 0 }
    )
}

private fun Long?.orEmptyMillis(): Long = this ?: 0L

private const val MINUTE_MILLIS = 60_000L
