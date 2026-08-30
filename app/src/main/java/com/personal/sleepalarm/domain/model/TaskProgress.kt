package com.personal.sleepalarm.domain.model

import com.personal.sleepalarm.data.db.entity.TaskEntity
import kotlin.math.min

private const val MINUTE_MILLIS = 60_000L

/** Configured whole-task budget; zero means that no total limit was set. */
fun TaskEntity.effectiveWorkBudgetMinutes(): Int = workBudgetMinutes.coerceAtLeast(0)

fun TaskEntity.remainingWorkMinutesOrNull(): Int? {
    val remaining = remainingWorkMillisOrNull() ?: return null
    return ((remaining + MINUTE_MILLIS - 1L) / MINUTE_MILLIS).toInt()
}

/** Remaining budget is derived from activity history's cached total. */
fun TaskEntity.remainingWorkMillisOrNull(): Long? {
    val budget = effectiveWorkBudgetMinutes()
    if (budget <= 0) return null
    return (budget.toLong() * MINUTE_MILLIS - spentMillis).coerceAtLeast(0L)
}

/**
 * Duration of the next task-linked focus cycle.
 *
 * The last cycle may be shorter than the regular five-minute picker minimum:
 * otherwise a task with two minutes remaining would always overshoot its budget.
 * Zero means that the task budget has already been exhausted.
 */
fun TaskEntity.nextFocusDurationMinutes(): Int {
    val bout = estimatedMinutes.coerceAtLeast(1)
    val remainingMinutes = remainingWorkMinutesOrNull() ?: return bout
    if (remainingMinutes <= 0) return 0
    return min(bout, remainingMinutes).coerceAtLeast(1)
}
