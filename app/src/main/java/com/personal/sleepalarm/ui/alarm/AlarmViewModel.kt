package com.personal.sleepalarm.ui.alarm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.R
import com.personal.sleepalarm.alarm.AlarmScheduler
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.data.repository.MoodRepository
import com.personal.sleepalarm.data.repository.SleepProfileRepository
import com.personal.sleepalarm.data.repository.SleepSessionRepository
import com.personal.sleepalarm.domain.model.DismissType
import com.personal.sleepalarm.domain.model.MathChallenge
import com.personal.sleepalarm.service.BriefingTextBuilder
import com.personal.sleepalarm.service.SleepForegroundService
import com.personal.sleepalarm.service.SleepNotificationBuilder
import com.personal.sleepalarm.util.MathChallengeGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    application: Application
) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val database = AppDatabase.getInstance(context)

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
    private val briefingCoordinator =
        (application as com.personal.sleepalarm.app.App).serviceLocator.briefingCoordinator    // === ДОБАВЛЕНО (v5): настроение + брифинг после подъёма ===(context, briefingPreference)
    private val moodRepository = MoodRepository(database.moodEntryDao())
    private val briefingTextBuilder = BriefingTextBuilder(
        calendarEventDao = database.calendarEventDao(),
        studySessionDao = database.studySessionDao(),
        ddayDao = database.ddayDao(),
        sessionDao = database.sleepSessionDao()
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

    /**
     * Запускает экран будильника для sessionId.
     *
     * Если sessionId < 0 — использует активную сессию.
     */
    fun startAlarm(sessionId: Int) {
        val targetSessionId = sessionId.takeIf { it >= 0 }

        if (started && _state.value.sessionId == targetSessionId) {
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

            val challenge = MathChallengeGenerator.generate(
                difficulty = profile.mathDifficulty
            )

            // ДОБАВЛЕНО (F2): парсим пользовательскую мелодию.
            val customUri = profile.alarmRingtoneUri?.let { raw ->
                runCatching { Uri.parse(raw) }.getOrNull()
            }

            _state.value = AlarmUiState(
                sessionId = session?.id,
                session = session,
                challenge = challenge,
                userInput = "",
                isAnswerCorrect = false,
                wrongAttempts = 0,
                showHint = false,
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

            // Foreground service держит звук и вибрацию независимо от Activity.
            SleepForegroundService.triggerAlarm(context, session.id)

            // ДОБАВЛЕНО (F10): умные повторы.
            if (profile.smartRepeatEnabled) {
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
        if (_state.value.isAnswerCorrect) {
            return
        }

        val digitsOnly = text.filter { it.isDigit() }.take(4)

        _state.value = _state.value.copy(
            userInput = digitsOnly,
            errorMessage = null
        )
    }

    /**
     * Проверяет ответ.
     *
     * При правильном ответе немедленно останавливает повторы и вибрацию,
     * чтобы между «верно» и нажатием «Выключить» будильник не продолжал давить.
     */
    fun checkAnswer() {
        val currentState = _state.value
        val challenge = currentState.challenge ?: return

        if (currentState.isAnswerCorrect || currentState.isProcessing) {
            return
        }

        val input = currentState.userInput.toIntOrNull()

        if (input == null) {
            _state.value = currentState.copy(
                errorMessage = context.getString(R.string.alarm_error_empty_answer)
            )
            return
        }

        if (input == challenge.answer) {
            // ДОБАВЛЕНО: правильный ответ → тишина по повторам и вибрации.
            stopSmartRepeat()
            SleepForegroundService.stopAlarmVibration(context)

            _state.value = currentState.copy(
                isAnswerCorrect = true,
                errorMessage = null,
                nextRepeatAtMillis = null
            )
        } else {
            val attempts = currentState.wrongAttempts + 1

            _state.value = currentState.copy(
                wrongAttempts = attempts,
                showHint = attempts >= MAX_WRONG_ATTEMPTS_BEFORE_HINT,
                errorMessage = context.getString(R.string.alarm_wrong_answer)
            )
        }
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

    /**
     * Пользователь выбрал настроение.
     * Сохраняем его; если брифинг включён — озвучиваем сводку; затем finish.
     */
    fun onMoodSelected(mood: Int) {
        _showMoodPicker.value = false

        viewModelScope.launch {
            moodRepository.saveToday(mood)

            _isBriefingPlaying.value = true
            val text = briefingTextBuilder.build(context)
            briefingCoordinator.speak(text, com.personal.sleepalarm.service.audio.VoiceScenario.MORNING) {
                viewModelScope.launch {
                    _isBriefingPlaying.value = false
                    emitFinish()
                }
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
        stopSmartRepeat()              // ДОБАВЛЕНО (F10)
        briefingCoordinator.stop()
        super.onCleared()
    }

    companion object {
        private const val MINUTE_MS = 60L * 1000L
        private const val MAX_WRONG_ATTEMPTS_BEFORE_HINT = 3
        private const val ALARM_EARLY_TOLERANCE_MS = 5L * MINUTE_MS
        private const val ALARM_LATE_TOLERANCE_MS = 10L * MINUTE_MS

        // ДОБАВЛЕНО (F10): стартовая громкость первого импульса повтора.
        private const val SMART_REPEAT_START_VOLUME = 0.5f
        private const val END_VOLUME = 1.0f
    }
}
