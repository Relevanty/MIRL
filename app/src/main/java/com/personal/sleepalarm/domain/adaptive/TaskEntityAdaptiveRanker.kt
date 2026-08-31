package com.personal.sleepalarm.domain.adaptive

import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.NextActionRanker

/** Result bridge for existing callers; the persistence model stays outside the engine itself. */
data class TaskEntityAdaptiveRanking(
    val orderedTasks: List<TaskEntity>,
    val details: AdaptiveRanking<Int>
)

/**
 * Explicit safety bridge to the existing canonical ranker. Until a state has
 * enough confidence, [orderedTasks] is exactly [NextActionRanker]'s result.
 */
object TaskEntityAdaptiveRanker {
    fun rank(
        tasks: List<TaskEntity>,
        context: PlanningContext,
        demandOverrides: Map<Int, TaskDemand<Int>> = emptyMap()
    ): TaskEntityAdaptiveRanking {
        val fallback = NextActionRanker.rank(tasks, context.nowMillis)
        val fallbackIds = fallback.map(TaskEntity::id)
        val demands = tasks.map { task ->
            val base = task.toAdaptiveDemand()
            val override = demandOverrides[task.id]
            if (override == null) {
                base
            } else {
                override.copy(
                    id = task.id,
                    earliestStartMillis = override.earliestStartMillis ?: task.startAtMillis,
                    dueAtMillis = override.dueAtMillis ?: task.dueAtMillis,
                    mandatory = override.mandatory || task.isDailyRequired,
                    completed = override.completed || task.isDone || task.isMorningRoutine
                )
            }
        }
        val details = AdaptiveTaskRanker.rank(demands, fallbackIds, context)
        val byId = fallback.associateBy(TaskEntity::id)
        val ordered = details.tasks.mapNotNull { ranked -> byId[ranked.demand.id] }
        return TaskEntityAdaptiveRanking(orderedTasks = ordered, details = details)
    }

    fun TaskEntity.toAdaptiveDemand(): TaskDemand<Int> {
        val (energy, cognitive) = when (energyLevel.uppercase()) {
            "LOW" -> 3.0 to 0.30
            "HIGH" -> 8.0 to 0.80
            else -> 5.5 to 0.55
        }
        return TaskDemand(
            id = id,
            durationMinutes = estimatedMinutes.coerceAtLeast(1),
            energyDemand = energy,
            cognitiveDemand = cognitive,
            earliestStartMillis = startAtMillis,
            dueAtMillis = dueAtMillis,
            // A task due date is urgent, but an overdue task remains actionable.
            deadlineIsHard = false,
            mandatory = isDailyRequired,
            completed = isDone || isMorningRoutine
        )
    }
}
