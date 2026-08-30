package com.personal.sleepalarm.domain.automation

import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepAutomationStateTest {

    @Test
    fun `no active sleep lets explicit focus proceed`() {
        assertEquals(
            ExplicitAwakeSleepConflict.PROCEED,
            null.conflictForExplicitAwakeAction()
        )
        assertEquals(
            ExplicitAwakeSleepConflict.PROCEED,
            session(source = AUTOMATION_ARMED_SOURCE, isActive = false)
                .conflictForExplicitAwakeAction()
        )
    }

    @Test
    fun `automatic armed and detected sleep yield to explicit focus`() {
        listOf(AUTOMATION_ARMED_SOURCE, AUTOMATION_DETECTED_SOURCE).forEach { source ->
            assertEquals(
                ExplicitAwakeSleepConflict.PAUSE_AUTOMATIC_SLEEP,
                session(source = source).conflictForExplicitAwakeAction()
            )
        }
    }

    @Test
    fun `pausing detection restores immutable safety wake`() {
        val safetyWake = 30_000L
        val detected = session(source = AUTOMATION_DETECTED_SOURCE).copy(
            estimatedWakeTime = 9_000L,
            automationSafetyWakeTime = safetyWake,
            detectedSleepOnsetTime = 7_000L,
            detectedOnsetLatencyMinutes = 42,
            detectedOnsetConfidencePercent = 91,
            detectedOnsetUncertaintyMinutes = 8
        )

        val paused = detected.pauseAutomaticDetectionForFocus()

        assertEquals(safetyWake, paused.estimatedWakeTime)
        assertEquals(safetyWake, paused.automationSafetyWakeTime)
        assertEquals(AUTOMATION_FOCUS_PAUSED_SOURCE, paused.detectedOnsetSource)
        assertEquals(null, paused.detectedSleepOnsetTime)
        assertEquals(true, paused.isActive)

        val resumed = paused.resumeAutomaticDetectionAfterFocus()
        assertEquals(safetyWake, resumed.estimatedWakeTime)
        assertEquals(AUTOMATION_ARMED_SOURCE, resumed.detectedOnsetSource)
    }

    @Test
    fun `manual sleep blocks explicit focus until user ends it`() {
        assertEquals(
            ExplicitAwakeSleepConflict.BLOCKED_BY_MANUAL_SLEEP,
            session(source = null).conflictForExplicitAwakeAction()
        )
    }

    private fun session(source: String?, isActive: Boolean = true) = SleepSessionEntity(
        bedTimePlanned = 1_000L,
        sleepOnsetLatencyMinutes = 20,
        estimatedSleepStartTime = 2_000L,
        cycleLengthMinutes = 90,
        cyclesPlanned = 5,
        estimatedWakeTime = 3_000L,
        cuesEnabled = false,
        cueVolumePercent = 0,
        cuesScheduledCount = 0,
        isActive = isActive,
        detectedOnsetSource = source
    )
}
