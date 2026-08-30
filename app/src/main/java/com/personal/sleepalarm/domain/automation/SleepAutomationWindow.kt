package com.personal.sleepalarm.domain.automation

import java.time.ZonedDateTime

data class NightWindow(
    val start: ZonedDateTime,
    val endExclusive: ZonedDateTime
) {
    val id: Long get() = start.toLocalDate().toEpochDay()

    fun contains(moment: ZonedDateTime): Boolean =
        !moment.isBefore(start) && moment.isBefore(endExclusive)
}

/** Чистая календарная логика, включая окна через полночь. */
object SleepAutomationWindow {
    fun containing(
        now: ZonedDateTime,
        startMinutes: Int,
        endMinutes: Int
    ): NightWindow? {
        val start = startMinutes.coerceIn(0, 1439)
        val end = endMinutes.coerceIn(0, 1439)
        if (start == end) return null

        val nowMinutes = now.hour * 60 + now.minute
        val date = now.toLocalDate()
        return if (start < end) {
            if (nowMinutes in start until end) {
                NightWindow(atMinutes(now, date, start), atMinutes(now, date, end))
            } else null
        } else {
            when {
                nowMinutes >= start -> NightWindow(
                    atMinutes(now, date, start),
                    atMinutes(now, date.plusDays(1), end)
                )
                nowMinutes < end -> NightWindow(
                    atMinutes(now, date.minusDays(1), start),
                    atMinutes(now, date, end)
                )
                else -> null
            }
        }
    }

    fun nextStart(
        now: ZonedDateTime,
        startMinutes: Int
    ): ZonedDateTime {
        val today = atMinutes(now, now.toLocalDate(), startMinutes.coerceIn(0, 1439))
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    private fun atMinutes(
        template: ZonedDateTime,
        date: java.time.LocalDate,
        minutes: Int
    ): ZonedDateTime = date
        .atTime(minutes / 60, minutes % 60)
        .atZone(template.zone)
}
