package com.personal.sleepalarm.ui.alarm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.R
import com.personal.sleepalarm.alarm.AlarmScheduler
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.DailyCheckInEntity
import com.personal.sleepalarm.data.db.entity.EnergyObservationEntity
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.data.repository.MoodRepository
import com.personal.sleepalarm.data.repository.SleepProfileRepository
import com.personal.sleepalarm.data.repository.SleepSessionRepository
import com.personal.sleepalarm.data.preferences.BriefingPreference
import com.personal.sleepalarm.data.preferences.DailyPlanNudgePreferences
import com.personal.sleepalarm.domain.model.DismissType
import com.personal.sleepalarm.domain.math.MathChallengeRunEffect
import com.personal.sleepalarm.domain.math.MathChallengeRunEngine
import com.personal.sleepalarm.domain.math.MathChallengeRunState
import com.personal.sleepalarm.domain.model.MathChallenge
import com.personal.sleepalarm.domain.model.MathDifficulty
import com.personal.sleepalarm.service.BriefingTextBuilder
import com.personal.sleepalarm.service.SleepForegroundService
import com.personal.sleepalarm.service.SleepNotificationBuilder
import com.personal.sleepalarm.ui.mood.MorningCheckInInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Состояние экрана будильника.
 *
 * ДОБАВЛЕНО (F1, F2, F10):
 * - vibrationEnabled — играть ли вибрацию;
 * - customRingtoneUri — пользовательская мелодия (F2);
 * - smartRepeatEnabled / smartRepeatMaxCount — параметры повторов (F10);
 * - repeatCount — сколько импульсов уже сработало;
 * - nextRepeatAtMillis — абсолютное время следующего импульса
 *   (UI рисует countdown локально по currentTime);
 * - repeatsExhausted — лимит повторов достигнут, звук на максимуме.
 */
data class AlarmUiState(
    val sessionId: Int? = null,
    val session: SleepSessionEntity? = null,
    val challenge: MathChallenge? = null,
    val userInput: String = "",
    val isAnswerCorrect: Boolean = false,
    val challengeIndex: Int = 0,
    val challengeCount: Int = 1,
    val isAdvancingChallenge: Boolean = false,
    val wrongAttempts: Int = 0,
    val showHint: Boolean = false,
    val errorMessage: String? = null,
    val isProcessing: Boolean = false,
    val snoozeConfirmVisible: Boolean = false,
    val cycleLengthMinutes: Int = 90,
    val quietAlarm: Boolean = false,
    // ДОБАВЛЕНО:
    val vibrationEnabled: Boolean = false,
    val customRingtoneUri: Uri? = null,
    val smartRepeatEnabled: Boolean = false,
    val smartRepeatMaxCount: Int = 0,
    val repeatCount: Int = 0,
    val nextRepeatAtMillis: Long? = null,
    val repeatsExhausted: Boolean = false
)

/**
 * Одноразовые события для Activity.
 */
sealed interface AlarmEvent {
    data object Finish : AlarmEvent
}

internal fun shouldIgnoreAlarmStart(
    alreadyStarted: Boolean,
    currentSessionId: Int?,
    requestedSessionId: Int?
): Boolean = alreadyStarted && (
    requestedSessionId == null || currentSessionId == requestedSessionId
)

/**
 * ViewModel экрана будильника.
 *
 * ДОБАВЛЕНО:
 * - AlarmVibrator: ALARM_RAMP при старте, cancel при остановке (F1);
 * - customRingtoneUri передаётся в AlarmSoundPlayer (F2);
 * - smart-repeat корутина в viewModelScope: импульсы = громкость↑ + REPEAT_BURST,
 *   после лимита звук фиксируется на 100% и НЕ выключается (F10);
 * - Поток пробуждения (v5): после успешного dismiss показывается диалог настроения,
 *   затем (если включён) озвучивается брифинг, затем finish().
 *
 * Существующая логика (задача, dismiss, snooze, сохранение actualWakeTime) НЕ сломана.
 */
class AlarmViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val database = AppDatabase.getInstance(context)
    private val briefingPreference = BriefingPreference(context)
    private val dailyPlanNudgePreferences = DailyPlanNudgePreferences(context)

    private val profileRepository = SleepProfileRepository(
        profileDao = database.alarmProfileDao()
    )

    private val sessionRepository = SleepSessionRepository(
        database = database,
        sessionDao = database.sleepSessionDao(),
        cueEventDao = database.cueEventDao()
    )

    private val alarmScheduler = AlarmScheduler.create(
        context = context,
        sessionRepository = sessionRepository
    )

    // === ДОБАВЛЕНО (v5): настроение + брифинг после подъёма ===
    // Координатор живёт на уровне приложения (ServiceLocator),
    // поэтому TTS переживает пересоздание Activity и готов утром.
    private val serviceLocator =
        (application as com.personal.sleepalarm.app.App).serviceLocator
    private val briefingCoordinator = serviceLocator.briefingCoordinator
    private val dailyCheckInRepository = serviceLocator.dailyCheckInRepository
    private val energyObservationRepository = serviceLocator.energyObservationRepository
    private val moodRepository = MoodRepository(database.moodEntryDao())
    private val briefingTextBuilder = BriefingTextBuilder(
        calendarEventDao = database.calendarEventDao(),
        activityRecordDao = database.activityRecordDao(),
        studySessionDao = database.studySessionDao(),
        ddayDao = database.ddayDao(),
        sessionDao = database.sleepSessionDao(),
        taskDao = database.taskDao(),
        alarmProfileDao = database.alarmProfileDao()
    )

    private val _state = MutableStateFlow(AlarmUiState())
    val state: StateFlow<AlarmUiState> = _state

    private val _events = Channel<AlarmEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // === ДОБАВЛЕНО (v5): состояния потока пробуждения ===
    private val _showMoodPicker = MutableStateFlow(false)
    val showMoodPicker: StateFlow<Boolean> = _showMoodPicker

    private val _isBriefingPlaying = MutableStateFlow(false)
    val isBriefingPlaying: StateFlow<Boolean> = _isBriefingPlaying

    private var started = false

    // ДОБАВЛЕНО (F10): корутина умных повторов.
    private var smartRepeatJob: Job? = null
    private var challengeAdvanceJob: Job? = null
    private var challengeRun: MathChallengeRunState? = null

    /**
     * Запускает экран будильника для sessionId.
     *
     * Если sessionId < 0 — использует активную сессию.
     */
    fun startAlarm(sessionId: Int) {
        val targetSessionId = sessionId.takeIf { it >= 0 }

        // AlarmActivity can be recreated with the legacy -1 extra. In that case targetSessionId is
        // null while state already contains the resolved active id; restarting here would reset the
        // sound service and the in-progress maths sequence.
        if (shouldIgnoreAlarmStart(started, _state.value.sessionId, targetSessionId)) {
            return
        }

        started = true

        // Останавливаем предыдущие повторы на случай повторного запуска.
        stopSmartRepeat()

        viewModelScope.launch {
            val targetSession = if (targetSessionId != null) {
                sessionRepository.getSession(targetSessionId)
            } else {
                null
            }

            val session = targetSession ?: sessionRepository.getActiveSession()
            val now = System.currentTimeMillis()
            if (session == null ||
                !session.isActive ||
                session.estimatedWakeTime > now + ALARM_EARLY_TOLERANCE_MS ||
                now > session.estimatedWakeTime + ALARM_LATE_TOLERANCE_MS
            ) {
                _events.send(AlarmEvent.Finish)
                return@launch
            }

            val profile = profileRepository.getProfile()

            challengeAdvanceJob?.cancel()
            val run = restoreOrCreateChallengeRun(
                sessionId = session.id,
                estimatedWakeTime = session.estimatedWakeTime,
                profileDifficulty = profile.mathDifficulty,
                profileChallengeCount = profile.mathChallengeCount
            )
            challengeRun = run
            persistChallengeRun(session.id, run)

            // ДОБАВЛЕНО (F2): парсим пользовательскую мелодию.
            val customUri = profile.alarmRingtoneUri?.let { raw ->
                runCatching { Uri.parse(raw) }.getOrNull()
            }

            _state.value = AlarmUiState(
                sessionId = session?.id,
                session = session,
                challenge = run.currentChallenge,
                userInput = run.userInput,
                isAnswerCorrect = run.isComplete,
                challengeIndex = run.currentIndex,
                challengeCount = run.challengeCount,
                isAdvancingChallenge = run.isTransitioning,
                wrongAttempts = run.wrongAttempts,
                showHint = run.wrongAttempts >= MAX_WRONG_ATTEMPTS_BEFORE_HINT,
                errorMessage = null,
                isProcessing = false,
                snoozeConfirmVisible = false,
                cycleLengthMinutes = session?.cycleLengthMinutes
                    ?: profile.cycleLengthMinutes,
                quietAlarm = profile.quietAlarmEnabled,
                // ДОБАВЛЕНО:
                vibrationEnabled = profile.vibrationEnabled,
                customRingtoneUri = customUri,
                smartRepeatEnabled = profile.smartRepeatEnabled,
                smartRepeatMaxCount = profile.smartRepeatMaxCount,
                repeatCount = 0,
                nextRepeatAtMillis = null,
                repeatsExhausted = false
            )

            if (run.isTransitioning) {
                scheduleChallengeAdvance()
            }

            // Foreground service держит звук и вибрацию независимо от Activity.
            SleepForegroundService.triggerAlarm(context, session.id)

            // ДОБАВЛЕНО (F10): умные повторы.
            if (profile.smartRepeatEnabled && !run.isComplete) {
                startSmartRepeat(
                    firstDelayMinutes = profile.smartRepeatFirstDelayMinutes,
                    intervalMinutes = profile.smartRepeatIntervalMinutes,
                    maxCount = profile.smartRepeatMaxCount,
                    vibrationEnabled = profile.vibrationEnabled
                )
            }
        }
    }

    /**
     * Обновляет пользовательский ввод.
     */
    fun onInputChanged(text: String) {
        val currentRun = challengeRun ?: return
        val updated = MathChallengeRunEngine.updateInput(currentRun, text)
        if (updated == currentRun) return
        challengeRun = updated
        _state.value = _state.value.copy(userInput = updated.userInput, errorMessage = null)
        _state.value.sessionId?.let { persistChallengeRun(it, updated) }
    }

    /**
     * Проверяет ответ.
     *
     * Промежуточный правильный ответ только переводит серию к следующей задаче. Повторы и
     * вибрация останавливаются после последней задачи, когда становится доступно выключение.
     */
    fun checkAnswer() {
        val currentState = _state.value
        val currentRun = challengeRun ?: return

        if (
            currentState.isAnswerCorrect ||
            currentState.isAdvancingChallenge ||
            currentState.isProcessing
        ) {
            return
        }

        val update = MathChallengeRunEngine.check(currentRun)
        val updatedRun = update.state
        challengeRun = updatedRun

        val errorMessage = when (val effect = update.effect) {
            is MathChallengeRunEffect.Invalid -> context.getString(
                if (effect.reason == com.personal.sleepalarm.domain.model.MathAnswerParseError.EMPTY) {
                    R.string.alarm_error_empty_answer
                } else {
                    R.string.alarm_error_answer_format
                }
            )
            MathChallengeRunEffect.Incorrect -> context.getString(R.string.alarm_wrong_answer)
            else -> null
        }

        _state.value = currentState.copy(
            challenge = updatedRun.currentChallenge,
            userInput = updatedRun.userInput,
            isAnswerCorrect = updatedRun.isComplete,
            challengeIndex = updatedRun.currentIndex,
            challengeCount = updatedRun.challengeCount,
            isAdvancingChallenge = updatedRun.isTransitioning,
            wrongAttempts = updatedRun.wrongAttempts,
            showHint = updatedRun.wrongAttempts >= MAX_WRONG_ATTEMPTS_BEFORE_HINT,
            errorMessage = errorMessage,
            nextRepeatAtMillis = if (updatedRun.isComplete) null else currentState.nextRepeatAtMillis
        )
        currentState.sessionId?.let { persistChallengeRun(it, updatedRun) }

        when (update.effect) {
            MathChallengeRunEffect.Advance -> scheduleChallengeAdvance()
            MathChallengeRunEffect.Completed -> {
                // Only the final solved task may stop repeat escalation and unlock dismiss.
                stopSmartRepeat()
                SleepForegroundService.stopAlarmVibration(context)
            }
            else -> Unit
        }
    }

    private fun scheduleChallengeAdvance() {
        challengeAdvanceJob?.cancel()
        challengeAdvanceJob = viewModelScope.launch {
            delay(CHALLENGE_ADVANCE_DELAY_MS)
            if (_state.value.isProcessing) return@launch

            val currentRun = challengeRun ?: return@launch
            val advanced = MathChallengeRunEngine.advance(currentRun)
            if (advanced == currentRun) return@launch

            challengeRun = advanced
            _state.update { state ->
                state.copy(
                    challenge = advanced.currentChallenge,
                    userInput = advanced.userInput,
                    isAnswerCorrect = advanced.isComplete,
                    challengeIndex = advanced.currentIndex,
                    challengeCount = advanced.challengeCount,
                    isAdvancingChallenge = advanced.isTransitioning,
                    wrongAttempts = advanced.wrongAttempts,
                    showHint = false,
                    errorMessage = null
                )
            }
            _state.value.sessionId?.let { persistChallengeRun(it, advanced) }
        }
    }

    private fun restoreOrCreateChallengeRun(
        sessionId: Int,
        estimatedWakeTime: Long,
        profileDifficulty: MathDifficulty,
        profileChallengeCount: Int
    ): MathChallengeRunState {
        val savedSessionId = savedStateHandle.get<Int>(KEY_RUN_SESSION_ID)
        if (savedSessionId != sessionId) {
            return MathChallengeRunEngine.start(
                difficulty = profileDifficulty,
                challengeCount = profileChallengeCount,
                seed = alarmRunSeed(sessionId, estimatedWakeTime)
            )
        }

        val difficulty = savedStateHandle.get<String>(KEY_RUN_DIFFICULTY)
            ?.let { name -> runCatching { MathDifficulty.valueOf(name) }.getOrNull() }
            ?: profileDifficulty
        return MathChallengeRunEngine.restore(
            difficulty = difficulty,
            challengeCount = savedStateHandle.get<Int>(KEY_RUN_COUNT) ?: profileChallengeCount,
            seed = savedStateHandle.get<Int>(KEY_RUN_SEED)
                ?: alarmRunSeed(sessionId, estimatedWakeTime),
            currentIndex = savedStateHandle.get<Int>(KEY_RUN_INDEX) ?: 0,
            completedCount = savedStateHandle.get<Int>(KEY_RUN_COMPLETED) ?: 0,
            userInput = savedStateHandle.get<String>(KEY_RUN_INPUT).orEmpty(),
            wrongAttempts = savedStateHandle.get<Int>(KEY_RUN_WRONG) ?: 0,
            totalWrongAttempts = savedStateHandle.get<Int>(KEY_RUN_TOTAL_WRONG) ?: 0,
            totalAttempts = savedStateHandle.get<Int>(KEY_RUN_TOTAL_ATTEMPTS) ?: 0,
            isTransitioning = savedStateHandle.get<Boolean>(KEY_RUN_TRANSITIONING) ?: false,
            isComplete = savedStateHandle.get<Boolean>(KEY_RUN_COMPLETE) ?: false
        )
    }

    private fun persistChallengeRun(sessionId: Int, run: MathChallengeRunState) {
        savedStateHandle[KEY_RUN_SESSION_ID] = sessionId
        savedStateHandle[KEY_RUN_DIFFICULTY] = run.difficulty.name
        savedStateHandle[KEY_RUN_COUNT] = run.challengeCount
        savedStateHandle[KEY_RUN_SEED] = run.seed
        savedStateHandle[KEY_RUN_INDEX] = run.currentIndex
        savedStateHandle[KEY_RUN_COMPLETED] = run.completedCount
        savedStateHandle[KEY_RUN_INPUT] = run.userInput
        savedStateHandle[KEY_RUN_WRONG] = run.wrongAttempts
        savedStateHandle[KEY_RUN_TOTAL_WRONG] = run.totalWrongAttempts
        savedStateHandle[KEY_RUN_TOTAL_ATTEMPTS] = run.totalAttempts
        savedStateHandle[KEY_RUN_TRANSITIONING] = run.isTransitioning
        savedStateHandle[KEY_RUN_COMPLETE] = run.isComplete
    }

    private fun alarmRunSeed(sessionId: Int, estimatedWakeTime: Long): Int {
        val mixed = estimatedWakeTime xor (sessionId.toLong() shl 32)
        return (mixed xor (mixed ushr 32)).toInt()
    }

    fun showSnoozeConfirmation() {
        if (_state.value.isProcessing) {
            return
        }

        _state.value = _state.value.copy(snoozeConfirmVisible = true)
    }

    fun hideSnoozeConfirmation() {
        _state.value = _state.value.copy(snoozeConfirmVisible = false)
    }

    /**
     * Выключает будильник (только после правильного ответа).
     *
     * ДОБАВЛЕНО (v5): вместо прямого Finish вызывается beginPostDismiss(),
     * который показывает диалог настроения и (если включён) озвучивает брифинг.
     */
    fun dismissAlarm() {
        val currentState = _state.value

        if (!currentState.isAnswerCorrect || currentState.isProcessing) {
            return
        }

        _state.value = currentState.copy(isProcessing = true)

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val session = currentState.session

            if (session != null && session.isActive) {
                // Finishing the session wakes the app-level morning scheduler.
                // When this alarm flow will speak the same morning briefing,
                // mark it first so no duplicate notification or sound appears.
                val voiceSettings = briefingPreference.getVoiceSettings()
                if (briefingPreference.isEnabled() && voiceSettings.morningEnabled) {
                    val localDate = java.time.Instant.ofEpochMilli(now)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                        .toString()
                    dailyPlanNudgePreferences.markMorningShown(localDate)
                }
                alarmScheduler.cancelAllAlarmsForSession(session.id)
                sessionRepository.finishSession(
                    sessionId = session.id,
                    actualWakeTime = now,
                    dismissType = DismissType.NORMAL
                )
            }

            stopAlarmComponents()

            // ДОБАВЛЕНО (v5): поток пробуждения (настроение + брифинг).
            beginPostDismiss()
        }
    }

    /**
     * Подтверждает snooze (новая сессия = now + cycleLength, без cue).
     *
     * НЕ запускает поток пробуждения — это не окончательный dismiss.
     */
    fun confirmSnooze() {
        val currentState = _state.value

        if (currentState.isProcessing) {
            return
        }

        _state.value = currentState.copy(
            isProcessing = true,
            snoozeConfirmVisible = false
        )

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val session = currentState.session

            if (session != null && session.isActive) {
                val newWakeTime = now + session.cycleLengthMinutes * MINUTE_MS

                val snoozeSessionId = sessionRepository.snoozeSession(
                    currentSession = session,
                    newWakeTime = newWakeTime
                )

                val snoozeSession = sessionRepository.getSession(snoozeSessionId)

                alarmScheduler.cancelAllAlarmsForSession(session.id)

                if (snoozeSession != null) {
                    alarmScheduler.scheduleMainAlarm(snoozeSession)
                }
            } else if (session != null) {
                alarmScheduler.cancelAllAlarmsForSession(session.id)
            }

            stopAlarmComponents()

            // Snooze — прямой Finish, без настроения/брифинга.
            _events.send(AlarmEvent.Finish)
        }
    }

    // =================================================================
    // ДОБАВЛЕНО (F10): умные повторные будильники
    // =================================================================

    /**
     * Запускает корутину повторов.
     *
     * Цикл:
     * 1. ждать firstDelayMinutes (UI видит countdown через nextRepeatAtMillis);
     * 2. до maxCount раз:
     *    - если уже отвечено правильно — выход;
     *    - импульс: громкость растёт к 100% + REPEAT_BURST вибрации;
     *    - если не последний — ждать intervalMinutes;
     * 3. после лимита (если всё ещё не отвечено) — звук на 100%, НЕ выключать.
     *
     * viewModelScope переживает поворот экрана, поэтому повторы не сбиваются
     * при конфигурационных изменениях.
     */
    private fun startSmartRepeat(
        firstDelayMinutes: Int,
        intervalMinutes: Int,
        maxCount: Int,
        vibrationEnabled: Boolean
    ) {
        smartRepeatJob?.cancel()

        smartRepeatJob = viewModelScope.launch {
            val firstMs = firstDelayMinutes.toLong() * MINUTE_MS
            val intervalMs = intervalMinutes.toLong() * MINUTE_MS

            // Countdown до первого импульса.
            _state.update {
                it.copy(nextRepeatAtMillis = System.currentTimeMillis() + firstMs)
            }
            delay(firstMs)

            for (index in 1..maxCount) {
                if (_state.value.isAnswerCorrect) break

                fireRepeatImpulse(
                    index = index,
                    maxCount = maxCount,
                    vibrationEnabled = vibrationEnabled
                )

                if (index < maxCount) {
                    _state.update {
                        it.copy(nextRepeatAtMillis = System.currentTimeMillis() + intervalMs)
                    }
                    delay(intervalMs)
                }
            }

            // Лимит достигнут, а задача не решена → звук на максимум, не выключаем.
            if (!_state.value.isAnswerCorrect) {
                SleepForegroundService.setAlarmVolume(context, END_VOLUME)
                _state.update {
                    it.copy(
                        repeatsExhausted = true,
                        nextRepeatAtMillis = null
                    )
                }
            }
        }
    }

    /**
     * Один импульс повтора.
     *
     * Громкость растёт от SMART_REPEAT_START_VOLUME к 100% пропорционально
     * прогрессу (index / maxCount). setVolumeFraction отменяет ramp, чтобы
     * зафиксировать заданный уровень.
     *
     * Вибрация — постоянный REPEAT_BURST (тактильный якорь импульса);
     * нарастание интенсивности пробуждения идёт через громкость звука
     * (см. допущение в заголовке Части 6).
     */
    private fun fireRepeatImpulse(
        index: Int,
        maxCount: Int,
        vibrationEnabled: Boolean
    ) {
        val progress = index.toFloat() / maxCount.toFloat()
        val volume = (SMART_REPEAT_START_VOLUME +
                (END_VOLUME - SMART_REPEAT_START_VOLUME) * progress)
            .coerceIn(0f, 1f)

        SleepForegroundService.setAlarmVolume(context, volume)

        _state.update {
            it.copy(
                repeatCount = index,
                nextRepeatAtMillis = null
            )
        }

        if (vibrationEnabled) {
            SleepForegroundService.pulseAlarmVibration(context)
        }
    }

    /**
     * Останавливает корутину повторов и сбрасывает countdown в UI.
     */
    private fun stopSmartRepeat() {
        smartRepeatJob?.cancel()
        smartRepeatJob = null
        _state.update { it.copy(nextRepeatAtMillis = null) }
    }

    // =================================================================
    // ДОБАВЛЕНО (v5): поток пробуждения (настроение + брифинг)
    // =================================================================

    /**
     * Вызывается ВМЕСТО прямого эмит Finish в конце успешного dismiss().
     * Показывает диалог настроения; озвучка и finish происходят после выбора.
     */
    private fun beginPostDismiss() {
        _showMoodPicker.value = true
    }

    /** Saves the explicit morning state without conflating mood and energy. */
    fun onMorningCheckIn(input: MorningCheckInInput) {
        _showMoodPicker.value = false

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()

            // Keep the existing five-point mood history populated for legacy statistics.
            runCatching { moodRepository.saveToday(input.mood) }
            runCatching {
                dailyCheckInRepository.save(
                    DailyCheckInEntity(
                        localDate = LocalDate.now(zone).toString(),
                        timestamp = now,
                        zoneId = zone.id,
                        energy = input.energy,
                        mood = input.mood,
                        clarity = input.clarity?.minus(1),
                        source = "ALARM"
                    )
                )
            }
            runCatching {
                energyObservationRepository.record(
                    EnergyObservationEntity(
                        timestamp = now,
                        absoluteEnergy = input.energy,
                        context = "MORNING",
                        source = "MORNING_CHECK_IN"
                    )
                )
            }

            playBriefingAndFinish()
        }
    }

    /** Skipping is a missing observation, not an invented neutral value. */
    fun skipMorningCheckIn() {
        _showMoodPicker.value = false
        viewModelScope.launch { playBriefingAndFinish() }
    }

    private suspend fun playBriefingAndFinish() {
        _isBriefingPlaying.value = true
        val text = briefingTextBuilder.build(context)
        briefingCoordinator.speak(text, com.personal.sleepalarm.service.audio.VoiceScenario.MORNING) {
            viewModelScope.launch {
                _isBriefingPlaying.value = false
                emitFinish()
            }
        }
    }
    /**
     * Эмит финиша через Channel.send().
     */
    private fun emitFinish() {
        viewModelScope.launch {
            _events.send(AlarmEvent.Finish)
        }
    }

    // =================================================================
    // Остановка компонентов
    // =================================================================

    /**
     * Останавливает звук, вибрацию, повторы, уведомление и сервис.
     */
    private fun stopAlarmComponents() {
        stopSmartRepeat()              // ДОБАВЛЕНО (F10)
        SleepNotificationBuilder.cancelAlarmNotification(context)

        SleepForegroundService.stop(
            context = context,
            cancelSession = false
        )
    }

    override fun onCleared() {
        challengeAdvanceJob?.cancel()
        stopSmartRepeat()              // ДОБАВЛЕНО (F10)
        briefingCoordinator.stop()
        super.onCleared()
    }

    companion object {
        private const val MINUTE_MS = 60L * 1000L
        private const val MAX_WRONG_ATTEMPTS_BEFORE_HINT = 3
        private const val ALARM_EARLY_TOLERANCE_MS = 5L * MINUTE_MS
        private const val ALARM_LATE_TOLERANCE_MS = 10L * MINUTE_MS
        private const val CHALLENGE_ADVANCE_DELAY_MS = 550L

        private const val KEY_RUN_SESSION_ID = "alarm_math_run_session_id"
        private const val KEY_RUN_DIFFICULTY = "alarm_math_run_difficulty"
        private const val KEY_RUN_COUNT = "alarm_math_run_count"
        private const val KEY_RUN_SEED = "alarm_math_run_seed"
        private const val KEY_RUN_INDEX = "alarm_math_run_index"
        private const val KEY_RUN_COMPLETED = "alarm_math_run_completed"
        private const val KEY_RUN_INPUT = "alarm_math_run_input"
        private const val KEY_RUN_WRONG = "alarm_math_run_wrong"
        private const val KEY_RUN_TOTAL_WRONG = "alarm_math_run_total_wrong"
        private const val KEY_RUN_TOTAL_ATTEMPTS = "alarm_math_run_total_attempts"
        private const val KEY_RUN_TRANSITIONING = "alarm_math_run_transitioning"
        private const val KEY_RUN_COMPLETE = "alarm_math_run_complete"

        // ДОБАВЛЕНО (F10): стартовая громкость первого импульса повтора.
        private const val SMART_REPEAT_START_VOLUME = 0.5f
        private const val END_VOLUME = 1.0f
    }
}
