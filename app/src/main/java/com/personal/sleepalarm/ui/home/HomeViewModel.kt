package com.personal.sleepalarm.ui.home

import android.app.Application
import android.content.Intent
import android.os.SystemClock
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
import com.personal.sleepalarm.data.db.entity.DailyCheckInEntity
import com.personal.sleepalarm.data.db.entity.EnergyObservationEntity
import com.personal.sleepalarm.data.db.entity.TaskDemandProfileEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.data.db.entity.TaskDependencyEntity
import com.personal.sleepalarm.data.db.entity.RecommendationDecisionEntity
import com.personal.sleepalarm.data.db.entity.CalendarEventEntity
import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.preferences.QuickNotesPreference
import com.personal.sleepalarm.data.preferences.SleepAutomationPreference
import com.personal.sleepalarm.data.preferences.SleepAutomationSettings
import com.personal.sleepalarm.data.preferences.ExternalContextPreferences
import com.personal.sleepalarm.data.externalcontext.DefaultExternalContextProvider
import com.personal.sleepalarm.data.externalcontext.OpenMeteoWeatherClient
import com.personal.sleepalarm.data.repository.SleepProfileRepository
import com.personal.sleepalarm.data.repository.SleepSessionRepository
import com.personal.sleepalarm.domain.calculator.CueScheduleCalculator
import com.personal.sleepalarm.domain.calculator.DailyTaskFocusCalculator
import com.personal.sleepalarm.domain.calculator.DailyTaskFocusProgress
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
import com.personal.sleepalarm.domain.calculator.liveTaskFocusIntervals
import com.personal.sleepalarm.domain.adaptive.AdaptiveRanking
import com.personal.sleepalarm.domain.adaptive.AdaptivePlanningBridge
import com.personal.sleepalarm.domain.adaptive.AdaptivePlanningInput
import com.personal.sleepalarm.domain.adaptive.PersonalState
import com.personal.sleepalarm.domain.adaptive.PersonalStateObservation
import com.personal.sleepalarm.domain.adaptive.PlanningContext
import com.personal.sleepalarm.domain.adaptive.RankingMode
import com.personal.sleepalarm.domain.adaptive.ScoreFactor
import com.personal.sleepalarm.domain.adaptive.StateEstimator
import com.personal.sleepalarm.domain.adaptive.TaskDemand
import com.personal.sleepalarm.domain.adaptive.TaskEntityAdaptiveRanker
import com.personal.sleepalarm.domain.adaptive.TimeWindow
import com.personal.sleepalarm.domain.externalcontext.ExternalContextResult
import com.personal.sleepalarm.domain.externalcontext.WeatherContextState
import com.personal.sleepalarm.domain.externalcontext.WeatherContextOrigin
import com.personal.sleepalarm.service.SleepForegroundService
import com.personal.sleepalarm.ui.mood.MorningCheckInInput
import com.personal.sleepalarm.util.PermissionChecker
import com.personal.sleepalarm.util.PermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.LocalDate
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

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

enum class AdaptivePlanReason {
    DEADLINE,
    REQUIRED,
    ENERGY_MATCH,
    CAPACITY_MATCH,
    DEFAULT_ORDER
}

data class AdaptiveHomePlan(
    val orderedTasks: List<TaskEntity> = emptyList(),
    val personalState: PersonalState? = null,
    val rankingMode: RankingMode = RankingMode.FALLBACK_NO_STATE,
    val topReason: AdaptivePlanReason = AdaptivePlanReason.DEFAULT_ORDER,
    val reasonByTaskId: Map<Int, AdaptivePlanReason> = emptyMap(),
    val shouldOfferMorningCheckIn: Boolean = false,
    val shouldOfferRecoveryCheckIn: Boolean = false,
    val recoveryTaskId: Int? = null,
    val recoveryFocusSessionId: Int? = null,
    val daylightMinutes: Int? = null,
    val sunriseMillis: Long? = null,
    val sunsetMillis: Long? = null,
    val daylightZoneId: String? = null,
    val temperatureCelsius: Double? = null,
    val apparentTemperatureCelsius: Double? = null,
    val relativeHumidityPercent: Int? = null,
    val precipitationMillimeters: Double? = null,
    val weatherCode: Int? = null,
    val windSpeedKilometersPerHour: Double? = null,
    val outdoorFeasible: Boolean? = null
) {
    val isAdaptive: Boolean get() = rankingMode == RankingMode.ADAPTIVE
}

