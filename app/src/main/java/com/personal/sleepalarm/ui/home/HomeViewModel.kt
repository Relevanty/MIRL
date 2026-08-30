package com.personal.sleepalarm.ui.home

import android.app.Application
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.R
import com.personal.sleepalarm.alarm.AlarmScheduler
import com.personal.sleepalarm.alarm.SleepAutomationScheduler
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.AlarmProfileEntity
import com.personal.sleepalarm.data.db.entity.CueEventEntity
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.data.preferences.QuickNotesPreference
import com.personal.sleepalarm.data.preferences.SleepAutomationPreference
import com.personal.sleepalarm.data.preferences.SleepAutomationSettings
import com.personal.sleepalarm.data.repository.SleepProfileRepository
import com.personal.sleepalarm.data.repository.SleepSessionRepository
import com.personal.sleepalarm.domain.calculator.CueScheduleCalculator
import com.personal.sleepalarm.domain.calculator.SleepCalculator
import com.personal.sleepalarm.domain.automation.SleepAutomationWindow
import com.personal.sleepalarm.domain.automation.isAutomationArmed
import com.personal.sleepalarm.domain.automation.isAutomaticSleepSession
import com.personal.sleepalarm.domain.automation.AUTOMATION_ARMED_SOURCE
import com.personal.sleepalarm.domain.automation.AUTOMATION_WINDOW_EXPIRED_SOURCE
import com.personal.sleepalarm.domain.model.CueSchedule
import com.personal.sleepalarm.domain.model.SleepPlan
import com.personal.sleepalarm.domain.model.SleepPlanWarning
import com.personal.sleepalarm.domain.model.SleepWindow
import com.personal.sleepalarm.service.SleepForegroundService
import com.personal.sleepalarm.util.PermissionChecker
import com.personal.sleepalarm.util.PermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Состояние главного экрана.
 *
 * Упрощено под новую логику: убраны alternatives и recommendedCycles.
 * Plan всегда считается от текущего момента.
 */
data class HomeUiState(
    val profile: AlarmProfileEntity = AlarmProfileEntity(),
    val activeSession: SleepSessionEntity? = null,
    val latestCompletedSession: SleepSessionEntity? = null,
    val plan: SleepPlan? = null,
    val cueSchedule: CueSchedule = CueSchedule(emptyList(), emptySet()),
    val planWarnings: Set<SleepPlanWarning> = emptySet(),
    val permissions: PermissionState = PermissionState(),
    val sleepAutomation: SleepAutomationSettings = SleepAutomationSettings(),
    val now: Long = System.currentTimeMillis()
)

/**
 * ViewModel главного экрана.
 *
 * Логика:
 * - план считается от текущего момента (calculateFromNow);
 * - будильник ставится на расчётное время с обрезкой по preferredWakeTime;
 * - системный дублёр (опционально) ставится на расчётное wake;
 * - cue играют только выбранным пользователем звуком (cueRingtoneUri).
 */
