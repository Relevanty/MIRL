package com.personal.sleepalarm.ui.settings

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.R
import com.personal.sleepalarm.alarm.AlarmScheduler
import com.personal.sleepalarm.alarm.EventAlarmScheduler
import com.personal.sleepalarm.alarm.ReminderScheduler
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.AlarmProfileEntity
import com.personal.sleepalarm.data.repository.SleepProfileRepository
import com.personal.sleepalarm.data.repository.SleepSessionRepository
import com.personal.sleepalarm.domain.calculator.CueScheduleCalculator
import com.personal.sleepalarm.domain.calculator.SleepCalculator
import com.personal.sleepalarm.domain.model.CueSchedule
import com.personal.sleepalarm.domain.model.CueScheduleMode
import com.personal.sleepalarm.domain.model.DismissType
import com.personal.sleepalarm.domain.model.MathDifficulty
import com.personal.sleepalarm.domain.model.SleepPlan
import com.personal.sleepalarm.domain.model.SleepPlanWarning
import com.personal.sleepalarm.service.SleepForegroundService
import com.personal.sleepalarm.service.audio.AppNotificationSoundPlayer
import com.personal.sleepalarm.util.ProfileJsonCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    val planWarnings: Set<SleepPlanWarning> = emptySet()

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

    private val _isPreviewPlaying = MutableStateFlow(false)
    val isPreviewPlaying: StateFlow<Boolean> = _isPreviewPlaying

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
    private val alarmScheduler = AlarmScheduler.create(context, sessionRepository)

    private val _message = MutableStateFlow<String?>(null)

    val message: StateFlow<String?> = _message

    val uiState: StateFlow<SettingsUiState> = profileRepository.observeProfile()
        .map { profile -> buildPreview(profile) }
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
        if (uriString.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null

        // Сначала пробуем RingtoneManager (системные и media).
        val ringtoneTitle = runCatching {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        }.getOrNull()
        if (!ringtoneTitle.isNullOrBlank()) return ringtoneTitle

        // Fallback: имя файла через DISPLAY_NAME.
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        }.getOrNull()
    }

    /**
     * Проигрывает мелодию для прослушивания.
     * Без нарастания и без зацикливания — как есть.
     * Канал USAGE_ALARM, чтобы слышно было как в будильнике.
     */
    fun previewRingtone(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return

        stopPreview()
        val player = MediaPlayer()
        previewPlayer = player

        previewJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                player.setDataSource(context, uri)
                player.isLooping = false

                player.setOnCompletionListener { mp ->
                    _isPreviewPlaying.value = false
                    mp.release()
                    if (previewPlayer === mp) previewPlayer = null
                    previewJob = null
                }
                player.setOnErrorListener { mp, _, _ ->
                    _isPreviewPlaying.value = false
                    mp.release()
                    if (previewPlayer === mp) previewPlayer = null
                    previewJob = null
                    true
                }

                player.prepare()
                if (previewPlayer !== player) {
                    runCatching { player.release() }
                    return@launch
                }
                player.start()
                _isPreviewPlaying.value = true
            } catch (_: Throwable) {
                runCatching { player.release() }
                if (previewPlayer === player) previewPlayer = null
                _isPreviewPlaying.value = false
                previewJob = null
            }
        }
    }

    /**
     * Останавливает прослушивание.
     */
    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        previewPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) player.stop()
                player.release()
            }
        }
        previewPlayer = null
        _isPreviewPlaying.value = false
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

    fun previewAppNotificationSound() {
        if (notificationPreviewJob?.isActive == true) return
        stopPreview()
        notificationPreviewJob = viewModelScope.launch {
            val played = AppNotificationSoundPlayer.play(context)
            if (!played) {
                _message.value = context.getString(R.string.notification_sound_preview_failed)
            }
            notificationPreviewJob = null
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

    fun setQuietAlarm(enabled: Boolean) = updateProfile { it.copy(quietAlarmEnabled = enabled) }

    // ДОБАВЛЕНО (F1): вибрация.
    fun setVibration(enabled: Boolean) = updateProfile { it.copy(vibrationEnabled = enabled) }

    // ДОБАВЛЕНО (F2): URI мелодии (null = системная по умолчанию).
    fun setAlarmRingtoneUri(uri: String?) = saveSoundUri(uri) { profile, savedUri ->
        profile.copy(alarmRingtoneUri = savedUri)
    }

    // ДОБАВЛЕНО: свой звук для ночного пиипа.
    fun setCueRingtoneUri(uri: String?) = saveSoundUri(uri) { profile, savedUri ->
        profile.copy(cueRingtoneUri = savedUri)
    }

    private fun saveSoundUri(
        uriString: String?,
        transform: (AlarmProfileEntity, String?) -> AlarmProfileEntity
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalized = uriString?.trim()?.takeIf { it.isNotEmpty() }
            if (normalized != null && !isAudioUriReadable(normalized)) {
                _message.value = context.getString(R.string.sound_file_unavailable)
                return@launch
            }
            profileRepository.updateProfile { profile -> transform(profile, normalized) }
        }
    }

    private fun isAudioUriReadable(uriString: String): Boolean {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        val isSystemRingtone = runCatching {
            RingtoneManager.getRingtone(context, uri) != null
        }.getOrDefault(false)
        if (isSystemRingtone) return true
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
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
    fun setAutoDetectOnset(enabled: Boolean) =
        updateProfile { it.copy(autoDetectOnsetEnabled = enabled) }

    fun setAutoCorrectWake(enabled: Boolean) =
        updateProfile { it.copy(autoCorrectWakeEnabled = enabled) }

    fun setAutoCorrectMinConfidence(value: Int) =
        updateProfile { it.copy(autoCorrectMinConfidencePercent = value.coerceIn(50, 95)) }

    fun setAutoCorrectMaxShift(value: Int) =
        updateProfile { it.copy(autoCorrectMaxShiftMinutes = value.coerceIn(0, 120)) }

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
                oldReminders.forEach { reminderScheduler.cancel(it.id) }
                val eventScheduler = EventAlarmScheduler(context)
                oldEvents.forEach { eventScheduler.cancel(it.id) }

                database.reminderDao().getAll()
                    .filter { it.isEnabled }
                    .forEach { reminderScheduler.schedule(it) }
                eventScheduler.rescheduleAll(database.calendarEventDao().getAll())

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
                            firstCueTime = firstCue
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
                _message.value = context.getString(R.string.import_all_success)
            }.onFailure {
                _message.value = context.getString(R.string.import_error) + ": " + it.message
            }
        }
    }



}
