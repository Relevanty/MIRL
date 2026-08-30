package com.personal.sleepalarm.domain.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SleepAutomationWindowTest {
    private val zone = ZoneId.of("Europe/Moscow")

    @Test
    fun `overnight window keeps one id on both sides of midnight`() {
        val evening = at(2026, 8, 28, 23, 0)
        val morning = at(2026, 8, 29, 1, 30)

        val first = SleepAutomationWindow.containing(evening, 22 * 60, 2 * 60)!!
        val second = SleepAutomationWindow.containing(morning, 22 * 60, 2 * 60)!!

        assertEquals(first.id, second.id)
        assertEquals(at(2026, 8, 28, 22, 0), first.start)
        assertEquals(at(2026, 8, 29, 2, 0), first.endExclusive)
    }

    @Test
    fun `end of window is exclusive`() {
        assertNull(
            SleepAutomationWindow.containing(
                at(2026, 8, 29, 2, 0),
                22 * 60,
                2 * 60
            )
        )
    }

    @Test
    fun `daytime does not belong to overnight window`() {
        assertNull(
            SleepAutomationWindow.containing(
                at(2026, 8, 29, 12, 0),
                22 * 60,
                2 * 60
            )
        )
    }

    @Test
    fun `regular same-day window is supported`() {
        val window = SleepAutomationWindow.containing(
            at(2026, 8, 29, 15, 0),
            14 * 60,
            18 * 60
        )
        assertTrue(window != null)
        assertEquals(at(2026, 8, 29, 18, 0), window!!.endExclusive)
    }

    @Test
    fun `next start advances to tomorrow after start has passed`() {
        assertEquals(
            at(2026, 8, 29, 22, 0),
            SleepAutomationWindow.nextStart(at(2026, 8, 28, 23, 0), 22 * 60)
        )
    }

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): ZonedDateTime = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)
}
