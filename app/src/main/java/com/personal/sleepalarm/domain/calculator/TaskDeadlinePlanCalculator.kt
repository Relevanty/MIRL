package com.personal.sleepalarm.domain.calculator

import com.personal.sleepalarm.data.db.entity.TaskEntity
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * A read-only explanation of one task's workload and canonical deadline.
 *
 * [remainingMinutes] is zero when [budgetConfigured] is false: the amount of
 * work is unknown, not complete. Consumers must gate workload labels on that
 * flag. An exhausted estimate likewise does not complete the task.
 *
 * [isManualDailyGoalSufficient] compares the user's daily goal with the required
 * average only. It is not a schedule guarantee; [cannotFitBeforeDeadline] takes
 * precedence, and calendar appointments, sleep and energy are not modeled here.
 */
data class TaskDeadlinePlan(
    val budgetConfigured: Boolean,
    val totalMinutes: Int,
    val spentMinutes: Int,
    val remainingMinutes: Int,
    val requiredMinutesPerDay: Int?,
    val calendarDaysRemaining: Int?,
    val wallClockMinutesRemaining: Long?,
    val overdue: Boolean,
    val estimateExhaustedButTaskOpen: Boolean,
    val manualDailyGoalMinutes: Int,
    val isManualDailyGoalSufficient: Boolean?,
    val cannotFitBeforeDeadline: Boolean
)

/**
 * Shared explanatory calculation for the task editor and calendar deadline.
 *
 * The whole-task estimate is [TaskEntity.workBudgetMinutes]. Counted all-time
 * work is [TaskEntity.spentMillis], not today's focus progress. A caller that
 * already calculates unique in-flight work may include it in the supplied task
 * snapshot; this calculator never adds a second live interval itself.
 * [TaskEntity.estimatedMinutes] describes one focus bout and is deliberately not
 * used as either the total estimate or the daily goal.
 *
 * [TaskEntity.dueAtMillis] is the exact deadline instant. A positive remainder
 * is spread evenly over local calendar dates from today through the deadline's
 * date, inclusively, then rounded up to whole minutes. Each date has equal
 * weight in this average; this does not assert that a full workday remains on
 * the partial first/last dates. It is a pace, not an allocation. The exact
 * remaining wall-clock time supplies a necessary
 * impossibility check, without promising the work fits the real schedule.
 *
 * No pace is recommended for an unknown/exhausted estimate, a completed task,
 * a missing deadline, or a deadline already reached. The configured daily goal
 * and focus bout are never changed.
 */
object TaskDeadlinePlanCalculator {
    private const val MINUTE_MILLIS = 60_000L

    fun calculate(task: TaskEntity, nowMillis: Long, zone: ZoneId): TaskDeadlinePlan {
        val totalMinutes = task.workBudgetMinutes.coerceAtLeast(0)
        val budgetConfigured = totalMinutes > 0
        val totalMillis = totalMinutes.toLong() * MINUTE_MILLIS
        val spentMillis = task.spentMillis.coerceAtLeast(0L)
        val budgetRemainingMillis = if (budgetConfigured) {
            (totalMillis - spentMillis).coerceAtLeast(0L)
        } else 0L
        val remainingMillis = if (task.isDone) 0L else budgetRemainingMillis
        val dueAt = task.dueAtMillis
        val deadlineReached = dueAt != null && dueAt <= nowMillis
        val wallClockMillis = dueAt?.let { positiveDifference(it, nowMillis) }
        val days = dueAt?.let {
            if (deadlineReached) 0 else {
                val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
                val dueDate = Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                (ChronoUnit.DAYS.between(today, dueDate).coerceAtLeast(0L) + 1L)
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
        }
        val remainingMinutes = ceilMinutes(remainingMillis)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val requiredMinutesPerDay = if (
            budgetConfigured && !task.isDone && remainingMillis > 0L &&
            days != null && days > 0 && !deadlineReached
        ) {
            ((remainingMinutes.toLong() + days - 1L) / days).toInt()
        } else null
        val manualDailyGoal = task.plannedFocusMinutes.coerceAtLeast(0)

        return TaskDeadlinePlan(
            budgetConfigured = budgetConfigured,
            totalMinutes = totalMinutes,
            spentMinutes = (spentMillis / MINUTE_MILLIS)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            remainingMinutes = remainingMinutes,
            requiredMinutesPerDay = requiredMinutesPerDay,
            calendarDaysRemaining = days,
            wallClockMinutesRemaining = wallClockMillis?.let(::ceilMinutes),
            overdue = !task.isDone && deadlineReached,
            estimateExhaustedButTaskOpen = budgetConfigured && !task.isDone && budgetRemainingMillis == 0L,
            manualDailyGoalMinutes = manualDailyGoal,
            isManualDailyGoalSufficient = requiredMinutesPerDay?.let { manualDailyGoal >= it },
            cannotFitBeforeDeadline = !task.isDone && budgetConfigured &&
                wallClockMillis != null && remainingMillis > wallClockMillis
        )
    }

    /** Ceiling without an addition that can overflow for extreme instants. */
    private fun ceilMinutes(millis: Long): Long =
        millis / MINUTE_MILLIS + if (millis % MINUTE_MILLIS == 0L) 0L else 1L

    /** Saturates only when two valid epoch values are farther apart than Long.MAX_VALUE. */
    private fun positiveDifference(endMillis: Long, startMillis: Long): Long {
        if (endMillis <= startMillis) return 0L
        val difference = endMillis - startMillis
        return if (difference < 0L) Long.MAX_VALUE else difference
    }
}
