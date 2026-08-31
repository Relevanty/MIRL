package com.personal.sleepalarm.ui.settings

import com.personal.sleepalarm.ui.theme.appAccents
import com.personal.sleepalarm.ui.theme.AppAccentTone

import com.personal.sleepalarm.ui.system.SystemCheckScreen
import com.personal.sleepalarm.ui.settings.ThemesScreen
import android.app.Activity
import android.media.RingtoneManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.preferences.AppSignalSettings
import com.personal.sleepalarm.data.preferences.AppSignalType
import com.personal.sleepalarm.data.preferences.AppSoundMode
import com.personal.sleepalarm.data.preferences.AppSoundSelection
import com.personal.sleepalarm.data.preferences.DailyPlanNudgeSettings
import com.personal.sleepalarm.domain.automation.SleepAutomationWindow
import com.personal.sleepalarm.domain.externalcontext.ExternalContextSettings
import com.personal.sleepalarm.domain.model.CueScheduleMode
import com.personal.sleepalarm.domain.model.MathDifficulty
import com.personal.sleepalarm.ui.components.ChoiceChips
import com.personal.sleepalarm.ui.components.HelpCuesDialog
import com.personal.sleepalarm.ui.components.LabeledSlider
import com.personal.sleepalarm.ui.components.SectionCard
import com.personal.sleepalarm.ui.components.SwitchSetting
import com.personal.sleepalarm.ui.components.TimeStepper
import com.personal.sleepalarm.ui.misc.BriefingSettingsContent
import com.personal.sleepalarm.ui.misc.BriefingSettingsViewModel
import com.personal.sleepalarm.util.AppLanguageManager
import com.personal.sleepalarm.util.ProfileJsonCodec
import com.personal.sleepalarm.util.RingtonePickerHelper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.ZonedDateTime
import java.util.Locale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
/**
 * Экран настроек.
 *
 * Упрощён под новую логику будильника (пункт 4 промпта):
 *  - режим расчёта и время отхода ко сну удалены;
 *  - выбор типа cue (BEEP/BINAURAL/TTS) удалён — играет только файл,
 *    выбранный пользователем (cueRingtoneUri);
 *  - если звук не выбран, подсказки пропускаются — показывается красное предупреждение;
 *  - кнопка «Проверка надёжности» (SystemCheckDialog) переехала с главного экрана.
 *
 * Структура CuesSection: включение → звук (обязательный) → режим →
 * параметры режима → громкость → справка.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    briefingViewModel: BriefingSettingsViewModel = composeViewModel()
) {
    var showThemes by remember { mutableStateOf(false) }
    var showLauncherIcons by remember { mutableStateOf(false) }

    var showSystemCheck by remember { mutableStateOf(false) }
    var selectedPageName by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    val selectedPage = selectedPageName?.let { saved ->
        SettingsPage.entries.firstOrNull { it.name == saved }
    }
    var settingsQuery by rememberSaveable { mutableStateOf("") }

    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }


    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val pomodoroSignal by viewModel.pomodoroSignalSettings.collectAsStateWithLifecycle()
    val reminderSignal by viewModel.reminderSignalSettings.collectAsStateWithLifecycle()
    val calendarSignal by viewModel.calendarSignalSettings.collectAsStateWithLifecycle()
    val dailyPlanSignal by viewModel.dailyPlanSignalSettings.collectAsStateWithLifecycle()
    val dailyPlanNudges by viewModel.dailyPlanNudgeSettings.collectAsStateWithLifecycle()
    val briefingEnabled by briefingViewModel.enabled.collectAsStateWithLifecycle()

    var externalCity by rememberSaveable { mutableStateOf("") }
    var externalLatitude by rememberSaveable { mutableStateOf("") }
    var externalLongitude by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.externalContext.location) {
        state.externalContext.location?.let { location ->
            externalCity = location.cityLabel
            externalLatitude = location.latitude.toString()
            externalLongitude = location.longitude.toString()
        }
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val isPreviewPlaying by viewModel.isPreviewPlaying.collectAsStateWithLifecycle()

    val ringtoneDisplayName = remember(state.profile.alarmRingtoneUri) {
        viewModel.getRingtoneName(state.profile.alarmRingtoneUri)
            ?: state.profile.alarmRingtoneUri?.let {
                context.getString(R.string.sound_file_unavailable)
            }
    }
    val cueRingtoneDisplayName = remember(state.profile.cueRingtoneUri) {
        viewModel.getRingtoneName(state.profile.cueRingtoneUri)
            ?: state.profile.cueRingtoneUri?.let {
                context.getString(R.string.sound_file_unavailable)
            }
    }

    var showHelp by remember { mutableStateOf(false) }
    var pendingSignalType by rememberSaveable { mutableStateOf(AppSignalType.POMODORO) }

    // === Pickers ===

    val cuePickSystemLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = RingtonePickerHelper.parsePickedUri(result.data)
            viewModel.stopPreview()
            if (uri != null && RingtonePickerHelper.isDefaultAlias(uri, RingtoneManager.TYPE_ALARM)) {
                viewModel.setCueRingtoneUri(uri.toString())
            } else if (uri != null) viewModel.importCueRingtone(uri)
            else viewModel.setCueRingtoneUri(null)
        }
    }

    val cuePickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.stopPreview()
            viewModel.importCueRingtone(uri)
        }
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.stopPreview()
            viewModel.importAlarmRingtone(uri)
        }
    }

    val signalSystemSoundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = RingtonePickerHelper.parsePickedUri(result.data)
            if (uri != null) {
                viewModel.stopPreview()
                if (RingtonePickerHelper.isDefaultAlias(uri, RingtoneManager.TYPE_NOTIFICATION)) {
                    viewModel.setAppSignalSound(pendingSignalType, AppSoundMode.SYSTEM)
                } else {
                    viewModel.importAppSignalSound(
                        pendingSignalType,
                        AppSoundMode.SYSTEM,
                        uri
                    )
                }
            }
        }
    }

    val signalFileSoundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.stopPreview()
            viewModel.importAppSignalSound(
                pendingSignalType,
                AppSoundMode.FILE,
                uri
            )
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = ProfileJsonCodec.encode(state.profile)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    val exportAllLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportAllData(uri)
        }
    }

    val importAllLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            showImportConfirm = true
            pendingImportUri = uri
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val text = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
                        .readText()
                    viewModel.importProfileJson(text)
                }
            }
        }
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = RingtonePickerHelper.parsePickedUri(result.data)
            viewModel.stopPreview()

            if (uri != null) {
                if (RingtonePickerHelper.isDefaultAlias(uri, RingtoneManager.TYPE_ALARM)) {
                    viewModel.setAlarmRingtoneUri(null)
                } else {
                    viewModel.importAlarmRingtone(uri)
                }
            } else {
                viewModel.setAlarmRingtoneUri(null)
            }
        }
    }

    LaunchedEffect(message) {
        val text = message
        if (!text.isNullOrBlank()) {
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    if (showThemes) {
        ThemesScreen(onBack = { showThemes = false })
        return
    }

    if (showLauncherIcons) {
        LauncherIconsScreen(onBack = { showLauncherIcons = false })
        return
    }

    if (showSystemCheck) {
        SystemCheckScreen(onBack = { showSystemCheck = false })
        return
    }

    BackHandler(enabled = selectedPage != null) {
        selectedPageName = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedPage == null) {
                SettingsRootContent(
                    query = settingsQuery,
                    onQueryChange = { settingsQuery = it },
                    wakeHour = state.profile.preferredWakeHour,
                    wakeMinute = state.profile.preferredWakeMinute,
                    cycles = state.profile.cycles,
                    cuesEnabled = state.profile.cuesEnabled,
                    externalContextEnabled = state.externalContext.enabled,
                    briefingEnabled = briefingEnabled,
                    onOpen = { selectedPageName = it.name }
                )
                return@Column
            }

            SettingsDetailHeader(
                title = stringResource(selectedPage.titleRes),
                tone = selectedPage.accentTone(),
                onBack = { selectedPageName = null }
            )

            if (selectedPage == SettingsPage.SLEEP) {
                TimeSection(
                    wakeHour = state.profile.preferredWakeHour,
                    wakeMinute = state.profile.preferredWakeMinute,
                    onWakeTimeChange = { h, m -> viewModel.setWakeTime(h, m) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                CyclesSection(
                    cycleLength = state.profile.cycleLengthMinutes,
                    cycles = state.profile.cycles,
                    onsetLatency = state.profile.onsetLatencyMinutes,
                    onCycleLengthChange = { viewModel.setCycleLength(it) },
                    onCyclesChange = { viewModel.setCycles(it) },
                    onOnsetChange = { viewModel.setOnsetLatency(it) }
                )
            }

            if (selectedPage == SettingsPage.WAKE_AND_DREAMS) {
                AlarmSection(
                    mathDifficulty = state.profile.mathDifficulty,
                    mathChallengeCount = state.profile.mathChallengeCount,
                    quietAlarm = state.profile.quietAlarmEnabled,
                    vibration = state.profile.vibrationEnabled,
                    ringtoneUri = state.profile.alarmRingtoneUri,
                    ringtoneName = ringtoneDisplayName,
                    isPreviewPlaying = isPreviewPlaying,
                    smartRepeatEnabled = state.profile.smartRepeatEnabled,
                    smartRepeatFirst = state.profile.smartRepeatFirstDelayMinutes,
                    smartRepeatInterval = state.profile.smartRepeatIntervalMinutes,
                    smartRepeatMax = state.profile.smartRepeatMaxCount,
                    mirrorToSystem = state.profile.mirrorToSystemClock,
                    onMathDifficultyChange = { viewModel.setMathDifficulty(it) },
                    onMathChallengeCountChange = { viewModel.setMathChallengeCount(it) },
                    onQuietAlarmChange = { viewModel.setQuietAlarm(it) },
                    onVibrationChange = { viewModel.setVibration(it) },
                    onPickRingtone = {
                        viewModel.stopPreview()
                        ringtoneLauncher.launch(
                            RingtonePickerHelper.createPickerIntent(
                                title = context.getString(R.string.ringtone_picker_title),
                                existingUriString = state.profile.alarmRingtoneUri
                            )
                        )
                    },
                    onPickFromStorage = { pickFileLauncher.launch(arrayOf("audio/*")) },
                    onPreviewToggle = {
                        if (isPreviewPlaying) viewModel.stopPreview()
                        else viewModel.previewRingtone(state.profile.alarmRingtoneUri, 100)
                    },
                    onResetRingtone = {
                        viewModel.stopPreview()
                        viewModel.setAlarmRingtoneUri(null)
                    },
                    onSmartRepeatEnabledChange = { viewModel.setSmartRepeatEnabled(it) },
                    onSmartRepeatFirstChange = { viewModel.setSmartRepeatFirst(it) },
                    onSmartRepeatIntervalChange = { viewModel.setSmartRepeatInterval(it) },
                    onSmartRepeatMaxChange = { viewModel.setSmartRepeatMax(it) },
                    onMirrorChange = { viewModel.setMirrorToSystemClock(it) }
                )
            }

            if (selectedPage == SettingsPage.WAKE_AND_DREAMS) {
                CuesSection(
                    cuesEnabled = state.profile.cuesEnabled,
                    cueRingtoneUri = state.profile.cueRingtoneUri,
                    cueRingtoneName = cueRingtoneDisplayName,
                    isPreviewPlaying = isPreviewPlaying,
                    cueScheduleMode = state.profile.cueScheduleMode,
                    firstCueDelay = state.profile.firstCueDelayMinutes,
                    cueInterval = state.profile.cueIntervalMinutes,
                    cueVolume = state.profile.cueVolumePercent,
                    remOffset = state.profile.remCueOffsetPercent,
                    cueCount = state.cueSchedule.scheduledCount,
                    onCuesEnabledChange = { viewModel.setCuesEnabled(it) },
                    onPickCueSystem = {
                        viewModel.stopPreview()
                        cuePickSystemLauncher.launch(
                            RingtonePickerHelper.createPickerIntent(
                                title = context.getString(R.string.cue_picker_title),
                                existingUriString = state.profile.cueRingtoneUri
                            )
                        )
                    },
                    onPickCueFile = { cuePickFileLauncher.launch(arrayOf("audio/*")) },
                    onPreviewCueToggle = {
                        if (isPreviewPlaying) viewModel.stopPreview()
                        else viewModel.previewRingtone(
                            state.profile.cueRingtoneUri,
                            state.profile.cueVolumePercent
                        )
                    },
                    onResetCueRingtone = {
                        viewModel.stopPreview()
                        viewModel.setCueRingtoneUri(null)
                    },
                    onCueScheduleModeChange = { viewModel.setCueScheduleMode(it) },
                    onFirstCueDelayChange = { viewModel.setFirstCueDelay(it) },
                    onCueIntervalChange = { viewModel.setCueInterval(it) },
                    onCueVolumeChange = { viewModel.setCueVolume(it) },
                    onRemOffsetChange = { viewModel.setRemCueOffset(it) },
                    onOpenHelp = { showHelp = true }
                )
            }

            if (selectedPage == SettingsPage.AUDIO_AND_VOICE) {
                NotificationSoundsSection(
                    legacyVolume = state.profile.notificationVolumePercent,
                    pomodoro = pomodoroSignal,
                    reminders = reminderSignal,
                    calendar = calendarSignal,
                    dailyPlan = dailyPlanSignal,
                    soundTitle = { selection ->
                        val uriString = selection.uriString ?: if (
                            selection.mode == AppSoundMode.SYSTEM
                        ) {
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                                ?.toString()
                        } else {
                            null
                        }
                        RingtonePickerHelper.getSoundTitle(context, uriString)
                    },
                    onChooseSystem = { type, currentUri ->
                        pendingSignalType = type
                        signalSystemSoundLauncher.launch(
                            RingtonePickerHelper.createPickerIntent(
                                title = context.getString(
                                    R.string.app_signal_choose_system_title,
                                    context.getString(appSignalTitleRes(type))
                                ),
                                existingUriString = currentUri,
                                ringtoneType = RingtoneManager.TYPE_NOTIFICATION,
                                showSilent = false
                            )
                        )
                    },
                    onChooseFile = { type ->
                        pendingSignalType = type
                        signalFileSoundLauncher.launch(arrayOf("audio/*"))
                    },
                    onSilent = { type ->
                        viewModel.setAppSignalSound(type, AppSoundMode.SILENT)
                    },
                    onVolumeChange = viewModel::setAppSignalVolume,
                    onPreview = viewModel::previewAppNotificationSound
                )
                Spacer(modifier = Modifier.height(16.dp))
                SectionCard(
                    title = stringResource(R.string.briefing_settings_title),
                    tone = MaterialTheme.appAccents.calm
                ) {
                    BriefingSettingsContent(viewModel = briefingViewModel)
                }
            }

            if (selectedPage == SettingsPage.SLEEP) {
                AutoDetectSection(
                    automaticStart = state.sleepAutomation.enabled,
                    windowStartMinutes = state.sleepAutomation.windowStartMinutes,
                    windowEndMinutes = state.sleepAutomation.windowEndMinutes,
                    autoDetect = state.profile.autoDetectOnsetEnabled,
                    autoCorrect = state.profile.autoCorrectWakeEnabled,
                    minConfidence = state.profile.autoCorrectMinConfidencePercent,
                    maxShiftMinutes = state.profile.autoCorrectMaxShiftMinutes,
                    onAutomaticStartChange = viewModel::setAutomaticNightStart,
                    onWindowStartChange = viewModel::setAutomaticWindowStart,
                    onWindowEndChange = viewModel::setAutomaticWindowEnd,
                    onAutoDetectChange = { viewModel.setAutoDetectOnset(it) },
                    onAutoCorrectChange = { viewModel.setAutoCorrectWake(it) },
                    onMinConfidenceChange = viewModel::setAutoCorrectMinConfidence,
                    onMaxShiftChange = viewModel::setAutoCorrectMaxShift
                )
            }

            if (selectedPage == SettingsPage.PLANNING) {
                val now = ZonedDateTime.now()
                val automationCandidate = SleepAutomationWindow.containing(
                    now,
                    state.sleepAutomation.windowStartMinutes,
                    state.sleepAutomation.windowEndMinutes
                )?.start ?: SleepAutomationWindow.nextStart(
                    now,
                    state.sleepAutomation.windowStartMinutes
                )
                val automationEffective = state.sleepAutomation.enabled &&
                    state.profile.autoDetectOnsetEnabled &&
                    state.sleepAutomation.skippedWindowStartEpochDay !=
                    automationCandidate.toLocalDate().toEpochDay()
                DailyPlanNudgeSection(
                    settings = dailyPlanNudges,
                    automationEffective = automationEffective,
                    automationStartMinutes = state.sleepAutomation.windowStartMinutes,
                    onEnabledChange = viewModel::setDailyPlanNudgesEnabled,
                    onMorningEnabledChange = viewModel::setDailyPlanMorningEnabled,
                    onBufferChange = viewModel::setDailyPlanBufferMinutes,
                    onRepeatEnabledChange = viewModel::setDailyPlanRepeatEnabled,
                    onRepeatIntervalChange = viewModel::setDailyPlanRepeatIntervalMinutes,
                    onCutoffChange = viewModel::setDailyPlanCutoffMinutesOfDay
                )
                Spacer(modifier = Modifier.height(16.dp))
                SectionCard(
                    title = stringResource(R.string.settings_category_day_context),
                    tone = MaterialTheme.appAccents.info
                ) {
                    ExternalContextSection(
                        settings = state.externalContext,
                        city = externalCity,
                        latitude = externalLatitude,
                        longitude = externalLongitude,
                        onCityChange = { externalCity = it },
                        onLatitudeChange = { externalLatitude = it },
                        onLongitudeChange = { externalLongitude = it },
                        onSaveLocation = {
                            viewModel.saveExternalContextLocation(
                                externalCity,
                                externalLatitude,
                                externalLongitude
                            )
                        },
                        onEnabledChange = viewModel::setExternalContextEnabled,
                        onWeatherEnabledChange = viewModel::setExternalWeatherEnabled,
                        onTest = viewModel::testExternalContext,
                        onClear = {
                            externalCity = ""
                            externalLatitude = ""
                            externalLongitude = ""
                            viewModel.clearExternalContext()
                        }
                    )
                }
            }

            if (selectedPage == SettingsPage.APPEARANCE) {
                SectionCard(
                    title = stringResource(R.string.settings_category_appearance),
                    tone = MaterialTheme.appAccents.creative
                ) {
                LanguageSection()
                Spacer(modifier = Modifier.height(16.dp))
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth >= 360.dp) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { showThemes = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = stringResource(R.string.action_choose_theme))
                            }
                            OutlinedButton(
                                onClick = { showLauncherIcons = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = stringResource(R.string.action_choose_launcher_icon))
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showThemes = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(R.string.action_choose_theme))
                            }
                            OutlinedButton(
                                onClick = { showLauncherIcons = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(R.string.action_choose_launcher_icon))
                            }
                        }
                    }
                }
                }
            }

            if (selectedPage == SettingsPage.DATA_AND_SYSTEM) {
                SectionCard(
                    title = stringResource(R.string.settings_category_data),
                    tone = MaterialTheme.appAccents.info
                ) {
                OutlinedButton(
                    onClick = { exportAllLauncher.launch("sleep_alarm_full_backup.json") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(text = stringResource(R.string.action_export_all_data)) }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { importAllLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(text = stringResource(R.string.action_import_all_data)) }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { exportJsonLauncher.launch("sleep_alarm_profile.json") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(text = stringResource(R.string.action_export_settings)) }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { importJsonLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(text = stringResource(R.string.action_import_settings)) }
            }

                Spacer(modifier = Modifier.height(16.dp))
                SectionCard(
                    title = stringResource(R.string.settings_category_reliability),
                    tone = MaterialTheme.appAccents.success
                ) {
                    OutlinedButton(
                        onClick = { showSystemCheck = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(text = stringResource(R.string.action_system_check)) }
                }
                Spacer(modifier = Modifier.height(12.dp))
                DisclaimerSection()
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text(stringResource(R.string.import_all_title)) },
            text = {
                Text(stringResource(R.string.import_all_warning))
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri?.let { viewModel.importAllData(it) }
                    showImportConfirm = false
                    pendingImportUri = null
                }) {
                    Text(stringResource(R.string.action_import))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    pendingImportUri = null
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
    if (showHelp) {
        HelpCuesDialog(onDismiss = { showHelp = false })
    }

}

private enum class SettingsPage(val titleRes: Int) {
    SLEEP(R.string.settings_hub_sleep),
    WAKE_AND_DREAMS(R.string.settings_hub_wake_dreams),
    PLANNING(R.string.settings_hub_planning),
    AUDIO_AND_VOICE(R.string.settings_hub_audio_voice),
    APPEARANCE(R.string.settings_hub_appearance),
    DATA_AND_SYSTEM(R.string.settings_hub_data_system)
}

private data class SettingsHubItem(
    val page: SettingsPage,
    val title: String,
    val summary: String,
    val tone: AppAccentTone
)

private data class SettingsSearchEntry(
    val page: SettingsPage,
    val title: String
)

@Composable
private fun SettingsRootContent(
    query: String,
    onQueryChange: (String) -> Unit,
    wakeHour: Int,
    wakeMinute: Int,
    cycles: Int,
    cuesEnabled: Boolean,
    externalContextEnabled: Boolean,
    briefingEnabled: Boolean,
    onOpen: (SettingsPage) -> Unit
) {
    Text(
        text = stringResource(R.string.tab_settings),
        style = MaterialTheme.typography.headlineMedium
    )

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        label = { Text(stringResource(R.string.settings_search_label)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (query.isBlank()) null else {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.settings_search_clear)
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    val hubItems = listOf(
        SettingsHubItem(
            page = SettingsPage.SLEEP,
            title = stringResource(SettingsPage.SLEEP.titleRes),
            summary = stringResource(
                R.string.settings_hub_sleep_summary,
                wakeHour,
                wakeMinute,
                cycles
            ),
            tone = SettingsPage.SLEEP.accentTone()
        ),
        SettingsHubItem(
            page = SettingsPage.WAKE_AND_DREAMS,
            title = stringResource(SettingsPage.WAKE_AND_DREAMS.titleRes),
            summary = stringResource(
                if (cuesEnabled) R.string.settings_hub_wake_summary_on
                else R.string.settings_hub_wake_summary_off
            ),
            tone = SettingsPage.WAKE_AND_DREAMS.accentTone()
        ),
        SettingsHubItem(
            page = SettingsPage.PLANNING,
            title = stringResource(SettingsPage.PLANNING.titleRes),
            summary = stringResource(
                if (externalContextEnabled) R.string.settings_hub_planning_summary_on
                else R.string.settings_hub_planning_summary_off
            ),
            tone = SettingsPage.PLANNING.accentTone()
        ),
        SettingsHubItem(
            page = SettingsPage.AUDIO_AND_VOICE,
            title = stringResource(SettingsPage.AUDIO_AND_VOICE.titleRes),
            summary = stringResource(
                if (briefingEnabled) R.string.settings_hub_audio_summary_on
                else R.string.settings_hub_audio_summary_off
            ),
            tone = SettingsPage.AUDIO_AND_VOICE.accentTone()
        ),
        SettingsHubItem(
            page = SettingsPage.APPEARANCE,
            title = stringResource(SettingsPage.APPEARANCE.titleRes),
            summary = stringResource(R.string.settings_hub_appearance_summary),
            tone = SettingsPage.APPEARANCE.accentTone()
        ),
        SettingsHubItem(
            page = SettingsPage.DATA_AND_SYSTEM,
            title = stringResource(SettingsPage.DATA_AND_SYSTEM.titleRes),
            summary = stringResource(R.string.settings_hub_data_system_summary),
            tone = SettingsPage.DATA_AND_SYSTEM.accentTone()
        )
    )

    if (query.isBlank()) {
        hubItems.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    SettingsHubCard(
                        item = item,
                        onClick = { onOpen(item.page) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
        return
    }

    val searchEntries = settingsSearchEntries()
    val needle = query.trim()
    val results = searchEntries.filter { entry ->
        entry.title.contains(needle, ignoreCase = true) ||
            hubItems.first { it.page == entry.page }.title.contains(needle, ignoreCase = true) ||
            hubItems.first { it.page == entry.page }.summary.contains(needle, ignoreCase = true)
    }
    if (results.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_search_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 24.dp)
        )
    } else {
        results.forEach { entry ->
            SettingsSearchResult(
                entry = entry,
                category = hubItems.first { it.page == entry.page }.title,
                tone = hubItems.first { it.page == entry.page }.tone,
                onClick = { onOpen(entry.page) }
            )
        }
    }
}

@Composable
private fun SettingsHubCard(
    item: SettingsHubItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = item.tone.container,
        contentColor = item.tone.onContainer,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = item.tone.onContainer,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = item.tone.onContainer.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsSearchResult(
    entry: SettingsSearchEntry,
    category: String,
    tone: AppAccentTone,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = tone.container,
        contentColor = tone.onContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.bodyLarge, color = tone.onContainer)
                Text(
                    category,
                    style = MaterialTheme.typography.bodySmall,
                    color = tone.onContainer.copy(alpha = 0.78f)
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun SettingsDetailHeader(title: String, tone: AppAccentTone, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = tone.color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SettingsPage.accentTone(): AppAccentTone = when (this) {
    SettingsPage.SLEEP -> MaterialTheme.appAccents.sleep
    SettingsPage.WAKE_AND_DREAMS -> MaterialTheme.appAccents.energy
    SettingsPage.PLANNING -> MaterialTheme.appAccents.schedule
    SettingsPage.AUDIO_AND_VOICE -> MaterialTheme.appAccents.calm
    SettingsPage.APPEARANCE -> MaterialTheme.appAccents.creative
    SettingsPage.DATA_AND_SYSTEM -> MaterialTheme.appAccents.info
}

@Composable
private fun settingsSearchEntries(): List<SettingsSearchEntry> = listOf(
    SettingsSearchEntry(SettingsPage.SLEEP, stringResource(R.string.setting_wake_time)),
    SettingsSearchEntry(SettingsPage.SLEEP, stringResource(R.string.setting_cycles_count)),
    SettingsSearchEntry(SettingsPage.SLEEP, stringResource(R.string.setting_cycle_length)),
    SettingsSearchEntry(SettingsPage.SLEEP, stringResource(R.string.setting_onset_latency)),
    SettingsSearchEntry(SettingsPage.SLEEP, stringResource(R.string.setting_auto_start_sleep)),
    SettingsSearchEntry(SettingsPage.SLEEP, stringResource(R.string.setting_auto_detect_onset)),
    SettingsSearchEntry(SettingsPage.SLEEP, stringResource(R.string.setting_auto_correct_wake)),
    SettingsSearchEntry(SettingsPage.WAKE_AND_DREAMS, stringResource(R.string.setting_ringtone)),
    SettingsSearchEntry(SettingsPage.WAKE_AND_DREAMS, stringResource(R.string.setting_vibration)),
    SettingsSearchEntry(SettingsPage.WAKE_AND_DREAMS, stringResource(R.string.setting_math_difficulty)),
    SettingsSearchEntry(SettingsPage.WAKE_AND_DREAMS, stringResource(R.string.setting_smart_repeat_enabled)),
    SettingsSearchEntry(SettingsPage.WAKE_AND_DREAMS, stringResource(R.string.setting_cues_enabled)),
    SettingsSearchEntry(SettingsPage.WAKE_AND_DREAMS, stringResource(R.string.setting_cue_schedule_mode)),
    SettingsSearchEntry(SettingsPage.PLANNING, stringResource(R.string.daily_plan_settings_title)),
    SettingsSearchEntry(SettingsPage.PLANNING, stringResource(R.string.daily_plan_buffer)),
    SettingsSearchEntry(SettingsPage.PLANNING, stringResource(R.string.daily_plan_cutoff)),
    SettingsSearchEntry(SettingsPage.PLANNING, stringResource(R.string.external_context_city)),
    SettingsSearchEntry(SettingsPage.PLANNING, stringResource(R.string.external_context_weather_enabled)),
    SettingsSearchEntry(SettingsPage.AUDIO_AND_VOICE, stringResource(R.string.section_notification_sounds)),
    SettingsSearchEntry(SettingsPage.AUDIO_AND_VOICE, stringResource(R.string.setting_notification_volume)),
    SettingsSearchEntry(SettingsPage.AUDIO_AND_VOICE, stringResource(R.string.app_signal_pomodoro)),
    SettingsSearchEntry(SettingsPage.AUDIO_AND_VOICE, stringResource(R.string.app_signal_reminders)),
    SettingsSearchEntry(SettingsPage.AUDIO_AND_VOICE, stringResource(R.string.app_signal_calendar)),
    SettingsSearchEntry(SettingsPage.AUDIO_AND_VOICE, stringResource(R.string.briefing_settings_title)),
    SettingsSearchEntry(SettingsPage.AUDIO_AND_VOICE, stringResource(R.string.briefing_voice_volume)),
    SettingsSearchEntry(SettingsPage.AUDIO_AND_VOICE, stringResource(R.string.briefing_voice_rate)),
    SettingsSearchEntry(SettingsPage.APPEARANCE, stringResource(R.string.setting_app_language)),
    SettingsSearchEntry(SettingsPage.APPEARANCE, stringResource(R.string.action_choose_theme)),
    SettingsSearchEntry(SettingsPage.APPEARANCE, stringResource(R.string.action_choose_launcher_icon)),
    SettingsSearchEntry(SettingsPage.DATA_AND_SYSTEM, stringResource(R.string.action_export_all_data)),
    SettingsSearchEntry(SettingsPage.DATA_AND_SYSTEM, stringResource(R.string.action_import_all_data)),
    SettingsSearchEntry(SettingsPage.DATA_AND_SYSTEM, stringResource(R.string.action_system_check))
)

@Composable
private fun ExternalContextSection(
    settings: ExternalContextSettings,
    city: String,
    latitude: String,
    longitude: String,
    onCityChange: (String) -> Unit,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onSaveLocation: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onWeatherEnabledChange: (Boolean) -> Unit,
    onTest: () -> Unit,
    onClear: () -> Unit
) {
    Text(
        text = stringResource(R.string.external_context_intro),
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = city,
        onValueChange = onCityChange,
        label = { Text(stringResource(R.string.external_context_city)) },
        supportingText = { Text(stringResource(R.string.external_context_city_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = latitude,
            onValueChange = onLatitudeChange,
            label = { Text(stringResource(R.string.external_context_latitude)) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = longitude,
            onValueChange = onLongitudeChange,
            label = { Text(stringResource(R.string.external_context_longitude)) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
    Text(
        text = stringResource(R.string.external_context_coordinates_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
    OutlinedButton(
        onClick = onSaveLocation,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Text(stringResource(R.string.external_context_save_location))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        val location = settings.location
        val locationLabel = when {
            location == null -> ""
            location.cityLabel.isBlank() -> stringResource(R.string.external_context_city_unnamed)
            else -> location.cityLabel
        }
        Text(
            text = if (location == null) {
                stringResource(R.string.external_context_not_configured)
            } else {
                stringResource(
                    R.string.external_context_current_configuration,
                    locationLabel,
                    location.latitude,
                    location.longitude,
                    location.zoneId
                )
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp)
        )
    }

    Spacer(modifier = Modifier.height(14.dp))
    SwitchSetting(
        label = stringResource(R.string.external_context_enabled),
        checked = settings.enabled,
        onCheckedChange = onEnabledChange
    )
    Text(
        text = stringResource(R.string.external_context_enabled_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(10.dp))
    SwitchSetting(
        label = stringResource(R.string.external_context_weather_enabled),
        checked = settings.weatherEnabled,
        onCheckedChange = onWeatherEnabledChange
    )
    Text(
        text = stringResource(R.string.external_context_weather_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.appAccents.calm.container.copy(alpha = 0.62f),
        contentColor = MaterialTheme.appAccents.calm.onContainer
    ) {
        Text(
            text = stringResource(R.string.external_context_privacy),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp)
        )
    }

    OutlinedButton(
        onClick = onTest,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Text(stringResource(R.string.external_context_test))
    }
    TextButton(
        onClick = onClear,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.external_context_clear))
    }
}

@Composable
private fun NotificationSoundsSection(
    legacyVolume: Int,
    pomodoro: AppSignalSettings,
    reminders: AppSignalSettings,
    calendar: AppSignalSettings,
    dailyPlan: AppSignalSettings,
    soundTitle: (AppSoundSelection) -> String?,
    onChooseSystem: (AppSignalType, String?) -> Unit,
    onChooseFile: (AppSignalType) -> Unit,
    onSilent: (AppSignalType) -> Unit,
    onVolumeChange: (AppSignalType, Int) -> Unit,
    onPreview: (AppSignalType) -> Unit
) {
    var selectedTypeName by rememberSaveable { mutableStateOf(AppSignalType.POMODORO.name) }
    val selectedType = AppSignalType.entries.firstOrNull { it.name == selectedTypeName }
        ?: AppSignalType.POMODORO
    val selectedSettings = when (selectedType) {
        AppSignalType.POMODORO -> pomodoro
        AppSignalType.REMINDER -> reminders
        AppSignalType.CALENDAR -> calendar
        AppSignalType.DAILY_PLAN -> dailyPlan
    }

    SectionCard(
        title = stringResource(R.string.section_notification_sounds),
        tone = MaterialTheme.appAccents.energy
    ) {
        Text(
            text = stringResource(R.string.app_signals_mixer_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        ChoiceChips(
            label = stringResource(R.string.settings_signal_type),
            options = AppSignalType.entries,
            selected = selectedType,
            optionText = { type -> stringResource(appSignalTitleRes(type)) },
            onSelect = { selectedTypeName = it.name }
        )

        Spacer(modifier = Modifier.height(12.dp))

        AppSignalControl(
            type = selectedType,
            settings = selectedSettings,
            legacyVolume = legacyVolume,
            soundTitle = soundTitle,
            onChooseSystem = onChooseSystem,
            onChooseFile = onChooseFile,
            onSilent = onSilent,
            onVolumeChange = onVolumeChange,
            onPreview = onPreview
        )
    }
}

@Composable
private fun DailyPlanNudgeSection(
    settings: DailyPlanNudgeSettings,
    automationEffective: Boolean,
    automationStartMinutes: Int,
    onEnabledChange: (Boolean) -> Unit,
    onMorningEnabledChange: (Boolean) -> Unit,
    onBufferChange: (Int) -> Unit,
    onRepeatEnabledChange: (Boolean) -> Unit,
    onRepeatIntervalChange: (Int) -> Unit,
    onCutoffChange: (Int) -> Unit
) {
    SectionCard(
        title = stringResource(R.string.daily_plan_settings_title),
        tone = MaterialTheme.appAccents.schedule
    ) {
        Text(
            text = stringResource(R.string.daily_plan_settings_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        SwitchSetting(
            label = stringResource(R.string.daily_plan_enabled),
            checked = settings.enabled,
            onCheckedChange = onEnabledChange
        )
        if (settings.enabled) {
            SwitchSetting(
                label = stringResource(R.string.daily_plan_morning_enabled),
                checked = settings.morningReminderEnabled,
                onCheckedChange = onMorningEnabledChange
            )
            LabeledSlider(
                label = stringResource(R.string.daily_plan_buffer),
                value = settings.bufferMinutes,
                valueText = stringResource(
                    R.string.daily_plan_minutes_format,
                    settings.bufferMinutes
                ),
                valueRange = 0f..720f,
                steps = 23,
                onValueChange = onBufferChange
            )
            SwitchSetting(
                label = stringResource(R.string.daily_plan_repeat_enabled),
                checked = settings.repeatEnabled,
                onCheckedChange = onRepeatEnabledChange
            )
            if (settings.repeatEnabled) {
                LabeledSlider(
                    label = stringResource(R.string.daily_plan_repeat_interval),
                    value = settings.repeatIntervalMinutes,
                    valueText = stringResource(
                        R.string.daily_plan_minutes_format,
                        settings.repeatIntervalMinutes
                    ),
                    valueRange = 5f..120f,
                    steps = 22,
                    onValueChange = onRepeatIntervalChange
                )
            }
            val cutoffHour = settings.cutoffMinutesOfDay / 60
            val cutoffMinute = settings.cutoffMinutesOfDay % 60
            TimeStepper(
                label = stringResource(R.string.daily_plan_cutoff),
                hour = cutoffHour,
                minute = cutoffMinute,
                onHourChange = { onCutoffChange(it * 60 + cutoffMinute) },
                onMinuteChange = { onCutoffChange(cutoffHour * 60 + it) }
            )
            val cutoffExplanation = if (automationEffective) {
                val automationTime = String.format(
                    Locale.getDefault(),
                    "%02d:%02d",
                    automationStartMinutes / 60,
                    automationStartMinutes % 60
                )
                stringResource(
                    R.string.daily_plan_effective_cutoff_automation,
                    automationTime
                )
            } else {
                stringResource(R.string.daily_plan_effective_cutoff_fallback)
            }
            Text(
                text = cutoffExplanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun AppSignalControl(
    type: AppSignalType,
    settings: AppSignalSettings,
    legacyVolume: Int,
    soundTitle: (AppSoundSelection) -> String?,
    onChooseSystem: (AppSignalType, String?) -> Unit,
    onChooseFile: (AppSignalType) -> Unit,
    onSilent: (AppSignalType) -> Unit,
    onVolumeChange: (AppSignalType, Int) -> Unit,
    onPreview: (AppSignalType) -> Unit
) {
    val volume = settings.effectiveVolume(legacyVolume)
    val currentSound = when (settings.sound.mode) {
        AppSoundMode.SILENT -> stringResource(R.string.app_signal_silent)
        AppSoundMode.SYSTEM -> soundTitle(settings.sound) ?: if (
            settings.sound.uriString != null
        ) {
            stringResource(R.string.sound_file_unavailable)
        } else {
            stringResource(R.string.app_signal_system_default)
        }
        AppSoundMode.FILE -> soundTitle(settings.sound)
            ?: stringResource(R.string.sound_file_unavailable)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = stringResource(appSignalTitleRes(type)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(appSignalDescriptionRes(type)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.appAccents.calm.container.copy(alpha = 0.72f),
                contentColor = MaterialTheme.appAccents.calm.onContainer
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Text(
                        text = stringResource(R.string.app_signal_current_sound),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appAccents.calm.onContainer.copy(alpha = 0.72f)
                    )
                    Text(
                        text = currentSound,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onChooseSystem(
                            type,
                            settings.sound.uriString.takeIf {
                                settings.sound.mode == AppSoundMode.SYSTEM
                            }
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.app_signal_choose_system))
                }
                OutlinedButton(
                    onClick = { onChooseFile(type) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.app_signal_choose_file))
                }
            }

            TextButton(
                onClick = { onSilent(type) },
                enabled = settings.sound.mode != AppSoundMode.SILENT,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.app_signal_make_silent))
            }

            LabeledSlider(
                label = stringResource(R.string.setting_notification_volume),
                value = volume,
                valueText = stringResource(R.string.percent_format, volume),
                valueRange = 0f..100f,
                steps = 19,
                onValueChange = { onVolumeChange(type, it) }
            )

            OutlinedButton(
                onClick = { onPreview(type) },
                enabled = settings.sound.mode != AppSoundMode.SILENT && volume > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_test_notification_sound))
            }
        }
    }
}

private fun appSignalTitleRes(type: AppSignalType): Int = when (type) {
    AppSignalType.POMODORO -> R.string.app_signal_pomodoro
    AppSignalType.REMINDER -> R.string.app_signal_reminders
    AppSignalType.CALENDAR -> R.string.app_signal_calendar
    AppSignalType.DAILY_PLAN -> R.string.app_signal_daily_plan
}

private fun appSignalDescriptionRes(type: AppSignalType): Int = when (type) {
    AppSignalType.POMODORO -> R.string.app_signal_pomodoro_description
    AppSignalType.REMINDER -> R.string.app_signal_reminders_description
    AppSignalType.CALENDAR -> R.string.app_signal_calendar_description
    AppSignalType.DAILY_PLAN -> R.string.app_signal_daily_plan_description
}

@Composable
private fun LanguageSection() {
    val context = LocalContext.current
    val selectedLanguage = AppLanguageManager.currentLanguage(context)

    ChoiceChips(
        label = stringResource(R.string.setting_app_language),
        options = listOf(
            AppLanguageManager.SYSTEM,
            AppLanguageManager.RUSSIAN,
            AppLanguageManager.ENGLISH
        ),
        selected = selectedLanguage,
        optionText = { language ->
            stringResource(
                when (language) {
                    AppLanguageManager.RUSSIAN -> R.string.language_russian
                    AppLanguageManager.ENGLISH -> R.string.language_english
                    else -> R.string.language_system
                }
            )
        },
        onSelect = { language ->
            if (language != selectedLanguage) {
                AppLanguageManager.setLanguage(context, language)
            }
        }
    )

    Text(
        text = stringResource(R.string.setting_app_language_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

// =====================================================================
// Секция: время (только подъём — ориентир для расчёта от now)
// =====================================================================

@Composable
private fun TimeSection(
    wakeHour: Int,
    wakeMinute: Int,
    onWakeTimeChange: (Int, Int) -> Unit
) {
    SectionCard(
        title = stringResource(R.string.section_time),
        tone = MaterialTheme.appAccents.sleep
    ) {
        TimeStepper(
            label = stringResource(R.string.setting_wake_time),
            hour = wakeHour,
            minute = wakeMinute,
            onHourChange = { onWakeTimeChange(it, wakeMinute) },
            onMinuteChange = { onWakeTimeChange(wakeHour, it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.hint_wake_time),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// =====================================================================
// Секция: циклы (без зависимости от режима)
// =====================================================================

@Composable
private fun CyclesSection(
    cycleLength: Int,
    cycles: Int,
    onsetLatency: Int,
    onCycleLengthChange: (Int) -> Unit,
    onCyclesChange: (Int) -> Unit,
    onOnsetChange: (Int) -> Unit
) {
    SectionCard(
        title = stringResource(R.string.section_cycles),
        tone = MaterialTheme.appAccents.progress
    ) {
        LabeledSlider(
            label = stringResource(R.string.setting_cycle_length),
            value = cycleLength,
            valueText = stringResource(R.string.minutes_format, cycleLength),
            valueRange = 75f..120f,
            steps = 8,
            onValueChange = onCycleLengthChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        ChoiceChips(
            label = stringResource(R.string.setting_cycles_count),
            options = listOf(3, 4, 5, 6, 7),
            selected = cycles,
            optionText = { it.toString() },
            onSelect = onCyclesChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        LabeledSlider(
            label = stringResource(R.string.setting_onset_latency),
            value = onsetLatency,
            valueText = stringResource(R.string.minutes_format, onsetLatency),
            valueRange = 5f..45f,
            steps = 7,
            onValueChange = onOnsetChange
        )
    }
}

// =====================================================================
// Секция: lucid-подсказки (пункт 4 — только свой звук)
// =====================================================================

/**
 * Порядок в секции:
 * 1) включение подсказок;
 * 2) обязательный выбор звука (системный / файл / прослушать / сбросить)
 *    + красное предупреждение, если звук не выбран;
 * 3) режим расписания (PERIODIC / REM_TARGETED);
 * 4) параметры режима (первый сигнал+интервал ИЛИ REM offset);
 * 5) громкость + предупреждение о громкости;
 * 6) справка (кнопка «Как это работает…»).
 */
