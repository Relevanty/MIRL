package com.personal.sleepalarm.service

import android.content.Context
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.dao.CalendarEventDao
import com.personal.sleepalarm.data.db.dao.DDayDao
import com.personal.sleepalarm.data.db.dao.SleepSessionDao
import com.personal.sleepalarm.data.db.dao.StudySessionDao
import com.personal.sleepalarm.ui.calendar.eventsOn
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

class BriefingTextBuilder(
    private val calendarEventDao: CalendarEventDao,
    private val studySessionDao: StudySessionDao,
    private val ddayDao: DDayDao,
    private val sessionDao: SleepSessionDao
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
        val studyList = studySessionDao.observeByDate(yesterdayKey).first()
        val studyTotal = studyList.sumOf { it.durationMillis } / 60_000

        if (studyTotal > 0) {
            sb.append(context.getString(R.string.briefing_study_yesterday, studyTotal.toInt()))
            sb.append(' ')
        }

        return sb.toString().trim()
    }
}