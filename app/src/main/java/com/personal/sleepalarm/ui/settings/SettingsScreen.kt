package com.personal.sleepalarm.ui.settings

import com.personal.sleepalarm.ui.system.SystemCheckScreen
import com.personal.sleepalarm.ui.settings.ThemesScreen
import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.sleepalarm.R
import com.personal.sleepalarm.domain.model.CueScheduleMode
import com.personal.sleepalarm.domain.model.MathDifficulty
import com.personal.sleepalarm.ui.components.ChoiceChips
import com.personal.sleepalarm.ui.components.HelpCuesDialog
import com.personal.sleepalarm.ui.components.LabeledSlider
import com.personal.sleepalarm.ui.components.SectionCard
import com.personal.sleepalarm.ui.components.SwitchSetting
import com.personal.sleepalarm.ui.components.TimeStepper
import com.personal.sleepalarm.util.AppLanguageManager
import com.personal.sleepalarm.util.ProfileJsonCodec
import com.personal.sleepalarm.util.RingtonePickerHelper
import java.io.BufferedReader
import java.io.InputStreamReader
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
    modifier: Modifier = Modifier
) {
    var showThemes by remember { mutableStateOf(false) }

    var showSystemCheck by remember { mutableStateOf(false) }
    var expandedCategory by rememberSaveable {
        mutableStateOf<String?>(SettingsCategory.BASICS.name)
    }

    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }


    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val isPreviewPlaying by viewModel.isPreviewPlaying.collectAsStateWithLifecycle()

    val ringtoneDisplayName = remember(state.profile.alarmRingtoneUri) {
        viewModel.getRingtoneName(state.profile.alarmRingtoneUri)
    }
    val cueRingtoneDisplayName = remember(state.profile.cueRingtoneUri) {
        viewModel.getRingtoneName(state.profile.cueRingtoneUri)
    }

    var showHelp by remember { mutableStateOf(false) }

    // === Pickers ===

    val cuePickSystemLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = RingtonePickerHelper.parsePickedUri(result.data)
            viewModel.stopPreview()
            viewModel.setCueRingtoneUri(uri?.toString())
        }
    }

    val cuePickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val persisted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.isSuccess
            viewModel.stopPreview()
            if (persisted) viewModel.setCueRingtoneUri(uri.toString())
            else viewModel.reportSoundPermissionError()
        }
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val persisted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.isSuccess
            viewModel.stopPreview()
            if (persisted) viewModel.setAlarmRingtoneUri(uri.toString())
            else viewModel.reportSoundPermissionError()
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
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                viewModel.setAlarmRingtoneUri(uri.toString())
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

    if (showSystemCheck) {
        SystemCheckScreen(onBack = { showSystemCheck = false })
        return
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
            Text(
                text = stringResource(R.string.tab_settings),
                style = MaterialTheme.typography.headlineMedium
            )

            SettingsCategoryCard(
                category = SettingsCategory.BASICS,
                expandedCategory = expandedCategory,
                title = stringResource(R.string.settings_category_basics),
                summary = stringResource(R.string.settings_category_basics_summary),
                onToggle = { expandedCategory = toggleCategory(expandedCategory, it) }
            ) {
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

            SettingsCategoryCard(
                category = SettingsCategory.CUES,
                expandedCategory = expandedCategory,
                title = stringResource(R.string.settings_category_cues),
                summary = stringResource(R.string.settings_category_cues_summary),
                onToggle = { expandedCategory = toggleCategory(expandedCategory, it) }
            ) {
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
                        else viewModel.previewRingtone(state.profile.cueRingtoneUri)
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

            SettingsCategoryCard(
                category = SettingsCategory.ALARM,
                expandedCategory = expandedCategory,
                title = stringResource(R.string.settings_category_alarm),
                summary = stringResource(R.string.settings_category_alarm_summary),
                onToggle = { expandedCategory = toggleCategory(expandedCategory, it) }
            ) {
                AlarmSection(
                    mathDifficulty = state.profile.mathDifficulty,
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
                        else viewModel.previewRingtone(state.profile.alarmRingtoneUri)
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

            SettingsCategoryCard(
                category = SettingsCategory.AUTOMATION,
                expandedCategory = expandedCategory,
                title = stringResource(R.string.settings_category_automation),
                summary = stringResource(R.string.settings_category_automation_summary),
                onToggle = { expandedCategory = toggleCategory(expandedCategory, it) }
            ) {
                AutoDetectSection(
                    autoDetect = state.profile.autoDetectOnsetEnabled,
                    autoCorrect = state.profile.autoCorrectWakeEnabled,
                    onAutoDetectChange = { viewModel.setAutoDetectOnset(it) },
                    onAutoCorrectChange = { viewModel.setAutoCorrectWake(it) }
                )
            }

            SettingsCategoryCard(
                category = SettingsCategory.APPEARANCE,
                expandedCategory = expandedCategory,
                title = stringResource(R.string.settings_category_appearance),
                summary = stringResource(R.string.settings_category_appearance_summary),
                onToggle = { expandedCategory = toggleCategory(expandedCategory, it) }
            ) {
                LanguageSection()
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showThemes = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.action_open_themes))
                }
            }

            SettingsCategoryCard(
                category = SettingsCategory.DATA,
                expandedCategory = expandedCategory,
                title = stringResource(R.string.settings_category_data),
                summary = stringResource(R.string.settings_category_data_summary),
                onToggle = { expandedCategory = toggleCategory(expandedCategory, it) }
            ) {
                OutlinedButton(
                    onClick = { exportJsonLauncher.launch("sleep_alarm_profile.json") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(text = stringResource(R.string.action_export_settings)) }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { importJsonLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(text = stringResource(R.string.action_import_settings)) }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { exportAllLauncher.launch("sleep_alarm_full_backup.json") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(text = stringResource(R.string.action_export_all_data)) }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { importAllLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(text = stringResource(R.string.action_import_all_data)) }
            }

            SettingsCategoryCard(
                category = SettingsCategory.RELIABILITY,
                expandedCategory = expandedCategory,
                title = stringResource(R.string.settings_category_reliability),
                summary = stringResource(R.string.settings_category_reliability_summary),
                onToggle = { expandedCategory = toggleCategory(expandedCategory, it) }
            ) {
                OutlinedButton(
                    onClick = { showSystemCheck = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(text = stringResource(R.string.action_system_check)) }
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

private enum class SettingsCategory {
    BASICS,
    CUES,
    ALARM,
    AUTOMATION,
    APPEARANCE,
    DATA,
    RELIABILITY
}

private fun toggleCategory(current: String?, category: SettingsCategory): String? {
    return if (current == category.name) null else category.name
}

@Composable
private fun SettingsCategoryCard(
    category: SettingsCategory,
    expandedCategory: String?,
    title: String,
    summary: String,
    onToggle: (SettingsCategory) -> Unit,
    content: @Composable () -> Unit
) {
    val expanded = expandedCategory == category.name

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        tonalElevation = if (expanded) 3.dp else 1.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(category) }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.settings_collapse else R.string.settings_expand
                    )
                )
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                Column(modifier = Modifier.padding(12.dp)) {
                    content()
                }
            }
        }
    }
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
    SectionCard(title = stringResource(R.string.section_time)) {
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
    SectionCard(title = stringResource(R.string.section_cycles)) {
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
    SectionCard(title = stringResource(R.string.section_lucid_cues)) {
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
                color = MaterialTheme.colorScheme.error
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
            color = MaterialTheme.colorScheme.primary
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
    SectionCard(title = stringResource(R.string.section_alarm)) {
        ChoiceChips(
            label = stringResource(R.string.setting_math_difficulty),
            options = listOf(MathDifficulty.EASY, MathDifficulty.MEDIUM, MathDifficulty.HARD),
            selected = mathDifficulty,
            optionText = { value ->
                when (value) {
                    MathDifficulty.EASY -> stringResource(R.string.math_difficulty_easy)
                    MathDifficulty.MEDIUM -> stringResource(R.string.math_difficulty_medium)
                    MathDifficulty.HARD -> stringResource(R.string.math_difficulty_hard)
                }
            },
            onSelect = onMathDifficultyChange
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

        if (ringtoneUri != null) {
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
    autoDetect: Boolean,
    autoCorrect: Boolean,
    onAutoDetectChange: (Boolean) -> Unit,
    onAutoCorrectChange: (Boolean) -> Unit
) {
    SectionCard(title = stringResource(R.string.section_auto_detect)) {
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
    SectionCard(title = stringResource(R.string.section_data)) {
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
    SectionCard(title = stringResource(R.string.section_disclaimer)) {
        Text(
            text = stringResource(R.string.disclaimer_text),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
