package com.personal.sleepalarm.ui.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.RepeatMode
import com.personal.sleepalarm.domain.calculator.ReminderTimeCalculator
import com.personal.sleepalarm.ui.components.TimeStepper
import com.personal.sleepalarm.ui.components.CatText

@Composable
fun ReminderEditScreen(
    editReminderId: Int?,
    linkedTaskId: Int?,
    onBack: () -> Unit,
    viewModel: ReminderEditViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(editReminderId, linkedTaskId) {
        viewModel.init(editReminderId, linkedTaskId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // === Заголовок с котом ===
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                text = stringResource(
                    if (editReminderId == null) R.string.reminder_edit_new
                    else R.string.reminder_edit_title
                ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            CatText(
                text = "=^..^=",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === Секция 1: Основная информация ===
        Text(
            text = stringResource(R.string.reminder_edit_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(12.dp)
        ) {
            Column {
                TextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.reminder_field_title)) },
                    placeholder = { Text(stringResource(R.string.reminder_title_placeholder)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                TimeStepper(
                    label = stringResource(R.string.reminder_field_time),
                    hour = state.timeHour,
                    minute = state.timeMinute,
                    onHourChange = viewModel::setTimeHour,
                    onMinuteChange = viewModel::setTimeMinute
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === Секция 2: Повторение ===
        Text(
            text = stringResource(R.string.reminder_field_repeat),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(12.dp)
        ) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RepeatMode.values().forEach { mode ->
                        FilterChip(
                            selected = state.repeatMode == mode,
                            onClick = { viewModel.setRepeatMode(mode) },
                            label = { Text(repeatShort(mode)) }
                        )
                    }
                }

                // === WEEKLY: дни недели (короткие, помещаются) ===
                if (state.repeatMode == RepeatMode.WEEKLY) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.reminder_field_days),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        dayLabels().forEachIndexed { index, label ->
                            val dayValue = index + 1
                            FilterChip(
                                selected = ReminderTimeCalculator.isDaySelected(state.daysOfWeek, dayValue),
                                onClick = { viewModel.toggleDay(dayValue) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                // === INTERVAL: раз в N ===
                if (state.repeatMode == RepeatMode.INTERVAL) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.reminder_field_interval, state.intervalDays),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.setIntervalDays(state.intervalDays - 1) }) {
                            Text("−", style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.secondary)
                        }
                        IconButton(onClick = { viewModel.setIntervalDays(state.intervalDays + 1) }) {
                            Text("+", style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // === Кнопка сохранения ===
        Button(
            onClick = {
                if (viewModel.save()) onBack()
            },
            enabled = state.title.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Text(
                text = stringResource(R.string.reminder_save),
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun repeatShort(mode: RepeatMode): String = when (mode) {
    RepeatMode.ONCE -> stringResource(R.string.repeat_once)
    RepeatMode.DAILY -> stringResource(R.string.repeat_daily)
    RepeatMode.WEEKLY -> stringResource(R.string.repeat_weekly)
    RepeatMode.INTERVAL -> stringResource(R.string.repeat_interval)
}

@Composable
private fun dayLabels(): List<String> = listOf(
    stringResource(R.string.day_mon),
    stringResource(R.string.day_tue),
    stringResource(R.string.day_wed),
    stringResource(R.string.day_thu),
    stringResource(R.string.day_fri),
    stringResource(R.string.day_sat),
    stringResource(R.string.day_sun)
)
