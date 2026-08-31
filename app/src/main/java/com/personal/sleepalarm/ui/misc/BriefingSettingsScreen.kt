package com.personal.sleepalarm.ui.misc

import com.personal.sleepalarm.ui.theme.appAccents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R

/**
 * Настройки голосового брифинга.
 *
 * Полный редактор системного офлайн-TTS Android. Те же элементы встраиваются
 * в раздел «Звуки и голос», чтобы настройки не расходились между экранами.
 */
@Composable
fun BriefingSettingsScreen(
    onBack: () -> Unit,
    onOpenDailyPlanAssistant: () -> Unit = {},
    viewModel: BriefingSettingsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                text = stringResource(R.string.briefing_settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.appAccents.calm.color
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        BriefingSettingsContent(
            viewModel = viewModel,
            onOpenDailyPlanAssistant = onOpenDailyPlanAssistant,
            showDailyPlanAssistantLink = true
        )
    }
}

/**
 * Reusable briefing controls. The global Settings hub embeds this content so
 * there is only one implementation of the voice editor and one source of truth.
 */
@Composable
fun BriefingSettingsContent(
    viewModel: BriefingSettingsViewModel,
    onOpenDailyPlanAssistant: () -> Unit = {},
    showDailyPlanAssistantLink: Boolean = false
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val voiceSettings by viewModel.voiceSettings.collectAsStateWithLifecycle()
    val offlineVoices by viewModel.offlineVoices.collectAsStateWithLifecycle()
    var languageMenu by remember { mutableStateOf(false) }
    var voiceMenu by remember { mutableStateOf(false) }

    // === Включение ===
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.briefing_enable),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.appAccents.calm.color,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = enabled,
            onCheckedChange = viewModel::setEnabled,
            colors = calmSwitchColors()
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = stringResource(R.string.briefing_system_tts_info),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.appAccents.calm.color.copy(alpha = 0.78f)
    )

    if (!enabled) return

    Spacer(modifier = Modifier.height(20.dp))

    Text(stringResource(R.string.briefing_offline_voice), style = MaterialTheme.typography.titleMedium)
    val languages = offlineVoices.distinctBy { it.languageTag }
    val selectedLanguage = languages.firstOrNull { it.languageTag == voiceSettings.languageTag }
        ?: languages.firstOrNull {
            it.languageTag.substringBefore('-') == voiceSettings.languageTag.substringBefore('-')
        }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { languageMenu = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                selectedLanguage?.let { "${it.languageLabel} · ${it.languageTag}" }
                    ?: stringResource(R.string.briefing_choose_language)
            )
        }
        DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
            languages.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${option.languageLabel} · ${option.languageTag}") },
                    onClick = {
                        viewModel.setLanguage(option.languageTag)
                        languageMenu = false
                    }
                )
            }
        }
    }
    val voicesForLanguage = offlineVoices.filter {
        it.languageTag == (selectedLanguage?.languageTag ?: voiceSettings.languageTag)
    }
    val selectedVoice = voicesForLanguage.firstOrNull { it.name == voiceSettings.voiceName }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { voiceMenu = true },
            enabled = voicesForLanguage.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedVoice?.voiceLabel ?: stringResource(R.string.briefing_voice_automatic))
        }
        DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
            voicesForLanguage.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.voiceLabel) },
                    onClick = {
                        viewModel.setVoice(option.name, option.languageTag)
                        voiceMenu = false
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    VoiceSlider(
        label = stringResource(R.string.briefing_voice_volume),
        value = voiceSettings.volumePercent,
        range = 0..100,
        onChange = viewModel::setVolume
    )
    VoiceSlider(
        label = stringResource(R.string.briefing_voice_rate),
        value = voiceSettings.ratePercent,
        range = 50..150,
        onChange = viewModel::setRate
    )
    VoiceSlider(
        label = stringResource(R.string.briefing_voice_pitch),
        value = voiceSettings.pitchPercent,
        range = 50..150,
        onChange = viewModel::setPitch
    )
    VoiceSlider(
        label = stringResource(R.string.briefing_voice_brevity),
        value = voiceSettings.brevityPercent,
        range = 0..100,
        onChange = viewModel::setBrevity
    )

    Spacer(modifier = Modifier.height(12.dp))
    Text(stringResource(R.string.briefing_scenarios), style = MaterialTheme.typography.titleMedium)
    VoiceToggle(stringResource(R.string.briefing_scenario_morning), voiceSettings.morningEnabled, viewModel::setMorning)
    VoiceToggle(stringResource(R.string.briefing_scenario_focus), voiceSettings.focusEnabled, viewModel::setFocus)
    VoiceToggle(stringResource(R.string.briefing_scenario_reminders), voiceSettings.reminderEnabled, viewModel::setReminder)
    VoiceToggle(stringResource(R.string.briefing_scenario_assistant), voiceSettings.assistantEnabled, viewModel::setAssistant)
    VoiceToggle(stringResource(R.string.briefing_scenario_personal_data), voiceSettings.personalDataEnabled, viewModel::setPersonalData)
    VoiceToggle(stringResource(R.string.briefing_headphones_only), voiceSettings.headphonesOnly, viewModel::setHeadphonesOnly)

    if (showDailyPlanAssistantLink) {
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onOpenDailyPlanAssistant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.briefing_daily_plan_voice_action))
        }
        Text(
            text = stringResource(R.string.briefing_daily_plan_voice_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appAccents.calm.color.copy(alpha = 0.78f)
        )
    }

    OutlinedButton(onClick = viewModel::preview, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.briefing_preview_voice))
    }
    Text(
        stringResource(R.string.briefing_offline_voice_info),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.appAccents.calm.color.copy(alpha = 0.78f)
    )
}

@Composable
private fun VoiceToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = calmSwitchColors()
        )
    }
}

@Composable
private fun VoiceSlider(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text("$value%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.appAccents.calm.color)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / 5) - 1,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.appAccents.calm.color,
                activeTrackColor = MaterialTheme.appAccents.calm.color,
                inactiveTrackColor = MaterialTheme.appAccents.calm.action
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun calmSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.appAccents.calm.onColor,
    checkedTrackColor = MaterialTheme.appAccents.calm.color,
    uncheckedThumbColor = MaterialTheme.appAccents.calm.onAction,
    uncheckedTrackColor = MaterialTheme.appAccents.calm.action
)
