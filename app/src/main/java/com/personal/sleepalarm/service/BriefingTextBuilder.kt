package com.personal.sleepalarm.service

import android.content.Context
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.dao.ActivityRecordDao
import com.personal.sleepalarm.data.db.dao.AlarmProfileDao
import com.personal.sleepalarm.data.db.dao.CalendarEventDao
import com.personal.sleepalarm.data.db.dao.DDayDao
import com.personal.sleepalarm.data.db.dao.SleepSessionDao
import com.personal.sleepalarm.data.db.dao.StudySessionDao
import com.personal.sleepalarm.data.db.dao.TaskDao
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.repository.AdaptiveRecommendationRepository
import com.personal.sleepalarm.data.preferences.DailyPlanNudgePreferences
import com.personal.sleepalarm.data.preferences.SleepAutomationPreference
import com.personal.sleepalarm.domain.dailyplan.DailyPlanScheduleCalculator
import com.personal.sleepalarm.domain.dailyplan.DailyPlanSleepAutomationInput
import com.personal.sleepalarm.domain.calculator.StudyActivityDurationCalculator
import com.personal.sleepalarm.domain.calculator.StudyTimeInterval
import com.personal.sleepalarm.domain.calculator.effectiveActivityEndMillis
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.domain.model.snapshotActivityType
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.ui.calendar.eventsOn
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

