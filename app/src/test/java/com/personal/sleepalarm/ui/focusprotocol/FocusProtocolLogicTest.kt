package com.personal.sleepalarm.ui.focusprotocol

import com.personal.sleepalarm.domain.model.FocusProtocolPhase
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusProtocolLogicTest {
    private val zone = ZoneId.of("Europe/Moscow")

    @Test
    fun `next bedtime is calculated from preferred wake and sleep need`() {
        val now = LocalDateTime.of(2026, 8, 24, 20, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val bedtime = FocusProtocolViewModel.calculateNextBedtime(
            nowMillis = now,
            wakeHour = 7,
            wakeMinute = 0,
            sleepMinutes = 7 * 60 + 45,
            zoneId = zone
        )

        assertEquals(
            LocalDateTime.of(2026, 8, 24, 23, 15)
                .atZone(zone)
                .toInstant()
                .toEpochMilli(),
            bedtime
        )
    }

    @Test
    fun `bedtime remains in the past when user is already inside sleep window`() {
        val now = LocalDateTime.of(2026, 8, 24, 1, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val bedtime = FocusProtocolViewModel.calculateNextBedtime(
            nowMillis = now,
            wakeHour = 7,
            wakeMinute = 0,
            sleepMinutes = 7 * 60 + 45,
            zoneId = zone
        )

        assertTrue(bedtime < now)
    }

    @Test
    fun `clock rounds a partial second up`() {
        assertEquals("00:01", formatFocusClock(1L))
        assertEquals("01:01", formatFocusClock(60_001L))
        assertEquals("00:00", formatFocusClock(0L))
    }

    @Test
    fun `only timed phases expose countdown`() {
        assertTrue(FocusProtocolPhase.RESET.hasCountdown)
        assertTrue(FocusProtocolPhase.FOCUS.hasCountdown)
        assertTrue(FocusProtocolPhase.RECOVERY.hasCountdown)
        assertFalse(FocusProtocolPhase.ACTIVATE.hasCountdown)
        assertFalse(FocusProtocolPhase.FOCUS_PAUSED.hasCountdown)
        assertFalse(FocusProtocolPhase.CYCLE_READY.hasCountdown)
        assertTrue(FocusProtocolPhase.COMPLETE.isTerminal)
    }

    @Test
    fun `block summary formats accumulated cycles`() {
        assertEquals("0м", formatCompactDuration(0L))
        assertEquals("42м", formatCompactDuration(42L * 60_000L))
        assertEquals("1:35", formatCompactDuration(95L * 60_000L))
    }

    @Test
    fun `focus sliders snap to their configured step`() {
        assertEquals(25, snapFocusSetting(27.2f, 5, 180, 5))
        assertEquals(30, snapFocusSetting(28.1f, 5, 180, 5))
        assertEquals(0, snapFocusSetting(-2f, 0, 20, 5))
        assertEquals(20, snapFocusSetting(24f, 0, 20, 5))
    }
}
