package com.personal.sleepalarm.ui.alarm

import com.personal.sleepalarm.ui.theme.ThemedAlertDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.sleepalarm.R
import com.personal.sleepalarm.ui.mood.MoodPickerDialog
import com.personal.sleepalarm.util.TimeFormatter
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Compose UI экрана будильника.
 *
 * ДОБАВЛЕНО (v5): после успешного dismiss показывается диалог настроения,
 * затем (если включён) индикатор озвучки брифинга, затем finish().
 *
 * Существующая структура (время, задача, dismiss, snooze, BackHandler)
 * НЕ изменена.
 */
@Composable
fun AlarmScreen(
    viewModel: AlarmViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // === ДОБАВЛЕНО (v5): состояния потока пробуждения ===
    val showMoodPicker by viewModel.showMoodPicker.collectAsStateWithLifecycle()
    val isBriefingPlaying by viewModel.isBriefingPlaying.collectAsStateWithLifecycle()

    // Запрещаем случайное закрытие экрана будильника кнопкой Back.
    BackHandler(enabled = true) {
        // Ничего не делаем.
    }

    var currentTime by remember {
        mutableStateOf(ZonedDateTime.now())
    }

    // Секундный тикер: обновляет часы И служит основой для countdown повторов.
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = ZonedDateTime.now()
            delay(1_000L)
        }
    }

    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm:ss")
    }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = timeFormatter.format(currentTime),
                style = MaterialTheme.typography.displayLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.alarm_subtitle_between_cycles),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            state.session?.let { session ->
                Text(
                    text = stringResource(
                        R.string.alarm_session_info,
                        session.cyclesPlanned,
                        session.cycleLengthMinutes,
                        TimeFormatter.formatEpochMillis(
                            session.estimatedWakeTime,
                            session.zoneId
                        )
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // ДОБАВЛЕНО (F10): статус умных повторов.
            if (state.smartRepeatEnabled) {
                SmartRepeatStatus(
                    repeatCount = state.repeatCount,
                    maxCount = state.smartRepeatMaxCount,
                    nextRepeatAtMillis = state.nextRepeatAtMillis,
                    repeatsExhausted = state.repeatsExhausted,
                    nowMillis = currentTime.toInstant().toEpochMilli()
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            val challenge = state.challenge

            if (challenge == null) {
                Text(
                    text = stringResource(R.string.alarm_loading),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Text(
                    text = stringResource(R.string.alarm_solve_to_disable),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = challenge.question,
                    style = MaterialTheme.typography.displaySmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = state.userInput,
                    onValueChange = { viewModel.onInputChanged(it) },
                    label = {
                        Text(text = stringResource(R.string.alarm_answer_label))
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true,
                    enabled = !state.isAnswerCorrect,
                    modifier = Modifier.fillMaxWidth()
                )

                state.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (state.showHint) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(
                            R.string.alarm_hint_answer,
                            challenge.answer
                        ),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!state.isAnswerCorrect) {
                    Button(
                        onClick = { viewModel.checkAnswer() },
                        enabled = state.userInput.isNotBlank() && !state.isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(text = stringResource(R.string.alarm_action_check))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.alarm_answer_correct),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.dismissAlarm() },
                    enabled = state.isAnswerCorrect && !state.isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = stringResource(R.string.alarm_action_dismiss),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { viewModel.showSnoozeConfirmation() },
                enabled = !state.isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.alarm_action_snooze,
                        TimeFormatter.formatMinutes(state.cycleLengthMinutes.toLong())
                    )
                )
            }
        }
    }

    // === Существующий: подтверждение snooze ===
    if (state.snoozeConfirmVisible) {
        ThemedAlertDialog(
            onDismissRequest = { viewModel.hideSnoozeConfirmation() },
            title = {
                Text(text = stringResource(R.string.alarm_snooze_title))
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.alarm_snooze_confirm,
                        TimeFormatter.formatMinutes(state.cycleLengthMinutes.toLong())
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmSnooze() }) {
                    Text(text = stringResource(R.string.alarm_snooze_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideSnoozeConfirmation() }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // === ДОБАВЛЕНО (v5): индикатор озвучки брифинга ===
    if (isBriefingPlaying) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.briefing_playing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }

    // === ДОБАВЛЕНО (v5): диалог настроения ===
    if (showMoodPicker) {
        MoodPickerDialog(onSelect = { mood -> viewModel.onMoodSelected(mood) })
    }
}

// =====================================================================
// ДОБАВЛЕНО (F10): блок статуса умных повторов
// =====================================================================

/**
 * Показывает счётчик повторов и обратный отсчёт до следующего импульса.
 */
@Composable
private fun SmartRepeatStatus(
    repeatCount: Int,
    maxCount: Int,
    nextRepeatAtMillis: Long?,
    repeatsExhausted: Boolean,
    nowMillis: Long
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.alarm_repeat_counter, repeatCount, maxCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val statusText = when {
            repeatsExhausted -> stringResource(R.string.alarm_repeat_exhausted)

            nextRepeatAtMillis != null -> {
                val secondsLeft = ((nextRepeatAtMillis - nowMillis) / 1000L)
                    .coerceAtLeast(0)
                stringResource(
                    R.string.alarm_repeat_next,
                    formatCountdown(secondsLeft)
                )
            }

            else -> null
        }

        if (statusText != null) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

/**
 * Форматирует секунды как M:SS для обратного отсчёта.
 */
private fun formatCountdown(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}