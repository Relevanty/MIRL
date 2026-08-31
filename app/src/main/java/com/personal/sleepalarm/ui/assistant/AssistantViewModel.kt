package com.personal.sleepalarm.ui.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.preferences.DailyPlanNudgePreferences
import com.personal.sleepalarm.data.preferences.AppSignalPreferences
import com.personal.sleepalarm.data.preferences.AppSignalType
import com.personal.sleepalarm.data.preferences.AppSoundMode
import com.personal.sleepalarm.data.preferences.AppSoundSelection
import com.personal.sleepalarm.service.ai.PersonalAssistant
import com.personal.sleepalarm.service.ai.SleepPredictor
import com.personal.sleepalarm.service.ai.TrainingSample
import com.personal.sleepalarm.app.App
import com.personal.sleepalarm.service.audio.VoiceScenario
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.ordinaryTasks
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.domain.model.effectiveSleepStartMillis
import com.personal.sleepalarm.data.repository.AdaptiveRecommendationRepository
import com.personal.sleepalarm.domain.model.nextFocusDurationMinutes
import com.personal.sleepalarm.domain.assistant.DailyPlanCommand
import com.personal.sleepalarm.domain.assistant.DailyPlanCommandError
import com.personal.sleepalarm.domain.assistant.DailyPlanCommandParser
import com.personal.sleepalarm.domain.assistant.DailyPlanParseResult
import com.personal.sleepalarm.domain.assistant.DailyPlanTaskMatch
import com.personal.sleepalarm.domain.assistant.DailyPlanTaskMatcher
import com.personal.sleepalarm.domain.assistant.DailyPlanSignalMode
import com.personal.sleepalarm.domain.coordinator.TaskLifecycleCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
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

data class ActivityGapSuggestion(
    val startMillis: Long,
    val endMillis: Long
)

data class AssistantProposedAction(
    val taskId: Int,
    val title: String,
    val focusMinutes: Int
)

internal fun TaskEntity.toAssistantProposedAction(): AssistantProposedAction? {
    val focusMinutes = nextFocusDurationMinutes()
    if (focusMinutes <= 0) return null
    return AssistantProposedAction(
        taskId = id,
        title = primaryLabel(),
        focusMinutes = focusMinutes
    )
}

