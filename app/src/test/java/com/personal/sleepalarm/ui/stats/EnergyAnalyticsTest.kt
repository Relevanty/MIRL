package com.personal.sleepalarm.ui.stats

import com.personal.sleepalarm.data.db.entity.DailyCheckInEntity
import com.personal.sleepalarm.data.db.entity.EnergyObservationEntity
import com.personal.sleepalarm.data.db.entity.FocusProtocolSessionEntity
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.data.db.entity.TaskDemandProfileEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.CueType
import com.personal.sleepalarm.domain.model.DismissType
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.domain.model.FocusProtocolPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class EnergyAnalyticsTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun `readings exclude invalid and learning-excluded rows and deduplicate mirrored check-in`() {
        val morning = time("2026-08-01T08:00:00Z")
        val daytime = time("2026-08-01T13:00:00Z")
        val result = aggregateEnergyAnalytics(
            checkIns = listOf(
                checkIn(morning, 6),
                checkIn(daytime, 8),
                checkIn(daytime + 1_000, 2, excluded = true)
            ),
            observations = listOf(
                observation(daytime + 30_000, 8, context = "AD_HOC"),
                observation(daytime + 60_000, 7, context = "AD_HOC", quality = "INVALID"),
                observation(daytime + 90_000, 4, context = "AD_HOC", excluded = true)
            ),
            profiles = emptyList(),
            tasks = emptyList(),
            activities = emptyList(),
            focusSessions = emptyList(),
            sleepSessions = emptyList(),
            periodStartMillis = morning - 1,
            snapshotTimeMillis = daytime + 100_000,
            zoneId = utc
        )

        assertEquals(2, result.energySampleCount)
        assertEquals(listOf("MORNING", "DAY"), result.averageByTimeOfDay.map { it.key })
        assertEquals(6f, result.averageByTimeOfDay.first().average)
        assertFalse(result.hasEnoughEnergyData)
    }

    @Test
    fun `completed linked pair is grouped by work mode and domain while cancelled focus is excluded`() {
        val start = time("2026-08-02T09:00:00Z")
        val completed = focusSession(
            id = 20,
            phase = FocusProtocolPhase.COMPLETE,
            itemId = -1,
            createdAt = start,
            completedAt = start + 60_000
        )
        val cancelled = focusSession(
            id = 21,
            phase = FocusProtocolPhase.CANCELLED,
            itemId = -1,
            createdAt = start + 120_000,
            completedAt = start + 180_000,
            cancelReason = "USER"
        )
        val result = aggregateEnergyAnalytics(
            checkIns = emptyList(),
            observations = listOf(
                observation(start, 7, "BEFORE_TASK", focusId = 20, taskId = 1),
                observation(start + 60_000, 4, "AFTER_TASK", focusId = 20, taskId = 1),
                observation(start + 120_000, 8, "BEFORE_TASK", focusId = 21, taskId = 1),
                observation(start + 180_000, 2, "AFTER_TASK", focusId = 21, taskId = 1)
            ),
            profiles = listOf(
                TaskDemandProfileEntity(taskId = 1, domain = "STUDY", workMode = "DEEP_WORK")
            ),
            tasks = listOf(TaskEntity(id = 1, title = "Read")),
            activities = emptyList(),
            focusSessions = listOf(completed, cancelled),
            sleepSessions = emptyList(),
            periodStartMillis = start - 1,
            snapshotTimeMillis = start + 300_000,
            zoneId = utc
        )

        assertEquals(1, result.episodeSampleCount)
        assertEquals("DEEP_WORK", result.deltaByWorkMode.single().key)
        assertEquals("STUDY", result.deltaByDomain.single().key)
        assertEquals(-3f, result.deltaByWorkMode.single().averageDelta)
    }

    @Test
    fun `hours awake use latest valid actual wake and ignore cancelled sleep`() {
        val wake = time("2026-08-03T06:00:00Z")
        val result = aggregateEnergyAnalytics(
            checkIns = listOf(
                checkIn(wake + 60 * 60_000L, 7),
                checkIn(wake + 4 * 60 * 60_000L, 5)
            ),
            observations = emptyList(),
            profiles = emptyList(),
            tasks = emptyList(),
            activities = emptyList(),
            focusSessions = emptyList(),
            sleepSessions = listOf(
                sleepSession(id = 1, wake = wake, dismissType = DismissType.NORMAL),
                sleepSession(id = 2, wake = wake + 3 * 60 * 60_000L, dismissType = DismissType.CANCELLED)
            ),
            periodStartMillis = wake - 1,
            snapshotTimeMillis = wake + 5 * 60 * 60_000L,
            zoneId = utc
        )

        assertEquals(listOf("H0_3", "H3_6"), result.averageByHoursAwake.map { it.key })
        assertEquals(listOf(7f, 5f), result.averageByHoursAwake.map { it.average })
        assertTrue(result.averageByHoursAwake.all { it.sampleCount == 1 })
    }

    private fun checkIn(timestamp: Long, energy: Int, excluded: Boolean = false) =
        DailyCheckInEntity(
            localDate = "2026-08-01",
            timestamp = timestamp,
            zoneId = "UTC",
            energy = energy,
            excludedFromLearning = excluded
        )

    private fun observation(
        timestamp: Long,
        energy: Int,
        context: String,
        focusId: Int? = null,
        taskId: Int? = null,
        quality: String = "EXACT",
        excluded: Boolean = false
    ) = EnergyObservationEntity(
        timestamp = timestamp,
        absoluteEnergy = energy,
        context = context,
        taskId = taskId,
        focusProtocolSessionId = focusId,
        quality = quality,
        excludedFromLearning = excluded
    )

    private fun focusSession(
        id: Int,
        phase: FocusProtocolPhase,
        itemId: Int,
        createdAt: Long,
        completedAt: Long?,
        cancelReason: String? = null
    ) = FocusProtocolSessionEntity(
        id = id,
        activityType = FocusActivityType.STUDY,
        itemId = itemId,
        itemName = "Read",
        outcome = "Done",
        phase = phase,
        createdAt = createdAt,
        phaseStartedAt = createdAt,
        resetDurationMinutes = 1,
        focusDurationMinutes = 25,
        recoveryDurationMinutes = 5,
        energyBefore = 7,
        energyAfter = 4,
        completedAt = completedAt,
        cancelReason = cancelReason
    )

    @Suppress("DEPRECATION")
    private fun sleepSession(id: Int, wake: Long, dismissType: DismissType) = SleepSessionEntity(
        id = id,
        bedTimePlanned = wake - 9 * 60 * 60_000L,
        sleepOnsetLatencyMinutes = 15,
        estimatedSleepStartTime = wake - 8 * 60 * 60_000L,
        cycleLengthMinutes = 90,
        cyclesPlanned = 5,
        estimatedWakeTime = wake,
        actualWakeTime = wake,
        dismissType = dismissType,
        cuesEnabled = false,
        cueType = CueType.BEEP,
        cueVolumePercent = 50,
        cuesScheduledCount = 0,
        isActive = false
    )

    private fun time(value: String): Long = Instant.parse(value).toEpochMilli()
}
