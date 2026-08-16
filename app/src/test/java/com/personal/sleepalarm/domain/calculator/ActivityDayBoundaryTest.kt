package com.personal.sleepalarm.domain.calculator

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityDayBoundaryTest {
    private val zone = ZoneId.of("Europe/Moscow")

    @Test
    fun timeBeforeFourBelongsToPreviousDate() {
        val millis = LocalDate.of(2026, 8, 15).atTime(3, 59).atZone(zone).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 8, 14), ActivityDayBoundary.dateFor(millis, zone))
    }

    @Test
    fun fourAmStartsNewStatisticsDate() {
        val date = LocalDate.of(2026, 8, 15)
        val millis = date.atTime(4, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(date, ActivityDayBoundary.dateFor(millis, zone))
    }
}
