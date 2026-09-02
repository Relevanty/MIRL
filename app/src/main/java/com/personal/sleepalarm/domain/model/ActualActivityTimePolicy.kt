package com.personal.sleepalarm.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

enum class ActualActivityTimeError(val reason: String) {
    INVALID_TIME("time_format"),
    INVALID_DURATION("duration"),
    FUTURE("future"),
    TOO_LONG("too_long")
}

data class ManualActivityInterval(val startedAt: Long, val endedAt: Long)

/** Actual work has already happened: no clock tolerance and no automatic date adjustment. */
object ActualActivityTimePolicy {
    const val MAX_MANUAL_DURATION_MILLIS = 24L * 60L * 60_000L
    private val timeFormat = DateTimeFormatter.ofPattern("H:mm").withResolverStyle(ResolverStyle.STRICT)

    fun validate(startedAt: Long, endedAt: Long, nowMillis: Long): ActualActivityTimeError? {
        if (endedAt <= startedAt) return ActualActivityTimeError.INVALID_DURATION
        if (startedAt > nowMillis || endedAt > nowMillis) return ActualActivityTimeError.FUTURE
        val duration = endedAt - startedAt
        if (duration < 0L || duration > MAX_MANUAL_DURATION_MILLIS) return ActualActivityTimeError.TOO_LONG
        return null
    }

    /**
     * Parses exactly what the user entered. An overnight interval requires an
     * explicit next-day end; a short/zero/long duration is never silently clamped.
     * Validation is separate so UI and the repository can use their current clock.
     */
    fun parse(
        date: LocalDate,
        startText: String,
        endText: String,
        durationText: String,
        useDuration: Boolean,
        endsNextDay: Boolean,
        zone: ZoneId
    ): ManualActivityInterval? = runCatching {
        val startTime = LocalTime.parse(startText.trim(), timeFormat)
        val start = resolveLocalTime(LocalDateTime.of(date, startTime), zone)
        val end = if (useDuration) {
            val minutes = durationText.trim().toLong()
            Math.addExact(start, Math.multiplyExact(minutes, 60_000L))
        } else {
            val endTime = LocalTime.parse(endText.trim(), timeFormat)
            val endDate = if (endsNextDay) date.plusDays(1) else date
            resolveLocalTime(LocalDateTime.of(endDate, endTime), zone)
        }
        ManualActivityInterval(start, end)
    }.getOrNull()

    private fun resolveLocalTime(value: LocalDateTime, zone: ZoneId): Long {
        // atZone normally shifts a nonexistent DST-gap time forward silently.
        require(zone.rules.getValidOffsets(value).isNotEmpty())
        return value.atZone(zone).toInstant().toEpochMilli()
    }
}
