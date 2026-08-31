package com.personal.sleepalarm.service.ai

import android.content.Context
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.domain.model.ordinaryTasks
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.domain.model.effectiveWorkBudgetMinutes
import com.personal.sleepalarm.domain.model.effectiveSleepStartMillis
import com.personal.sleepalarm.data.repository.AdaptiveRecommendationRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Личный оффлайн-ассистент на правилах (НЕ генеративный LLM).
 *
 * Отвечает на типовые вопросы, подставляя локальные данные
 * и предсказания модели. Никакого интернета.
 */
class PersonalAssistant(
    private val context: Context,
    private val database: AppDatabase,
    private val predictor: SleepPredictor
) {

    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE
    private val zone = ZoneId.systemDefault()
    private val adaptiveRecommendations = AdaptiveRecommendationRepository(database)

    /** Возвращает текстовый ответ на вопрос. */
    suspend fun answer(question: String): String {
        val q = question.lowercase()

        return when {
            q.contains("спал") || q.contains("сколько") && q.contains("сон") ||
                    q.contains("сон") || q.contains("sleep") || q.contains("slept") -> sleepAnswer()

            q.contains("задач") || q.contains("дел") || q.contains("task") ||
                    q.contains("todo") -> tasksAnswer()

            q.contains("дедлайн") || q.contains("dday") || q.contains("событ") ||
                    q.contains("отсчёт") || q.contains("deadline") || q.contains("event") ||
                    q.contains("countdown") -> ddayAnswer()

            q.contains("утро") || q.contains("подъём") || q.contains("проснусь") ||
                    q.contains("какое") || q.contains("morning") || q.contains("wake") -> morningAnswer()

            q.contains("настроен") || q.contains("mood") || q.contains("feel") -> moodAnswer()

            q.contains("snooze") || q.contains("отложить") || q.contains("повтор") ||
                    q.contains("repeat") ->
                snoozeAnswer()

            q.contains("статистик") || q.contains("средн") || q.contains("statistic") ||
                    q.contains("average") -> statsAnswer()

            q.contains("привет") || q.contains("здравствуй") || q.contains("hello") ||
                    q.contains("hi") ->
                context.getString(R.string.assistant_greeting_back)

            else -> context.getString(R.string.assistant_fallback)
        }
    }

    // =====================================================================
    // Ответы по темам
    // =====================================================================

    private suspend fun sleepAnswer(): String {
        val session = database.sleepSessionDao().getLatestCompleted()
            ?: return context.getString(R.string.assistant_no_sleep_data)

        val wakeTime = session.actualWakeTime
            ?: return context.getString(R.string.assistant_no_sleep_data)
        val minutes = (wakeTime - session.effectiveSleepStartMillis()) / 60_000
        return context.getString(
            R.string.assistant_sleep_answer,
            minutes.toInt(),
            session.cyclesPlanned
        )
    }

    private suspend fun tasksAnswer(): String {
        val tasks = database.taskDao().getAll()
        val pending = adaptiveRecommendations.rank(tasks).orderedTasks
        return if (pending.isEmpty()) {
            context.getString(R.string.assistant_no_tasks)
        } else {
            val now = System.currentTimeMillis()
            val overdue = pending.filter { it.dueAtMillis?.let { due -> due < now } == true }
            val dueSoon = pending
                .filter { it.dueAtMillis?.let { due -> due >= now } == true }
                .sortedBy { it.dueAtMillis }
            buildString {
                append(context.getString(R.string.assistant_tasks_answer, pending.size))
                if (overdue.isNotEmpty()) {
                    append("\n")
                    append(context.getString(R.string.assistant_tasks_overdue, overdue.size))
                }
                append("\n")
                append(pending.take(5).joinToString("\n") { task ->
                    val budget = task.effectiveWorkBudgetMinutes()
                    val progress = if (budget > 0) {
                        " ${task.spentMillis / 60_000}/$budget"
                    } else ""
                    "• ${task.primaryLabel()}$progress"
                })
                dueSoon.firstOrNull()?.let { next ->
                    append("\n")
                    append(
                        context.getString(
                            R.string.assistant_tasks_next_deadline,
                            next.primaryLabel()
                        )
                    )
                }
            }
        }
    }

    private suspend fun ddayAnswer(): String {
        val today = LocalDate.now().format(dateFormat)
        val nearest = database.ddayDao().getNearest(today)
            ?: return context.getString(R.string.assistant_no_dday)

        val days = ChronoUnit.DAYS.between(
            LocalDate.now(),
            LocalDate.parse(nearest.targetDate, dateFormat)
        ).toInt()

        return if (days == 0) {
            context.getString(R.string.assistant_dday_today, nearest.title)
        } else {
            context.getString(R.string.assistant_dday_answer, nearest.title, days)
        }
    }

    private suspend fun morningAnswer(): String {
        val predicted = predictTomorrow()
            ?: return context.getString(R.string.assistant_not_trained)

        val heavy = predictor.isHeavyMorning(predicted)
        return if (heavy) {
            context.getString(R.string.assistant_morning_heavy, "%.1f".format(predicted))
        } else {
            context.getString(R.string.assistant_morning_good, "%.1f".format(predicted))
        }
    }

    private suspend fun moodAnswer(): String {
        val today = LocalDate.now().format(dateFormat)
        val entry = database.moodEntryDao().getByDate(today)
        return if (entry != null) {
            context.getString(R.string.assistant_mood_answer, entry.mood)
        } else {
            context.getString(R.string.assistant_no_mood)
        }
    }

    private suspend fun snoozeAnswer(): String {
        val predicted = predictTomorrow()
        return if (predicted == null) {
            context.getString(R.string.assistant_snooze_default)
        } else {
            val limit = predictor.recommendSnoozeLimit(predicted)
            context.getString(R.string.assistant_snooze_answer, limit)
        }
    }

    private suspend fun statsAnswer(): String {
        val sessions = database.sleepSessionDao().getLatestCompleted()
        val moods = database.moodEntryDao().getByDate(LocalDate.now().format(dateFormat))

        val sb = StringBuilder()
        if (sessions != null && sessions.actualWakeTime != null) {
            val minutes = (sessions.actualWakeTime - sessions.effectiveSleepStartMillis()) / 60_000
            sb.append(context.getString(R.string.assistant_stats_sleep, minutes.toInt()))
        }
        if (moods != null) {
            sb.append("\n").append(context.getString(R.string.assistant_stats_mood, moods.mood))
        }
        return if (sb.isEmpty()) context.getString(R.string.assistant_no_data) else sb.toString()
    }

    // =====================================================================
    // Предсказание на основе последней ночи
    // =====================================================================

    private suspend fun predictTomorrow(): Double? {
        if (!predictor.isTrained) return null

        val session = database.sleepSessionDao().getLatestCompleted() ?: return null
        val wake = session.actualWakeTime ?: return null

        val sleepMinutes = (wake - session.effectiveSleepStartMillis()) / 60_000.0
        val sleepStartZdt = Instant.ofEpochMilli(session.effectiveSleepStartMillis()).atZone(zone)
        // Минуты от 18:00 до засыпания (сон обычно вечером).
        val onsetOffset = minutesSinceEvening(sleepStartZdt)
        val dayOfWeek = (Instant.ofEpochMilli(wake).atZone(zone).dayOfWeek.value - 1).toDouble()

        val previousDate = Instant.ofEpochMilli(wake).atZone(zone).toLocalDate().minusDays(1).toString()
        val previousTasks = database.taskDao().getAll().ordinaryTasks().count { it.doneDate == previousDate }
        val features = doubleArrayOf(
            sleepMinutes,
            onsetOffset.toDouble(),
            session.cyclesPlanned.toDouble(),
            previousTasks.toDouble(),
            0.0,   // prevMood — упрощённо
            dayOfWeek
        )
        return predictor.predict(features)
    }

    private fun minutesSinceEvening(zdt: java.time.ZonedDateTime): Int {
        val evening = zdt.toLocalDate().atTime(18, 0).atZone(zone)
        val base = if (zdt.isBefore(evening)) evening.minusDays(1) else evening
        return ChronoUnit.MINUTES.between(base, zdt).toInt().coerceAtLeast(0)
    }
}
