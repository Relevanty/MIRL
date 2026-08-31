package com.personal.sleepalarm.domain.adaptive

import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.CalendarEventEntity
import com.personal.sleepalarm.data.db.entity.DailyCheckInEntity
import com.personal.sleepalarm.data.db.entity.EnergyObservationEntity
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.data.db.entity.TaskDemandProfileEntity
import com.personal.sleepalarm.data.db.entity.TaskDependencyEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity

/** Persistence bridge shared by Home, briefings, notifications and the assistant. */
data class AdaptivePlanningInput(
    val nowMillis: Long,
    val tasks: List<TaskEntity>,
    val profiles: List<TaskDemandProfileEntity> = emptyList(),
    val dependencies: List<TaskDependencyEntity> = emptyList(),
    val latestCheckIn: DailyCheckInEntity? = null,
    val latestSleep: SleepSessionEntity? = null,
    val activities: List<ActivityRecordEntity> = emptyList(),
    val energyObservations: List<EnergyObservationEntity> = emptyList(),
    val calendarEvents: List<CalendarEventEntity> = emptyList(),
    val photoperiodMinutes: Int? = null,
    val personalSeasonSensitivity: Double = 0.0,
    /** Null means unknown; false only suppresses explicitly OUTDOOR work. */
    val outdoorFeasible: Boolean? = null,
    val horizonMinutes: Int = 24 * 60
)

data class AdaptivePlanningSnapshot(
    val orderedTasks: List<TaskEntity>,
    val personalState: PersonalState,
    val ranking: AdaptiveRanking<Int>,
    val sequencePlan: SequencePlan<Int>
)

object AdaptivePlanningBridge {
    fun build(input: AdaptivePlanningInput): AdaptivePlanningSnapshot {
        val horizonMinutes = input.horizonMinutes.coerceIn(30, 7 * 24 * 60)
        val horizonEnd = safeAdd(input.nowMillis, horizonMinutes.toLong() * 60_000L)
        val acceptedWake = input.latestSleep?.actualWakeTime
            ?.takeIf { it <= input.nowMillis && input.nowMillis - it <= 36L * 60L * 60_000L }
        val sleepMinutes = acceptedWake?.let { wake ->
            val sleep = input.latestSleep ?: return@let null
            val onset = sleep.detectedSleepOnsetTime ?: sleep.estimatedSleepStartTime
            ((wake - onset).coerceAtLeast(0L) / 60_000L).toInt().coerceAtMost(24 * 60)
        }
        val freshCheckIn = input.latestCheckIn?.takeIf {
            it.timestamp <= input.nowMillis &&
                input.nowMillis - it.timestamp <= 8L * 60L * 60_000L
        }
        val recentStart = input.nowMillis - 3L * 60L * 60_000L
        val recentLoad = input.activities.sumOf { activity ->
            val start = maxOf(activity.startedAt, recentStart)
            val end = minOf(activity.endedAt, input.nowMillis)
            (end - start).coerceAtLeast(0L)
        }.toDouble().div(3L * 60L * 60_000L).coerceIn(0.0, 1.0)
        val state = StateEstimator.estimate(
            PersonalStateObservation(
                nowMillis = input.nowMillis,
                wakeTimeMillis = acceptedWake,
                sleepMinutes = sleepMinutes,
                currentEnergy = freshCheckIn?.energy,
                currentEnergyMeasuredAtMillis = freshCheckIn?.timestamp,
                recentLoad = recentLoad,
                photoperiodMinutes = input.photoperiodMinutes,
                personalSeasonSensitivity = input.personalSeasonSensitivity
            )
        )

        val fixedWindows = input.calendarEvents.mapNotNull { event ->
            val start = maxOf(event.startMillis, input.nowMillis)
            val end = minOf(event.endMillis, horizonEnd)
            if (end > start) TimeWindow(start, end) else null
        }
        val profiles = input.profiles.associateBy(TaskDemandProfileEntity::taskId)
        val allTasks = input.tasks.associateBy(TaskEntity::id)
        val dependencies = input.dependencies.groupBy(TaskDependencyEntity::taskId)
        val learnedTaskDeltas = learnedEnergyDeltas(input.energyObservations)
        val learnedModeDeltas = learnedModeEnergyDeltas(
            taskDeltas = learnedTaskDeltas,
            profiles = profiles
        )
        val activeTasks = input.tasks.filterNot { it.isDone || it.isMorningRoutine }
        val demands = activeTasks.associate { task ->
            val profile = profiles[task.id]
            val dependencyBlocked = dependencies[task.id].orEmpty().any { edge ->
                allTasks[edge.dependsOnTaskId]?.isDone != true
            }
            val weatherBlocked = input.outdoorFeasible == false && profile?.placeContext == "OUTDOOR"
            val learnedDelta = learnedTaskDeltas[task.id]?.takeIf { it.sampleCount >= 3 }
                ?: profile?.workMode?.let(learnedModeDeltas::get)?.takeIf { it.sampleCount >= 5 }
            task.id to task.toDemand(profile, learnedDelta)
                .copy(blocked = dependencyBlocked || weatherBlocked)
        }
        val planningContext = PlanningContext(
            nowMillis = input.nowMillis,
            horizonEndMillis = horizonEnd,
            personalState = state,
            fixedCalendarWindows = fixedWindows
        )
        val result = TaskEntityAdaptiveRanker.rank(
            tasks = activeTasks,
            context = planningContext,
            demandOverrides = demands
        )
        return AdaptivePlanningSnapshot(
            orderedTasks = result.orderedTasks,
            personalState = state,
            ranking = result.details,
            sequencePlan = ShortHorizonPlanner.plan(result.details, planningContext)
        )
    }

