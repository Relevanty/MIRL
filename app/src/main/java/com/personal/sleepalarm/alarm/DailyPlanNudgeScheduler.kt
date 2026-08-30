package com.personal.sleepalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.preferences.DailyPlanNudgePreferences
import com.personal.sleepalarm.data.preferences.DailyPlanNudgeSettings
import com.personal.sleepalarm.data.preferences.SleepAutomationPreference
import com.personal.sleepalarm.domain.calculator.DailyTaskFocusCalculator
import com.personal.sleepalarm.domain.calculator.TaskFocusInterval
import com.personal.sleepalarm.domain.calculator.liveTaskFocusIntervals
import com.personal.sleepalarm.domain.dailyplan.DailyPlanCutoff
import com.personal.sleepalarm.domain.dailyplan.DailyPlanCutoffSource
import com.personal.sleepalarm.domain.dailyplan.DailyPlanNudgePolicy
import com.personal.sleepalarm.domain.dailyplan.DailyPlanProgressSnapshot
import com.personal.sleepalarm.domain.dailyplan.DailyPlanScheduleCalculator
import com.personal.sleepalarm.domain.dailyplan.DailyPlanSleepAutomationInput
import com.personal.sleepalarm.domain.dailyplan.DailyPlanTaskInput
import com.personal.sleepalarm.domain.dailyplan.DailyPlanTaskEligibility
import com.personal.sleepalarm.domain.automation.isAutomationPausedForFocus
import com.personal.sleepalarm.service.DailyPlanNotificationBuilder
import com.personal.sleepalarm.service.DailyPlanNotificationPhase
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class DailyPlanEvaluation(
    val snapshot: DailyPlanProgressSnapshot,
    val cutoff: DailyPlanCutoff,
    val settings: DailyPlanNudgeSettings,
    val localDate: String,
    val preferredWakeMinutesOfDay: Int,
    val activeFocus: Boolean
)

enum class DailyPlanRefreshResult {
    DISABLED,
    SLEEPING,
    NO_REQUIRED_TASKS,
    SUPPRESSED,
    EXPIRED,
    MORNING_SHOWN,
    URGENCY_SHOWN,
    SCHEDULED
}

/**
 * Reconciles the current task/activity truth with one notification and one
 * inexact AlarmManager wake-up. Every delivery recalculates from Room, so old
 * PendingIntents cannot resurrect stale work.
 */
