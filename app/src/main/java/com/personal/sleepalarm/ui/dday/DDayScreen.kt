package com.personal.sleepalarm.ui.dday

import com.personal.sleepalarm.ui.theme.appAccents
import com.personal.sleepalarm.ui.theme.AppAccentTone

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.ui.components.CatText

@Composable
fun DDayScreen(
    onBack: () -> Unit,
    viewModel: DDayViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedProjectId by remember { mutableStateOf<Int?>(null) }
    var selectedTaskId by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf(false) }
    val scheduleTone = MaterialTheme.appAccents.schedule
    val scheduleChipColors = FilterChipDefaults.filterChipColors(
        containerColor = scheduleTone.action.copy(alpha = 0.62f),
        labelColor = scheduleTone.onAction,
        selectedContainerColor = scheduleTone.color,
        selectedLabelColor = scheduleTone.onColor
    )
    val scheduleFieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        focusedTextColor = scheduleTone.onContainer,
        unfocusedTextColor = scheduleTone.onContainer,
        cursorColor = scheduleTone.color,
        focusedBorderColor = scheduleTone.color,
        unfocusedBorderColor = scheduleTone.onContainer.copy(alpha = 0.34f),
        focusedLabelColor = scheduleTone.color,
        unfocusedLabelColor = scheduleTone.onContainer.copy(alpha = 0.78f),
        focusedPlaceholderColor = scheduleTone.onContainer.copy(alpha = 0.58f),
        unfocusedPlaceholderColor = scheduleTone.onContainer.copy(alpha = 0.58f)
    )

    Column(
        modifier = modifier
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
                text = stringResource(R.string.dday_title),
                style = MaterialTheme.typography.headlineMedium,
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

        // === Секция 1: Добавление события ===
        Text(
            text = stringResource(R.string.dday_new_event),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.dday_field_title)) },
                    placeholder = { Text(stringResource(R.string.dday_title_placeholder)) },
                    singleLine = true,
                    colors = scheduleFieldColors
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = day,
                        onValueChange = { day = it.filter { c -> c.isDigit() }.take(2) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.dday_field_day)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = scheduleFieldColors
                    )
                    OutlinedTextField(
                        value = month,
                        onValueChange = { month = it.filter { c -> c.isDigit() }.take(2) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.dday_field_month)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = scheduleFieldColors
                    )
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it.filter { c -> c.isDigit() }.take(4) },
                        modifier = Modifier.weight(1.4f),
                        label = { Text(stringResource(R.string.dday_field_year)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = scheduleFieldColors
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Связать с планом",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheduleTone.onContainer.copy(alpha = 0.78f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedProjectId == null && selectedTaskId == null,
                        onClick = { selectedProjectId = null; selectedTaskId = null },
                        label = { Text("Без связи") },
                        colors = scheduleChipColors
                    )
                    state.projects.forEach { project ->
                        FilterChip(
                            selected = selectedProjectId == project.id,
                            onClick = { selectedProjectId = project.id; selectedTaskId = null },
                            label = { Text("Проект: ${project.title}", maxLines = 1) },
                            colors = scheduleChipColors
                        )
                    }
                    state.tasks.forEach { task ->
                        FilterChip(
                            selected = selectedTaskId == task.id,
                            onClick = { selectedTaskId = task.id; selectedProjectId = null },
                            label = { Text("Задача: ${task.primaryLabel()}", maxLines = 1) },
                            colors = scheduleChipColors
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Что должно быть готово к этой дате") },
                    minLines = 2,
                    colors = scheduleFieldColors
                )

                if (error) {
                    val warningTone = MaterialTheme.appAccents.warning
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = warningTone.action,
                        contentColor = warningTone.onAction
                    ) {
                        Text(
                            text = stringResource(R.string.dday_error_date),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = warningTone.onAction
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = {
                        val date = buildDate(day, month, year)
                        if (date == null) {
                            error = true
                        } else {
                            error = false
                            if (viewModel.addEvent(
                                    title = title,
                                    targetDate = date,
                                    projectId = selectedProjectId,
                                    taskId = selectedTaskId,
                                    notes = notes
                                )) {
                                title = ""; day = ""; month = ""; year = ""; notes = ""
                                selectedProjectId = null; selectedTaskId = null
                            }
                        }
                    },
                    enabled = title.isNotBlank(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = scheduleTone.onContainer,
                        disabledContentColor = scheduleTone.onContainer.copy(alpha = 0.38f)
                    )
                ) {
                    Text(stringResource(R.string.dday_add))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === Секция 2: Список событий ===
        Text(
            text = stringResource(R.string.dday_events_section),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = scheduleTone.color,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        if (state.events.isEmpty()) {
            // === Красивый пустой state с котом ===
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = " /\\_/\\\n( -.- ) zZ\n > ^ <",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 40.sp,
                        lineHeight = 46.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = scheduleTone.color
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.dday_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheduleTone.color.copy(alpha = 0.78f)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.events.forEach { event ->
                    DDayRow(
                        event = event,
                        days = viewModel.daysUntil(event),
                        plan = state.plans[event.id],
                        onDelete = { viewModel.deleteEvent(event.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DDayRow(
    event: DDayEntity,
    days: Int,
    plan: DDayPlanInfo?,
    onDelete: () -> Unit
) {
    val eventTone: AppAccentTone = when {
        days <= 0 -> MaterialTheme.appAccents.urgent
        days <= 30 -> MaterialTheme.appAccents.warning
        else -> MaterialTheme.appAccents.schedule
    }
    val progressTone = MaterialTheme.appAccents.progress
    val urgentTone = MaterialTheme.appAccents.urgent

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = eventTone.container.copy(alpha = 0.4f),
        contentColor = eventTone.onContainer
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = eventTone.onContainer
                    )
                    Text(
                        text = event.targetDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = eventTone.onContainer.copy(alpha = 0.74f)
                    )
                    if (event.notes.isNotBlank()) {
                        Text(
                            text = event.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = eventTone.onContainer.copy(alpha = 0.74f),
                            maxLines = 2
                        )
                    }
                }

                Text(
                    text = if (days < 0) {
                        stringResource(R.string.dday_passed)
                    } else if (days == 0) {
                        stringResource(R.string.dday_today)
                    } else {
                        stringResource(R.string.dday_days_left, days)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = eventTone.onContainer
                )

                Surface(
                    shape = CircleShape,
                    color = urgentTone.action,
                    contentColor = urgentTone.onAction
                ) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.dday_delete),
                            tint = urgentTone.onAction
                        )
                    }
                }
            }

            if (plan != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = progressTone.container.copy(alpha = 0.7f),
                    contentColor = progressTone.onContainer
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        LinearProgressIndicator(
                            progress = { plan.readinessPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = progressTone.color,
                            trackColor = progressTone.onContainer.copy(alpha = 0.16f)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = buildString {
                                append(plan.linkedTitle)
                                append(" · готовность ${plan.readinessPercent}%")
                                if (plan.remainingMinutes > 0) {
                                    append(" · осталось ${plan.remainingMinutes / 60} ч ${plan.remainingMinutes % 60} мин")
                                    append(" · по ${plan.minutesPerDay} мин/день")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = progressTone.onContainer
                        )
                        val paceTone = when {
                            plan.isOnTrack -> progressTone
                            days <= 0 -> urgentTone
                            else -> MaterialTheme.appAccents.warning
                        }
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = paceTone.action,
                            contentColor = paceTone.onAction
                        ) {
                            Text(
                                text = if (plan.isOnTrack) "Темп достаточный" else "Нужно увеличить темп",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = paceTone.onAction
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildDate(day: String, month: String, year: String): String? {
    return runCatching {
        val d = day.toInt()
        val m = month.toInt()
        val y = year.toInt()
        java.time.LocalDate.of(y, m, d)
        "%04d-%02d-%02d".format(y, m, d)
    }.getOrNull()
}