@Composable
private fun CuesSection(
    cuesEnabled: Boolean,
    cueRingtoneUri: String?,
    cueRingtoneName: String?,
    isPreviewPlaying: Boolean,
    cueScheduleMode: CueScheduleMode,
    firstCueDelay: Int,
    cueInterval: Int,
    cueVolume: Int,
    remOffset: Int,
    cueCount: Int,
    onCuesEnabledChange: (Boolean) -> Unit,
    onPickCueSystem: () -> Unit,
    onPickCueFile: () -> Unit,
    onPreviewCueToggle: () -> Unit,
    onResetCueRingtone: () -> Unit,
    onCueScheduleModeChange: (CueScheduleMode) -> Unit,
    onFirstCueDelayChange: (Int) -> Unit,
    onCueIntervalChange: (Int) -> Unit,
    onCueVolumeChange: (Int) -> Unit,
    onRemOffsetChange: (Int) -> Unit,
    onOpenHelp: () -> Unit
) {
    SectionCard(
        title = stringResource(R.string.section_lucid_cues),
        tone = MaterialTheme.appAccents.creative
    ) {
        // 1. Включение.
        SwitchSetting(
            label = stringResource(R.string.setting_cues_enabled),
            checked = cuesEnabled,
            onCheckedChange = onCuesEnabledChange
        )

        if (!cuesEnabled) {
            Text(
                text = stringResource(R.string.hint_cues_disabled),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            return@SectionCard
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. ЗВУК — обязательный выбор.
        Text(
            text = stringResource(R.string.setting_cue_sound),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = cueRingtoneName ?: stringResource(R.string.cue_sound_default),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Красное предупреждение, если звук не выбран — подсказки будут пропускаться.
        if (cueRingtoneUri == null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.warning_no_cue_sound),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appAccents.warning.color
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(onClick = onPickCueSystem, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.action_pick_ringtone))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(onClick = onPickCueFile, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.action_pick_from_storage))
        }

        if (cueRingtoneUri != null) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(onClick = onPreviewCueToggle, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = if (isPreviewPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        if (isPreviewPlaying) R.string.action_stop_preview
                        else R.string.action_preview_ringtone
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(onClick = onResetCueRingtone, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.action_remove_cue_sound))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Режим расписания.
        ChoiceChips(
            label = stringResource(R.string.setting_cue_schedule_mode),
            options = listOf(CueScheduleMode.PERIODIC, CueScheduleMode.REM_TARGETED),
            selected = cueScheduleMode,
            optionText = { value ->
                when (value) {
                    CueScheduleMode.PERIODIC -> stringResource(R.string.cue_mode_periodic)
                    CueScheduleMode.REM_TARGETED -> stringResource(R.string.cue_mode_rem)
                }
            },
            onSelect = onCueScheduleModeChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Параметры режима.
        when (cueScheduleMode) {
            CueScheduleMode.PERIODIC -> {
                LabeledSlider(
                    label = stringResource(R.string.setting_first_cue_delay),
                    value = firstCueDelay,
                    valueText = stringResource(R.string.minutes_format, firstCueDelay),
                    valueRange = 20f..120f,
                    steps = 19,
                    onValueChange = onFirstCueDelayChange
                )

                Spacer(modifier = Modifier.height(16.dp))

                ChoiceChips(
                    label = stringResource(R.string.setting_cue_interval),
                    options = listOf(20, 30, 45, 60),
                    selected = cueInterval,
                    optionText = { stringResource(R.string.minutes_format, it) },
                    onSelect = onCueIntervalChange
                )
            }

            CueScheduleMode.REM_TARGETED -> {
                LabeledSlider(
                    label = stringResource(R.string.setting_rem_offset),
                    value = remOffset,
                    valueText = stringResource(R.string.percent_format, remOffset),
                    valueRange = 10f..90f,
                    steps = 15,
                    onValueChange = onRemOffsetChange
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Громкость + предупреждение.
        LabeledSlider(
            label = stringResource(R.string.setting_cue_volume),
            value = cueVolume,
            valueText = stringResource(R.string.percent_format, cueVolume),
            valueRange = 5f..100f,
            steps = 18,
            onValueChange = onCueVolumeChange
        )

        Text(
            text = stringResource(R.string.setting_cue_volume_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.setting_cues_preview, cueCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appAccents.focus.color
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 6. Справка (последней — как расширенная информация).
        OutlinedButton(onClick = onOpenHelp, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.action_cues_help))
        }
    }
}

// =====================================================================
// Секция: будильник
// =====================================================================

@Composable
private fun AlarmSection(
    mathDifficulty: MathDifficulty,
    mathChallengeCount: Int,
    quietAlarm: Boolean,
    vibration: Boolean,
    ringtoneUri: String?,
    ringtoneName: String?,
    isPreviewPlaying: Boolean,
    smartRepeatEnabled: Boolean,
    smartRepeatFirst: Int,
    smartRepeatInterval: Int,
    smartRepeatMax: Int,
    mirrorToSystem: Boolean,
    onMathDifficultyChange: (MathDifficulty) -> Unit,
    onMathChallengeCountChange: (Int) -> Unit,
    onQuietAlarmChange: (Boolean) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onPickRingtone: () -> Unit,
    onPickFromStorage: () -> Unit,
    onPreviewToggle: () -> Unit,
    onResetRingtone: () -> Unit,
    onSmartRepeatEnabledChange: (Boolean) -> Unit,
    onSmartRepeatFirstChange: (Int) -> Unit,
    onSmartRepeatIntervalChange: (Int) -> Unit,
    onSmartRepeatMaxChange: (Int) -> Unit,
    onMirrorChange: (Boolean) -> Unit
) {
    SectionCard(
        title = stringResource(R.string.section_alarm),
        tone = MaterialTheme.appAccents.energy
    ) {
        ChoiceChips(
            label = stringResource(R.string.setting_math_difficulty),
            options = MathDifficulty.entries,
            selected = mathDifficulty,
            optionText = { value ->
                when (value) {
                    MathDifficulty.EASY -> stringResource(R.string.math_difficulty_easy)
                    MathDifficulty.MEDIUM -> stringResource(R.string.math_difficulty_medium)
                    MathDifficulty.HARD -> stringResource(R.string.math_difficulty_hard)
                    MathDifficulty.EXPERT -> stringResource(R.string.math_difficulty_expert)
                    MathDifficulty.EXTREME -> stringResource(R.string.math_difficulty_extreme)
                }
            },
            onSelect = onMathDifficultyChange
        )

        Text(
            text = stringResource(
                when (mathDifficulty) {
                    MathDifficulty.EASY -> R.string.math_difficulty_easy_hint
                    MathDifficulty.MEDIUM -> R.string.math_difficulty_medium_hint
                    MathDifficulty.HARD -> R.string.math_difficulty_hard_hint
                    MathDifficulty.EXPERT -> R.string.math_difficulty_expert_hint
                    MathDifficulty.EXTREME -> R.string.math_difficulty_extreme_hint
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        LabeledSlider(
            label = stringResource(R.string.setting_math_challenge_count),
            value = mathChallengeCount,
            valueText = mathChallengeCount.toString(),
            valueRange = 1f..10f,
            steps = 8,
            onValueChange = onMathChallengeCountChange
        )

        Text(
            text = stringResource(R.string.setting_math_challenge_count_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        SwitchSetting(
            label = stringResource(R.string.setting_quiet_alarm),
            checked = quietAlarm,
            onCheckedChange = onQuietAlarmChange
        )

        Spacer(modifier = Modifier.height(12.dp))

        SwitchSetting(
            label = stringResource(R.string.setting_vibration),
            checked = vibration,
            onCheckedChange = onVibrationChange
        )

        Spacer(modifier = Modifier.height(12.dp))

        // === Мелодия будильника ===
        Text(
            text = stringResource(R.string.setting_ringtone),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = ringtoneName ?: stringResource(R.string.ringtone_system_default),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(onClick = onPickRingtone, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.action_pick_ringtone))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(onClick = onPickFromStorage, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.action_pick_from_storage))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(onClick = onPreviewToggle, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = if (isPreviewPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(
                    if (isPreviewPlaying) R.string.action_stop_preview
                    else R.string.action_preview_ringtone
                )
            )
        }

        if (ringtoneUri != null) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(onClick = onResetRingtone, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.action_reset_ringtone))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === Smart-repeat ===
        SwitchSetting(
            label = stringResource(R.string.setting_smart_repeat_enabled),
            checked = smartRepeatEnabled,
            onCheckedChange = onSmartRepeatEnabledChange
        )

        if (smartRepeatEnabled) {
            Spacer(modifier = Modifier.height(12.dp))

            LabeledSlider(
                label = stringResource(R.string.setting_smart_repeat_first),
                value = smartRepeatFirst,
                valueText = stringResource(R.string.minutes_format, smartRepeatFirst),
                valueRange = 1f..10f,
                steps = 8,
                onValueChange = onSmartRepeatFirstChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            LabeledSlider(
                label = stringResource(R.string.setting_smart_repeat_interval),
                value = smartRepeatInterval,
                valueText = stringResource(R.string.minutes_format, smartRepeatInterval),
                valueRange = 1f..10f,
                steps = 8,
                onValueChange = onSmartRepeatIntervalChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            LabeledSlider(
                label = stringResource(R.string.setting_smart_repeat_max),
                value = smartRepeatMax,
                valueText = smartRepeatMax.toString(),
                valueRange = 1f..20f,
                steps = 18,
                onValueChange = onSmartRepeatMaxChange
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === Системный дублёр ===
        SwitchSetting(
            label = stringResource(R.string.setting_mirror_system),
            checked = mirrorToSystem,
            onCheckedChange = onMirrorChange
        )
        Text(
            text = stringResource(R.string.hint_mirror),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// =====================================================================
// Секция: автоопределение засыпания
// =====================================================================

@Composable
private fun AutoDetectSection(
    automaticStart: Boolean,
    windowStartMinutes: Int,
    windowEndMinutes: Int,
    autoDetect: Boolean,
    autoCorrect: Boolean,
    minConfidence: Int,
    maxShiftMinutes: Int,
    onAutomaticStartChange: (Boolean) -> Unit,
    onWindowStartChange: (Int, Int) -> Unit,
    onWindowEndChange: (Int, Int) -> Unit,
    onAutoDetectChange: (Boolean) -> Unit,
    onAutoCorrectChange: (Boolean) -> Unit,
    onMinConfidenceChange: (Int) -> Unit,
    onMaxShiftChange: (Int) -> Unit
) {
    SectionCard(
        title = stringResource(R.string.section_auto_detect),
        tone = MaterialTheme.appAccents.info
    ) {
        SwitchSetting(
            label = stringResource(R.string.setting_auto_start_sleep),
            checked = automaticStart,
            onCheckedChange = onAutomaticStartChange
        )

        if (automaticStart) {
            Spacer(modifier = Modifier.height(12.dp))
            TimeStepper(
                label = stringResource(R.string.setting_auto_start_window_begin),
                hour = windowStartMinutes / 60,
                minute = windowStartMinutes % 60,
                onHourChange = { onWindowStartChange(it, windowStartMinutes % 60) },
                onMinuteChange = { onWindowStartChange(windowStartMinutes / 60, it) }
            )
            Spacer(modifier = Modifier.height(10.dp))
            TimeStepper(
                label = stringResource(R.string.setting_auto_start_window_end),
                hour = windowEndMinutes / 60,
                minute = windowEndMinutes % 60,
                onHourChange = { onWindowEndChange(it, windowEndMinutes % 60) },
                onMinuteChange = { onWindowEndChange(windowEndMinutes / 60, it) }
            )
            Text(
                text = stringResource(R.string.hint_auto_start_sleep),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        SwitchSetting(
            label = stringResource(R.string.setting_auto_detect_onset),
            checked = autoDetect,
            onCheckedChange = onAutoDetectChange
        )

        if (autoDetect) {
            Spacer(modifier = Modifier.height(12.dp))

            SwitchSetting(
                label = stringResource(R.string.setting_auto_correct_wake),
                checked = autoCorrect,
                onCheckedChange = onAutoCorrectChange
            )
            if (autoCorrect) {
                Spacer(modifier = Modifier.height(12.dp))
                LabeledSlider(
                    label = stringResource(R.string.setting_auto_min_confidence),
                    value = minConfidence,
                    valueText = "$minConfidence%",
                    valueRange = 50f..95f,
                    steps = 8,
                    onValueChange = onMinConfidenceChange
                )
                Spacer(modifier = Modifier.height(12.dp))
                LabeledSlider(
                    label = stringResource(R.string.setting_auto_max_shift),
                    value = maxShiftMinutes,
                    valueText = stringResource(R.string.minutes_format, maxShiftMinutes),
                    valueRange = 0f..120f,
                    steps = 23,
                    onValueChange = onMaxShiftChange
                )
                Text(
                    stringResource(R.string.setting_auto_hard_wake_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.hint_auto_detect),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// =====================================================================
// Секция: тема
// =====================================================================


// =====================================================================
// Секция: данные
// =====================================================================

@Composable
private fun DataSection(
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    SectionCard(
        title = stringResource(R.string.section_data),
        tone = MaterialTheme.appAccents.info
    ) {
        OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.action_export_settings))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.action_import_settings))
        }
    }
}

// =====================================================================
// Секция: дисклеймер
// =====================================================================

@Composable
private fun DisclaimerSection() {
    SectionCard(
        title = stringResource(R.string.section_disclaimer),
        tone = MaterialTheme.appAccents.warning
    ) {
        Text(
            text = stringResource(R.string.disclaimer_text),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
