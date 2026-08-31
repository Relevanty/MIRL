package com.personal.sleepalarm.ui.reminders

import com.personal.sleepalarm.ui.theme.appAccents

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.domain.calculator.ReminderTimeCalculator
import com.personal.sleepalarm.ui.components.CatText

@Composable
fun ReminderEditScreen(
    editReminderId: Int?,
    linkedTaskId: Int?,
    onBack: () -> Unit,
    viewModel: ReminderEditViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val requiresTask = state.triggerRule in setOf(
        "BEFORE_DEADLINE", "NO_PROGRESS", "BECOMES_URGENT", "BEFORE_FOCUS"
    )
    var taskMenuExpanded by remember { mutableStateOf(false) }
    val scheduleTone = MaterialTheme.appAccents.schedule
    val reminderChipColors = FilterChipDefaults.filterChipColors(
        containerColor = scheduleTone.action.copy(alpha = 0.62f),
        labelColor = scheduleTone.onAction,
        selectedContainerColor = scheduleTone.color,
        selectedLabelColor = scheduleTone.onColor
    )
    val reminderFieldColors = TextFieldDefaults.colors(
        unfocusedContainerColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        focusedTextColor = scheduleTone.onContainer,
        unfocusedTextColor = scheduleTone.onContainer,
        cursorColor = scheduleTone.color,
        focusedIndicatorColor = scheduleTone.color,
        unfocusedIndicatorColor = scheduleTone.onContainer.copy(alpha = 0.34f),
        focusedLabelColor = scheduleTone.color,
        unfocusedLabelColor = scheduleTone.onContainer.copy(alpha = 0.78f),
        focusedPlaceholderColor = scheduleTone.onContainer.copy(alpha = 0.58f),
        unfocusedPlaceholderColor = scheduleTone.onContainer.copy(alpha = 0.58f)
    )

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
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = scheduleTone.color
                )
            }
            Text(
                text = stringResource(
                    if (editReminderId == null) R.string.reminder_edit_new
                    else R.string.reminder_edit_title
                ),
                style = MaterialTheme.typography.titleLarge,
                color = scheduleTone.color,
                modifier = Modifier.weight(1f)
            )
            CatText(
                text = "=^..^=",
                color = scheduleTone.color,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Связь и условие",
            style = MaterialTheme.typography.titleSmall,
            color = scheduleTone.color,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheduleTone.container.copy(alpha = 0.4f))
                .padding(12.dp)
        ) {
            Box {
                val selectedTask = tasks.firstOrNull { it.id == state.linkedTaskId }
                OutlinedButton(
                    onClick = { taskMenuExpanded = true },
                    border = BorderStroke(1.dp, scheduleTone.color),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = scheduleTone.onContainer)
                ) {
                    Text(
                        selectedTask?.primaryLabel() ?: "Связать с задачей",
                        color = scheduleTone.onContainer
                    )
                }
                DropdownMenu(
                    expanded = taskMenuExpanded,
                    onDismissRequest = { taskMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Без задачи", color = scheduleTone.color) },
                        onClick = {
                            viewModel.setLinkedTask(null)
                            taskMenuExpanded = false
                        }
                    )
                    tasks.forEach { task ->
                        DropdownMenuItem(
                            text = { Text(task.primaryLabel(), color = scheduleTone.color) },
                            onClick = {
                                viewModel.setLinkedTask(task.id)
                                taskMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "AT_TIME" to "В указанное время",
                    "BEFORE_DEADLINE" to "До дедлайна",
                    "NO_PROGRESS" to "Нет прогресса",
                    "BECOMES_URGENT" to "Скоро срочная",
                    "BEFORE_FOCUS" to "Перед фокусом",
                    "BEFORE_SLEEP" to "Перед сном"
                ).forEach { (rule, label) ->
                    FilterChip(
                        selected = state.triggerRule == rule,
                        onClick = { viewModel.setTriggerRule(rule) },
                        label = { Text(label) },
                        colors = reminderChipColors
                    )
                }
            }
            if (requiresTask && state.linkedTaskId == null) {
                ReminderWarningText("Для этого условия выберите задачу")
            }
            if (state.triggerRule in setOf("BEFORE_DEADLINE", "BECOMES_URGENT") &&
                tasks.firstOrNull { it.id == state.linkedTaskId }?.dueAtMillis == null
            ) {
                ReminderWarningText("У выбранной задачи должен быть дедлайн")
            }
            if (state.triggerRule == "BEFORE_FOCUS" &&
                tasks.firstOrNull { it.id == state.linkedTaskId }?.startAtMillis == null
            ) {
                ReminderWarningText("У задачи должно быть запланированное время начала")
            }
            if (state.triggerRule in setOf("BEFORE_DEADLINE", "BECOMES_URGENT", "BEFORE_FOCUS")) {
                StepperRow(
                    label = "За ${state.offsetMinutes} мин",
                    onMinus = { viewModel.setOffsetMinutes(state.offsetMinutes - 15) },
                    onPlus = { viewModel.setOffsetMinutes(state.offsetMinutes + 15) }
                )
            }
            if (state.triggerRule == "NO_PROGRESS") {
                StepperRow(
                    label = "После ${state.inactivityHours} ч без работы",
                    onMinus = { viewModel.setInactivityHours(state.inactivityHours - 1) },
                    onPlus = { viewModel.setInactivityHours(state.inactivityHours + 1) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === Секция 1: Основная информация ===
        Text(
            text = stringResource(R.string.reminder_edit_title),
            style = MaterialTheme.typography.titleSmall,
            color = scheduleTone.color,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheduleTone.container.copy(alpha = 0.4f))
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
                    colors = reminderFieldColors
                )

                if (state.triggerRule == "AT_TIME" || state.triggerRule == "BEFORE_SLEEP") {
                    Spacer(modifier = Modifier.height(12.dp))
                    ReminderTimeStepper(
                        label = stringResource(R.string.reminder_field_time),
                        hour = state.timeHour,
                        minute = state.timeMinute,
                        onHourChange = viewModel::setTimeHour,
                        onMinuteChange = viewModel::setTimeMinute
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === Секция 2: Повторение ===
        Text(
            text = stringResource(R.string.reminder_field_repeat),
            style = MaterialTheme.typography.titleSmall,
            color = scheduleTone.color,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheduleTone.container.copy(alpha = 0.4f))
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RepeatMode.values().forEach { mode ->
                        FilterChip(
                            selected = state.repeatMode == mode,
                            onClick = { viewModel.setRepeatMode(mode) },
                            label = { Text(repeatShort(mode)) },
                            colors = reminderChipColors
                        )
                    }
                }

                // === WEEKLY: дни недели (короткие, помещаются) ===
                if (state.repeatMode == RepeatMode.WEEKLY) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.reminder_field_days),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheduleTone.onContainer.copy(alpha = 0.78f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        dayLabels().forEachIndexed { index, label ->
                            val dayValue = index + 1
                            FilterChip(
                                selected = ReminderTimeCalculator.isDaySelected(state.daysOfWeek, dayValue),
                                onClick = { viewModel.toggleDay(dayValue) },
                                label = { Text(label) },
                                colors = reminderChipColors
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
                            color = scheduleTone.onContainer
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.setIntervalDays(state.intervalDays - 1) }) {
                            Text("−", style = MaterialTheme.typography.titleLarge,
                                color = scheduleTone.onContainer)
                        }
                        IconButton(onClick = { viewModel.setIntervalDays(state.intervalDays + 1) }) {
                            Text("+", style = MaterialTheme.typography.titleLarge,
                                color = scheduleTone.onContainer)
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
            enabled = state.title.isNotBlank() &&
                (!requiresTask || state.linkedTaskId != null) &&
                (state.triggerRule !in setOf("BEFORE_DEADLINE", "BECOMES_URGENT") ||
                    tasks.firstOrNull { it.id == state.linkedTaskId }?.dueAtMillis != null) &&
                (state.triggerRule != "BEFORE_FOCUS" ||
                    tasks.firstOrNull { it.id == state.linkedTaskId }?.startAtMillis != null),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = scheduleTone.color,
                contentColor = scheduleTone.onColor
            )
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
private fun ReminderWarningText(text: String) {
    val warningTone = MaterialTheme.appAccents.warning
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(10.dp),
        color = warningTone.action,
        contentColor = warningTone.onAction
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            color = warningTone.onAction,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ReminderTimeStepper(
    label: String,
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    val tone = MaterialTheme.appAccents.schedule
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tone.onContainer
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReminderTimeValue(
                value = "%02d".format(hour),
                unit = stringResource(R.string.time_unit_hours),
                onMinus = { onHourChange((hour - 1 + 24) % 24) },
                onPlus = { onHourChange((hour + 1) % 24) }
            )
            ReminderTimeValue(
                value = "%02d".format(minute),
                unit = stringResource(R.string.time_unit_minutes),
                onMinus = { onMinuteChange((minute - 5 + 60) % 60) },
                onPlus = { onMinuteChange((minute + 5) % 60) }
            )
        }
    }
}

@Composable
private fun ReminderTimeValue(
    value: String,
    unit: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    val tone = MaterialTheme.appAccents.schedule
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMinus) {
            Icon(Icons.Default.Remove, contentDescription = null, tint = tone.onContainer)
        }
        Text(value, style = MaterialTheme.typography.headlineSmall, color = tone.onContainer)
        IconButton(onClick = onPlus) {
            Icon(Icons.Default.Add, contentDescription = null, tint = tone.onContainer)
        }
        Text(unit, style = MaterialTheme.typography.bodyMedium, color = tone.onContainer)
    }
}

@Composable
private fun StepperRow(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val tone = MaterialTheme.appAccents.schedule
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = tone.onContainer
        )
        IconButton(onClick = onMinus) {
            Text("−", style = MaterialTheme.typography.titleLarge, color = tone.onContainer)
        }
        IconButton(onClick = onPlus) {
            Text("+", style = MaterialTheme.typography.titleLarge, color = tone.onContainer)
        }
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