private data class AdaptiveHomeInputs(
    val tasks: List<TaskEntity>,
    val latestCheckIn: DailyCheckInEntity?,
    val profiles: List<TaskDemandProfileEntity>,
    val dependencies: List<TaskDependencyEntity> = emptyList(),
    val latestSleep: SleepSessionEntity?,
    val activities: List<ActivityRecordEntity>,
    val energyObservations: List<EnergyObservationEntity> = emptyList(),
    val latestEnergyObservation: EnergyObservationEntity? = null
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
    private val serviceLocator = (application as com.personal.sleepalarm.app.App).serviceLocator
    private val dailyCheckInRepository = serviceLocator.dailyCheckInRepository
    private val energyObservationRepository = serviceLocator.energyObservationRepository
    private val demandProfileRepository = serviceLocator.taskDemandProfileRepository
    private val recommendationRepository = serviceLocator.recommendationRepository
    private val externalContextPreferences = ExternalContextPreferences(context)
    private val externalContextProvider = DefaultExternalContextProvider(
        settingsStore = externalContextPreferences,
        weatherCache = externalContextPreferences,
        weatherClient = OpenMeteoWeatherClient()
    )
    private val externalContext = MutableStateFlow<ExternalContextResult>(ExternalContextResult.Disabled)
    private val externalContextRefreshVersion = MutableStateFlow(0L)

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

    private val adaptiveBaseInputs = combine(
        database.taskDao().observeAll(),
        dailyCheckInRepository.observeLatest(),
        demandProfileRepository.observeAll(),
        database.sleepSessionDao().observeLatestCompleted(),
        database.activityRecordDao().observeAll()
    ) { tasks, checkIn, profiles, sleep, activities ->
        AdaptiveHomeInputs(
            tasks = tasks,
            latestCheckIn = checkIn,
            profiles = profiles,
            latestSleep = sleep,
            activities = activities
        )
    }

    private val adaptiveCoreInputs = combine(
        adaptiveBaseInputs,
        demandProfileRepository.observeAllDependencies()
    ) { inputs, dependencies ->
        inputs.copy(dependencies = dependencies)
    }

    private val adaptiveInputs = combine(
        adaptiveCoreInputs,
        energyObservationRepository.observeFrom(
            System.currentTimeMillis() - 90L * 24L * 60L * 60_000L
        )
    ) { inputs, observations ->
        inputs.copy(
            energyObservations = observations,
            latestEnergyObservation = observations.maxByOrNull(EnergyObservationEntity::timestamp)
        )
    }

    val adaptivePlan: StateFlow<AdaptiveHomePlan> = combine(
        adaptiveInputs,
        database.calendarEventDao().observeAll(),
        externalContext,
        merge(refreshTrigger, tickerFlow)
    ) { inputs, events, external, nowMillis ->
        buildAdaptivePlan(inputs, events, external, nowMillis)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AdaptiveHomePlan()
    )

    val dailyTaskProgress: StateFlow<Map<Int, DailyTaskFocusProgress>> = combine(
        adaptiveBaseInputs,
        database.focusProtocolDao().observeActive(),
        merge(refreshTrigger, tickerFlow)
    ) { inputs, activeFocus, nowMillis ->
        DailyTaskFocusCalculator.calculateForTasks(
            tasks = inputs.tasks,
            records = inputs.activities,
            nowMillis = nowMillis,
            zoneId = ZoneId.systemDefault(),
            liveIntervals = activeFocus?.liveTaskFocusIntervals(nowMillis).orEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap()
    )

    init {
        viewModelScope.launch {
            profileRepository.ensureProfileExists()
        }
        viewModelScope.launch {
            observeExternalContext()
        }
    }

    /**
     * Keeps daylight and weather current while preserving the opt-in boundary. A settings change
     * cancels the active request immediately; when the feature is disabled no provider call is
     * made. DataStore failures are retried instead of permanently stopping updates.
     */
    private suspend fun observeExternalContext() {
        while (true) {
            try {
                externalContextPreferences.observeSettings()
                    .distinctUntilChanged()
                    .collectLatest { settings ->
                        if (!settings.enabled) {
                            externalContext.value = ExternalContextResult.Disabled
                            return@collectLatest
                        }
                        runExternalContextRefreshLoop()
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "External context settings stream failed", error)
                // Fail closed: if the opt-in state cannot be read, do not retain or fetch context.
                externalContext.value = ExternalContextResult.Disabled
            }
            delay(EXTERNAL_CONTEXT_SETTINGS_RETRY_MS)
        }
    }

    /**
     * Refreshes on a fixed TTL and sooner after transient failures. Manual refreshes wake this
     * loop immediately, while a short monotonic cooldown coalesces repeated resume events.
     */
    private suspend fun runExternalContextRefreshLoop() {
        var retryDelayMillis = EXTERNAL_CONTEXT_INITIAL_RETRY_MS
        var lastAttemptElapsedMillis = Long.MIN_VALUE

        while (true) {
            val nowElapsedMillis = SystemClock.elapsedRealtime()
            val earliestNextAttempt = if (lastAttemptElapsedMillis == Long.MIN_VALUE) {
                nowElapsedMillis
            } else {
                lastAttemptElapsedMillis + EXTERNAL_CONTEXT_MIN_ATTEMPT_INTERVAL_MS
            }
            val cooldownMillis = (earliestNextAttempt - nowElapsedMillis).coerceAtLeast(0L)
            if (cooldownMillis > 0L) delay(cooldownMillis)

            lastAttemptElapsedMillis = SystemClock.elapsedRealtime()
            val outcome = refreshExternalContextSafely()
            val handledRefreshVersion = externalContextRefreshVersion.value
            val waitMillis = when (outcome) {
                ExternalContextRefreshOutcome.CURRENT -> {
                    retryDelayMillis = EXTERNAL_CONTEXT_INITIAL_RETRY_MS
                    EXTERNAL_CONTEXT_REFRESH_TTL_MS
                }
                ExternalContextRefreshOutcome.RETRYABLE_FAILURE -> {
                    val currentDelay = retryDelayMillis
                    retryDelayMillis = (retryDelayMillis * 2L)
                        .coerceAtMost(EXTERNAL_CONTEXT_MAX_RETRY_MS)
                    currentDelay
                }
            }

            // A StateFlow version retains a refresh that arrives during the wait and coalesces
            // several rapid ON_RESUME calls into one subsequent attempt.
            withTimeoutOrNull(waitMillis) {
                externalContextRefreshVersion.first { it != handledRefreshVersion }
            }
        }
    }

    private suspend fun refreshExternalContextSafely(): ExternalContextRefreshOutcome {
        val refreshed = try {
            withTimeoutOrNull(EXTERNAL_CONTEXT_TIMEOUT_MS) {
                externalContextProvider.getContext()
            } ?: return ExternalContextRefreshOutcome.RETRYABLE_FAILURE
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "External context refresh failed", error)
            return ExternalContextRefreshOutcome.RETRYABLE_FAILURE
        }

        externalContext.value = refreshed
        return if (refreshed.needsExternalContextRetry()) {
            ExternalContextRefreshOutcome.RETRYABLE_FAILURE
        } else {
            ExternalContextRefreshOutcome.CURRENT
        }
    }

    private fun ExternalContextResult.needsExternalContextRetry(): Boolean =
        when (this) {
            ExternalContextResult.Disabled,
            ExternalContextResult.NotConfigured -> false
            is ExternalContextResult.Available -> when (val weatherState = snapshot.weather) {
                WeatherContextState.Disabled -> false
                is WeatherContextState.Unavailable -> true
                is WeatherContextState.Available ->
                    weatherState.value.origin == WeatherContextOrigin.STALE_CACHE
            }
        }

    private enum class ExternalContextRefreshOutcome {
        CURRENT,
        RETRYABLE_FAILURE
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

    private fun buildAdaptivePlan(
        inputs: AdaptiveHomeInputs,
        events: List<CalendarEventEntity>,
        external: ExternalContextResult,
        nowMillis: Long
    ): AdaptiveHomePlan {
        val externalSnapshot = (external as? ExternalContextResult.Available)?.snapshot
        val weather = externalSnapshot?.weather as? WeatherContextState.Available
        val outdoorFeasible = weather?.value?.let { current ->
            val code = current.weatherCode ?: 0
            val severePrecipitation = code in 65..82 || code in 95..99
            !severePrecipitation &&
                (current.precipitationMillimeters ?: 0.0) < 1.5 &&
                (current.windSpeedKilometersPerHour ?: 0.0) < 40.0
        }
        val adaptive = AdaptivePlanningBridge.build(
            AdaptivePlanningInput(
                nowMillis = nowMillis,
                tasks = inputs.tasks,
                profiles = inputs.profiles,
                dependencies = inputs.dependencies,
                latestCheckIn = inputs.latestCheckIn,
                latestSleep = inputs.latestSleep,
                activities = inputs.activities,
                energyObservations = inputs.energyObservations,
                calendarEvents = events,
                photoperiodMinutes = externalSnapshot?.daylight?.daylightMinutes,
                outdoorFeasible = outdoorFeasible
            )
        )
        val wakeTime = inputs.latestSleep?.actualWakeTime
            ?.takeIf { it <= nowMillis && nowMillis - it <= 36L * 60L * 60_000L }
        val reasonByTaskId = adaptive.ranking.tasks.associate { rankedTask ->
            rankedTask.demand.id to primaryReason(rankedTask)
        }
        val topReason = adaptive.ranking.tasks.firstOrNull()?.let(::primaryReason)
            ?: AdaptivePlanReason.DEFAULT_ORDER
        val zone = ZoneId.systemDefault()
        val latestIsToday = inputs.latestCheckIn?.localDate == LocalDate.now(zone).toString()
        val morningWindow = wakeTime != null && nowMillis - wakeTime <= 6L * 60L * 60_000L
        val alarmRatingNeedsRecheck = inputs.latestCheckIn?.let { checkIn ->
            latestIsToday && checkIn.source == "ALARM" &&
                nowMillis - checkIn.timestamp >= 45L * 60_000L
        } == true
        val pendingRecovery = inputs.latestEnergyObservation?.takeIf { observation ->
            observation.context == "AFTER_TASK" &&
                nowMillis >= observation.timestamp + 20L * 60_000L &&
                nowMillis <= observation.timestamp + 3L * 60L * 60_000L
        }
        return AdaptiveHomePlan(
            orderedTasks = adaptive.orderedTasks,
            personalState = adaptive.personalState,
            rankingMode = adaptive.ranking.mode,
            topReason = topReason,
            reasonByTaskId = reasonByTaskId,
            shouldOfferMorningCheckIn = morningWindow && (!latestIsToday || alarmRatingNeedsRecheck),
            shouldOfferRecoveryCheckIn = pendingRecovery != null,
            recoveryTaskId = pendingRecovery?.taskId,
            recoveryFocusSessionId = pendingRecovery?.focusProtocolSessionId,
            daylightMinutes = externalSnapshot?.daylight?.daylightMinutes,
            sunriseMillis = externalSnapshot?.daylight?.sunrise?.toEpochMilli(),
            sunsetMillis = externalSnapshot?.daylight?.sunset?.toEpochMilli(),
            daylightZoneId = externalSnapshot?.daylight?.zoneId,
            temperatureCelsius = weather?.value?.temperatureCelsius,
            apparentTemperatureCelsius = weather?.value?.apparentTemperatureCelsius,
            relativeHumidityPercent = weather?.value?.relativeHumidityPercent,
            precipitationMillimeters = weather?.value?.precipitationMillimeters,
            weatherCode = weather?.value?.weatherCode,
            windSpeedKilometersPerHour = weather?.value?.windSpeedKilometersPerHour,
            outdoorFeasible = outdoorFeasible
        )
    }

    private fun primaryReason(task: com.personal.sleepalarm.domain.adaptive.RankedTask<Int>): AdaptivePlanReason {
        val positive = task.explanations.filter { it.contribution > 0.0 }
            .maxByOrNull { it.contribution }
            ?.factor
        return when (positive) {
            ScoreFactor.DEADLINE_URGENCY -> AdaptivePlanReason.DEADLINE
            ScoreFactor.MANDATORY_TASK -> AdaptivePlanReason.REQUIRED
            ScoreFactor.ENERGY_FIT -> AdaptivePlanReason.ENERGY_MATCH
            ScoreFactor.COGNITIVE_FIT -> AdaptivePlanReason.CAPACITY_MATCH
            else -> AdaptivePlanReason.DEFAULT_ORDER
        }
    }

    fun saveMorningRecheck(input: MorningCheckInInput) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            runCatching {
                dailyCheckInRepository.save(
                    DailyCheckInEntity(
                        localDate = LocalDate.now(zone).toString(),
                        timestamp = now,
                        zoneId = zone.id,
                        energy = input.energy,
                        mood = input.mood,
                        clarity = input.clarity?.minus(1),
                        source = "MORNING_RECHECK"
                    )
                )
            }
            runCatching {
                energyObservationRepository.record(
                    EnergyObservationEntity(
                        timestamp = now,
                        absoluteEnergy = input.energy,
                        context = "MORNING",
                        source = "MORNING_RECHECK"
                    )
                )
            }
            refresh()
        }
    }

    fun saveRecoveryEnergy(
        energy: Int,
        taskId: Int?,
        focusProtocolSessionId: Int?
    ) {
        viewModelScope.launch {
            runCatching {
                energyObservationRepository.record(
                    EnergyObservationEntity(
                        timestamp = System.currentTimeMillis(),
                        absoluteEnergy = energy,
                        context = "AFTER_RECOVERY",
                        taskId = taskId,
                        focusProtocolSessionId = focusProtocolSessionId,
                        source = "RECOVERY_CHECK_IN"
                    )
                )
            }
            refresh()
        }
    }

    fun recordRecommendationAccepted(task: TaskEntity) {
        val plan = adaptivePlan.value
        viewModelScope.launch {
            val state = plan.personalState
            runCatching {
                recommendationRepository.record(
                    RecommendationDecisionEntity(
                        generatedAt = System.currentTimeMillis(),
                        modelVersion = "adaptive-energy-v1",
                        strategy = plan.rankingMode.name,
                        stateSnapshotJson = JSONObject()
                            .put("estimatedEnergy", state?.estimatedEnergy)
                            .put("confidence", state?.confidence?.value)
                            .put("minutesSinceWake", state?.minutesSinceWake)
                            .toString(),
                        selectedTaskId = task.id,
                        candidateTaskIds = JSONArray(
                            plan.orderedTasks.take(8).map(TaskEntity::id)
                        ).toString(),
                        reasonCodes = JSONArray(
                            listOf((plan.reasonByTaskId[task.id] ?: plan.topReason).name)
                        ).toString(),
                        confidence = state?.confidence?.value?.toFloat() ?: 0f,
                        accepted = true
                    )
                )
            }
        }
    }

    // === Голосовой брифинг ===
    private val briefingCoordinator = serviceLocator.briefingCoordinator
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
        externalContextRefreshVersion.update { it + 1L }
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
        private const val EXTERNAL_CONTEXT_TIMEOUT_MS = 15_000L
        private const val EXTERNAL_CONTEXT_REFRESH_TTL_MS = 30L * 60_000L
        private const val EXTERNAL_CONTEXT_MIN_ATTEMPT_INTERVAL_MS = 15_000L
        private const val EXTERNAL_CONTEXT_INITIAL_RETRY_MS = 60_000L
        private const val EXTERNAL_CONTEXT_MAX_RETRY_MS = 15L * 60_000L
        private const val EXTERNAL_CONTEXT_SETTINGS_RETRY_MS = 60_000L
    }
}