class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val quickNotesPreference = QuickNotesPreference(context)
    private val sleepAutomationPreference = SleepAutomationPreference(context)
    private val sleepAutomationScheduler = SleepAutomationScheduler(context, sleepAutomationPreference)

    val quickNotes: StateFlow<String> = quickNotesPreference.observeText().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ""
    )

    fun updateQuickNotes(text: String) {
        viewModelScope.launch { quickNotesPreference.setText(text) }
    }

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

    private val refreshTrigger = MutableStateFlow(System.currentTimeMillis())

    private val tickerFlow = flow {
        while (true) {
            delay(REFRESH_INTERVAL_MS)
            emit(System.currentTimeMillis())
        }
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    val uiState: StateFlow<HomeUiState> = combine(
        profileRepository.observeProfile(),
        sessionRepository.observeActiveSession(),
        sessionRepository.observeLatestCompleted(),
        sleepAutomationPreference.observe(),
        merge(refreshTrigger, tickerFlow)
    ) { profile, activeSession, latestCompleted, automation, nowMillis ->
        buildState(
            profile = profile,
            activeSession = activeSession,
            latestCompleted = latestCompleted,
            sleepAutomation = automation,
            nowMillis = nowMillis
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    init {
        viewModelScope.launch {
            profileRepository.ensureProfileExists()
        }
    }

    // =================================================================
    // Построение состояния
    // =================================================================

    private fun buildState(
        profile: AlarmProfileEntity,
        activeSession: SleepSessionEntity?,
        latestCompleted: SleepSessionEntity?,
        sleepAutomation: SleepAutomationSettings,
        nowMillis: Long
    ): HomeUiState {
        val permissions = PermissionChecker.state(context)

        return try {
            val zone = ZoneId.systemDefault()
            val now = Instant.ofEpochMilli(nowMillis).atZone(zone)

            // План считается от текущего момента.
            val plan = SleepCalculator.calculateFromNow(
                now = now,
                onsetLatencyMinutes = profile.onsetLatencyMinutes,
                cycleLengthMinutes = profile.cycleLengthMinutes,
                requestedCycles = profile.cycles,
                preferredWakeTime = LocalTime.of(
                    profile.preferredWakeHour,
                    profile.preferredWakeMinute
                )
            )

            val cueSchedule = if (profile.cuesEnabled) {
                CueScheduleCalculator.buildCueSchedule(
                    window = plan.toSleepWindow(),
                    cueScheduleMode = profile.cueScheduleMode,
                    cycleLengthMinutes = profile.cycleLengthMinutes,
                    cycles = plan.cycles,
                    firstCueDelayMinutes = profile.firstCueDelayMinutes,
                    cueIntervalMinutes = profile.cueIntervalMinutes,
                    remCueOffsetPercent = profile.remCueOffsetPercent,
                    stopCuesOneCycleBeforeWake = true
                )
            } else {
                CueSchedule(cues = emptyList(), warnings = emptySet())
            }

            val planWarnings = SleepCalculator.warningsFor(plan = plan, now = now)

            HomeUiState(
                profile = profile,
                activeSession = activeSession,
                latestCompletedSession = latestCompleted,
                plan = plan,
                cueSchedule = cueSchedule,
                planWarnings = planWarnings,
                permissions = permissions,
                sleepAutomation = sleepAutomation,
                now = nowMillis
            )
        } catch (throwable: Throwable) {
            HomeUiState(
                profile = profile,
                activeSession = activeSession,
                latestCompletedSession = latestCompleted,
                plan = null,
                cueSchedule = CueSchedule(emptyList(), emptySet()),
                planWarnings = emptySet(),
                permissions = permissions,
                sleepAutomation = sleepAutomation,
                now = nowMillis
            )
        }
    }

    // === Голосовой брифинг ===
    private val briefingCoordinator =
        (application as com.personal.sleepalarm.app.App).serviceLocator.briefingCoordinator
    private val briefingTextBuilder = com.personal.sleepalarm.service.BriefingTextBuilder(
        calendarEventDao = database.calendarEventDao(),
        activityRecordDao = database.activityRecordDao(),
        studySessionDao = database.studySessionDao(),
        ddayDao = database.ddayDao(),
        sessionDao = database.sleepSessionDao(),
        taskDao = database.taskDao(),
        alarmProfileDao = database.alarmProfileDao()
    )

    private val _isBriefingPlaying = MutableStateFlow(false)
    val isBriefingPlaying: StateFlow<Boolean> = _isBriefingPlaying
    private var briefingJob: Job? = null

    fun playBriefing() {
        if (_isBriefingPlaying.value) {
            stopBriefing()
            return
        }

        // Выставляем состояние синхронно, чтобы два быстрых нажатия
        // не успели запустить две корутины озвучки.
        _isBriefingPlaying.value = true
        briefingJob?.cancel()
        briefingJob = viewModelScope.launch {
            try {
                val text = briefingTextBuilder.build(getApplication())
                currentCoroutineContext().ensureActive()
                briefingCoordinator.speak(text) {
                    _isBriefingPlaying.value = false
                    briefingJob = null
                }
            } catch (e: Throwable) {
                _isBriefingPlaying.value = false
                briefingJob = null
            }
        }
    }

    private fun stopBriefing() {
        briefingJob?.cancel()
        briefingJob = null
        briefingCoordinator.stop()
        _isBriefingPlaying.value = false
    }

    override fun onCleared() {
        stopBriefing()
        super.onCleared()
    }

    // =================================================================
    // Refresh / ошибки
    // =================================================================

    fun refresh() {
        refreshTrigger.value = System.currentTimeMillis()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /** Ручная коррекция результата, если телефон не был поставлен на таймер. */
    fun correctSleepDuration(session: SleepSessionEntity, durationMinutes: Long) {
        val wake = session.actualWakeTime ?: return
        val safeDuration = durationMinutes.coerceIn(1L, 24L * 60L)
        viewModelScope.launch {
            sessionRepository.updateSession(
                session.copy(
                    detectedSleepOnsetTime = wake - safeDuration * 60_000L,
                    detectedOnsetLatencyMinutes = (((wake - safeDuration * 60_000L) - session.bedTimePlanned) / 60_000L)
                        .toInt().coerceAtLeast(0),
                    detectedOnsetConfidencePercent = 100,
                    detectedOnsetSource = "MANUAL_CORRECTION",
                    detectedOnsetUncertaintyMinutes = 0,
                    onsetReviewState = "CORRECTED"
                )
            )
            refresh()
        }
    }

    fun confirmSleepOnset(session: SleepSessionEntity) {
        viewModelScope.launch {
            sessionRepository.updateSession(session.copy(onsetReviewState = "CONFIRMED"))
            refresh()
        }
    }

    /** Полностью откатывает ложное автоопределение и при возможности запускает его заново. */
    fun rejectDetectedSleepOnset() {
        viewModelScope.launch {
            val active = sessionRepository.getActiveSession()
                ?.takeIf { it.detectedSleepOnsetTime != null }
                ?: return@launch
            val profile = profileRepository.getProfile()
            val zone = runCatching { ZoneId.of(active.zoneId) }.getOrDefault(ZoneId.systemDefault())
            val now = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone)
            val bed = Instant.ofEpochMilli(active.bedTimePlanned).atZone(zone)
            val automatic = active.isAutomaticSleepSession()
            val automationSettings = sleepAutomationPreference.get()
            val currentWindow = SleepAutomationWindow.containing(
                now,
                automationSettings.windowStartMinutes,
                automationSettings.windowEndMinutes
            )
            val originalWindow = SleepAutomationWindow.containing(
                bed,
                automationSettings.windowStartMinutes,
                automationSettings.windowEndMinutes
            )
            val canRearmAutomation = automatic &&
                automationSettings.enabled &&
                currentWindow?.id == originalWindow?.id

            var hardWake = bed.toLocalDate()
                .atTime(profile.preferredWakeHour, profile.preferredWakeMinute)
                .atZone(zone)
            if (!hardWake.isAfter(bed)) hardWake = hardWake.plusDays(1)

            // Reconstruct the exact pre-detection manual plan from the
            // immutable session snapshot. Current settings may have changed
            // while the user was asleep and must not alter this rollback.
            val restoredSleepStart = active.bedTimePlanned +
                active.sleepOnsetLatencyMinutes * 60_000L
            val restoredWake = if (automatic) {
                active.automationSafetyWakeTime ?: hardWake.toInstant().toEpochMilli()
            } else {
                restoredSleepStart +
                    active.cyclesPlanned.toLong() * active.cycleLengthMinutes * 60_000L
            }
            if (restoredWake <= System.currentTimeMillis()) return@launch

            val cueSchedule = if (!automatic && profile.cuesEnabled) {
                CueScheduleCalculator.buildCueSchedule(
                    window = SleepWindow(
                        sleepStart = Instant.ofEpochMilli(restoredSleepStart).atZone(zone),
                        wake = Instant.ofEpochMilli(restoredWake).atZone(zone)
                    ),
                    cueScheduleMode = profile.cueScheduleMode,
                    cycleLengthMinutes = active.cycleLengthMinutes,
                    cycles = active.cyclesPlanned,
                    firstCueDelayMinutes = profile.firstCueDelayMinutes,
                    cueIntervalMinutes = profile.cueIntervalMinutes,
                    remCueOffsetPercent = profile.remCueOffsetPercent,
                    stopCuesOneCycleBeforeWake = true
                )
            } else CueSchedule(emptyList(), emptySet())
            val cues = cueSchedule.cues.map { cue ->
                CueEventEntity(
                    sessionId = active.id,
                    cueIndex = cue.index,
                    scheduledTime = cue.time.toInstant().toEpochMilli()
                )
            }
            val restored = active.copy(
                estimatedSleepStartTime = restoredSleepStart,
                estimatedWakeTime = restoredWake,
                cuesEnabled = !automatic && profile.cuesEnabled,
                cuesScheduledCount = cues.size,
                detectedSleepOnsetTime = null,
                detectedOnsetLatencyMinutes = null,
                detectedOnsetConfidencePercent = null,
                detectedOnsetSource = when {
                    canRearmAutomation -> AUTOMATION_ARMED_SOURCE
                    automatic -> AUTOMATION_WINDOW_EXPIRED_SOURCE
                    else -> null
                },
                detectedOnsetUncertaintyMinutes = null,
                onsetReviewState = "PENDING"
            )
            // Commit the rollback first. rescheduleAllForSession cancels the old
            // PendingIntents itself; avoiding an earlier cancel leaves no gap in
            // which Room still exposes the corrected (possibly earlier) wake time.
            sessionRepository.replaceCues(restored, cues)
            alarmScheduler.rescheduleAllForSession(restored)
            if (!automatic || canRearmAutomation) {
                SleepForegroundService.rearmOnset(context, active.id)
            }
            refresh()
        }
    }

    // =================================================================
    // Запуск и отмена сессии
    // =================================================================

    fun startSleepSession() {
        viewModelScope.launch {
            _errorMessage.value = null

            val permissions = PermissionChecker.state(context)
            if (!permissions.allRequiredGranted) {
                _errorMessage.value = context.getString(R.string.error_required_permissions)
                return@launch
            }

            val profile = profileRepository.getProfile()
            val zone = ZoneId.systemDefault()
            val now = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone)

            // Расчёт от текущего момента с обрезкой по preferredWakeTime.
            val plan = SleepCalculator.calculateFromNow(
                now = now,
                onsetLatencyMinutes = profile.onsetLatencyMinutes,
                cycleLengthMinutes = profile.cycleLengthMinutes,
                requestedCycles = profile.cycles,
                preferredWakeTime = LocalTime.of(
                    profile.preferredWakeHour,
                    profile.preferredWakeMinute
                )
            )

            // Если ни один полный цикл не помещается — не ставим бессмысленный будильник.
            if (plan.cyclesDidNotFit && plan.cycles == 0) {
                _errorMessage.value = context.getString(R.string.error_no_cycle_fits)
                return@launch
            }

            // Explicit sleep wins over focus only after the sleep plan has
            // passed validation. The manager preserves elapsed work and clears
            // its alarms/notification before the sleep service starts.
            val focusManager = (getApplication<Application>() as com.personal.sleepalarm.app.App)
                .serviceLocator.focusProtocolManager
            database.focusProtocolDao().getActive().forEach { focus ->
                focusManager.cancel(focus.id, "SLEEP_STARTED")
            }

            // Старая активная сессия отменяется.
            val oldActive = sessionRepository.getActiveSession()
            if (oldActive != null) {
                alarmScheduler.cancelAllAlarmsForSession(oldActive.id)
                if (profile.mirrorToSystemClock && !oldActive.isAutomaticSleepSession()) {
                    dismissMirroredAlarm(
                        wakeEpoch = oldActive.estimatedWakeTime,
                        zoneId = oldActive.zoneId
                    )
                }
            }

            // Cue-расписание.
            val cueSchedule = if (profile.cuesEnabled) {
                CueScheduleCalculator.buildCueSchedule(
                    window = plan.toSleepWindow(),
                    cueScheduleMode = profile.cueScheduleMode,
                    cycleLengthMinutes = profile.cycleLengthMinutes,
                    cycles = plan.cycles,
                    firstCueDelayMinutes = profile.firstCueDelayMinutes,
                    cueIntervalMinutes = profile.cueIntervalMinutes,
                    remCueOffsetPercent = profile.remCueOffsetPercent,
                    stopCuesOneCycleBeforeWake = true
                )
            } else {
                CueSchedule(cues = emptyList(), warnings = emptySet())
            }

            val session = SleepSessionEntity(
                bedTimePlanned = plan.bedTime.toInstant().toEpochMilli(),
                sleepOnsetLatencyMinutes = profile.onsetLatencyMinutes,
                estimatedSleepStartTime = plan.estimatedSleepStart.toInstant().toEpochMilli(),
                cycleLengthMinutes = profile.cycleLengthMinutes,
                cyclesPlanned = plan.cycles,
                estimatedWakeTime = plan.estimatedWake.toInstant().toEpochMilli(),
                actualWakeTime = null,
                dismissType = null,
                cuesEnabled = profile.cuesEnabled,
                cueVolumePercent = profile.cueVolumePercent,
                cuesScheduledCount = cueSchedule.cues.size,
                isActive = true,
                isSnoozeSession = false,
                parentSessionId = null,
                zoneId = zone.id,
                cueRingtoneUri = profile.cueRingtoneUri
            )

            val cueEntities = cueSchedule.cues.map { cue ->
                CueEventEntity(
                    sessionId = 0,
                    cueIndex = cue.index,
                    scheduledTime = cue.time.toInstant().toEpochMilli()
                )
            }

            val sessionId = sessionRepository.startSession(
                session = session,
                cues = cueEntities
            )

            val savedSession = sessionRepository.getSession(sessionId)
                ?: session.copy(id = sessionId)

            var mainAlarmScheduled = false
            runCatching {
                mainAlarmScheduled = alarmScheduler.scheduleMainAlarm(savedSession)

                if (savedSession.cuesEnabled) {
                    val scheduledCues = sessionRepository.getScheduledCues(sessionId)
                    alarmScheduler.scheduleCueAlarms(sessionId, scheduledCues)
                }

                // Системный дублёр.
                if (profile.mirrorToSystemClock) {
                    // Создаём новый дублёр на расчётное время wake.
                    mirrorAlarmToSystem(
                        wakeEpoch = savedSession.estimatedWakeTime,
                        zoneId = zone.id,
                        vibrate = profile.vibrationEnabled
                    )
                }
            }.onFailure {
                Log.e(TAG, "Alarm scheduling failed", it)
                _errorMessage.value = context.getString(R.string.error_failed_to_schedule_alarm)
            }

            if (!mainAlarmScheduled) {
                _errorMessage.value = context.getString(R.string.error_failed_to_schedule_alarm)
            }

            val firstCueTime = cueSchedule.cues.firstOrNull()?.time?.toInstant()?.toEpochMilli()

            SleepForegroundService.start(
                context = context,
                sessionId = sessionId,
                wakeTime = savedSession.estimatedWakeTime,
                firstCueTime = firstCueTime
            )

            refresh()
        }
    }

    fun cancelActiveSession() {
        viewModelScope.launch {
            val active = sessionRepository.getActiveSession() ?: return@launch

            alarmScheduler.cancelAllAlarmsForSession(active.id)
            sessionRepository.cancelSession(active.id)

            // Пытаемся снять дублёр, если тумблер включён сейчас.
            val profile = profileRepository.getProfile()
            if (profile.mirrorToSystemClock && !active.isAutomaticSleepSession()) {
                dismissMirroredAlarm(
                    wakeEpoch = active.estimatedWakeTime,
                    zoneId = active.zoneId
                )
            }

            SleepForegroundService.stop(context = context, cancelSession = false)

            refresh()
        }
    }

    fun skipSleepAutomationTonight() {
        viewModelScope.launch {
            val now = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault())
            val settings = sleepAutomationPreference.get()
            val window = SleepAutomationWindow.containing(
                now,
                settings.windowStartMinutes,
                settings.windowEndMinutes
            )
            if (window != null) sleepAutomationPreference.skipWindow(window.id)

            val active = sessionRepository.getActiveSession()
            if (active?.isAutomationArmed() == true) {
                alarmScheduler.cancelAllAlarmsForSession(active.id)
                sessionRepository.cancelSession(active.id)
                SleepForegroundService.stop(context = context, cancelSession = false)
            }
            sleepAutomationScheduler.scheduleNext()
            refresh()
        }
    }

    // =================================================================
    // Интеграция с системными часами (дублёр)
    // =================================================================

    /**
     * Ставит видимый системный будильник-дублёр на время подъёма.
     * Обёрнуто в runCatching: если системных часов нет или они не принимают
     * intent без UI — тихо пропускаем.
     */
    private fun mirrorAlarmToSystem(
        wakeEpoch: Long,
        zoneId: String,
        vibrate: Boolean
    ) {
        runCatching {
            val zone = runCatching { ZoneId.of(zoneId) }
                .getOrDefault(ZoneId.systemDefault())
            val wake = Instant.ofEpochMilli(wakeEpoch).atZone(zone).toLocalTime()

            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, wake.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, wake.minute)
                putExtra(
                    AlarmClock.EXTRA_MESSAGE,
                    context.getString(R.string.system_alarm_mirror_message)
                )
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                putExtra(AlarmClock.EXTRA_VIBRATE, vibrate)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            Log.i(TAG, "Mirrored alarm set to ${wake.hour}:${wake.minute}")
        }.onFailure {
            Log.w(TAG, "Failed to mirror alarm to system clock", it)
        }
    }

    /**
     * Пытается снять системный будильник-дублёр.
     * Честно: ACTION_DISMISS_ALARM без UI НЕ гарантирован на всех прошивках.
     */
    private fun dismissMirroredAlarm(wakeEpoch: Long, zoneId: String) {
        runCatching {
            val zone = runCatching { ZoneId.of(zoneId) }
                .getOrDefault(ZoneId.systemDefault())
            val wake = Instant.ofEpochMilli(wakeEpoch).atZone(zone).toLocalTime()

            val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, wake.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, wake.minute)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        }.onFailure {
            Log.w(TAG, "Failed to dismiss mirrored alarm (may need manual removal)", it)
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
        private const val REFRESH_INTERVAL_MS = 60_000L
    }
}
