package com.personal.sleepalarm.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Форматирование времени для UI.
 */
object TimeFormatter {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Форматирует ZonedDateTime как HH:mm.
     */
    fun formatZonedDateTime(time: ZonedDateTime): String {
        return timeFormatter.format(time)
    }

    /**
     * Форматирует epoch millis как HH:mm в тайм-зоне сессии.
     */
    fun formatEpochMillis(
        epochMillis: Long,
        zoneIdString: String
    ): String {
        val zone = runCatching { ZoneId.of(zoneIdString) }
            .getOrDefault(ZoneId.systemDefault())

        val zonedDateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return timeFormatter.format(zonedDateTime)
    }

    /**
     * Форматирует продолжительность.
     *
     * Пример:
     * 450 -> "7 ч 30 мин"
     * 90 -> "1 ч 30 мин"
     * 45 -> "45 мин"
     */
    fun formatMinutes(totalMinutes: Long): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val isEnglish = Locale.getDefault().language == "en"

        return when {
            hours > 0 && minutes > 0 && isEnglish -> "$hours hr $minutes min"
            hours > 0 && minutes > 0 -> "$hours ч $minutes мин"
            hours > 0 && isEnglish -> "$hours hr"
            hours > 0 -> "$hours ч"
            isEnglish -> "$minutes min"
            else -> "$minutes мин"
        }
    }

    /**
     * Форматирует количество минут до события.
     *
     * Пример:
     * 0 -> "менее минуты"
     * 1 -> "1 мин"
     * 12 -> "12 мин"
     */
    fun formatMinutesUntil(minutes: Long): String {
        val isEnglish = Locale.getDefault().language == "en"
        return if (minutes <= 0) {
            if (isEnglish) "less than a minute" else "менее минуты"
        } else {
            if (isEnglish) "$minutes min" else "$minutes мин"
        }
    }
}
