package com.personal.sleepalarm.domain.adaptive

import kotlin.math.abs

/**
 * Reorders only after hard tiers have been established. If inputs are weak or
 * malformed, the supplied canonical order is returned unchanged.
 */
object AdaptiveTaskRanker {
    fun <ID : Any> rank(
        tasks: List<TaskDemand<ID>>,
        fallbackOrder: List<ID>,
        context: PlanningContext
    ): AdaptiveRanking<ID> {
        val byId = LinkedHashMap<ID, TaskDemand<ID>>()
        var hasDuplicateTaskIds = false
        tasks.forEach { task ->
            if (byId.putIfAbsent(task.id, task) != null) hasDuplicateTaskIds = true
        }
        val uniqueFallback = fallbackOrder.distinct()
        val hasDuplicateFallbackIds = uniqueFallback.size != fallbackOrder.size
        val canonicalIds = buildList {
            uniqueFallback.forEach { id -> if (id in byId) add(id) }
            byId.keys.forEach { id -> if (id !in this) add(id) }
        }
        val eligible = canonicalIds.mapNotNull(byId::get)
            .filterNot { it.completed || it.blocked }
        val fallbackIndex = eligible.mapIndexed { index, task -> task.id to index }.toMap()

        val invalidInput = hasDuplicateTaskIds || hasDuplicateFallbackIds ||
            eligible.any { !it.isNumericallyValid() }
        if (invalidInput) {
            return fallback(
                eligible,
                fallbackIndex,
                RankingMode.FALLBACK_INVALID_INPUT,
                "duplicate ids or invalid demand values"
            )
        }
        if (!context.isValid()) {
            return fallback(
                eligible,
                fallbackIndex,
                RankingMode.FALLBACK_INVALID_CONTEXT,
                "invalid planning horizon or planner limits"
            )
        }
        val state = context.personalState ?: return fallback(
            eligible,
            fallbackIndex,
            RankingMode.FALLBACK_NO_STATE,
            "no personal state estimate"
        )
        if (state.confidence.value < context.minimumAdaptiveConfidence) {
            return fallback(
                eligible,
                fallbackIndex,
                RankingMode.FALLBACK_LOW_CONFIDENCE,
                "state confidence ${state.confidence.value} is below ${context.minimumAdaptiveConfidence}"
            )
        }

        val availability = AvailabilityCalculator.calculate(
            horizonStartMillis = context.nowMillis,
            horizonEndMillis = context.horizonEndMillis,
            fixedWindows = context.fixedCalendarWindows
        )
        val ranked = eligible.map { demand ->
            score(
                demand = demand,
                fallbackIndex = fallbackIndex.getValue(demand.id),
                fallbackCount = eligible.size,
                state = state,
                context = context,
                freeWindows = availability.freeWindows
            )
        }.sortedWith(
            compareBy<RankedTask<ID>> { it.hardConstraintTier }
                .thenBy { protectedTime(it) }
                .thenByDescending { it.score }
                .thenBy { it.fallbackIndex }
        )

        return AdaptiveRanking(tasks = ranked, mode = RankingMode.ADAPTIVE)
    }

