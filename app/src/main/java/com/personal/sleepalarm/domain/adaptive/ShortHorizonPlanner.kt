package com.personal.sleepalarm.domain.adaptive

import kotlin.math.abs

/**
 * Bounded deterministic lookahead for the next few tasks. It never changes
 * fixed commitments and never places a strict-deadline task after its boundary.
 */
object ShortHorizonPlanner {
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MAX_LOOKAHEAD_CANDIDATES = 8
    private const val MAX_LOOKAHEAD_DEPTH = 5
    private const val SCORE_EPSILON = 1e-9

    fun <ID : Any> plan(
        ranking: AdaptiveRanking<ID>,
        context: PlanningContext
    ): SequencePlan<ID> {
        val availability = AvailabilityCalculator.calculate(
            horizonStartMillis = context.nowMillis,
            horizonEndMillis = context.horizonEndMillis,
            fixedWindows = context.fixedCalendarWindows
        )
        if (!context.isValid() || ranking.tasks.isEmpty() || availability.freeWindows.isEmpty()) {
            return SequencePlan(
                tasks = emptyList(),
                rankingMode = ranking.mode,
                freeWindows = availability.freeWindows,
                totalAdaptiveScore = 0.0
            )
        }

        val planned = if (!ranking.isAdaptive) {
            greedyFallback(ranking.tasks, context, availability.freeWindows)
        } else {
            adaptiveLookahead(ranking.tasks, context, availability.freeWindows)
        }
        return SequencePlan(
            tasks = planned,
            rankingMode = ranking.mode,
            freeWindows = availability.freeWindows,
            totalAdaptiveScore = planned.sumOf { it.rankedTask.score }
        )
    }

    /** Fallback mode is intentionally order-preserving rather than "almost adaptive". */
    private fun <ID : Any> greedyFallback(
        ranked: List<RankedTask<ID>>,
        context: PlanningContext,
        freeWindows: List<TimeWindow>
    ): List<PlannedTask<ID>> {
        val result = mutableListOf<PlannedTask<ID>>()
        var cursor = context.nowMillis
        ranked.forEach { task ->
            if (result.size >= context.maxSequenceTasks) return@forEach
            val placement = placement(task, cursor, freeWindows) ?: return@forEach
            result += PlannedTask(task, placement.startMillis, placement.endMillis)
            cursor = afterBuffer(placement.endMillis, context.bufferMinutes)
        }
        return result
    }

    private fun <ID : Any> adaptiveLookahead(
        ranked: List<RankedTask<ID>>,
        context: PlanningContext,
        freeWindows: List<TimeWindow>
    ): List<PlannedTask<ID>> {
        val candidates = ranked.take(minOf(context.candidateLimit, MAX_LOOKAHEAD_CANDIDATES))
        val depthLimit = minOf(context.maxSequenceTasks, MAX_LOOKAHEAD_DEPTH)
        var best: List<PlannedTask<ID>> = emptyList()

        fun search(
            current: List<PlannedTask<ID>>,
            remaining: List<RankedTask<ID>>,
            cursor: Long
        ) {
            if (isBetter(current, best, candidates)) best = current
            if (current.size >= depthLimit || remaining.isEmpty()) return

            remaining.forEachIndexed { index, task ->
                val slot = placement(task, cursor, freeWindows) ?: return@forEachIndexed
                val next = current + PlannedTask(task, slot.startMillis, slot.endMillis)
                val nextRemaining = remaining.toMutableList().also { it.removeAt(index) }
                search(
                    current = next,
                    remaining = nextRemaining,
                    cursor = afterBuffer(slot.endMillis, context.bufferMinutes)
                )
            }
        }

        search(emptyList(), candidates, context.nowMillis)
        return best
    }

    private fun <ID : Any> placement(
        task: RankedTask<ID>,
        cursor: Long,
        freeWindows: List<TimeWindow>
    ): TimeWindow? = AvailabilityCalculator.findEarliestPlacement(
        freeWindows = freeWindows,
        durationMinutes = task.demand.durationMinutes,
        notBeforeMillis = cursor,
        earliestStartMillis = task.demand.earliestStartMillis,
        fixedStartMillis = task.demand.fixedStartMillis,
        finishByMillis = task.demand.dueAtMillis.takeIf { task.demand.deadlineIsHard }
    )

    private fun afterBuffer(endMillis: Long, bufferMinutes: Int): Long =
        runCatching { Math.addExact(endMillis, bufferMinutes.toLong() * MILLIS_PER_MINUTE) }
            .getOrElse { Long.MAX_VALUE }

    private fun <ID : Any> isBetter(
        candidate: List<PlannedTask<ID>>,
        incumbent: List<PlannedTask<ID>>,
        rankedCandidates: List<RankedTask<ID>>
    ): Boolean {
        if (candidate === incumbent) return false
        val candidateIds = candidate.mapTo(linkedSetOf()) { it.rankedTask.demand.id }
        val incumbentIds = incumbent.mapTo(linkedSetOf()) { it.rankedTask.demand.id }

        val critical = rankedCandidates.filter { it.hardConstraintTier <= 1 }
        compareCoverage(candidateIds, incumbentIds, critical)?.let { return it }
        val deadlines = rankedCandidates.filter { it.hardConstraintTier <= 2 }
        compareCoverage(candidateIds, incumbentIds, deadlines)?.let { return it }
        val mandatory = rankedCandidates.filter { it.demand.mandatory }
        compareCoverage(candidateIds, incumbentIds, mandatory)?.let { return it }

        if (candidate.isNotEmpty() != incumbent.isNotEmpty()) return candidate.isNotEmpty()
        val candidateScore = candidate.sumOf { it.rankedTask.score }
        val incumbentScore = incumbent.sumOf { it.rankedTask.score }
        if (abs(candidateScore - incumbentScore) > SCORE_EPSILON) {
            return candidateScore > incumbentScore
        }
        if (candidate.size != incumbent.size) return candidate.size > incumbent.size

        val candidateFinish = candidate.lastOrNull()?.endMillis ?: Long.MAX_VALUE
        val incumbentFinish = incumbent.lastOrNull()?.endMillis ?: Long.MAX_VALUE
        if (candidateFinish != incumbentFinish) return candidateFinish < incumbentFinish

        val candidateOrder = candidate.map { it.rankedTask.fallbackIndex }
        val incumbentOrder = incumbent.map { it.rankedTask.fallbackIndex }
        for (index in 0 until minOf(candidateOrder.size, incumbentOrder.size)) {
            if (candidateOrder[index] != incumbentOrder[index]) {
                return candidateOrder[index] < incumbentOrder[index]
            }
        }
        return false
    }

    /** Returns true/false for a decisive comparison, null for a tie. */
    private fun <ID : Any> compareCoverage(
        candidateIds: Set<ID>,
        incumbentIds: Set<ID>,
        protectedTasks: List<RankedTask<ID>>
    ): Boolean? {
        val candidateCount = protectedTasks.count { it.demand.id in candidateIds }
        val incumbentCount = protectedTasks.count { it.demand.id in incumbentIds }
        if (candidateCount != incumbentCount) return candidateCount > incumbentCount
        protectedTasks.forEach { task ->
            val candidateHas = task.demand.id in candidateIds
            val incumbentHas = task.demand.id in incumbentIds
            if (candidateHas != incumbentHas) return candidateHas
        }
        return null
    }
}
