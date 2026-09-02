package com.personal.sleepalarm.ui.activity

import com.personal.sleepalarm.ui.theme.appAccents

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
import androidx.compose.material3.SelectableDates
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
import androidx.compose.runtime.produceState
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
import com.personal.sleepalarm.domain.model.ActualActivityTimePolicy
import com.personal.sleepalarm.domain.model.ActualActivityTimeError
import com.personal.sleepalarm.domain.model.ManualActivityInterval
import com.personal.sleepalarm.domain.model.focusActivityType
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.ui.theme.ThemedModalBottomSheet
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.ZoneOffset
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
    val isSaving by viewModel.saving.collectAsState()
    val zone = ZoneId.systemDefault()
    val scope = rememberCoroutineScope()
    val initialStart = remember(editing?.id, initialStartMillis) {
        editing?.startedAt ?: initialStartMillis ?: (System.currentTimeMillis() - 25L * 60_000L)
    }
    val initialEnd = remember(editing?.id, initialStartMillis) { editing?.endedAt ?: System.currentTimeMillis() }
    val originalStart = Instant.ofEpochMilli(initialStart).atZone(zone)
    val originalEnd = Instant.ofEpochMilli(initialEnd).atZone(zone)
    val originalDurationText = ((initialEnd - initialStart) / 60_000L).toString()
    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L)
        }
    }
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
        mutableStateOf(originalDurationText)
    }
    var useDuration by rememberSaveable(editing?.id) { mutableStateOf(true) }
    var endsNextDay by rememberSaveable(editing?.id) {
        mutableStateOf(originalEnd.toLocalDate().isAfter(originalStart.toLocalDate()))
    }
    var result by rememberSaveable(editing?.id) { mutableStateOf(editing?.result.orEmpty()) }
    var material by rememberSaveable(editing?.id) { mutableStateOf(editing?.material.orEmpty()) }
    var note by rememberSaveable(editing?.id) { mutableStateOf(editing?.note.orEmpty()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var pendingInput by remember { mutableStateOf<ManualActivityInput?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val selectedTask = tasks.firstOrNull { it.id == selectedTaskId }

    LaunchedEffect(selectedDate, startText, endText, durationText, useDuration, endsNextDay, title) {
        error = null
        pendingInput = null
        viewModel.clearConflicts()
    }

    LaunchedEffect(tasks, selectedTaskId) {
        val task = tasks.firstOrNull { it.id == selectedTaskId }
        if (task != null) {
            if (title.isBlank()) title = task.primaryLabel()
            activityType = task.focusActivityType()
        }
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

    fun buildInterval(): ManualActivityInterval? {
        // Metadata-only history edits keep the recorded seconds/milliseconds.
        // Minute-level fields must not silently round the original interval.
        val unchangedStart = selectedDate == originalStart.toLocalDate() &&
            startText == originalStart.toLocalTime().format(TIME_FORMAT)
        val unchangedEnd = if (useDuration) durationText == originalDurationText else
            endText == originalEnd.toLocalTime().format(TIME_FORMAT) &&
                endsNextDay == originalEnd.toLocalDate().isAfter(originalStart.toLocalDate())
        if (editing != null && unchangedStart && unchangedEnd) {
            return ManualActivityInterval(initialStart, initialEnd)
        }
        return ActualActivityTimePolicy.parse(
            selectedDate, startText, endText, durationText, useDuration, endsNextDay, zone
        )
    }

    fun buildInput(interval: ManualActivityInterval): ManualActivityInput {
        val task = tasks.firstOrNull { it.id == selectedTaskId }
        val resolvedActivityType = task?.focusActivityType() ?: activityType
        return ManualActivityInput(
            id = editing?.id ?: 0,
            taskId = selectedTaskId,
            projectId = task?.projectId,
            activityType = resolvedActivityType,
            subjectId = (editing?.subjectId ?: initialSubjectId).takeIf {
                task == null && resolvedActivityType == FocusActivityType.STUDY
            },
            otherActivityId = (editing?.otherActivityId ?: initialOtherActivityId).takeIf {
                task == null && resolvedActivityType == FocusActivityType.OTHER
            },
            title = title.ifBlank { task?.primaryLabel().orEmpty() },
            startedAt = interval.startedAt,
            endedAt = interval.endedAt,
            result = result,
            material = material,
            note = note
        )
    }
    val previewInterval = buildInterval()
    val timeError = if (previewInterval == null) ActualActivityTimeError.INVALID_TIME.reason else
        ActualActivityTimePolicy.validate(previewInterval.startedAt, previewInterval.endedAt, nowMillis)?.reason

    ThemedModalBottomSheet(onDismissRequest = { if (!isSaving) onDismiss() }) {
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
            Text(
                text = stringResource(R.string.activity_actual_only_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appAccents.info.color
            )

            Text(stringResource(R.string.activity_category), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusActivityType.entries.forEach { type ->
                    FilterChip(
                        selected = activityType == type,
                        onClick = { if (selectedTask == null) activityType = type },
                        enabled = selectedTask == null,
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
                                activityType = task.focusActivityType()
                                title = task.primaryLabel()
                            },
                            label = { Text(task.primaryLabel().take(28)) }
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
                        onValueChange = { durationText = it.take(8) },
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
            if (!useDuration) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.activity_end_next_day), modifier = Modifier.weight(1f))
                    Switch(checked = endsNextDay, onCheckedChange = { endsNextDay = it })
                }
            }
            previewInterval?.takeIf { it.endedAt > it.startedAt }?.let { interval ->
                val endDateTime = Instant.ofEpochMilli(interval.endedAt).atZone(zone)
                Text(
                    stringResource(R.string.activity_actual_end_at, endDateTime.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                        val minutes = durationText.toLongOrNull()
                        when {
                            minutes == null -> error = ActualActivityTimeError.INVALID_TIME.reason
                            minutes <= 0L -> error = ActualActivityTimeError.INVALID_DURATION.reason
                            minutes > 24L * 60L -> error = ActualActivityTimeError.TOO_LONG.reason
                            else -> {
                                val end = ZonedDateTime.now(zone).withSecond(0).withNano(0)
                                val start = end.minusMinutes(minutes)
                                startText = start.format(TIME_FORMAT)
                                endText = end.format(TIME_FORMAT)
                                selectedDate = start.toLocalDate()
                                endsNextDay = end.toLocalDate().isAfter(start.toLocalDate())
                                useDuration = true
                            }
                        }
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
            (timeError ?: error)?.let { reason ->
                Text(
                    stringResource(activityValidationMessage(reason)),
                    color = MaterialTheme.appAccents.warning.color
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (editing != null) {
                    TextButton(onClick = { viewModel.delete(editing.id) }, enabled = !isSaving) {
                        Text(stringResource(R.string.reminders_delete), color = MaterialTheme.appAccents.urgent.color)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss, enabled = !isSaving) { Text(stringResource(R.string.action_cancel)) }
                Button(enabled = !isSaving, onClick = {
                    val interval = buildInterval()
                    val reason = if (interval == null) ActualActivityTimeError.INVALID_TIME.reason else
                        ActualActivityTimePolicy.validate(interval.startedAt, interval.endedAt, System.currentTimeMillis())?.reason
                    if (reason != null) error = reason else if (interval != null) {
                        val input = buildInput(interval)
                        if (input.title.isBlank()) error = "title" else {
                            pendingInput = input
                            viewModel.save(input)
                        }
                    }
                }) { Text(stringResource(R.string.task_save)) }
            }
        }
    }

    if (showDatePicker) {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today

                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(enabled = picker.selectedDateMillis?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() <= today
                } == true, onClick = {
                    picker.selectedDateMillis?.let {
                        selectedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
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
                    TextButton(enabled = !isSaving, onClick = { viewModel.save(pendingInput!!, ActivityConflictStrategy.REPLACE) }) {
                        Text(stringResource(R.string.activity_overlap_replace))
                    }
                    TextButton(enabled = !isSaving, onClick = { viewModel.save(pendingInput!!, ActivityConflictStrategy.MERGE) }) {
                        Text(stringResource(R.string.activity_overlap_merge))
                    }
                    TextButton(enabled = !isSaving, onClick = { viewModel.save(pendingInput!!, ActivityConflictStrategy.KEEP_PARALLEL) }) {
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

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

private fun activityValidationMessage(reason: String): Int = when (reason) {
    "future" -> R.string.activity_actual_future_error
    "duration" -> R.string.activity_actual_duration_error
    "too_long" -> R.string.activity_actual_too_long_error
    "time_format" -> R.string.activity_actual_time_format_error
    "title" -> R.string.activity_actual_title_error
    "missing_record", "not_manual" -> R.string.activity_actual_missing_record_error
    "missing_task" -> R.string.activity_actual_missing_task_error
    else -> R.string.activity_invalid
}