class DailyPlanNudgeScheduler(
    context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context.applicationContext),
    private val preferences: DailyPlanNudgePreferences = DailyPlanNudgePreferences(context),
    private val sleepAutomationPreference: SleepAutomationPreference = SleepAutomationPreference(context)
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notifications = DailyPlanNotificationBuilder(appContext)

    /** App/boot reconciliation is intentionally silent. */
    suspend fun reschedule(nowMillis: Long = System.currentTimeMillis()): DailyPlanRefreshResult =
        refreshNow(nowMillis = nowMillis, playSoundIfDue = false)

    /** Receiver and live data observers use this to show/update/cancel immediately. */
    suspend fun refreshNow(
        nowMillis: Long = System.currentTimeMillis(),
        playSoundIfDue: Boolean = true
    ): DailyPlanRefreshResult {
        cancelAlarm()
        val initialSettings = preferences.get()
        if (!initialSettings.enabled) {
            notifications.cancel()
            return DailyPlanRefreshResult.DISABLED
        }

        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val localDate = now.toLocalDate().toString()
        val settings = clearExpiredStateIfNeeded(initialSettings, localDate)

        val activeSession = database.sleepSessionDao().getActiveSession()
            ?.takeUnless { it.isAutomationPausedForFocus() }
        if (activeSession != null) {
            notifications.cancel()
            scheduleEarliest(
                nowMillis,
                listOf(
                    nextMidnight(now).toInstant().toEpochMilli(),
                    activeSession.estimatedWakeTime + POST_WAKE_RECHECK_DELAY_MS
                )
            )
            return DailyPlanRefreshResult.SLEEPING
        }

        val evaluation = evaluate(nowMillis, settings) ?: run {
            notifications.cancel()
            return DailyPlanRefreshResult.NO_REQUIRED_TASKS
        }
        val snapshot = evaluation.snapshot

        if (settings.dismissedLocalDate == localDate) {
            notifications.cancel()
            scheduleEarliest(nowMillis, listOf(snapshot.nextMidnightMillis))
            return DailyPlanRefreshResult.SUPPRESSED
        }
        val snoozedUntil = settings.snoozedUntilMillis
            ?.takeIf { settings.snoozedLocalDate == localDate && it > nowMillis }
        if (snoozedUntil != null) {
            notifications.cancel()
            scheduleEarliest(
                nowMillis,
                listOf(snoozedUntil, snapshot.cutoffMillis, snapshot.nextMidnightMillis)
            )
            return DailyPlanRefreshResult.SUPPRESSED
        } else if (settings.snoozedLocalDate == localDate) {
            preferences.clearSuppression()
        }

        if (nowMillis >= snapshot.cutoffMillis) {
            notifications.cancel()
            scheduleNextPassiveCheck(evaluation, now)
            return DailyPlanRefreshResult.EXPIRED
        }

        if (snapshot.shouldNudge && snapshot.totalRemainingMinutes > 0) {
            val alreadyShownToday = settings.lastUrgencyLocalDate == localDate
            val repeatDue = !alreadyShownToday || (
                settings.repeatEnabled &&
                    nowMillis - (settings.lastUrgencyAtMillis ?: 0L) >=
                    settings.repeatIntervalMinutes * MINUTE_MILLIS
                )
            val shown = notifications.show(
                snapshot = snapshot,
                phase = DailyPlanNotificationPhase.URGENCY,
                playSound = playSoundIfDue && repeatDue && !evaluation.activeFocus,
                dedupeKey = "daily-plan-$localDate-urgency-${repeatBucket(nowMillis, settings)}"
            )
            if (shown && repeatDue) preferences.markUrgencyShown(localDate, nowMillis)
            scheduleAfterUrgency(evaluation, nowMillis, repeatDue)
            return DailyPlanRefreshResult.URGENCY_SHOWN
        }

        val morningAt = DailyPlanScheduleCalculator.morningAt(
            date = now.toLocalDate(),
            wakeMinutesOfDay = evaluation.preferredWakeMinutesOfDay,
            zoneNow = now
        ).toInstant().toEpochMilli()
        val morningDue = settings.morningReminderEnabled &&
            settings.lastMorningLocalDate != localDate &&
            snapshot.unstartedTasks.isNotEmpty() &&
            nowMillis >= morningAt &&
            nowMillis < morningAt + MORNING_GRACE_MS
        if (morningDue) {
            val shown = notifications.show(
                snapshot = snapshot,
                phase = DailyPlanNotificationPhase.MORNING,
                playSound = playSoundIfDue && !evaluation.activeFocus,
                dedupeKey = "daily-plan-$localDate-morning"
            )
            if (shown) preferences.markMorningShown(localDate)
            scheduleNextActiveCheck(evaluation, nowMillis)
            return DailyPlanRefreshResult.MORNING_SHOWN
        }

        val keepMorningCard = settings.lastMorningLocalDate == localDate &&
            snapshot.unstartedTasks.isNotEmpty() &&
            nowMillis < morningAt + MORNING_GRACE_MS
        if (keepMorningCard) {
            notifications.show(
                snapshot = snapshot,
                phase = DailyPlanNotificationPhase.MORNING,
                playSound = false,
                dedupeKey = "daily-plan-$localDate-morning"
            )
            scheduleNextActiveCheck(evaluation, nowMillis)
            return DailyPlanRefreshResult.MORNING_SHOWN
        }

        notifications.cancel()
        scheduleNextActiveCheck(evaluation, nowMillis)
        return DailyPlanRefreshResult.SCHEDULED
    }

    suspend fun buildSnapshot(nowMillis: Long = System.currentTimeMillis()): DailyPlanProgressSnapshot? {
        val settings = preferences.get()
        if (!settings.enabled) return null
        return evaluate(nowMillis, settings)?.snapshot
    }

    fun cancel() {
        cancelAlarm()
        notifications.cancel()
    }

    private suspend fun evaluate(
        nowMillis: Long,
        settings: DailyPlanNudgeSettings
    ): DailyPlanEvaluation? {
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val tasks = database.taskDao().getAll()
            .asSequence()
            .filter {
                DailyPlanTaskEligibility.isEligible(
                    isDailyRequired = it.isDailyRequired,
                    isDone = it.isDone,
                    isMorningRoutine = it.isMorningRoutine,
                    startAtMillis = it.startAtMillis,
                    dailyTargetMinutes = it.plannedFocusMinutes,
                    nowMillis = nowMillis
                )
            }
            .sortedWith(
                compareBy<com.personal.sleepalarm.data.db.entity.TaskEntity> { it.matrixQuadrant }
                    .thenBy { it.dueAtMillis == null }
                    .thenBy { it.dueAtMillis ?: Long.MAX_VALUE }
                    .thenBy { it.sortOrder }
            )
            .toList()
        if (tasks.isEmpty()) return null

        val activeFocusSessions = database.focusProtocolDao().getActive()
        val liveIntervals = activeFocusSessions.flatMap { it.liveTaskFocusIntervals(nowMillis) }
        val bounds = DailyTaskFocusCalculator.localDayBounds(nowMillis, zone)
        val records = database.activityRecordDao().getOverlapping(
            from = bounds.startMillis,
            to = minOf(nowMillis, bounds.endMillis)
        )
        val todayMillisByTask = DailyTaskFocusCalculator.countedMillisByTask(
            records = records,
            periodStartMillis = bounds.startMillis,
            periodEndMillis = minOf(nowMillis, bounds.endMillis),
            liveIntervals = liveIntervals
        )
        val liveMillisByTask = DailyTaskFocusCalculator.countedMillisByTask(
            records = emptyList(),
            periodStartMillis = bounds.startMillis,
            periodEndMillis = minOf(nowMillis, bounds.endMillis),
            liveIntervals = liveIntervals
        )

        val sleepSettings = sleepAutomationPreference.get()
        val profile = database.alarmProfileDao().getProfile()
            ?: com.personal.sleepalarm.data.db.entity.AlarmProfileEntity()
        val cutoff = DailyPlanScheduleCalculator.effectiveCutoff(
            now = now,
            fallbackCutoffMinutesOfDay = settings.cutoffMinutesOfDay,
            sleepAutomation = DailyPlanSleepAutomationInput(
                enabled = sleepSettings.enabled && profile.autoDetectOnsetEnabled,
                windowStartMinutes = sleepSettings.windowStartMinutes,
                windowEndMinutes = sleepSettings.windowEndMinutes,
                skippedWindowStartEpochDay = sleepSettings.skippedWindowStartEpochDay
            )
        )

        val inputs = tasks.map { task ->
            val todayMillis = todayMillisByTask[task.id] ?: 0L
            val liveMillis = liveMillisByTask[task.id] ?: 0L
            val wholeRemaining = DailyPlanNudgePolicy.currentWholeBudgetRemainingMinutes(
                workBudgetMinutes = task.workBudgetMinutes,
                persistedAllTimeSpentMillis = task.spentMillis,
                liveElapsedMillis = liveMillis
            )
            DailyPlanTaskInput(
                taskId = task.id,
                title = task.title,
                dailyTargetMinutes = task.plannedFocusMinutes,
                wholeBudgetRemainingMinutes = wholeRemaining,
                todayProgressMinutes = (todayMillis / MINUTE_MILLIS)
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                boutMinutes = task.estimatedMinutes
            )
        }
        val snapshot = DailyPlanNudgePolicy.calculate(
            tasks = inputs,
            nowMillis = nowMillis,
            dayStartMillis = bounds.startMillis,
            nextMidnightMillis = bounds.endMillis,
            cutoffMillis = cutoff.at.toInstant().toEpochMilli(),
            bufferMinutes = settings.bufferMinutes
        )
        if (!snapshot.hasRequiredTasks) return null
        return DailyPlanEvaluation(
            snapshot = snapshot,
            cutoff = cutoff,
            settings = settings,
            localDate = now.toLocalDate().toString(),
            preferredWakeMinutesOfDay = profile.preferredWakeHour.coerceIn(0, 23) * 60 +
                profile.preferredWakeMinute.coerceIn(0, 59),
            activeFocus = activeFocusSessions.isNotEmpty()
        )
    }

    private suspend fun clearExpiredStateIfNeeded(
        settings: DailyPlanNudgeSettings,
        localDate: String
    ): DailyPlanNudgeSettings {
        val hasExpired = listOf(
            settings.dismissedLocalDate,
            settings.snoozedLocalDate,
            settings.lastMorningLocalDate,
            settings.lastUrgencyLocalDate
        ).any { it != null && it != localDate }
        if (!hasExpired) return settings
        preferences.clearExpiredDayState(localDate)
        return preferences.get()
    }

    private fun scheduleAfterUrgency(
        evaluation: DailyPlanEvaluation,
        nowMillis: Long,
        repeatWasDue: Boolean
    ) {
        val settings = evaluation.settings
        val nextRepeat = if (settings.repeatEnabled) {
            val base = if (repeatWasDue) nowMillis else settings.lastUrgencyAtMillis ?: nowMillis
            base + settings.repeatIntervalMinutes * MINUTE_MILLIS
        } else null
        scheduleEarliest(
            nowMillis,
            listOfNotNull(
                nextRepeat,
                evaluation.snapshot.cutoffMillis,
                evaluation.snapshot.nextMidnightMillis
            )
        )
    }

    private fun scheduleNextActiveCheck(evaluation: DailyPlanEvaluation, nowMillis: Long) {
        val snapshot = evaluation.snapshot
        val now = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
        val morning = if (
            evaluation.settings.morningReminderEnabled &&
            evaluation.settings.lastMorningLocalDate != evaluation.localDate &&
            snapshot.unstartedTasks.isNotEmpty()
        ) {
            DailyPlanScheduleCalculator.morningAt(
                now.toLocalDate(),
                evaluation.preferredWakeMinutesOfDay,
                now
            ).toInstant().toEpochMilli().takeIf { it > nowMillis }
        } else null
        val morningExpiry = if (evaluation.settings.lastMorningLocalDate == evaluation.localDate) {
            val shownMorningAt = DailyPlanScheduleCalculator.morningAt(
                now.toLocalDate(),
                evaluation.preferredWakeMinutesOfDay,
                now
            ).toInstant().toEpochMilli()
            (shownMorningAt + MORNING_GRACE_MS).takeIf { it > nowMillis }
        } else null
        val urgency = DailyPlanScheduleCalculator.nextUrgencyEvaluationMillis(
            nowMillis = nowMillis,
            cutoffMillis = snapshot.cutoffMillis,
            totalRemainingMinutes = snapshot.totalRemainingMinutes,
            bufferMinutes = evaluation.settings.bufferMinutes,
            repeatEnabled = evaluation.settings.repeatEnabled,
            repeatIntervalMinutes = evaluation.settings.repeatIntervalMinutes,
            lastShownAtMillis = evaluation.settings.lastUrgencyAtMillis
        )
        scheduleEarliest(
            nowMillis,
            listOfNotNull(
                morning,
                morningExpiry,
                urgency,
                snapshot.cutoffMillis,
                snapshot.nextMidnightMillis
            )
        )
    }

    private fun scheduleNextPassiveCheck(evaluation: DailyPlanEvaluation, now: ZonedDateTime) {
        val todayMorning = DailyPlanScheduleCalculator.morningAt(
            now.toLocalDate(),
            evaluation.preferredWakeMinutesOfDay,
            now
        )
        val nextMorning = if (todayMorning.isAfter(now)) todayMorning else todayMorning.plusDays(1)
        scheduleEarliest(
            now.toInstant().toEpochMilli(),
            listOf(
                evaluation.snapshot.nextMidnightMillis,
                nextMorning.toInstant().toEpochMilli()
            )
        )
    }

    private fun scheduleEarliest(nowMillis: Long, candidates: Iterable<Long>) {
        val at = candidates.filter { it > nowMillis + MIN_SCHEDULE_LEAD_MS }.minOrNull() ?: return
        val pendingIntent = evaluationPendingIntent(at)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, at, pendingIntent)
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "Cannot schedule daily-plan evaluation", error)
        }
    }

    private fun cancelAlarm() {
        alarmManager.cancel(evaluationPendingIntent(0L))
    }

    private fun evaluationPendingIntent(expectedAt: Long): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        EVALUATION_REQUEST_CODE,
        Intent(appContext, DailyPlanNudgeReceiver::class.java).apply {
            action = DailyPlanNudgeReceiver.ACTION_EVALUATE
            putExtra(DailyPlanNudgeReceiver.EXTRA_EXPECTED_AT, expectedAt)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun repeatBucket(nowMillis: Long, settings: DailyPlanNudgeSettings): Long {
        val interval = settings.repeatIntervalMinutes.coerceIn(5, 120) * MINUTE_MILLIS
        return nowMillis / interval
    }

    private fun nextMidnight(now: ZonedDateTime): ZonedDateTime =
        now.toLocalDate().plusDays(1).atStartOfDay(now.zone)

    companion object {
        private const val TAG = "DailyPlanNudge"
        private const val EVALUATION_REQUEST_CODE = 117_900
        private const val MINUTE_MILLIS = 60_000L
        private const val MIN_SCHEDULE_LEAD_MS = 1_000L
        private const val MORNING_GRACE_MS = 4L * 60L * MINUTE_MILLIS
        private const val POST_WAKE_RECHECK_DELAY_MS = 2L * MINUTE_MILLIS
    }
}
