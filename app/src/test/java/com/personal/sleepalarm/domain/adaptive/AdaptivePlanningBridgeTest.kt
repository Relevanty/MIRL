package com.personal.sleepalarm.domain.adaptive

import com.personal.sleepalarm.data.db.entity.DailyCheckInEntity
import com.personal.sleepalarm.data.db.entity.EnergyObservationEntity
import com.personal.sleepalarm.data.db.entity.TaskDemandProfileEntity
import com.personal.sleepalarm.data.db.entity.TaskDependencyEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePlanningBridgeTest {
    private val now = 10_000_000L

    @Test
    fun unfinishedPrerequisiteKeepsDependentTaskOutOfRecommendations() {
        val prerequisite = TaskEntity(id = 1, title = "Prepare data", matrixQuadrant = 2)
        val dependent = TaskEntity(id = 2, title = "Write report", matrixQuadrant = 1)

        val result = AdaptivePlanningBridge.build(
            baseInput(
                tasks = listOf(dependent, prerequisite),
                dependencies = listOf(TaskDependencyEntity(taskId = 2, dependsOnTaskId = 1))
            )
        )

        assertEquals(listOf(1), result.orderedTasks.map(TaskEntity::id))
    }

    @Test
    fun threeMeasuredEnergyDropsRaiseLearnedTaskDemandWithinBounds() {
        val task = TaskEntity(id = 7, title = "Difficult task", energyLevel = "MEDIUM")
        val baseline = AdaptivePlanningBridge.build(baseInput(tasks = listOf(task)))
            .ranking.tasks.single().demand.energyDemand
        val observations = (1..3).flatMap { sessionId ->
            listOf(
                observation(sessionId, "BEFORE_TASK", 8, sessionId * 1_000L),
                observation(sessionId, "AFTER_TASK", 4, sessionId * 1_000L + 500L)
            )
        }

        val learned = AdaptivePlanningBridge.build(
            baseInput(tasks = listOf(task), energyObservations = observations)
        ).ranking.tasks.single().demand.energyDemand

        assertTrue(learned > baseline)
        assertTrue(learned <= baseline + 1.5)
    }

    private fun baseInput(
        tasks: List<TaskEntity>,
        dependencies: List<TaskDependencyEntity> = emptyList(),
        energyObservations: List<EnergyObservationEntity> = emptyList()
    ) = AdaptivePlanningInput(
        nowMillis = now,
        tasks = tasks,
        dependencies = dependencies,
        latestCheckIn = DailyCheckInEntity(
            localDate = "1970-01-01",
            timestamp = now - 2L * 60L * 60_000L,
            zoneId = "UTC",
            energy = 6,
            mood = 3
        ),
        energyObservations = energyObservations
    )

    private fun observation(
        focusSessionId: Int,
        context: String,
        energy: Int,
        timestamp: Long
    ) = EnergyObservationEntity(
        timestamp = timestamp,
        absoluteEnergy = energy,
        context = context,
        taskId = 7,
        focusProtocolSessionId = focusSessionId,
        source = "TEST"
    )
}
