package com.personal.sleepalarm.domain.adaptive

import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.NextActionRanker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTaskRankerTest {
    @Test
    fun lowConfidenceReturnsCanonicalOrderExactly() {
        val tasks = listOf(
            TaskDemand("a", durationMinutes = 25, energyDemand = 2.0),
            TaskDemand("b", durationMinutes = 25, energyDemand = 9.0),
            TaskDemand("c", durationMinutes = 25, mandatory = true)
        )
        val result = AdaptiveTaskRanker.rank(
            tasks = tasks,
            fallbackOrder = listOf("c", "a", "b"),
            context = context(state = state(energy = 9.0, confidence = 0.20))
        )

        assertEquals(RankingMode.FALLBACK_LOW_CONFIDENCE, result.mode)
        assertEquals(listOf("c", "a", "b"), result.tasks.map { it.demand.id })
    }

    @Test
    fun taskEntityBridgeFallsBackToExistingNextActionRanker() {
        val now = 10_000_000L
        val tasks = listOf(
            TaskEntity(id = 1, title = "quadrant", matrixQuadrant = 1, energyLevel = "HIGH"),
            TaskEntity(
                id = 2,
                title = "overdue",
                matrixQuadrant = 3,
                dueAtMillis = now - 1,
                energyLevel = "LOW"
            ),
            TaskEntity(id = 3, title = "done", isDone = true)
        )
        val expected = NextActionRanker.rank(tasks, now)
        val actual = TaskEntityAdaptiveRanker.rank(
            tasks = tasks,
            context = PlanningContext(
                nowMillis = now,
                horizonEndMillis = now + minutes(240),
                personalState = null
            )
        )

        assertEquals(RankingMode.FALLBACK_NO_STATE, actual.details.mode)
        assertEquals(expected.map(TaskEntity::id), actual.orderedTasks.map(TaskEntity::id))
    }

    @Test
    fun protectedDeadlineTierDominatesEnergyFit() {
        val now = 2_000_000L
        val tasks = listOf(
            TaskDemand("perfect-fit", durationMinutes = 30, energyDemand = 9.0, cognitiveDemand = 0.9),
            TaskDemand(
                "deadline",
                durationMinutes = 30,
                energyDemand = 2.0,
                cognitiveDemand = 0.2,
                dueAtMillis = now + minutes(90)
            )
        )
        val result = AdaptiveTaskRanker.rank(
            tasks,
            fallbackOrder = listOf("perfect-fit", "deadline"),
            context = context(now = now, state = state(energy = 9.0))
        )

        assertEquals(RankingMode.ADAPTIVE, result.mode)
        assertEquals("deadline", result.tasks.first().demand.id)
        assertTrue(result.tasks[0].hardConstraintTier < result.tasks[1].hardConstraintTier)
    }

    @Test
    fun highCapacityCanReorderTasksWithinTheSameProtectedTier() {
        val result = AdaptiveTaskRanker.rank(
            tasks = listOf(
                TaskDemand("low", 25, energyDemand = 2.0, cognitiveDemand = 0.2),
                TaskDemand("high", 25, energyDemand = 9.0, cognitiveDemand = 0.9)
            ),
            fallbackOrder = listOf("low", "high"),
            context = context(state = state(energy = 9.0, capacity = 0.9, fatigue = 0.1))
        )

        assertEquals(listOf("high", "low"), result.tasks.map { it.demand.id })
        assertEquals(result.tasks[0].hardConstraintTier, result.tasks[1].hardConstraintTier)
    }

    @Test
    fun mandatoryTaskIsProtectedFromOrdinaryAdaptiveReordering() {
        val result = AdaptiveTaskRanker.rank(
            tasks = listOf(
                TaskDemand("ordinary-fit", 25, energyDemand = 9.0, cognitiveDemand = 0.9),
                TaskDemand("mandatory-mismatch", 25, energyDemand = 1.0, mandatory = true)
            ),
            fallbackOrder = listOf("ordinary-fit", "mandatory-mismatch"),
            context = context(state = state(energy = 9.0))
        )

        assertEquals("mandatory-mismatch", result.tasks.first().demand.id)
        assertEquals(3, result.tasks.first().hardConstraintTier)
    }

    @Test
    fun completedAndBlockedTasksNeverEscapeThroughFallback() {
        val result = AdaptiveTaskRanker.rank(
            tasks = listOf(
                TaskDemand("live", 25),
                TaskDemand("done", 25, completed = true),
                TaskDemand("blocked", 25, blocked = true)
            ),
            fallbackOrder = listOf("done", "blocked", "live"),
            context = context(state = null)
        )

        assertEquals(listOf("live"), result.tasks.map { it.demand.id })
    }

    @Test
    fun invalidDemandFailsBackWithoutProducingNonFiniteScores() {
        val result = AdaptiveTaskRanker.rank(
            tasks = listOf(
                TaskDemand("valid", 25),
                TaskDemand("invalid", 0, energyDemand = Double.NaN)
            ),
            fallbackOrder = listOf("invalid", "valid"),
            context = context(state = state())
        )

        assertEquals(RankingMode.FALLBACK_INVALID_INPUT, result.mode)
        assertEquals(listOf("invalid", "valid"), result.tasks.map { it.demand.id })
        assertTrue(result.tasks.all { it.score.isFinite() })
    }

    @Test
    fun adaptiveScoresAreDeterministicAndEqualTheirExplanations() {
        val tasks = (1..6).map { id ->
            TaskDemand(
                id = id,
                durationMinutes = 10 + id,
                energyDemand = 1.0 + id,
                cognitiveDemand = id / 10.0
            )
        }
        val planningContext = context(state = state())
        val first = AdaptiveTaskRanker.rank(tasks, tasks.map { it.id }, planningContext)
        val second = AdaptiveTaskRanker.rank(tasks, tasks.map { it.id }, planningContext)

        assertEquals(first, second)
        first.tasks.forEach { ranked ->
            assertEquals(
                ranked.score,
                ranked.explanations.sumOf(ScoreExplanation::contribution),
                1e-9
            )
        }
    }

    private fun context(
        now: Long = 1_000_000L,
        state: PersonalState?
    ) = PlanningContext(
        nowMillis = now,
        horizonEndMillis = now + minutes(6 * 60),
        personalState = state,
        bufferMinutes = 5
    )

    private fun state(
        energy: Double = 7.0,
        capacity: Double = 0.7,
        fatigue: Double = 0.3,
        confidence: Double = 0.90
    ) = PersonalState(
        capacity = capacity,
        estimatedEnergy = energy,
        fatigue = fatigue,
        minutesSinceWake = 120,
        sleepMinutes = 480,
        currentEnergy = energy.toInt().coerceIn(1, 10),
        confidence = EstimateConfidence.of(
            confidence,
            observedSignals = setOf(
                StateSignal.TIME_SINCE_WAKE,
                StateSignal.SLEEP_DURATION,
                StateSignal.CURRENT_ENERGY,
                StateSignal.RECENT_LOAD
            )
        ),
        explanations = emptyList()
    )

    private fun minutes(value: Int): Long = value * 60_000L
}
