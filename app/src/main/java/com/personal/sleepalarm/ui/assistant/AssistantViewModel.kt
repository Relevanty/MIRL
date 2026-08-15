package com.personal.sleepalarm.ui.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.service.ai.PersonalAssistant
import com.personal.sleepalarm.service.ai.SleepPredictor
import com.personal.sleepalarm.service.ai.TrainingSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Сообщение чата. */
data class ChatMessage(
    val fromUser: Boolean,
    val text: String
)

/** Карточки предсказаний над чатом. */
data class AssistantInsights(
    val isTrained: Boolean = false,
    val trainedOn: Int = 0,
    val predictedMood: Double? = null,
    val isHeavyMorning: Boolean = false,
    val snoozeLimit: Int? = null
)

/**
 * ViewModel ассистента: обучает модель на истории и ведёт чат.
 */
class AssistantViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = AppDatabase.getInstance(context)

    private val predictor = SleepPredictor()
    private val assistant = PersonalAssistant(context, database, predictor)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _insights = MutableStateFlow(AssistantInsights())
    val insights: StateFlow<AssistantInsights> = _insights

    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE
    private val zone = ZoneId.systemDefault()

    init {
        viewModelScope.launch {
            val samples = withContext(Dispatchers.IO) { buildTrainingSamples() }
            withContext(Dispatchers.Default) { predictor.train(samples) }
            _insights.value = buildInsights(samples.size)
        }
    }

    fun ask(question: String) {
        val text = question.trim()
        if (text.isBlank()) return

        _messages.value = _messages.value + ChatMessage(fromUser = true, text = text)

        viewModelScope.launch {
            val answer = assistant.answer(text)
            _messages.value = _messages.value + ChatMessage(fromUser = false, text = answer)
        }
    }

    // =====================================================================
    // Сбор обучающих примеров
    // =====================================================================

    /**
     * Для каждого дня с настроением берём ночь перед ним (сессия, чей
     * actualWakeTime попадает в этот день) + задачи накануне.
     */
    private suspend fun buildTrainingSamples(): List<TrainingSample> {
        val sessions = database.sleepSessionDao().getAllSessions()
        val moods = database.moodEntryDao().getAll()
        val taskCounts = database.taskDao().getDoneCountsByDate().associate { it.date to it.count }

        val samples = mutableListOf<TrainingSample>()

        moods.forEach { mood ->
            val day = runCatching { LocalDate.parse(mood.date, dateFormat) }.getOrNull()
                ?: return@forEach

            // Сессия, завершившаяся в этот день.
            val session = sessions.firstOrNull { s ->
                val wake = s.actualWakeTime ?: return@firstOrNull false
                Instant.ofEpochMilli(wake).atZone(zone).toLocalDate() == day
            } ?: return@forEach

            val wake = session.actualWakeTime ?: return@forEach
            val sleepMinutes = (wake - session.estimatedSleepStartTime) / 60_000.0
            val onsetOffset = minutesSinceEvening(
                Instant.ofEpochMilli(session.estimatedSleepStartTime).atZone(zone)
            )
            val prevDay = day.minusDays(1).format(dateFormat)
            val prevTasks = taskCounts[prevDay]?.toDouble() ?: 0.0
            val dayOfWeek = (day.dayOfWeek.value - 1).toDouble()

            val features = doubleArrayOf(
                sleepMinutes,
                onsetOffset.toDouble(),
                session.cyclesPlanned.toDouble(),
                prevTasks,
                0.0, // prevMood (упрощённо)
                dayOfWeek
            )

            samples.add(TrainingSample(features, mood.mood.toDouble()))
        }

        return samples
    }

    private suspend fun buildInsights(sampleCount: Int): AssistantInsights {
        if (!predictor.isTrained) {
            return AssistantInsights(isTrained = false, trainedOn = sampleCount)
        }

        // Предсказание на последней ночи.
        val predicted = runCatching { predictFromLatestSession() }.getOrNull()

        return AssistantInsights(
            isTrained = true,
            trainedOn = sampleCount,
            predictedMood = predicted,
            isHeavyMorning = predicted?.let { predictor.isHeavyMorning(it) } ?: false,
            snoozeLimit = predicted?.let { predictor.recommendSnoozeLimit(it) }
        )
    }

    private suspend fun predictFromLatestSession(): Double? {
        val session = database.sleepSessionDao().getLatestCompleted() ?: return null
        val wake = session.actualWakeTime ?: return null

        val sleepMinutes = (wake - session.estimatedSleepStartTime) / 60_000.0
        val onsetOffset = minutesSinceEvening(
            Instant.ofEpochMilli(session.estimatedSleepStartTime).atZone(zone)
        )
        val dayOfWeek = (Instant.ofEpochMilli(wake).atZone(zone).dayOfWeek.value - 1).toDouble()

        return predictor.predict(
            doubleArrayOf(
                sleepMinutes,
                onsetOffset.toDouble(),
                session.cyclesPlanned.toDouble(),
                0.0,
                0.0,
                dayOfWeek
            )
        )
    }

    private fun minutesSinceEvening(zdt: java.time.ZonedDateTime): Int {
        val evening = zdt.toLocalDate().atTime(18, 0).atZone(zone)
        val base = if (zdt.isBefore(evening)) evening.minusDays(1) else evening
        return ChronoUnit.MINUTES.between(base, zdt).toInt().coerceAtLeast(0)
    }
}
