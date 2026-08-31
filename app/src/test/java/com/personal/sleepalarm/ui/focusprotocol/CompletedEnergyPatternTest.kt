package com.personal.sleepalarm.ui.focusprotocol

import com.personal.sleepalarm.data.db.entity.FocusProtocolSessionEntity
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.domain.model.FocusProtocolPhase
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompletedEnergyPatternTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun `groups completed pairs by start hour and keeps before and after separate`() {
        val blocks = listOf(
            block(hour = 9, before = 4, after = 6),
            block(hour = 9, before = 6, after = 5),
            block(hour = 14, before = 8, after = null)
        )

        val points = buildCompletedEnergyPattern(blocks, zone)

        assertEquals(2, points.size)
        assertEquals(9, points[0].hour)
        assertEquals(5f, points[0].averageBefore)
        assertEquals(5.5f, points[0].averageAfter)
        assertEquals(2, points[0].sampleCount)
        assertEquals(14, points[1].hour)
        assertEquals(8f, points[1].averageBefore)
        assertNull(points[1].averageAfter)
    }

    @Test
    fun `ignores cancelled and unfinished sessions even if supplied`() {
        val complete = block(hour = 10, before = 5, after = 4)
        val cancelled = block(hour = 10, before = 1, after = null).copy(
            phase = FocusProtocolPhase.CANCELLED
        )
        val review = block(hour = 10, before = 9, after = null).copy(
            phase = FocusProtocolPhase.REVIEW,
            completedAt = null
        )

        val point = buildCompletedEnergyPattern(listOf(complete, cancelled, review), zone).single()

        assertEquals(5f, point.averageBefore)
        assertEquals(4f, point.averageAfter)
        assertEquals(1, point.sampleCount)
    }

    private fun block(hour: Int, before: Int, after: Int?): FocusProtocolSessionEntity {
        val startedAt = LocalDateTime.of(2026, 8, 30, hour, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        return FocusProtocolSessionEntity(
            activityType = FocusActivityType.WORK,
            itemId = 1,
            itemName = "Task",
            outcome = "Result",
            phase = FocusProtocolPhase.COMPLETE,
            createdAt = startedAt,
            phaseStartedAt = startedAt,
            resetDurationMinutes = 5,
            focusDurationMinutes = 25,
            recoveryDurationMinutes = 5,
            energyBefore = before,
            energyAfter = after,
            completedAt = startedAt + 30 * 60_000L
        )
    }
}
