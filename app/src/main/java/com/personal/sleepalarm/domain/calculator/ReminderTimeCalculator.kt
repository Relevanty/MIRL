package com.personal.sleepalarm.domain.calculator

import com.personal.sleepalarm.data.db.entity.RepeatMode
import java.time.ZonedDateTime

/**
 * Калькулятор следующего времени срабатывания напоминания.
 *
 * Битовая маска дней недели: Пн=1, Вт=2, Ср=4, Чт=8, Пт=16, Сб=32, Вс=64
 * (бит = 1 shl (DayOfWeek.value - 1)).
 */
object ReminderTimeCalculator {

    /** Бит для дня недели (Пн=1 .. Вс=64). */
    fun bitForDay(dayOfWeekValue: Int): Int = 1 shl (dayOfWeekValue - 1)

    /** Установлен ли бит дня в маске. */
    fun isDaySelected(daysOfWeek: Int, dayOfWeekValue: Int): Boolean =
        (daysOfWeek and bitForDay(dayOfWeekValue)) != 0

    /**
     * Следующее время срабатывания (epoch millis), строго позже now.
     *
     * @param lastTrigger время прошлого срабатывания (для INTERVAL).
     */
    fun nextTrigger(
        mode: RepeatMode,
        hour: Int,
        minute: Int,
        daysOfWeek: Int,
        intervalDays: Int,
        now: ZonedDateTime = ZonedDateTime.now(),
        lastTrigger: Long? = null
    ): Long {
        return when (mode) {
            RepeatMode.ONCE, RepeatMode.DAILY ->
                nextDaily(hour, minute, now)

            RepeatMode.WEEKLY ->
                nextWeekly(hour, minute, daysOfWeek, now)

            RepeatMode.INTERVAL ->
                nextInterval(hour, minute, intervalDays, now, lastTrigger)
        }
    }

    /** Сегодня в hour:minute, если ещё не прошло; иначе завтра. */
    private fun nextDaily(hour: Int, minute: Int, now: ZonedDateTime): Long {
        var candidate = now.toLocalDate().atTime(hour, minute).atZone(now.zone)
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1)
        }
        return candidate.toInstant().toEpochMilli()
    }

    /** Ближайший выбранный день недели (включая сегодня, если время не прошло). */
    private fun nextWeekly(
        hour: Int,
        minute: Int,
        daysOfWeek: Int,
        now: ZonedDateTime
    ): Long {
        if (daysOfWeek == 0) return nextDaily(hour, minute, now)

        for (offset in 0..7) {
            val day = now.plusDays(offset.toLong())
            if (isDaySelected(daysOfWeek, day.dayOfWeek.value)) {
                val candidate = day.toLocalDate().atTime(hour, minute).atZone(now.zone)
                if (candidate.isAfter(now)) {
                    return candidate.toInstant().toEpochMilli()
                }
            }
        }
        // Не должно случиться (маска непустая), но на всякий случай:
        return nextDaily(hour, minute, now)
    }

    /** lastTrigger + intervalDays в заданное время; если прошло — шагами вперёд. */
    private fun nextInterval(
        hour: Int,
        minute: Int,
        intervalDays: Int,
        now: ZonedDateTime,
        lastTrigger: Long?
    ): Long {
        val step = intervalDays.coerceAtLeast(1).toLong()

        if (lastTrigger == null) {
            return nextDaily(hour, minute, now)
        }

        var candidate = java.time.Instant.ofEpochMilli(lastTrigger)
            .atZone(now.zone)
            .plusDays(step)
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)

        while (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(step)
        }
        return candidate.toInstant().toEpochMilli()
    }
}