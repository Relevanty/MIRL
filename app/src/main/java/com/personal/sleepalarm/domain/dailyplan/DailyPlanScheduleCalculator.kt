package com.personal.sleepalarm.domain.dailyplan

import com.personal.sleepalarm.domain.automation.SleepAutomationWindow
import java.time.LocalDate
import java.time.ZonedDateTime

enum class DailyPlanCutoffSource {
    FALLBACK,
    SLEEP_AUTOMATION
}

data class DailyPlanSleepAutomationInput(
    val enabled: Boolean,
    val windowStartMinutes: Int,
    val windowEndMinutes: Int,
    val skippedWindowStartEpochDay: Long?
)

data class DailyPlanCutoff(
    val at: ZonedDateTime,
    val source: DailyPlanCutoffSource
)

object DailyPlanScheduleCalculator {
    private const val MINUTE_MILLIS = 60_000L

    fun nextFallbackCutoff(now: ZonedDateTime, cutoffMinutesOfDay: Int): ZonedDateTime {
        val safeMinutes = cutoffMinutesOfDay.coerceIn(0, 24 * 60 - 1)
        val today = now.toLocalDate().atTime(safeMinutes / 60, safeMinutes % 60).atZone(now.zone)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /**
     * Sleep automation replaces the fallback cutoff only for its nearest live,
     * non-skipped window. A containing window deliberately returns its already
     * reached start, allowing the caller to expire the notification immediately.
     */
    fun effectiveCutoff(
        now: ZonedDateTime,
        fallbackCutoffMinutesOfDay: Int,
        sleepAutomation: DailyPlanSleepAutomationInput?
    ): DailyPlanCutoff {
        val fallback = nextFallbackCutoff(now, fallbackCutoffMinutesOfDay)
        val automation = sleepAutomation?.takeIf { it.enabled }
            ?: return DailyPlanCutoff(fallback, DailyPlanCutoffSource.FALLBACK)
        if (automation.windowStartMinutes == automation.windowEndMinutes) {
            return DailyPlanCutoff(fallback, DailyPlanCutoffSource.FALLBACK)
        }

        val currentWindow = SleepAutomationWindow.containing(
            now = now,
            startMinutes = automation.windowStartMinutes,
            endMinutes = automation.windowEndMinutes
        )
        val candidate = currentWindow?.start
            ?: SleepAutomationWindow.nextStart(now, automation.windowStartMinutes)
        val candidateId = candidate.toLocalDate().toEpochDay()
        return if (automation.skippedWindowStartEpochDay == candidateId) {
            DailyPlanCutoff(fallback, DailyPlanCutoffSource.FALLBACK)
        } else {
            DailyPlanCutoff(candidate, DailyPlanCutoffSource.SLEEP_AUTOMATION)
        }
    }

    fun nextUrgencyEvaluationMillis(
        nowMillis: Long,
        cutoffMillis: Long,
        totalRemainingMinutes: Int,
        bufferMinutes: Int,
        repeatEnabled: Boolean,
        repeatIntervalMinutes: Int,
        lastShownAtMillis: Long?
    ): Long? {
        if (nowMillis >= cutoffMillis || totalRemainingMinutes <= 0) return null
        val threshold = saturatedSubtract(
            cutoffMillis,
            (totalRemainingMinutes.coerceAtLeast(0).toLong() +
                bufferMinutes.coerceAtLeast(0).toLong()) * MINUTE_MILLIS
        )
        if (threshold > nowMillis) return threshold
        if (!repeatEnabled) return null
        val intervalMillis = repeatIntervalMinutes.coerceIn(5, 120) * MINUTE_MILLIS
        return ((lastShownAtMillis ?: nowMillis) + intervalMillis)
            .coerceAtMost(cutoffMillis)
            .takeIf { it > nowMillis }
    }

    fun morningAt(date: LocalDate, wakeMinutesOfDay: Int, zoneNow: ZonedDateTime): ZonedDateTime {
        val safe = wakeMinutesOfDay.coerceIn(0, 24 * 60 - 1)
        return date.atTime(safe / 60, safe % 60).atZone(zoneNow.zone)
    }

    private fun saturatedSubtract(value: Long, decrement: Long): Long =
        if (decrement > 0L && value < Long.MIN_VALUE + decrement) Long.MIN_VALUE
        else value - decrement
}
