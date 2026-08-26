package com.personal.sleepalarm.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.repository.ActivityConflictStrategy
import com.personal.sleepalarm.data.repository.ManualActivityInput
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.ui.theme.ThemedModalBottomSheet
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManualActivitySheet(
    onDismiss: () -> Unit,
    initialTaskId: Int? = null,
    initialActivityType: FocusActivityType = FocusActivityType.WORK,
    initialSubjectId: Int? = null,
    initialOtherActivityId: Int? = null,
    initialTitle: String = "",
    initialStartMillis: Long? = null,
    editing: ActivityRecordEntity? = null,
    viewModel: ManualActivityViewModel = viewModel(),
    onSaved: () -> Unit = {}
) {
    val tasks by viewModel.tasks.collectAsState()
    val conflicts by viewModel.conflicts.collectAsState()
    val zone = ZoneId.systemDefault()
    val scope = rememberCoroutineScope()
    val initialStart = editing?.startedAt ?: initialStartMillis ?: (System.currentTimeMillis() - 25L * 60_000L)
    val initialEnd = editing?.endedAt ?: System.currentTimeMillis()
    var selectedTaskId by rememberSaveable(editing?.id, initialTaskId) {
        mutableStateOf(editing?.taskId ?: initialTaskId)
    }
    var activityType by rememberSaveable(editing?.id) {
        mutableStateOf(editing?.activityType ?: initialActivityType)
    }
    var title by rememberSaveable(editing?.id) { mutableStateOf(editing?.title ?: initialTitle) }
    var selectedDate by rememberSaveable(editing?.id) {
        mutableStateOf(Instant.ofEpochMilli(initialStart).atZone(zone).toLocalDate())
    }
    var startText by rememberSaveable(editing?.id) {
        mutableStateOf(Instant.ofEpochMilli(initialStart).atZone(zone).toLocalTime().format(TIME_FORMAT))
    }
    var endText by rememberSaveable(editing?.id) {
        mutableStateOf(Instant.ofEpochMilli(initialEnd).atZone(zone).toLocalTime().format(TIME_FORMAT))
    }
    var durationText by rememberSaveable(editing?.id) {
        mutableStateOf(((initialEnd - initialStart) / 60_000L).coerceAtLeast(1L).toString())
    }
    var useDuration by rememberSaveable(editing?.id) { mutableStateOf(true) }
    var result by rememberSaveable(editing?.id) { mutableStateOf(editing?.result.orEmpty()) }
    var material by rememberSaveable(editing?.id) { mutableStateOf(editing?.material.orEmpty()) }
    var note by rememberSaveable(editing?.id) { mutableStateOf(editing?.note.orEmpty()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var pendingInput by remember { mutableStateOf<ManualActivityInput?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tasks, selectedTaskId) {
        val task = tasks.firstOrNull { it.id == selectedTaskId }
        if (title.isBlank() && task != null) title = task.title.ifBlank { task.description }
        if (task != null) activityType = categoryToActivityType(task.category)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ManualActivityEvent.Saved, ManualActivityEvent.Deleted -> {
                    onSaved()
                    onDismiss()
                }
                is ManualActivityEvent.Error -> error = event.reason
            }
        }
    }

    fun buildInput(): ManualActivityInput? {
        val startTime = runCatching { LocalTime.parse(startText.trim(), FLEX_TIME_FORMAT) }.getOrNull()
            ?: return null
        val start = LocalDateTime.of(selectedDate, startTime).atZone(zone).toInstant().toEpochMilli()
        val end = if (useDuration) {
            val minutes = durationText.toIntOrNull()?.coerceIn(1, 24 * 60) ?: return null
            start + minutes * 60_000L
        } else {
            val parsedEnd = runCatching { LocalTime.parse(endText.trim(), FLEX_TIME_FORMAT) }.getOrNull()
                ?: return null
            var value = LocalDateTime.of(selectedDate, parsedEnd).atZone(zone).toInstant().toEpochMilli()
            if (value <= start) value += 24L * 60L * 60L * 1000L
            value
        }
        val task = tasks.firstOrNull { it.id == selectedTaskId }
        return ManualActivityInput(
            id = editing?.id ?: 0,
            taskId = selectedTaskId,
            projectId = task?.projectId,
            activityType = activityType,
            subjectId = editing?.subjectId ?: initialSubjectId.takeIf { activityType == FocusActivityType.STUDY },
            otherActivityId = editing?.otherActivityId ?: initialOtherActivityId.takeIf { activityType == FocusActivityType.OTHER },
            title = title.ifBlank { task?.title.orEmpty() },
            startedAt = start,
            endedAt = end,
            result = result,
            material = material,
            note = note
        )
    }

    ThemedModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(if (editing == null) R.string.activity_add_title else R.string.activity_edit_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.activity_manual_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(stringResource(R.string.activity_category), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusActivityType.entries.forEach { type ->
                    FilterChip(
                        selected = activityType == type,
                        onClick = { activityType = type },
                        label = { Text(activityTypeLabel(type)) }
                    )
                }
            }

            if (tasks.isNotEmpty()) {
                Text(stringResource(R.string.activity_task), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedTaskId == null,
                        onClick = { selectedTaskId = null },
                        label = { Text(stringResource(R.string.activity_without_task)) }
                    )
                    tasks.filterNot { it.isDone }.take(12).forEach { task ->
                        FilterChip(
                            selected = selectedTaskId == task.id,
                            onClick = {
                                selectedTaskId = task.id
                                if (title.isBlank()) title = task.title.ifBlank { task.description }
                            },
                            label = { Text(task.title.ifBlank { task.description }.take(28)) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.activity_what_did)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy")))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text(stringResource(R.string.activity_start)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                if (useDuration) {
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.activity_duration_input)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it },
                        label = { Text(stringResource(R.string.activity_end)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.activity_enter_duration), modifier = Modifier.weight(1f))
                Switch(checked = useDuration, onCheckedChange = { useDuration = it })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 25, 45, 60).forEach { minutes ->
                    FilterChip(
                        selected = useDuration && durationText == minutes.toString(),
                        onClick = {
                            useDuration = true
                            durationText = minutes.toString()
                        },
                        label = { Text(if (minutes == 60) stringResource(R.string.activity_one_hour) else "+$minutes") }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val minutes = durationText.toIntOrNull()?.coerceIn(1, 24 * 60) ?: 25
                        val now = LocalTime.now()
                        startText = now.minusMinutes(minutes.toLong()).format(TIME_FORMAT)
                        endText = now.format(TIME_FORMAT)
                        selectedDate = LocalDate.now()
                        useDuration = true
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.activity_working_since)) }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            viewModel.latestManual()?.let { last ->
                                selectedTaskId = last.taskId
                                activityType = last.activityType
                                title = last.title
                                durationText = (last.durationMillis / 60_000L).coerceAtLeast(1L).toString()
                                result = ""
                                material = last.material
                                note = last.note
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.activity_repeat_last)) }
            }

            OutlinedTextField(
                value = result,
                onValueChange = { result = it },
                label = { Text(stringResource(R.string.activity_result)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            OutlinedTextField(
                value = material,
                onValueChange = { material = it },
                label = { Text(stringResource(R.string.activity_material)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.activity_note)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            error?.let {
                Text(stringResource(R.string.activity_invalid), color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (editing != null) {
                    TextButton(onClick = { viewModel.delete(editing.id) }) {
                        Text(stringResource(R.string.reminders_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = {
                    val input = buildInput()
                    if (input == null) error = "invalid" else {
                        pendingInput = input
                        viewModel.save(input)
                    }
                }) { Text(stringResource(R.string.task_save)) }
            }
        }
    }

    if (showDatePicker) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        selectedDate = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.task_date_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) { DatePicker(picker) }
    }

    if (conflicts.isNotEmpty() && pendingInput != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearConflicts,
            title = { Text(stringResource(R.string.activity_overlap_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.activity_overlap_message, conflicts.first().title))
                    conflicts.take(3).forEach { conflict ->
                        Text(
                            "${Instant.ofEpochMilli(conflict.startedAt).atZone(zone).toLocalTime().format(TIME_FORMAT)}–" +
                                "${Instant.ofEpochMilli(conflict.endedAt).atZone(zone).toLocalTime().format(TIME_FORMAT)} · ${conflict.title}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { viewModel.save(pendingInput!!, ActivityConflictStrategy.REPLACE) }) {
                        Text(stringResource(R.string.activity_overlap_replace))
                    }
                    TextButton(onClick = { viewModel.save(pendingInput!!, ActivityConflictStrategy.MERGE) }) {
                        Text(stringResource(R.string.activity_overlap_merge))
                    }
                    TextButton(onClick = { viewModel.save(pendingInput!!, ActivityConflictStrategy.KEEP_PARALLEL) }) {
                        Text(stringResource(R.string.activity_overlap_parallel))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::clearConflicts) {
                    Text(stringResource(R.string.activity_overlap_adjust))
                }
            }
        )
    }
}

@Composable
private fun activityTypeLabel(type: FocusActivityType): String = when (type) {
    FocusActivityType.STUDY -> stringResource(R.string.activity_study)
    FocusActivityType.WORK -> stringResource(R.string.activity_work)
    FocusActivityType.OTHER -> stringResource(R.string.activity_other)
}

private fun categoryToActivityType(category: String): FocusActivityType = when (category.uppercase()) {
    "STUDY", "УЧЁБА", "УЧЕБА" -> FocusActivityType.STUDY
    "OTHER", "ДРУГОЕ" -> FocusActivityType.OTHER
    else -> FocusActivityType.WORK
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val FLEX_TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm")
