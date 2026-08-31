package com.personal.sleepalarm.ui.assistant

import com.personal.sleepalarm.ui.theme.appAccents
import com.personal.sleepalarm.ui.theme.AppAccentTone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.domain.assistant.DailyPlanCommand
import com.personal.sleepalarm.ui.activity.ManualActivitySheet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Экран ассистента: карточки предсказаний + чат с rule-based ответами.
 */
@Composable
fun AssistantScreen(
    onBack: () -> Unit,
    onStartTaskFocus: (Int) -> Unit = {},
    viewModel: AssistantViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val activityGap by viewModel.activityGap.collectAsStateWithLifecycle()
    val proposedAction by viewModel.proposedAction.collectAsStateWithLifecycle()
    val pendingDailyPlanChange by viewModel.pendingDailyPlanChange.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var showActivityForm by remember { mutableStateOf(false) }
    var speechStatusRes by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Заголовок.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                text = stringResource(R.string.assistant_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.appAccents.info.color
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // === Карточки предсказаний ===
        InsightsRow(insights = insights)

        activityGap?.let { gap ->
            Spacer(modifier = Modifier.height(10.dp))
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val zone = ZoneId.systemDefault()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.appAccents.energy.container)
                    .padding(12.dp)
            ) {
                Text(
                    "С ${Instant.ofEpochMilli(gap.startMillis).atZone(zone).format(formatter)} до " +
                        "${Instant.ofEpochMilli(gap.endMillis).atZone(zone).format(formatter)} нет активности. " +
                        "Вы работали без таймера?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appAccents.energy.onContainer
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.dismissActivityGap() }) { Text("Нет") }
                    TextButton(onClick = { showActivityForm = true }) { Text("Добавить время") }
                    TextButton(onClick = viewModel::snoozeActivityGap) { Text("Позже") }
                }
            }
        }

        proposedAction?.let { action ->
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.appAccents.focus.container).padding(12.dp)
            ) {
                Text(
                    "Начать «${action.title}» · ${action.focusMinutes} мин?",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.appAccents.focus.onContainer
                )
                Text(
                    "Запустится выбранная задача без повторной настройки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appAccents.focus.onContainer.copy(alpha = 0.78f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::dismissProposedAction) { Text("Отмена") }
                    TextButton(onClick = {
                        onStartTaskFocus(action.taskId)
                        viewModel.dismissProposedAction()
                    }) { Text("Подтвердить") }
                }
            }
        }

        pendingDailyPlanChange?.let { pending ->
            Spacer(modifier = Modifier.height(10.dp))
            DailyPlanConfirmationCard(
                pending = pending,
                onCancel = viewModel::cancelDailyPlanChange,
                onConfirm = viewModel::confirmDailyPlanChange
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === Чат ===
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.assistant_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appAccents.info.color
                    )
                }
            }
            items(messages) { msg ->
                ChatBubble(fromUser = msg.fromUser, text = msg.text)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Быстрые подсказки.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickChip(
                text = stringResource(R.string.assistant_quick_sleep),
                tone = MaterialTheme.appAccents.sleep
            ) {
                viewModel.ask(it)
            }
            QuickChip(
                text = stringResource(R.string.assistant_quick_morning),
                tone = MaterialTheme.appAccents.energy
            ) {
                viewModel.ask(it)
            }
            QuickChip(
                text = stringResource(R.string.assistant_quick_tasks),
                tone = MaterialTheme.appAccents.schedule
            ) {
                viewModel.ask(it)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Поле ввода.
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.assistant_placeholder)) },
                singleLine = true
            )
            OfflineSpeechCommandButton(
                onBeforeListen = viewModel::stopVoiceForListening,
                onRecognized = { recognized ->
                    input = recognized
                    speechStatusRes = null
                },
                onStatus = { speechStatusRes = it }
            )
            IconButton(
                onClick = {
                    viewModel.ask(input)
                    input = ""
                },
                enabled = input.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = stringResource(R.string.assistant_send))
            }
        }
        speechStatusRes?.let { status ->
            Text(
                text = stringResource(status),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 2.dp)
            )
        }
    }

    if (showActivityForm) {
        ManualActivitySheet(
            initialStartMillis = activityGap?.startMillis,
            onDismiss = {
                showActivityForm = false
                viewModel.dismissActivityGap()
            }
        )
    }
}

@Composable
private fun DailyPlanConfirmationCard(
    pending: PendingDailyPlanChange,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val body = when (pending) {
        is PendingDailyPlanChange.TaskChange -> when (pending.field) {
            AssistantTaskPlanField.DAILY_TARGET -> stringResource(
                R.string.daily_plan_change_target,
                pending.taskTitle,
                pending.oldIntValue ?: 0,
                pending.newIntValue ?: 0
            )
            AssistantTaskPlanField.BOUT_DURATION -> stringResource(
                R.string.daily_plan_change_bout,
                pending.taskTitle,
                pending.oldIntValue ?: 0,
                pending.newIntValue ?: 0
            )
            AssistantTaskPlanField.DAILY_REQUIRED -> stringResource(
                if (pending.newBooleanValue == true) {
                    R.string.daily_plan_change_required_on
                } else {
                    R.string.daily_plan_change_required_off
                },
                pending.taskTitle
            )
        }
        is PendingDailyPlanChange.GlobalChange -> stringResource(
            R.string.daily_plan_change_global,
            stringResource(pending.command.settingLabelResource()),
            pending.oldValue,
            pending.newValue
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.appAccents.schedule.container)
            .padding(14.dp)
    ) {
        Text(
            text = stringResource(R.string.daily_plan_confirmation_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.appAccents.schedule.onContainer
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.appAccents.schedule.onContainer
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.daily_plan_cancel))
            }
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.daily_plan_confirm))
            }
        }
    }
}