    private fun <ID : Any> score(
        demand: TaskDemand<ID>,
        fallbackIndex: Int,
        fallbackCount: Int,
        state: PersonalState,
        context: PlanningContext,
        freeWindows: List<TimeWindow>
    ): RankedTask<ID> {
        val strictFinish = demand.dueAtMillis.takeIf { demand.deadlineIsHard }
        val placement = AvailabilityCalculator.findEarliestPlacement(
            freeWindows = freeWindows,
            durationMinutes = demand.durationMinutes,
            notBeforeMillis = context.nowMillis,
            earliestStartMillis = demand.earliestStartMillis,
            fixedStartMillis = demand.fixedStartMillis,
            finishByMillis = strictFinish
        )
        val tier = hardTier(demand, placement, context)
        val explanations = mutableListOf<ScoreExplanation>()

        val baselineRatio = if (fallbackCount <= 1) {
            1.0
        } else {
            1.0 - fallbackIndex.toDouble() / (fallbackCount - 1).toDouble()
        }
        explanations += ScoreExplanation(
            factor = ScoreFactor.FALLBACK_PRIORITY,
            contribution = baselineRatio * 0.25,
            evidence = "canonical rank $fallbackIndex of $fallbackCount"
        )

        val energyFit = 1.0 - abs(state.estimatedEnergy - demand.energyDemand) / 9.0
        explanations += ScoreExplanation(
            factor = ScoreFactor.ENERGY_FIT,
            contribution = (energyFit.coerceIn(0.0, 1.0) - 0.5) * 0.50,
            evidence = "estimated ${state.estimatedEnergy}, demand ${demand.energyDemand}"
        )

        val cognitiveFit = 1.0 - abs(state.capacity - demand.cognitiveDemand)
        explanations += ScoreExplanation(
            factor = ScoreFactor.COGNITIVE_FIT,
            contribution = (cognitiveFit.coerceIn(0.0, 1.0) - 0.5) * 0.30,
            evidence = "capacity ${state.capacity}, cognitive demand ${demand.cognitiveDemand}"
        )
        explanations += ScoreExplanation(
            factor = ScoreFactor.FATIGUE_COST,
            contribution = -state.fatigue * demand.cognitiveDemand * 0.18,
            evidence = "fatigue ${state.fatigue}"
        )

        demand.dueAtMillis?.let { due ->
            val horizon = (context.horizonEndMillis - context.nowMillis).coerceAtLeast(1L)
            val remaining = (due - context.nowMillis).coerceAtLeast(0L)
            val urgency = if (due <= context.nowMillis) {
                1.0
            } else {
                1.0 - (remaining.toDouble() / horizon.toDouble()).coerceIn(0.0, 1.0)
            }
            explanations += ScoreExplanation(
                factor = ScoreFactor.DEADLINE_URGENCY,
                contribution = urgency * 0.35,
                evidence = "due at $due"
            )
        }

        if (demand.mandatory) {
            explanations += ScoreExplanation(
                factor = ScoreFactor.MANDATORY_TASK,
                contribution = 0.12,
                evidence = "mandatory within the planning policy"
            )
        }

        explanations += ScoreExplanation(
            factor = ScoreFactor.DURATION_FIT,
            contribution = if (placement != null) 0.08 else -0.30,
            evidence = if (placement != null) "fits an available window" else "does not fit the horizon"
        )
        placement?.let { slot ->
            val horizon = (context.horizonEndMillis - context.nowMillis).coerceAtLeast(1L)
            val delayRatio = ((slot.startMillis - context.nowMillis).coerceAtLeast(0L).toDouble() /
                horizon.toDouble()).coerceIn(0.0, 1.0)
            explanations += ScoreExplanation(
                factor = ScoreFactor.START_DELAY,
                contribution = -delayRatio * 0.08,
                evidence = "earliest placement ${slot.startMillis}"
            )
        }
        explanations += ScoreExplanation(
            factor = ScoreFactor.HARD_CONSTRAINT,
            contribution = 0.0,
            evidence = "protected tier $tier"
        )

        return RankedTask(
            demand = demand,
            score = explanations.sumOf(ScoreExplanation::contribution),
            hardConstraintTier = tier,
            fallbackIndex = fallbackIndex,
            explanations = explanations
        )
    }

    private fun <ID : Any> hardTier(
        demand: TaskDemand<ID>,
        placement: TimeWindow?,
        context: PlanningContext
    ): Int {
        val due = demand.dueAtMillis
        val fixed = demand.fixedStartMillis
        if ((due != null && due <= context.nowMillis) ||
            (fixed != null && fixed < context.nowMillis)
        ) {
            return 0
        }
        if (demand.deadlineIsHard && due != null && due <= context.horizonEndMillis && placement == null) {
            return 1
        }
        if ((due != null && due <= context.horizonEndMillis) ||
            (fixed != null && fixed < context.horizonEndMillis)
        ) {
            return 2
        }
        if (demand.mandatory) return 3
        if ((demand.earliestStartMillis ?: Long.MIN_VALUE) >= context.horizonEndMillis ||
            (fixed ?: Long.MIN_VALUE) >= context.horizonEndMillis
        ) {
            return 5
        }
        return if (placement != null) 4 else 6
    }

    private fun <ID : Any> protectedTime(task: RankedTask<ID>): Long =
        if (task.hardConstraintTier <= 2) {
            minOf(
                task.demand.dueAtMillis ?: Long.MAX_VALUE,
                task.demand.fixedStartMillis ?: Long.MAX_VALUE
            )
        } else {
            Long.MAX_VALUE
        }

    private fun <ID : Any> fallback(
        tasks: List<TaskDemand<ID>>,
        fallbackIndex: Map<ID, Int>,
        mode: RankingMode,
        reason: String
    ): AdaptiveRanking<ID> = AdaptiveRanking(
        tasks = tasks.map { demand ->
            val index = fallbackIndex.getValue(demand.id)
            val contribution = -index.toDouble()
            RankedTask(
                demand = demand,
                score = contribution,
                hardConstraintTier = 0,
                fallbackIndex = index,
                explanations = listOf(
                    ScoreExplanation(
                        factor = ScoreFactor.FALLBACK_PRIORITY,
                        contribution = contribution,
                        evidence = "canonical order retained: $reason"
                    )
                )
            )
        },
        mode = mode,
        fallbackReason = reason
    )
}