internal fun AssistantProposedAction.refreshFrom(
    tasks: List<TaskEntity>
): AssistantProposedAction? = tasks
    .firstOrNull { it.id == taskId && !it.isDone && !it.isMorningRoutine }
    ?.toAssistantProposedAction()

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
    private val adaptiveRecommendations = AdaptiveRecommendationRepository(database)
    private val voice = (application as App).serviceLocator.briefingCoordinator
    private val taskLifecycle = TaskLifecycleCoordinator(context, database)
    private val dailyPlanPreferences = DailyPlanNudgePreferences(context)
    private val appSignalPreferences = AppSignalPreferences(context)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _insights = MutableStateFlow(AssistantInsights())
    val insights: StateFlow<AssistantInsights> = _insights
    private val _activityGap = MutableStateFlow<ActivityGapSuggestion?>(null)
    val activityGap: StateFlow<ActivityGapSuggestion?> = _activityGap
    private val _proposedAction = MutableStateFlow<AssistantProposedAction?>(null)
    val proposedAction: StateFlow<AssistantProposedAction?> = _proposedAction
    private val _pendingDailyPlanChange = MutableStateFlow<PendingDailyPlanChange?>(null)
    val pendingDailyPlanChange: StateFlow<PendingDailyPlanChange?> = _pendingDailyPlanChange

    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE
    private val zone = ZoneId.systemDefault()
    private val assistantState = context.getSharedPreferences("assistant_state", 0)

    init {
        viewModelScope.launch {
            combine(
                database.sleepSessionDao().observeLatestCompleted(),
                database.moodEntryDao().observeAll(),
                database.taskDao().observeAll(),
                database.activityRecordDao().observeAll()
            ) { _, _, tasks, _ -> tasks }
                .debounce(350L)
                .collectLatest { tasks ->
                    _proposedAction.value = _proposedAction.value?.refreshFrom(tasks)
                    val samples = withContext(Dispatchers.IO) { buildTrainingSamples() }
                    withContext(Dispatchers.Default) { predictor.train(samples) }
                    _insights.value = buildInsights(samples.size)
                    detectUntrackedAfternoon()
                }
        }
    }

    fun dismissActivityGap() {
        _activityGap.value = null
        assistantState.edit().putString(KEY_GAP_DISMISSED_DATE, LocalDate.now().toString()).apply()
    }

    fun snoozeActivityGap() {
        _activityGap.value = null
        assistantState.edit()
            .putLong(KEY_GAP_SNOOZE_UNTIL, System.currentTimeMillis() + GAP_SNOOZE_MS)
            .apply()
    }

    fun dismissProposedAction() {
        _proposedAction.value = null
    }

    fun stopVoiceForListening() {
        voice.stop()
    }

    fun cancelDailyPlanChange() {
        if (_pendingDailyPlanChange.value == null) return
        _pendingDailyPlanChange.value = null
        appendAssistantMessage(context.getString(R.string.daily_plan_change_cancelled))
    }

    fun confirmDailyPlanChange() {
        val pending = _pendingDailyPlanChange.value ?: return
        // Remove the actionable card immediately. A second tap can no longer
        // race the first write; failures are reported as ordinary chat text.
        _pendingDailyPlanChange.value = null
        viewModelScope.launch {
            val applied = when (pending) {
                is PendingDailyPlanChange.TaskChange -> applyTaskPlanChange(pending)
                is PendingDailyPlanChange.GlobalChange -> applyGlobalPlanChange(pending.command)
            }
            val message = context.getString(
                if (applied == DailyPlanApplyResult.APPLIED) {
                    R.string.daily_plan_change_applied
                } else if (applied == DailyPlanApplyResult.STALE) {
                    R.string.daily_plan_change_stale
                } else {
                    R.string.daily_plan_change_failed
                }
            )
            appendAssistantMessage(message)
            voice.speak(message, VoiceScenario.ASSISTANT) {}
        }
    }

    private suspend fun detectUntrackedAfternoon() {
        val today = LocalDate.now()
        if (assistantState.getString(KEY_GAP_DISMISSED_DATE, null) == today.toString() ||
            assistantState.getLong(KEY_GAP_SNOOZE_UNTIL, 0L) > System.currentTimeMillis()
        ) {
            _activityGap.value = null
            return
        }
        val start = today.atTime(16, 0).atZone(zone).toInstant().toEpochMilli()
        val plannedEnd = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val end = minOf(System.currentTimeMillis(), plannedEnd)
        if (end - start < 30L * 60_000L) return
        _activityGap.value = if (database.activityRecordDao().findOverlaps(start, end).isEmpty()) {
            ActivityGapSuggestion(start, end)
        } else null
    }

    fun ask(question: String) {
        val text = question.trim()
        if (text.isBlank()) return

        _messages.value = _messages.value + ChatMessage(fromUser = true, text = text)

        viewModelScope.launch {
            when (val parsed = DailyPlanCommandParser.parse(text)) {
                DailyPlanParseResult.NotCommand -> Unit
                is DailyPlanParseResult.Invalid -> {
                    appendAssistantMessage(commandErrorMessage(parsed.error))
                    return@launch
                }
                is DailyPlanParseResult.Parsed -> {
                    prepareDailyPlanConfirmation(parsed.command)
                    return@launch
                }
            }
            val lower = text.lowercase()
            if (lower.contains("что лучше") || lower.contains("что сделать") || lower.contains("следующую задачу")) {
                val now = System.currentTimeMillis()
                val recommendation = adaptiveRecommendations.rank(
                    tasks = database.taskDao().getAll(),
                    nowMillis = now
                )
                val task = recommendation.orderedTasks
                    .firstOrNull { it.nextFocusDurationMinutes() > 0 }
                if (task != null) {
                    _proposedAction.value = task.toAssistantProposedAction()
                    _messages.value = _messages.value + ChatMessage(
                        fromUser = false,
                        text = if ((task.dueAtMillis ?: Long.MAX_VALUE) < now) {
                            "Эта задача просрочена и сейчас важнее остальных. Я подготовил действие — проверьте его ниже."
                        } else if (recommendation.ranking.isAdaptive) {
                            val energy = recommendation.personalState.estimatedEnergy.toInt().coerceIn(1, 10)
                            "Сейчас ваша оценочная энергия около $energy из 10, и эта задача лучше других совпадает с доступной нагрузкой. Дедлайны и обязательные шаги сохранены."
                        } else {
                            "Данных об энергии пока мало, поэтому я сохранил обычный порядок по квадранту и сроку. Я ничего не запущу без подтверждения."
                        }
                    )
                    return@launch
                }
            }
            val answer = assistant.answer(text)
            _messages.value = _messages.value + ChatMessage(fromUser = false, text = answer)
            voice.speak(answer, VoiceScenario.ASSISTANT) {}
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
        val taskCounts = database.taskDao().getAll()
            .ordinaryTasks()
            .filter(TaskEntity::isDone)
            .mapNotNull(TaskEntity::doneDate)
            .groupingBy { it }
            .eachCount()

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
            val effectiveStart = session.effectiveSleepStartMillis()
            val sleepMinutes = (wake - effectiveStart) / 60_000.0
            val onsetOffset = minutesSinceEvening(
                Instant.ofEpochMilli(effectiveStart).atZone(zone)
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

        val effectiveStart = session.effectiveSleepStartMillis()
        val sleepMinutes = (wake - effectiveStart) / 60_000.0
        val onsetOffset = minutesSinceEvening(
            Instant.ofEpochMilli(effectiveStart).atZone(zone)
        )
        val dayOfWeek = (Instant.ofEpochMilli(wake).atZone(zone).dayOfWeek.value - 1).toDouble()
        val previousDate = Instant.ofEpochMilli(wake).atZone(zone).toLocalDate().minusDays(1).toString()
        val previousTaskCount = database.taskDao().getAll().ordinaryTasks()
            .count { it.doneDate == previousDate }

        return predictor.predict(
            doubleArrayOf(
                sleepMinutes,
                onsetOffset.toDouble(),
                session.cyclesPlanned.toDouble(),
                previousTaskCount.toDouble(),
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

    private suspend fun prepareDailyPlanConfirmation(command: DailyPlanCommand) {
        _proposedAction.value = null
        val pending = when (command) {
            is DailyPlanCommand.SetTaskDailyTarget -> resolveTaskChange(
                query = command.taskQuery,
                field = AssistantTaskPlanField.DAILY_TARGET,
                newIntValue = command.minutes
            )
            is DailyPlanCommand.SetTaskBoutDuration -> resolveTaskChange(
                query = command.taskQuery,
                field = AssistantTaskPlanField.BOUT_DURATION,
                newIntValue = command.minutes
            )
            is DailyPlanCommand.SetTaskDailyRequired -> resolveTaskChange(
                query = command.taskQuery,
                field = AssistantTaskPlanField.DAILY_REQUIRED,
                newBooleanValue = command.required
            )
            else -> prepareGlobalChange(command)
        } ?: return
        _pendingDailyPlanChange.value = pending
        val prompt = context.getString(R.string.daily_plan_confirmation_prompt)
        appendAssistantMessage(prompt)
        voice.speak(prompt, VoiceScenario.ASSISTANT) {}
    }

    private suspend fun resolveTaskChange(
        query: String,
        field: AssistantTaskPlanField,
        newIntValue: Int? = null,
        newBooleanValue: Boolean? = null
    ): PendingDailyPlanChange.TaskChange? {
        return when (val match = DailyPlanTaskMatcher.match(database.taskDao().getAll(), query)) {
            DailyPlanTaskMatch.Missing -> {
                appendAssistantMessage(context.getString(R.string.daily_plan_task_missing, query))
                null
            }
            is DailyPlanTaskMatch.Ambiguous -> {
                appendAssistantMessage(context.getString(R.string.daily_plan_task_ambiguous, query))
                null
            }
            is DailyPlanTaskMatch.Unique -> {
                val task = match.task
                PendingDailyPlanChange.TaskChange(
                    taskId = task.id,
                    taskTitle = task.primaryLabel(),
                    expectedUpdatedAt = task.updatedAt,
                    field = field,
                    oldIntValue = when (field) {
                        AssistantTaskPlanField.DAILY_TARGET -> task.plannedFocusMinutes
                        AssistantTaskPlanField.BOUT_DURATION -> task.estimatedMinutes
                        AssistantTaskPlanField.DAILY_REQUIRED -> null
                    },
                    newIntValue = newIntValue,
                    oldBooleanValue = task.isDailyRequired.takeIf {
                        field == AssistantTaskPlanField.DAILY_REQUIRED
                    },
                    newBooleanValue = newBooleanValue
                )
            }
        }
    }

    private suspend fun prepareGlobalChange(
        command: DailyPlanCommand
    ): PendingDailyPlanChange.GlobalChange? {
        val settings = dailyPlanPreferences.get()
        val values = when (command) {
            is DailyPlanCommand.SetUrgencyEnabled ->
                enabledText(settings.enabled) to enabledText(command.enabled)
            is DailyPlanCommand.SetUrgencyBuffer ->
                minutesText(settings.bufferMinutes) to minutesText(command.minutes)
            is DailyPlanCommand.SetRepeatEnabled ->
                enabledText(settings.repeatEnabled) to enabledText(command.enabled)
            is DailyPlanCommand.SetRepeatInterval ->
                minutesText(settings.repeatIntervalMinutes) to minutesText(command.minutes)
            is DailyPlanCommand.SetMorningReminderEnabled ->
                enabledText(settings.morningReminderEnabled) to enabledText(command.enabled)
            is DailyPlanCommand.SetCutoffMinutesOfDay ->
                cutoffText(settings.cutoffMinutesOfDay) to cutoffText(command.minutesOfDay)
            is DailyPlanCommand.SetDailyPlanSignalVolume -> {
                val signal = appSignalPreferences.get(AppSignalType.DAILY_PLAN)
                val legacyVolume = database.alarmProfileDao().getProfile()
                    ?.notificationVolumePercent ?: DEFAULT_SIGNAL_VOLUME
                percentText(signal.effectiveVolume(legacyVolume)) to percentText(command.percent)
            }
            is DailyPlanCommand.SetDailyPlanSignalMode -> {
                val oldMode = appSignalPreferences.get(AppSignalType.DAILY_PLAN).sound.mode
                soundModeText(oldMode) to soundModeText(command.mode)
            }
            else -> return null
        }
        return PendingDailyPlanChange.GlobalChange(command, values.first, values.second)
    }

    private suspend fun applyTaskPlanChange(
        pending: PendingDailyPlanChange.TaskChange
    ): DailyPlanApplyResult {
        val current = pending.resolveCurrentTask(database.taskDao().getById(pending.taskId))
            ?: return DailyPlanApplyResult.STALE
        val updated = when (pending.field) {
            AssistantTaskPlanField.DAILY_TARGET -> current.copy(
                plannedFocusMinutes = pending.newIntValue ?: return DailyPlanApplyResult.FAILED
            )
            AssistantTaskPlanField.BOUT_DURATION -> current.copy(
                estimatedMinutes = pending.newIntValue ?: return DailyPlanApplyResult.FAILED
            )
            AssistantTaskPlanField.DAILY_REQUIRED -> current.copy(
                isDailyRequired = pending.newBooleanValue ?: return DailyPlanApplyResult.FAILED
            )
        }
        return if (taskLifecycle.save(updated) != null) {
            DailyPlanApplyResult.APPLIED
        } else {
            DailyPlanApplyResult.FAILED
        }
    }

    private suspend fun applyGlobalPlanChange(command: DailyPlanCommand): DailyPlanApplyResult {
        return runCatching {
            when (command) {
                is DailyPlanCommand.SetUrgencyEnabled ->
                    dailyPlanPreferences.setEnabled(command.enabled)
                is DailyPlanCommand.SetUrgencyBuffer ->
                    dailyPlanPreferences.setBufferMinutes(command.minutes)
                is DailyPlanCommand.SetRepeatEnabled ->
                    dailyPlanPreferences.setRepeatEnabled(command.enabled)
                is DailyPlanCommand.SetRepeatInterval ->
                    dailyPlanPreferences.setRepeatIntervalMinutes(command.minutes)
                is DailyPlanCommand.SetMorningReminderEnabled ->
                    dailyPlanPreferences.setMorningReminderEnabled(command.enabled)
                is DailyPlanCommand.SetCutoffMinutesOfDay ->
                    dailyPlanPreferences.setCutoffMinutesOfDay(command.minutesOfDay)
                is DailyPlanCommand.SetDailyPlanSignalVolume ->
                    appSignalPreferences.setVolume(AppSignalType.DAILY_PLAN, command.percent)
                is DailyPlanCommand.SetDailyPlanSignalMode ->
                    appSignalPreferences.setSound(
                        AppSignalType.DAILY_PLAN,
                        AppSoundSelection(
                            mode = when (command.mode) {
                                DailyPlanSignalMode.SYSTEM -> AppSoundMode.SYSTEM
                                DailyPlanSignalMode.SILENT -> AppSoundMode.SILENT
                            }
                        )
                    )
                else -> return DailyPlanApplyResult.FAILED
            }
        }.fold(
            onSuccess = { DailyPlanApplyResult.APPLIED },
            onFailure = { DailyPlanApplyResult.FAILED }
        )
    }

    private fun commandErrorMessage(error: DailyPlanCommandError): String = context.getString(
        when (error) {
            DailyPlanCommandError.SYNTAX,
            DailyPlanCommandError.EMPTY_TASK -> R.string.daily_plan_command_syntax
            DailyPlanCommandError.INVALID_DURATION -> R.string.daily_plan_command_invalid_duration
            DailyPlanCommandError.DAILY_TARGET_OUT_OF_RANGE -> R.string.daily_plan_command_target_range
            DailyPlanCommandError.BOUT_DURATION_OUT_OF_RANGE -> R.string.daily_plan_command_bout_range
            DailyPlanCommandError.URGENCY_BUFFER_OUT_OF_RANGE -> R.string.daily_plan_command_buffer_range
            DailyPlanCommandError.REPEAT_INTERVAL_OUT_OF_RANGE -> R.string.daily_plan_command_repeat_range
            DailyPlanCommandError.SIGNAL_VOLUME_OUT_OF_RANGE -> R.string.daily_plan_command_signal_volume_range
            DailyPlanCommandError.INVALID_TIME -> R.string.daily_plan_command_invalid_time
        }
    )

    private fun appendAssistantMessage(text: String) {
        _messages.value = _messages.value + ChatMessage(fromUser = false, text = text)
    }

    private fun enabledText(enabled: Boolean): String = context.getString(
        if (enabled) R.string.daily_plan_value_enabled else R.string.daily_plan_value_disabled
    )

    private fun minutesText(minutes: Int): String =
        context.getString(R.string.daily_plan_value_minutes, minutes)

    private fun percentText(percent: Int): String =
        context.getString(R.string.daily_plan_value_percent, percent)

    private fun soundModeText(mode: AppSoundMode): String = context.getString(
        when (mode) {
            AppSoundMode.SYSTEM -> R.string.daily_plan_value_sound_system
            AppSoundMode.FILE -> R.string.daily_plan_value_sound_custom
            AppSoundMode.SILENT -> R.string.daily_plan_value_sound_silent
        }
    )

    private fun soundModeText(mode: DailyPlanSignalMode): String = context.getString(
        when (mode) {
            DailyPlanSignalMode.SYSTEM -> R.string.daily_plan_value_sound_system
            DailyPlanSignalMode.SILENT -> R.string.daily_plan_value_sound_silent
        }
    )

    private fun cutoffText(minutesOfDay: Int): String = "%02d:%02d".format(
        minutesOfDay.coerceIn(0, 24 * 60 - 1) / 60,
        minutesOfDay.coerceIn(0, 24 * 60 - 1) % 60
    )

    private companion object {
        const val KEY_GAP_DISMISSED_DATE = "gap_dismissed_date"
        const val KEY_GAP_SNOOZE_UNTIL = "gap_snooze_until"
        const val GAP_SNOOZE_MS = 2L * 60L * 60L * 1000L
        const val DEFAULT_SIGNAL_VOLUME = 50
    }

    private enum class DailyPlanApplyResult { APPLIED, STALE, FAILED }
}
