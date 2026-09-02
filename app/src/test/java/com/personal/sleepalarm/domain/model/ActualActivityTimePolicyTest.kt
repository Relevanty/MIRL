package com.personal.sleepalarm.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActualActivityTimePolicyTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val date = LocalDate.parse("2026-09-02")
    private val now = at("2026-09-02T12:00:00")

    @Test fun `past work and work ending exactly now are valid`() {
        assertNull(ActualActivityTimePolicy.validate(now - 60_000L, now, now))
        assertNull(ActualActivityTimePolicy.validate(now - 120_000L, now - 60_000L, now))
    }

    @Test fun `even one millisecond of future work is rejected`() {
        assertEquals(ActualActivityTimeError.FUTURE, ActualActivityTimePolicy.validate(now - 60_000L, now + 1L, now))
        assertEquals(ActualActivityTimeError.FUTURE, ActualActivityTimePolicy.validate(now + 1L, now + 2L, now))
    }

    @Test fun `zero and reversed intervals are invalid`() {
        assertEquals(ActualActivityTimeError.INVALID_DURATION, ActualActivityTimePolicy.validate(now, now, now))
        assertEquals(ActualActivityTimeError.INVALID_DURATION, ActualActivityTimePolicy.validate(now, now - 1L, now))
    }

    @Test fun `manual intervals retain a precise 24 hour maximum`() {
        val day = ActualActivityTimePolicy.MAX_MANUAL_DURATION_MILLIS
        assertNull(ActualActivityTimePolicy.validate(now - day, now, now))
        assertEquals(ActualActivityTimeError.TOO_LONG, ActualActivityTimePolicy.validate(now - day - 1L, now, now))
    }

    @Test fun `overflow cannot bypass maximum duration`() {
        assertEquals(ActualActivityTimeError.TOO_LONG, ActualActivityTimePolicy.validate(Long.MIN_VALUE, Long.MAX_VALUE, Long.MAX_VALUE))
    }

    @Test fun `merging a legacy future row is rejected before any replacement`() {
        val validStart = now - 60_000L
        val validEnd = now
        assertNull(ActualActivityTimePolicy.validate(validStart, validEnd, now))
        val mergedStart = minOf(validStart, now - 30_000L)
        val mergedEnd = maxOf(validEnd, now + 90_000L)

        assertEquals(ActualActivityTimeError.FUTURE, ActualActivityTimePolicy.validate(mergedStart, mergedEnd, now))
    }

    @Test fun `merge cannot expand valid input beyond one day`() {
        val start = now - 60_000L
        val earlierOverlapStart = now - ActualActivityTimePolicy.MAX_MANUAL_DURATION_MILLIS - 1L
        assertNull(ActualActivityTimePolicy.validate(start, now, now))
        assertEquals(ActualActivityTimeError.TOO_LONG, ActualActivityTimePolicy.validate(earlierOverlapStart, now, now))
    }

    @Test fun `end before start is not silently moved to tomorrow`() {
        val interval = parse(start = "23:00", end = "01:00", useDuration = false, nextDay = false)!!

        assertTrue(interval.endedAt < interval.startedAt)
        assertEquals(ActualActivityTimeError.INVALID_DURATION, ActualActivityTimePolicy.validate(interval.startedAt, interval.endedAt, now))
    }

    @Test fun `equal clock times need an explicit next-day selection`() {
        val sameDay = parse(start = "09:00", end = "09:00", useDuration = false, nextDay = false)!!
        val nextDay = parse(start = "09:00", end = "09:00", useDuration = false, nextDay = true)!!

        assertEquals(0L, sameDay.endedAt - sameDay.startedAt)
        assertEquals(ActualActivityTimePolicy.MAX_MANUAL_DURATION_MILLIS, nextDay.endedAt - nextDay.startedAt)
    }

    @Test fun `explicit historical overnight interval preserves its chosen dates`() {
        val interval = ActualActivityTimePolicy.parse(
            date.minusDays(1), "23:00", "01:00", "", false, true, zone
        )!!

        assertEquals(at("2026-09-01T23:00:00"), interval.startedAt)
        assertEquals(at("2026-09-02T01:00:00"), interval.endedAt)
        assertNull(ActualActivityTimePolicy.validate(interval.startedAt, interval.endedAt, now))
    }

    @Test fun `duration can cross midnight without changing the selected start date`() {
        val interval = ActualActivityTimePolicy.parse(
            date.minusDays(1), "23:30", "", "60", true, false, zone
        )!!

        assertEquals(at("2026-09-01T23:30:00"), interval.startedAt)
        assertEquals(at("2026-09-02T00:30:00"), interval.endedAt)
    }

    @Test fun `zero and excessive typed durations are not clamped`() {
        val zero = parse(duration = "0")!!
        val tooLong = parse(duration = "1441")!!

        assertEquals(0L, zero.endedAt - zero.startedAt)
        assertEquals(1441L * 60_000L, tooLong.endedAt - tooLong.startedAt)
        assertEquals(ActualActivityTimeError.INVALID_DURATION, ActualActivityTimePolicy.validate(zero.startedAt, zero.endedAt, now))
        assertEquals(ActualActivityTimeError.TOO_LONG, ActualActivityTimePolicy.validate(tooLong.startedAt, tooLong.endedAt, now + 3L * 86_400_000L))
    }

    @Test fun `time parser rejects invalid clock input instead of normalizing it`() {
        assertNull(parse(start = "24:00"))
        assertNull(parse(start = "10:60"))
        assertNull(parse(start = "bad"))
        assertNull(parse(duration = "not a duration"))
        assertNotNull(parse(start = "9:05"))
        assertNotNull(parse(start = "09:05"))
    }

    @Test fun `huge duration cannot overflow into a past end`() {
        assertNull(parse(duration = Long.MAX_VALUE.toString()))
    }

    @Test fun `existing future interval must be corrected explicitly before resaving`() {
        val oldStart = now + 86_400_000L
        val oldEnd = oldStart + 60_000L

        assertEquals(ActualActivityTimeError.FUTURE, ActualActivityTimePolicy.validate(oldStart, oldEnd, now))
        assertNull(ActualActivityTimePolicy.validate(oldStart, oldEnd, oldEnd))
    }

    @Test fun `next-day end is a local date not a fixed 24 hour rollover`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val interval = ActualActivityTimePolicy.parse(
            LocalDate.parse("2026-03-28"), "12:00", "12:00", "", false, true, berlin
        )!!

        assertEquals(23L * 60L * 60_000L, interval.endedAt - interval.startedAt)
    }

    @Test fun `nonexistent daylight saving time is rejected instead of shifted`() {
        val interval = ActualActivityTimePolicy.parse(
            LocalDate.parse("2026-03-29"), "02:30", "", "30", true, false, ZoneId.of("Europe/Berlin")
        )

        assertNull(interval)
    }

    private fun parse(
        start: String = "10:00",
        end: String = "11:00",
        duration: String = "60",
        useDuration: Boolean = true,
        nextDay: Boolean = false
    ) = ActualActivityTimePolicy.parse(date, start, end, duration, useDuration, nextDay, zone)

    private fun at(value: String): Long = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
}