private fun DailyPlanCommand.settingLabelResource(): Int = when (this) {
    is DailyPlanCommand.SetUrgencyEnabled -> R.string.daily_plan_setting_urgency
    is DailyPlanCommand.SetUrgencyBuffer -> R.string.daily_plan_setting_buffer
    is DailyPlanCommand.SetRepeatEnabled -> R.string.daily_plan_setting_repeats
    is DailyPlanCommand.SetRepeatInterval -> R.string.daily_plan_setting_repeat_interval
    is DailyPlanCommand.SetMorningReminderEnabled -> R.string.daily_plan_setting_morning_reminder
    is DailyPlanCommand.SetCutoffMinutesOfDay -> R.string.daily_plan_setting_cutoff
    is DailyPlanCommand.SetDailyPlanSignalVolume -> R.string.daily_plan_setting_signal_volume
    is DailyPlanCommand.SetDailyPlanSignalMode -> R.string.daily_plan_setting_signal_sound
    else -> R.string.daily_plan_confirmation_title
}

@Composable
private fun OfflineSpeechCommandButton(
    onBeforeListen: () -> Unit,
    onRecognized: (String) -> Unit,
    onStatus: (Int?) -> Unit
) {
    val context = LocalContext.current
    val supported = remember(context) { isStrictOfflineSpeechAvailable(context) }
    val recognizer = remember(context, supported) {
        if (supported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()
        } else {
            null
        }
    }
    val intent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onStatus(R.string.daily_plan_speech_listening)
            }

            override fun onResults(results: Bundle?) {
                val result = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (result.isNullOrEmpty()) {
                    onStatus(R.string.daily_plan_speech_error)
                } else {
                    onRecognized(result)
                }
            }

            override fun onError(error: Int) {
                onStatus(R.string.daily_plan_speech_error)
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        onDispose {
            recognizer?.cancel()
            recognizer?.destroy()
        }
    }

    fun startRecognition() {
        if (recognizer == null) {
            onStatus(R.string.daily_plan_speech_unavailable)
            return
        }
        // Avoid feeding MIRL's own TTS response back into the recognizer.
        onBeforeListen()
        onStatus(R.string.daily_plan_speech_listening)
        runCatching { recognizer.startListening(intent) }
            .onFailure { onStatus(R.string.daily_plan_speech_error) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecognition()
        else onStatus(R.string.daily_plan_speech_permission_denied)
    }

    IconButton(
        onClick = {
            if (!supported) {
                onStatus(R.string.daily_plan_speech_unavailable)
            } else if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                startRecognition()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    ) {
        Icon(
            Icons.Default.Mic,
            contentDescription = stringResource(R.string.daily_plan_speech_content_description),
            tint = if (supported) {
                MaterialTheme.appAccents.calm.color
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            }
        )
    }
}

internal fun isStrictOfflineSpeechAvailable(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

// =====================================================================
// Карточки предсказаний
// =====================================================================

@Composable
private fun InsightsRow(insights: AssistantInsights) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!insights.isTrained) {
            Text(
                text = stringResource(R.string.assistant_not_trained),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.appAccents.info.container)
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.assistant_insight_morning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appAccents.info.onContainer
                    )
                    Text(
                        text = if (insights.isHeavyMorning) {
                            stringResource(R.string.assistant_heavy)
                        } else {
                            stringResource(R.string.assistant_good)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.appAccents.info.onContainer
                    )
                    insights.predictedMood?.let {
                        Text(
                            text = stringResource(R.string.assistant_predicted, "%.1f".format(it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appAccents.info.onContainer
                        )
                    }
                }
                insights.snoozeLimit?.let { limit ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.assistant_insight_snooze),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appAccents.info.onContainer
                        )
                        Text(
                            text = limit.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.appAccents.info.onContainer
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.assistant_trained_on, insights.trainedOn),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =====================================================================
// Пузырь чата
// =====================================================================

@Composable
private fun ChatBubble(fromUser: Boolean, text: String) {
    val bg = if (fromUser) {
        MaterialTheme.appAccents.focus.container
    } else {
        MaterialTheme.appAccents.info.container
    }
    val fg = if (fromUser) {
        MaterialTheme.appAccents.focus.onContainer
    } else {
        MaterialTheme.appAccents.info.onContainer
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

// =====================================================================
// Быстрый чип
// =====================================================================

@Composable
private fun QuickChip(text: String, tone: AppAccentTone, onAsk: (String) -> Unit) {
    androidx.compose.material3.AssistChip(
        onClick = { onAsk(text) },
        label = { Text(text, style = MaterialTheme.typography.bodySmall) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = tone.action,
            labelColor = tone.onAction
        )
    )
}
