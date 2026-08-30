package com.personal.sleepalarm.domain.dailyplan

import kotlin.math.min

data class DailyPlanTaskInput(
    val taskId: Int,
    val title: String,
    /** Existing TaskEntity.plannedFocusMinutes; this is today's target. */
    val dailyTargetMinutes: Int,
    /** Current whole-task remainder after persisted and live work; null means unlimited. */
    val wholeBudgetRemainingMinutes: Int?,
    val todayProgressMinutes: Int,
    /** Existing TaskEntity.estimatedMinutes; this is one suggested focus bout. */
    val boutMinutes: Int
)

data class DailyPlanTaskProgress(
    val taskId: Int,
    val title: String,
    val effectiveTargetMinutes: Int,
    val todayProgressMinutes: Int,
    val remainingMinutes: Int,
    val boutMinutes: Int
)

data class DailyPlanProgressSnapshot(
    val nowMillis: Long,
    val dayStartMillis: Long,
    val nextMidnightMillis: Long,
    val cutoffMillis: Long,
    val tasks: List<DailyPlanTaskProgress>,
    val totalRemainingMinutes: Int,
    val availableMinutes: Int,
    val slackMinutes: Int,
    val bufferMinutes: Int
) {
    val hasRequiredTasks: Boolean get() = tasks.isNotEmpty()
    val shouldNudge: Boolean
        get() = hasRequiredTasks && nowMillis < cutoffMillis && slackMinutes <= bufferMinutes
    val isOverloaded: Boolean get() = slackMinutes < 0
    val unstartedTasks: List<DailyPlanTaskProgress>
        get() = tasks.filter { it.todayProgressMinutes == 0 && it.remainingMinutes > 0 }
}

object DailyPlanTaskEligibility {
    fun isEligible(
        isDailyRequired: Boolean,
        isDone: Boolean,
        isMorningRoutine: Boolean,
        startAtMillis: Long?,
        dailyTargetMinutes: Int,
        nowMillis: Long
    ): Boolean = isDailyRequired &&
        !isDone &&
        !isMorningRoutine &&
        dailyTargetMinutes > 0 &&
        (startAtMillis == null || startAtMillis <= nowMillis)
}

/** Pure implementation of the daily-target/slack formula. */
object DailyPlanNudgePolicy {
    private const val MINUTE_MILLIS = 60_000L

    fun currentWholeBudgetRemainingMinutes(
        workBudgetMinutes: Int,
        persistedAllTimeSpentMillis: Long,
        liveElapsedMillis: Long
    ): Int? {
        if (workBudgetMinutes <= 0) return null
        val remainingMillis = (
            workBudgetMinutes.toLong() * MINUTE_MILLIS -
                persistedAllTimeSpentMillis.coerceAtLeast(0L) -
                liveElapsedMillis.coerceAtLeast(0L)
            ).coerceAtLeast(0L)
        return ((remainingMillis + MINUTE_MILLIS - 1L) / MINUTE_MILLIS)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun calculate(
        tasks: Iterable<DailyPlanTaskInput>,
        nowMillis: Long,
        dayStartMillis: Long,
        nextMidnightMillis: Long,
        cutoffMillis: Long,
        bufferMinutes: Int
    ): DailyPlanProgressSnapshot {
        val progress = tasks.mapNotNull(::calculateTask)
            .filter { it.remainingMinutes > 0 }
        val totalRemaining = progress.sumOf { it.remainingMinutes.toLong() }
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val availableMillis = (cutoffMillis - nowMillis).coerceAtLeast(0L)
        val availableMinutes = ((availableMillis + MINUTE_MILLIS - 1L) / MINUTE_MILLIS)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val slack = (availableMinutes.toLong() - totalRemaining.toLong())
            .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
            .toInt()

        return DailyPlanProgressSnapshot(
            nowMillis = nowMillis,
            dayStartMillis = dayStartMillis,
            nextMidnightMillis = nextMidnightMillis,
            cutoffMillis = cutoffMillis,
            tasks = progress,
            totalRemainingMinutes = totalRemaining,
            availableMinutes = availableMinutes,
            slackMinutes = slack,
            bufferMinutes = bufferMinutes.coerceAtLeast(0)
        )
    }

    fun calculateTask(task: DailyPlanTaskInput): DailyPlanTaskProgress? {
        val target = task.dailyTargetMinutes.coerceAtLeast(0)
        if (target == 0) return null
        val todayProgress = task.todayProgressMinutes.coerceAtLeast(0)
        val dailyRemaining = (target - todayProgress).coerceAtLeast(0)
        val budgetRemainder = task.wholeBudgetRemainingMinutes?.coerceAtLeast(0)
        val remaining = if (budgetRemainder == null) {
            dailyRemaining
        } else {
            min(dailyRemaining, budgetRemainder)
        }
        return DailyPlanTaskProgress(
            taskId = task.taskId,
            title = task.title,
            effectiveTargetMinutes = target,
            todayProgressMinutes = todayProgress,
            remainingMinutes = remaining,
            boutMinutes = task.boutMinutes.coerceIn(1, 24 * 60)
        )
    }
}