class BriefingTextBuilder(
    private val calendarEventDao: CalendarEventDao,
    private val activityRecordDao: ActivityRecordDao,
    private val studySessionDao: StudySessionDao,
    private val ddayDao: DDayDao,
    private val sessionDao: SleepSessionDao,
    private val taskDao: TaskDao,
    private val alarmProfileDao: AlarmProfileDao
) {

    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE

    suspend fun build(context: Context): String {
        val sb = StringBuilder()
        val today = LocalDate.now()

        // 1. Приветствие + дата.
        sb.append(context.getString(R.string.briefing_greeting)).append(' ')
        sb.append(today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ru")))
        sb.append(", ")
        sb.append(today.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))))
        sb.append(". ")

        // 2. Последний сон.
        val lastSession = sessionDao.getLatestCompleted()
        if (lastSession != null && lastSession.actualWakeTime != null) {
            val start = lastSession.detectedSleepOnsetTime ?: lastSession.estimatedSleepStartTime
            val minutes = (lastSession.actualWakeTime - start) / 60_000
            sb.append(
                context.getString(
                    R.string.briefing_last_sleep,
                    minutes.toInt(),
                    lastSession.cyclesPlanned
                )
            ).append(' ')

            lastSession.detectedOnsetLatencyMinutes?.let { latency ->
                sb.append(context.getString(R.string.briefing_detected_onset, latency)).append(' ')
            }
        }

        // 3. Ближайший D-Day.
        val todayStr = today.format(dateFormat)
        val nearest = ddayDao.getNearest(todayStr)
        if (nearest != null) {
            val days = runCatching {
                ChronoUnit.DAYS.between(
                    today,
                    LocalDate.parse(nearest.targetDate, dateFormat)
                ).toInt()
            }.getOrDefault(-1)

            if (days >= 0) {
                sb.append(
                    if (days == 0) {
                        context.getString(R.string.briefing_dday_today, nearest.title)
                    } else {
                        context.getString(R.string.briefing_dday, nearest.title, days)
                    }
                ).append(' ')
            }
        }

        // 4. События календаря на сегодня.
        val eventsList = calendarEventDao.observeAll().first()
        val todayEvents = eventsOn(eventsList, today)

        if (todayEvents.isEmpty()) {
            sb.append(context.getString(R.string.briefing_no_events)).append(' ')
        } else {
            sb.append(context.getString(R.string.briefing_events_prefix, todayEvents.size))
            sb.append(' ')
            sb.append(todayEvents.take(3).joinToString(", ") { it.title })
            sb.append(". ")
        }

        // 5. Учёба за вчера.
        val yesterday = today.minusDays(1)
        val yesterdayKey = yesterday.format(dateFormat)
        val zone = ZoneId.systemDefault()
        val periodStart = yesterday.atStartOfDay(zone).toInstant().toEpochMilli()
        val periodEnd = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val canonicalStudy = activityRecordDao.observeOverlapping(periodStart, periodEnd)
            .first()
            .asSequence()
            .filter { it.snapshotActivityType() == FocusActivityType.STUDY }
            .mapNotNull { record ->
                val end = record.effectiveActivityEndMillis()
                if (end > record.startedAt) StudyTimeInterval(record.startedAt, end) else null
            }
            .toList()
        val legacyStudy = if (canonicalStudy.isEmpty()) {
            studySessionDao.observeByDate(yesterdayKey).first().mapNotNull { session ->
                val end = minOf(session.endMillis, session.startMillis + session.durationMillis)
                if (end > session.startMillis) StudyTimeInterval(session.startMillis, end) else null
            }
        } else {
            emptyList()
        }
        val studyTotal = StudyActivityDurationCalculator.calculate(
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            canonicalIntervals = canonicalStudy,
            legacyIntervals = legacyStudy
        ) / 60_000

        if (studyTotal > 0) {
            sb.append(context.getString(R.string.briefing_study_yesterday, studyTotal.toInt()))
            sb.append(' ')
        }

        val adaptive = AdaptiveRecommendationRepository(AppDatabase.getInstance(context))
            .rank(taskDao.getAll(), System.currentTimeMillis())
        adaptive.orderedTasks.firstOrNull()?.let { next ->
            sb.append(
                context.getString(
                    if (adaptive.ranking.isAdaptive) R.string.briefing_adaptive_next
                    else R.string.briefing_default_next,
                    next.primaryLabel(),
                    adaptive.personalState.estimatedEnergy.toInt().coerceIn(1, 10)
                )
            ).append(' ')
        }

        // 6. Required daily focus plan. Planning uses the civil local day
        // (00:00–00:00); the separate 04:00 analytics boundary is untouched.
        appendDailyPlanStatus(context, sb, today, zone, adaptive.orderedTasks)

        return sb.toString().trim()
    }

    private suspend fun appendDailyPlanStatus(
        context: Context,
        sb: StringBuilder,
        today: LocalDate,
        zone: ZoneId,
        tasks: List<com.personal.sleepalarm.data.db.entity.TaskEntity>
    ) {
        val nowMillis = System.currentTimeMillis()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val dayStartMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val nextMidnightMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val settings = DailyPlanNudgePreferences(context).get()
        if (!settings.enabled || !settings.morningReminderEnabled) return
        val automation = SleepAutomationPreference(context).get()
        val alarmProfile = alarmProfileDao.getProfile()
        val cutoff = DailyPlanScheduleCalculator.effectiveCutoff(
            now = now,
            fallbackCutoffMinutesOfDay = settings.cutoffMinutesOfDay,
            sleepAutomation = DailyPlanSleepAutomationInput(
                enabled = automation.enabled && alarmProfile?.autoDetectOnsetEnabled == true,
                windowStartMinutes = automation.windowStartMinutes,
                windowEndMinutes = automation.windowEndMinutes,
                skippedWindowStartEpochDay = automation.skippedWindowStartEpochDay
            )
        ).at.toInstant().toEpochMilli()
        val records = activityRecordDao.observeOverlapping(dayStartMillis, nextMidnightMillis).first()
        val plan = calculateDailyBriefingPlan(
            tasks = tasks,
            records = records,
            nowMillis = nowMillis,
            dayStartMillis = dayStartMillis,
            nextMidnightMillis = nextMidnightMillis,
            cutoffMillis = cutoff,
            bufferMinutes = settings.bufferMinutes
        )
        val snapshot = plan.snapshot
        if (!snapshot.hasRequiredTasks) return

        if (plan.unstartedTasks.isNotEmpty()) {
            sb.append(context.getString(R.string.briefing_daily_not_started_prefix)).append(' ')
            sb.append(
                plan.unstartedTasks.take(MAX_BRIEFING_TASKS).joinToString(", ") { task ->
                    context.getString(
                        R.string.briefing_daily_task_item,
                        task.title,
                        task.effectiveTargetMinutes
                    )
                }
            ).append(". ")
            val hidden = plan.unstartedTasks.size - MAX_BRIEFING_TASKS
            if (hidden > 0) {
                sb.append(context.getString(R.string.briefing_daily_more, hidden)).append(' ')
            }
        }

        if (snapshot.totalRemainingMinutes <= 0) {
            sb.append(context.getString(R.string.briefing_daily_complete)).append(' ')
            return
        }
        sb.append(
            context.getString(R.string.briefing_daily_remaining, snapshot.totalRemainingMinutes)
        ).append(' ')
        if (snapshot.shouldNudge) {
            if (snapshot.isOverloaded) {
                sb.append(
                    context.getString(
                        R.string.briefing_daily_overloaded_warning,
                        -snapshot.slackMinutes.toLong()
                    )
                ).append(' ')
            } else {
                sb.append(
                    context.getString(
                        R.string.briefing_daily_slack_warning,
                        snapshot.slackMinutes
                    )
                ).append(' ')
            }
        }
    }

    private companion object {
        const val MAX_BRIEFING_TASKS = 3
    }
}
