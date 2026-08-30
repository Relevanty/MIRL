package com.personal.sleepalarm.ui.settings

import android.app.Application
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.R
import com.personal.sleepalarm.alarm.AlarmScheduler
import com.personal.sleepalarm.alarm.DailyPlanNudgeScheduler
import com.personal.sleepalarm.alarm.EventAlarmScheduler
import com.personal.sleepalarm.alarm.ReminderScheduler
import com.personal.sleepalarm.alarm.SleepAutomationScheduler
import com.personal.sleepalarm.alarm.TaskDeadlineScheduler
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.AlarmProfileEntity
import com.personal.sleepalarm.data.preferences.AppSignalPreferences
import com.personal.sleepalarm.data.preferences.AppSignalSettings
import com.personal.sleepalarm.data.preferences.AppSignalType
import com.personal.sleepalarm.data.preferences.AppSoundMode
import com.personal.sleepalarm.data.preferences.AppSoundSelection
import com.personal.sleepalarm.data.preferences.DailyPlanNudgePreferences
import com.personal.sleepalarm.data.preferences.DailyPlanNudgeSettings
import com.personal.sleepalarm.data.preferences.SleepAutomationPreference
import com.personal.sleepalarm.data.preferences.SleepAutomationSettings
import com.personal.sleepalarm.data.repository.SleepProfileRepository
import com.personal.sleepalarm.data.repository.SleepSessionRepository
import com.personal.sleepalarm.data.repository.TaskEcosystemRepository
import com.personal.sleepalarm.data.repository.ReminderRepository
import com.personal.sleepalarm.domain.calculator.CueScheduleCalculator
import com.personal.sleepalarm.domain.calculator.SleepCalculator
import com.personal.sleepalarm.domain.automation.isAutomationArmed
import com.personal.sleepalarm.domain.model.CueSchedule
import com.personal.sleepalarm.domain.model.CueScheduleMode
import com.personal.sleepalarm.domain.model.DismissType
import com.personal.sleepalarm.domain.model.MathDifficulty
import com.personal.sleepalarm.domain.model.SleepPlan
import com.personal.sleepalarm.domain.model.SleepPlanWarning
import com.personal.sleepalarm.service.SleepForegroundService
import com.personal.sleepalarm.service.SleepNotificationBuilder
import com.personal.sleepalarm.service.EventNotificationBuilder
import com.personal.sleepalarm.service.ReminderNotificationBuilder
import com.personal.sleepalarm.service.focus.FocusProtocolManager
import com.personal.sleepalarm.service.audio.AppNotificationSoundPlayer
import com.personal.sleepalarm.service.audio.AppAudioAttributes
import com.personal.sleepalarm.service.audio.AlarmStreamVolumeController
import com.personal.sleepalarm.service.audio.AppVolumeScale
import com.personal.sleepalarm.util.ProfileJsonCodec
import com.personal.sleepalarm.util.PermissionChecker
import com.personal.sleepalarm.util.ManagedSoundImport
import com.personal.sleepalarm.util.LatestSoundOperationPolicy
import com.personal.sleepalarm.util.RingtonePickerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Состояние экрана настроек.
 *
 * Содержит профиль + превью расчёта (plan / cueSchedule / warnings),
 * чтобы секция подсказок показывала, сколько сигналов получится
 * в выбранном режиме (PERIODIC / REM_TARGETED).
 */
data class SettingsUiState(
    val profile: AlarmProfileEntity = AlarmProfileEntity(),
    val plan: SleepPlan? = null,
    val cueSchedule: CueSchedule = CueSchedule(emptyList(), emptySet()),
    val planWarnings: Set<SleepPlanWarning> = emptySet(),
    val sleepAutomation: SleepAutomationSettings = SleepAutomationSettings()
)

/**
 * ViewModel экрана настроек (F8).
 *
 * Пишет профиль через SleepProfileRepository, тему — через ThemePreference.
 * Расчёт превью дублирует логику HomeViewModel (оба читают один профиль
 * через Flow; расчёт чистый и дешёвый, дублирование допустимо для личного
 * проекта и даёт чистое разделение ответственности экранов).
 */
class SettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    // === ДОБАВЛЕНО: прослушивание мелодии ===
    private var previewPlayer: MediaPlayer? = null
    private var previewJob: Job? = null
    private var notificationPreviewJob: Job? = null
    private var previewVolumeLease: AutoCloseable? = null
    private val previewLock = Any()

    private val soundOperationPolicy = LatestSoundOperationPolicy()
    private val soundOperationMutexes = mutableMapOf<String, Mutex>()

    private val _isPreviewPlaying = MutableStateFlow(false)
    val isPreviewPlaying: StateFlow<Boolean> = _isPreviewPlaying

    private val context = application.applicationContext

    private val database = AppDatabase.getInstance(context)
    private val signalPreferences = AppSignalPreferences(context)
    private val dailyPlanNudgePreferences = DailyPlanNudgePreferences(context)
    private val dailyPlanNudgeScheduler = DailyPlanNudgeScheduler(
        context = context,
        database = database,
        preferences = dailyPlanNudgePreferences
    )
    private val sleepAutomationPreference = SleepAutomationPreference(context)
    private val sleepAutomationScheduler = SleepAutomationScheduler(context, sleepAutomationPreference)

    private val profileRepository = SleepProfileRepository(
        profileDao = database.alarmProfileDao()
    )
    private val sessionRepository = SleepSessionRepository(
        database = database,
        sessionDao = database.sleepSessionDao(),
        cueEventDao = database.cueEventDao()
    )
    private val alarmScheduler = AlarmScheduler.create(context, sessionRepository)

    private val _message = MutableStateFlow<String?>(null)

    val message: StateFlow<String?> = _message

    val pomodoroSignalSettings: StateFlow<AppSignalSettings> =
        signalPreferences.observe(AppSignalType.POMODORO).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppSignalSettings()
        )

    val reminderSignalSettings: StateFlow<AppSignalSettings> =
        signalPreferences.observe(AppSignalType.REMINDER).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppSignalSettings()
        )

    val calendarSignalSettings: StateFlow<AppSignalSettings> =
        signalPreferences.observe(AppSignalType.CALENDAR).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppSignalSettings()
        )

    val dailyPlanSignalSettings: StateFlow<AppSignalSettings> =
        signalPreferences.observe(AppSignalType.DAILY_PLAN).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppSignalSettings()
        )

    val dailyPlanNudgeSettings: StateFlow<DailyPlanNudgeSettings> =
        dailyPlanNudgePreferences.observe().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DailyPlanNudgeSettings()
        )

    val uiState: StateFlow<SettingsUiState> = combine(
        profileRepository.observeProfile(),
        sleepAutomationPreference.observe()
    ) { profile, automation -> buildPreview(profile).copy(sleepAutomation = automation) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState()
        )

    init {
        viewModelScope.launch { profileRepository.ensureProfileExists() }
    }

    // =================================================================
    // Превью расчёта
    // =================================================================

    private fun buildPreview(profile: AlarmProfileEntity): SettingsUiState {
        return try {
            val zone = ZoneId.systemDefault()
            val now = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone)

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

            SettingsUiState(
                profile = profile,
                plan = plan,
                cueSchedule = cueSchedule,
                planWarnings = SleepCalculator.warningsFor(plan = plan, now = now)
            )
        } catch (_: Throwable) {
            SettingsUiState(profile = profile)
        }
    }

    /** Минимальный map-оператор для одиночного Flow без импорта оператора. */

    // =================================================================
    // Сообщения
    // =================================================================

    fun clearMessage() {
        _message.value = null
    }

    fun reportSoundPermissionError() {
        _message.value = context.getString(R.string.sound_permission_error)
    }

    // =================================================================
    // Профиль: режим и время
    // =================================================================

    private fun updateProfile(transform: (AlarmProfileEntity) -> AlarmProfileEntity) {
        viewModelScope.launch { profileRepository.updateProfile(transform) }
    }

    /**
     * Возвращает название мелодии по URI.
     * Для системных ringtone — через RingtoneManager,
     * для файлов из хранилища — через DISPLAY_NAME.
     */
    fun getRingtoneName(uriString: String?): String? {
        return RingtonePickerHelper.getSoundTitle(context, uriString)
    }

    /**
     * Проигрывает мелодию для прослушивания.
     * Без нарастания и без зацикливания — как есть.
     * Канал USAGE_ALARM, чтобы слышно было как в будильнике.
     */
    fun previewRingtone(uriString: String?, volumePercent: Int = 100) {
        val uri = resolvePreviewRingtoneUri(uriString)
        if (uri == null) {
            _message.value = context.getString(R.string.notification_sound_preview_failed)
            return
        }
        stopPreview()
        val player = MediaPlayer()
        synchronized(previewLock) { previewPlayer = player }

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val gain = AppVolumeScale.gainForPercent(volumePercent)
                player.setAudioAttributes(AppAudioAttributes.sonification)
                player.setDataSource(context, uri)
                player.isLooping = false
                player.setVolume(gain, gain)

                player.setOnCompletionListener { mp ->
                    finishRingtonePreview(mp, reportFailure = false)
                }
                player.setOnErrorListener { mp, _, _ ->
                    finishRingtonePreview(mp, reportFailure = true)
                    true
                }

                player.prepare()
                if (!isCurrentPreview(player)) return@launch

                val lease = if (gain > 0f) {
                    AlarmStreamVolumeController.acquire(context)
                } else {
                    null
                }
                if (!attachPreviewLease(player, lease)) {
                    lease?.close()
                    return@launch
                }
                player.start()
                if (isCurrentPreview(player)) _isPreviewPlaying.value = true
            } catch (_: Throwable) {
                finishRingtonePreview(player, reportFailure = true)
            }
        }
        synchronized(previewLock) {
            if (previewPlayer === player) previewJob = job else job.cancel()
        }
    }

    /**
     * Останавливает прослушивание.
     */
    fun stopPreview() {
        val resources = synchronized(previewLock) {
            val current = Triple(previewJob, previewPlayer, previewVolumeLease)
            previewJob = null
            previewPlayer = null
            previewVolumeLease = null
            current
        }
        resources.first?.cancel()
        releasePreviewPlayer(resources.second)
        resources.third?.close()
        _isPreviewPlaying.value = false
    }

    private fun resolvePreviewRingtoneUri(uriString: String?): Uri? {
        if (!uriString.isNullOrBlank()) {
            return runCatching { Uri.parse(uriString) }.getOrNull()
        }
        return runCatching {
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }.getOrNull()
    }

    private fun isCurrentPreview(player: MediaPlayer): Boolean = synchronized(previewLock) {
        previewPlayer === player
    }

    private fun attachPreviewLease(player: MediaPlayer, lease: AutoCloseable?): Boolean =
        synchronized(previewLock) {
            if (previewPlayer !== player) return@synchronized false
            previewVolumeLease = lease
            true
        }

    private fun finishRingtonePreview(player: MediaPlayer, reportFailure: Boolean) {
        val resources = synchronized(previewLock) {
            if (previewPlayer !== player) return
            val current = Pair(previewJob, previewVolumeLease)
            previewJob = null
            previewPlayer = null
            previewVolumeLease = null
            current
        }
        resources.first?.cancel()
        releasePreviewPlayer(player)
        resources.second?.close()
        _isPreviewPlaying.value = false
        if (reportFailure) {
            _message.value = context.getString(R.string.notification_sound_preview_failed)
        }
    }

    private fun releasePreviewPlayer(player: MediaPlayer?) {
        player ?: return
        runCatching { if (player.isPlaying) player.stop() }
        runCatching { player.reset() }
        runCatching { player.release() }
    }

    override fun onCleared() {
        notificationPreviewJob?.cancel()
        stopPreview()
        super.onCleared()
    }

    fun setWakeTime(hour: Int, minute: Int) =
        updateProfile {
            it.copy(
                preferredWakeHour = hour.coerceIn(0, 23),
                preferredWakeMinute = minute.coerceIn(0, 59)
            )
        }

    fun setCycleLength(value: Int) = updateProfile { it.copy(cycleLengthMinutes = value) }
    fun setCycles(value: Int) = updateProfile { it.copy(cycles = value) }
    fun setOnsetLatency(value: Int) = updateProfile { it.copy(onsetLatencyMinutes = value) }

    // =================================================================
    // Профиль: lucid-подсказки
    // =================================================================

    fun setCuesEnabled(enabled: Boolean) {
        viewModelScope.launch { profileRepository.setCuesEnabled(enabled) }
    }

    fun setFirstCueDelay(value: Int) = updateProfile { it.copy(firstCueDelayMinutes = value) }
    fun setCueInterval(value: Int) = updateProfile { it.copy(cueIntervalMinutes = value) }
    fun setCueVolume(value: Int) = updateProfile { it.copy(cueVolumePercent = value) }

    fun setNotificationVolume(value: Int) = updateProfile {
        it.copy(notificationVolumePercent = value.coerceIn(0, 100))
    }

    fun setAppSignalSound(type: AppSignalType, mode: AppSoundMode, uriString: String? = null) {
        val slot = signalSoundSlot(type)
        launchSoundOperation(slot) { generation ->
            val sound = AppSoundSelection(mode, uriString).normalized()
            if (sound.uriString != null && !isAudioUriReadable(sound.uriString)) {
                reportUnavailableIfLatest(slot, generation)
                return@launchSoundOperation
            }
            if (!soundOperationPolicy.isLatest(slot, generation)) return@launchSoundOperation
            signalPreferences.setSound(type, sound)
            if (!soundOperationPolicy.isLatest(slot, generation)) return@launchSoundOperation
            if (sound.uriString == null || !ManagedSoundImport.isOwnedUri(context, sound.uriString)) {
                deleteAllManagedCopies(slot)
            }
        }
    }

    fun importAppSignalSound(type: AppSignalType, mode: AppSoundMode, uri: Uri) {
        val slot = signalSoundSlot(type)
        launchSoundOperation(slot) { generation ->
            val imported = ManagedSoundImport.copyIntoApp(context, uri, slot)
            if (imported == null) {
                reportUnavailableIfLatest(slot, generation)
                return@launchSoundOperation
            }
            if (!soundOperationPolicy.isLatest(slot, generation)) {
                ManagedSoundImport.deleteImportedCopy(context, imported.filePath)
                return@launchSoundOperation
            }
            signalPreferences.setSound(
                type,
                AppSoundSelection(mode, imported.uriString).normalized()
            )
            if (soundOperationPolicy.isLatest(slot, generation)) {
                deleteOlderManagedCopies(slot, imported.filePath)
            }
        }
    }

    fun setAppSignalVolume(type: AppSignalType, value: Int) {
        viewModelScope.launch {
            signalPreferences.setVolume(type, value)
        }
    }

    fun previewAppNotificationSound(type: AppSignalType) {
        if (notificationPreviewJob?.isActive == true) return
        stopPreview()
        notificationPreviewJob = viewModelScope.launch {
            val played = AppNotificationSoundPlayer.play(
                context = context,
                settings = signalPreferences.get(type),
                allowSystemFallback = false
            )
            if (!played) {
                _message.value = context.getString(R.string.notification_sound_preview_failed)
            }
            notificationPreviewJob = null
        }
    }

    fun setDailyPlanNudgesEnabled(enabled: Boolean) = updateDailyPlanNudges {
        dailyPlanNudgePreferences.setEnabled(enabled)
    }

    fun setDailyPlanMorningEnabled(enabled: Boolean) = updateDailyPlanNudges {
        dailyPlanNudgePreferences.setMorningReminderEnabled(enabled)
    }

    fun setDailyPlanBufferMinutes(minutes: Int) = updateDailyPlanNudges {
        dailyPlanNudgePreferences.setBufferMinutes(minutes)
    }

    fun setDailyPlanRepeatEnabled(enabled: Boolean) = updateDailyPlanNudges {
        dailyPlanNudgePreferences.setRepeatEnabled(enabled)
    }

    fun setDailyPlanRepeatIntervalMinutes(minutes: Int) = updateDailyPlanNudges {
        dailyPlanNudgePreferences.setRepeatIntervalMinutes(minutes)
    }

    fun setDailyPlanCutoffMinutesOfDay(minutes: Int) = updateDailyPlanNudges {
        dailyPlanNudgePreferences.setCutoffMinutesOfDay(minutes)
    }

    private fun updateDailyPlanNudges(update: suspend () -> Unit) {
        viewModelScope.launch {
            update()
            dailyPlanNudgeScheduler.refreshNow(playSoundIfDue = false)
        }
    }

    // ДОБАВЛЕНО (F7): режим расписания + смещение в REM-окне.
    fun setCueScheduleMode(mode: CueScheduleMode) =
        updateProfile { it.copy(cueScheduleMode = mode) }

    fun setRemCueOffset(value: Int) = updateProfile { it.copy(remCueOffsetPercent = value) }

    // =================================================================
    // Профиль: будильник
    // =================================================================

    fun setMathDifficulty(difficulty: MathDifficulty) =
        updateProfile { it.copy(mathDifficulty = difficulty) }

    fun setMathChallengeCount(count: Int) =
        updateProfile { it.copy(mathChallengeCount = count) }

    fun setQuietAlarm(enabled: Boolean) = updateProfile { it.copy(quietAlarmEnabled = enabled) }

    // ДОБАВЛЕНО (F1): вибрация.
    fun setVibration(enabled: Boolean) = updateProfile { it.copy(vibrationEnabled = enabled) }

    // ДОБАВЛЕНО (F2): URI мелодии (null = системная по умолчанию).
    fun setAlarmRingtoneUri(uri: String?) = saveSoundUri(uri, ALARM_SOUND_SLOT) { profile, savedUri ->
        profile.copy(alarmRingtoneUri = savedUri)
    }

    fun importAlarmRingtone(uri: Uri) = importProfileSound(
        source = uri,
        slot = ALARM_SOUND_SLOT
    ) { profile, savedUri ->
        profile.copy(alarmRingtoneUri = savedUri)
    }

    // ДОБАВЛЕНО: свой звук для ночного пиипа.
    fun setCueRingtoneUri(uri: String?) = saveSoundUri(uri, CUE_SOUND_SLOT) { profile, savedUri ->
        profile.copy(cueRingtoneUri = savedUri)
    }

    fun importCueRingtone(uri: Uri) = importProfileSound(
        source = uri,
        slot = CUE_SOUND_SLOT
    ) { profile, savedUri ->
        profile.copy(cueRingtoneUri = savedUri)
    }

    private fun importProfileSound(
        source: Uri,
        slot: String,
        transform: (AlarmProfileEntity, String) -> AlarmProfileEntity
    ) {
        launchSoundOperation(slot) { generation ->
            val imported = ManagedSoundImport.copyIntoApp(context, source, slot)
            if (imported == null) {
                reportUnavailableIfLatest(slot, generation)
                return@launchSoundOperation
            }
            if (!soundOperationPolicy.isLatest(slot, generation)) {
                ManagedSoundImport.deleteImportedCopy(context, imported.filePath)
                return@launchSoundOperation
            }
            profileRepository.updateProfile { profile -> transform(profile, imported.uriString) }
            if (soundOperationPolicy.isLatest(slot, generation)) {
                deleteOlderManagedCopies(slot, imported.filePath)
            }
        }
    }

    private fun saveSoundUri(
        uriString: String?,
        managedSlot: String,
        transform: (AlarmProfileEntity, String?) -> AlarmProfileEntity
    ) {
        launchSoundOperation(managedSlot) { generation ->
            val normalized = uriString?.trim()?.takeIf { it.isNotEmpty() }
            if (normalized != null && !isAudioUriReadable(normalized)) {
                reportUnavailableIfLatest(managedSlot, generation)
                return@launchSoundOperation
            }
            if (!soundOperationPolicy.isLatest(managedSlot, generation)) return@launchSoundOperation
            profileRepository.updateProfile { profile -> transform(profile, normalized) }
            if (
                soundOperationPolicy.isLatest(managedSlot, generation) &&
                (normalized == null || !ManagedSoundImport.isOwnedUri(context, normalized))
            ) {
                deleteAllManagedCopies(managedSlot)
            }
        }
    }

    private fun isAudioUriReadable(uriString: String): Boolean {
        return RingtonePickerHelper.isSoundReadable(context, uriString)
    }

    private fun signalSoundSlot(type: AppSignalType): String =
        "signal_${type.storagePrefix}"

    private fun launchSoundOperation(
        slot: String,
        operation: suspend (generation: Long) -> Unit
    ) {
        val generation = soundOperationPolicy.begin(slot)
        viewModelScope.launch(Dispatchers.IO) {
            soundOperationMutex(slot).withLock {
                if (!soundOperationPolicy.isLatest(slot, generation)) return@withLock
                operation(generation)
            }
        }
    }

    private fun soundOperationMutex(slot: String): Mutex = synchronized(soundOperationMutexes) {
        soundOperationMutexes.getOrPut(slot) { Mutex() }
    }

    private fun reportUnavailableIfLatest(slot: String, generation: Long) {
        if (soundOperationPolicy.isLatest(slot, generation)) {
            _message.value = context.getString(R.string.sound_file_unavailable)
        }
    }

    private suspend fun deleteOlderManagedCopies(slot: String, keepFilePath: String) {
        ManagedSoundImport.deleteOlderCopies(
            context = context,
            slot = slot,
            keepFilePath = keepFilePath,
            protectedFilePaths = protectedManagedFilePaths(slot)
        )
    }

    private suspend fun deleteAllManagedCopies(slot: String) {
        ManagedSoundImport.deleteAllCopies(
            context = context,
            slot = slot,
            protectedFilePaths = protectedManagedFilePaths(slot)
        )
    }

    private suspend fun protectedManagedFilePaths(slot: String): Set<String> {
        if (slot != CUE_SOUND_SLOT) return emptySet()
        val activeCueUri = sessionRepository.getActiveSession()?.cueRingtoneUri
        val activeCuePath = ManagedSoundImport.ownedFilePath(context, activeCueUri)
        return activeCuePath?.let(::setOf).orEmpty()
    }

    private companion object {
        const val ALARM_SOUND_SLOT = "alarm"
        const val CUE_SOUND_SLOT = "cue"
    }

    // ДОБАВЛЕНО (F10): smart-repeat.
    fun setSmartRepeatEnabled(enabled: Boolean) =
        updateProfile { it.copy(smartRepeatEnabled = enabled) }

    fun setSmartRepeatFirst(value: Int) =
        updateProfile { it.copy(smartRepeatFirstDelayMinutes = value) }

    fun setSmartRepeatInterval(value: Int) =
        updateProfile { it.copy(smartRepeatIntervalMinutes = value) }

    fun setSmartRepeatMax(value: Int) =
        updateProfile { it.copy(smartRepeatMaxCount = value) }

    // ДОБАВЛЕНО (F11): системный дублёр.
    fun setMirrorToSystemClock(enabled: Boolean) =
        updateProfile { it.copy(mirrorToSystemClock = enabled) }

    // ДОБАВЛЕНО (F9): автоопределение засыпания.
    fun setAutoDetectOnset(enabled: Boolean) {
        viewModelScope.launch {
            profileRepository.updateProfile { it.copy(autoDetectOnsetEnabled = enabled) }
            if (!enabled) {
                sleepAutomationPreference.setEnabled(false)
                cancelWaitingAutomationIfNeeded()
                sleepAutomationScheduler.scheduleNext()
            }
        }
    }

    fun setAutoCorrectWake(enabled: Boolean) =
        updateProfile { it.copy(autoCorrectWakeEnabled = enabled) }

    fun setAutoCorrectMinConfidence(value: Int) =
        updateProfile { it.copy(autoCorrectMinConfidencePercent = value.coerceIn(50, 95)) }

    fun setAutoCorrectMaxShift(value: Int) =
        updateProfile { it.copy(autoCorrectMaxShiftMinutes = value.coerceIn(0, 120)) }

    fun setAutomaticNightStart(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                if (!PermissionChecker.state(context).exactAlarmsAllowed) {
                    _message.value = context.getString(R.string.sleep_automation_exact_alarm_required)
                    return@launch
                }
                // Автоматический режим обязан уметь определять onset. Системный
                // дублёр отключаем: Android не позволяет надёжно сдвинуть его
                // после пассивного определения, иначе возможен двойной звонок.
                profileRepository.updateProfile {
                    it.copy(
                        autoDetectOnsetEnabled = true,
                        autoCorrectWakeEnabled = true,
                        mirrorToSystemClock = false
                    )
                }
            }
            sleepAutomationPreference.setEnabled(enabled)
            if (!enabled) cancelWaitingAutomationIfNeeded()
            sleepAutomationScheduler.scheduleNext()
        }
    }

    private suspend fun cancelWaitingAutomationIfNeeded() {
        val active = sessionRepository.getActiveSession()
        if (active?.isAutomationArmed() != true) return
        alarmScheduler.cancelAllAlarmsForSession(active.id)
        sessionRepository.cancelSession(active.id)
        SleepForegroundService.stop(context = context, cancelSession = false)
    }

    fun setAutomaticWindowStart(hour: Int, minute: Int) {
        viewModelScope.launch {
            val value = hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)
            val current = sleepAutomationPreference.get()
            sleepAutomationPreference.setWindowStart(value)
            if (value == current.windowEndMinutes) {
                sleepAutomationPreference.setWindowEnd((value + 60) % (24 * 60))
            }
            sleepAutomationScheduler.scheduleNext()
        }
    }

    fun setAutomaticWindowEnd(hour: Int, minute: Int) {
        viewModelScope.launch {
            val value = hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)
            val current = sleepAutomationPreference.get()
            sleepAutomationPreference.setWindowEnd(value)
            if (value == current.windowStartMinutes) {
                sleepAutomationPreference.setWindowStart((value - 60 + 24 * 60) % (24 * 60))
            }
            sleepAutomationScheduler.scheduleNext()
        }
    }

    // =================================================================
    // ДОБАВЛЕНО (F3): тема (DataStore, не профиль)
    // =================================================================


    // =================================================================
    // ДОБАВЛЕНО (F6): импорт настроек из JSON
    // =================================================================

    /**
     * Импортирует профиль из JSON-строки (прочитанной UI из SAF-потока).
     *
     * Возвращает true при успехе. При ошибке парсинга — false + сообщение.
     */
    fun importProfileJson(json: String): Boolean {
        val decoded = ProfileJsonCodec.decode(json)

        if (decoded == null) {
            _message.value = context.getString(R.string.import_error)
            return false
        }

        viewModelScope.launch {
            profileRepository.updateProfile { decoded }
            _message.value = context.getString(R.string.import_success)
        }

        return true
    }
    // =================================================================
    // Экспорт/импорт всех данных (BackupManager)
    // =================================================================

    private val backupManager = com.personal.sleepalarm.data.backup.BackupManager(context)

    fun exportAllData(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                backupManager.exportToUri(uri)
                _message.value = context.getString(R.string.export_all_success)
            }.onFailure {
                _message.value = context.getString(R.string.export_error) + ": " + it.message
            }
        }
    }

    fun importAllData(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val oldActive = sessionRepository.getActiveSession()
                val oldCueIndexes = oldActive?.let { session ->
                    sessionRepository.getCuesForSession(session.id).map { it.cueIndex }
                }.orEmpty()
                val oldReminders = database.reminderDao().getAll()
                val oldEvents = database.calendarEventDao().getAll()
                val oldTasks = database.taskDao().getAll()

                if (oldActive != null) {
                    // Stop the runtime bound to the database snapshot that is
                    // about to be replaced; the imported active session is
                    // started again below with its own identity.
                    SleepForegroundService.stop(context, cancelSession = false)
                }
                backupManager.importFromUri(uri)

                // URI из резервной копии другого телефона не имеют выданных
                // здесь разрешений. Не сохраняем ложный «выбранный» звук.
                profileRepository.updateProfile { imported ->
                    imported.copy(
                        alarmRingtoneUri = imported.alarmRingtoneUri
                            ?.takeIf(::isAudioUriReadable),
                        cueRingtoneUri = imported.cueRingtoneUri
                            ?.takeIf(::isAudioUriReadable)
                    )
                }

                oldActive?.let { session ->
                    alarmScheduler.cancelMainAlarm(session.id)
                    oldCueIndexes.forEach { cueIndex ->
                        alarmScheduler.cancelCueAlarm(session.id, cueIndex)
                    }
                }
                val reminderScheduler = ReminderScheduler(context)
                val reminderNotifications = ReminderNotificationBuilder(context)
                oldReminders.forEach {
                    reminderScheduler.cancel(it.id)
                    reminderNotifications.cancelPre(it.id)
                    reminderNotifications.cancelFire(it.id)
                }
                val eventScheduler = EventAlarmScheduler(context)
                val eventNotifications = EventNotificationBuilder(context)
                oldEvents.forEach {
                    eventScheduler.cancel(it.id)
                    eventNotifications.cancel(it.id)
                }
                val deadlineScheduler = TaskDeadlineScheduler(context)
                oldTasks.forEach { deadlineScheduler.cancel(it.id) }

                TaskEcosystemRepository(database).repairIntegrity()
                FocusProtocolManager(context).reconcileActiveSessions()

                val reminderRepository = ReminderRepository(
                    database.reminderDao(),
                    database.taskDao(),
                    database.activityRecordDao()
                )
                val importedReminders = database.reminderDao().getAll()
                    .mapNotNull { reminderRepository.reconcileForScheduling(it) }
                importedReminders.forEach { reminderScheduler.schedule(it) }
                eventScheduler.rescheduleAll(database.calendarEventDao().getSchedulableForAlarms())
                val explicitDeadlineTaskIds = importedReminders.asSequence()
                    .filter { it.linkedType == "TASK" }
                    .filter { it.triggerRule in setOf("BEFORE_DEADLINE", "BECOMES_URGENT") }
                    .mapNotNull { it.linkedId }
                    .toSet()
                database.taskDao().getAll().forEach { task ->
                    if (task.id in explicitDeadlineTaskIds) deadlineScheduler.cancel(task.id)
                    else deadlineScheduler.schedule(task)
                }

                val importedProfile = profileRepository.getProfile()
                sessionRepository.getActiveSession()?.let { importedSession ->
                    val session = importedSession.copy(
                        cueRingtoneUri = importedProfile.cueRingtoneUri
                    )
                    if (session != importedSession) {
                        sessionRepository.updateSession(session)
                    }
                    if (session.estimatedWakeTime > System.currentTimeMillis()) {
                        sessionRepository.recoverInterruptedCuePlaybacks(session.id)
                        alarmScheduler.rescheduleAllForSession(session)
                        val firstCue = sessionRepository.getScheduledCues(session.id)
                            .minOfOrNull { it.scheduledTime }
                        SleepForegroundService.start(
                            context = context,
                            sessionId = session.id,
                            wakeTime = session.estimatedWakeTime,
                            firstCueTime = firstCue,
                            showStartConfirmation = false
                        )
                    } else {
                        alarmScheduler.cancelAllAlarmsForSession(session.id)
                        sessionRepository.finishSession(
                            sessionId = session.id,
                            actualWakeTime = System.currentTimeMillis(),
                            dismissType = DismissType.MISSED
                        )
                    }
                }
                if (sessionRepository.getActiveSession() == null) {
                    SleepNotificationBuilder.cancelSleepNotification(context)
                    SleepNotificationBuilder.cancelAlarmNotification(context)
                    SleepNotificationBuilder.cancelTransientNotifications(context)
                }
                _message.value = context.getString(R.string.import_all_success)
            }.onFailure {
                _message.value = context.getString(R.string.import_error) + ": " + it.message
            }
        }
    }



}
