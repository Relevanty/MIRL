package com.personal.sleepalarm.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CalendarRecurrenceCalculatorTest {

    @Test
    fun `one-time event in the past has no future occurrence`() {
        val zone = ZoneId.of("Europe/Moscow")
        val start = at(zone, 2026, 8, 20, 9, 0)
        val result = CalendarRecurrenceCalculator.nextOccurrence(
            startMillis = start,
            endMillis = start + 60 * 60_000L,
            repeatRule = "none",
            reminderMinutes = 30,
            nowMillis = at(zone, 2026, 8, 21, 9, 0),
            zoneId = zone
        )

        assertNull(result)
    }

    @Test
    fun `daily recurrence preserves local hour across daylight saving transition`() {
        val zone = ZoneId.of("Europe/Berlin")
        val baseStart = at(zone, 2026, 3, 28, 8, 0)
        val baseEnd = at(zone, 2026, 3, 28, 9, 0)
        val result = CalendarRecurrenceCalculator.nextOccurrence(
            startMillis = baseStart,
            endMillis = baseEnd,
            repeatRule = "daily",
            reminderMinutes = 0,
            nowMillis = baseStart,
            zoneId = zone
        ) ?: error("Expected next occurrence")

        val nextStart = java.time.Instant.ofEpochMilli(result.startMillis).atZone(zone)
        assertEquals(29, nextStart.dayOfMonth)
        assertEquals(8, nextStart.hour)
        assertEquals(23L * 60L * 60L * 1_000L, result.startMillis - baseStart)
    }

    @Test
    fun `reminder lead skips occurrence whose trigger has already passed`() {
        val zone = ZoneId.of("Europe/Moscow")
        val baseStart = at(zone, 2026, 8, 20, 10, 0)
        val now = at(zone, 2026, 8, 21, 9, 45)
        val result = CalendarRecurrenceCalculator.nextOccurrence(
            startMillis = baseStart,
            endMillis = baseStart + 60 * 60_000L,
            repeatRule = "daily",
            reminderMinutes = 30,
            nowMillis = now,
            zoneId = zone
        ) ?: error("Expected next occurrence")

        val nextStart = java.time.Instant.ofEpochMilli(result.startMillis).atZone(zone)
        assertEquals(22, nextStart.dayOfMonth)
        assertEquals(10, nextStart.hour)
        assertTrue(result.triggerAtMillis > now)
    }

    @Test
    fun `weekly recurrence keeps weekday and duration`() {
        val zone = ZoneId.of("Europe/Moscow")
        val baseStart = at(zone, 2026, 8, 3, 18, 30)
        val baseEnd = at(zone, 2026, 8, 3, 20, 0)
        val now = at(zone, 2026, 8, 17, 18, 0)
        val result = CalendarRecurrenceCalculator.nextOccurrence(
            startMillis = baseStart,
            endMillis = baseEnd,
            repeatRule = "weekly",
            reminderMinutes = 15,
            nowMillis = now,
            zoneId = zone
        ) ?: error("Expected next occurrence")

        val nextStart = java.time.Instant.ofEpochMilli(result.startMillis).atZone(zone)
        assertEquals(java.time.DayOfWeek.MONDAY, nextStart.dayOfWeek)
        assertEquals(18, nextStart.hour)
        assertEquals(30, nextStart.minute)
        assertEquals(90L * 60_000L, result.endMillis - result.startMillis)
    }

    private fun at(
        zone: ZoneId,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)
        .toInstant()
        .toEpochMilli()
}