    private fun TaskEntity.toDemand(
        profile: TaskDemandProfileEntity?,
        learnedDelta: LearnedEnergyDelta?
    ): TaskDemand<Int> {
        if (profile == null) {
            val energy = when (energyLevel.uppercase()) {
                "LOW" -> 3.0
                "HIGH" -> 8.0
                else -> 5.5
            }
            val learnedAdjustment = learnedDelta?.boundedDemandAdjustment().orZero()
            return TaskDemand(
                id = id,
                durationMinutes = estimatedMinutes.coerceAtLeast(1),
                energyDemand = (energy + learnedAdjustment).coerceIn(1.0, 10.0),
                cognitiveDemand = ((energy - 1.0) / 9.0).coerceIn(0.0, 1.0),
                earliestStartMillis = startAtMillis,
                dueAtMillis = dueAtMillis,
                mandatory = isDailyRequired,
                completed = isDone || isMorningRoutine
            )
        }
        val weightedDemand = (
            profile.difficulty * 1.2 +
                profile.concentrationDemand * 1.1 +
                profile.executiveDemand +
                profile.memoryDemand * 0.7 +
                profile.creativeDemand * 0.8 +
                profile.socialDemand * 0.6 +
                profile.physicalDemand +
                profile.emotionalDemand * 0.8
            ) / (7.2 * 4.0)
        val cognitive = (
            profile.concentrationDemand + profile.executiveDemand +
                profile.memoryDemand + profile.creativeDemand
            ) / 16.0
        val learnedAdjustment = learnedDelta?.boundedDemandAdjustment().orZero()
        return TaskDemand(
            id = id,
            durationMinutes = profile.preferredBlockMinutes.coerceAtLeast(1),
            energyDemand = (
                1.0 + weightedDemand.coerceIn(0.0, 1.0) * 9.0 + learnedAdjustment
            ).coerceIn(1.0, 10.0),
            cognitiveDemand = cognitive.coerceIn(0.0, 1.0),
            earliestStartMillis = startAtMillis,
            dueAtMillis = dueAtMillis,
            deadlineIsHard = false,
            fixedStartMillis = startAtMillis.takeIf { profile.fixedTime },
            mandatory = isDailyRequired,
            completed = isDone || isMorningRoutine
        )
    }

    private fun safeAdd(value: Long, increment: Long): Long =
        runCatching { Math.addExact(value, increment) }.getOrDefault(Long.MAX_VALUE)

    private data class LearnedEnergyDelta(val meanDelta: Double, val sampleCount: Int) {
        fun boundedDemandAdjustment(): Double {
            val reliability = (sampleCount / 8.0).coerceIn(0.0, 1.0)
            // A recurring 3-point loss raises required-energy demand by at most one point.
            return (-meanDelta / 3.0).coerceIn(-1.5, 1.5) * reliability
        }
    }

    private fun learnedEnergyDeltas(
        observations: List<EnergyObservationEntity>
    ): Map<Int, LearnedEnergyDelta> {
        val eligible = observations.asSequence()
            .filterNot(EnergyObservationEntity::excludedFromLearning)
            .filter { it.confidence >= 0.5f && it.absoluteEnergy != null }
            .filter { it.context == "BEFORE_TASK" || it.context == "AFTER_TASK" }
            .filter { it.taskId != null && it.focusProtocolSessionId != null }
            .toList()
        val deltasByTask = mutableMapOf<Int, MutableList<Double>>()
        eligible.groupBy { it.taskId to it.focusProtocolSessionId }.values.forEach { cycle ->
            val before = cycle.filter { it.context == "BEFORE_TASK" }
                .minByOrNull(EnergyObservationEntity::timestamp)
                ?.absoluteEnergy
            val after = cycle.filter { it.context == "AFTER_TASK" }
                .maxByOrNull(EnergyObservationEntity::timestamp)
                ?.absoluteEnergy
            val taskId = cycle.firstOrNull()?.taskId
            if (before != null && after != null && taskId != null) {
                deltasByTask.getOrPut(taskId, ::mutableListOf) += (after - before).toDouble()
            }
        }
        return deltasByTask.mapValues { (_, values) ->
            val recent = values.takeLast(20)
            LearnedEnergyDelta(recent.average(), recent.size)
        }
    }

    private fun learnedModeEnergyDeltas(
        taskDeltas: Map<Int, LearnedEnergyDelta>,
        profiles: Map<Int, TaskDemandProfileEntity>
    ): Map<String, LearnedEnergyDelta> {
        val weightedByMode = mutableMapOf<String, MutableList<Pair<Double, Int>>>()
        taskDeltas.forEach { (taskId, delta) ->
            val mode = profiles[taskId]?.workMode ?: return@forEach
            weightedByMode.getOrPut(mode, ::mutableListOf) += delta.meanDelta to delta.sampleCount
        }
        return weightedByMode.mapValues { (_, values) ->
            val count = values.sumOf { it.second }
            val mean = if (count == 0) 0.0 else {
                values.sumOf { (value, samples) -> value * samples } / count
            }
            LearnedEnergyDelta(mean, count)
        }
    }

    private fun Double?.orZero(): Double = this ?: 0.0
}
