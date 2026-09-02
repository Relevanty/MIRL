package com.personal.sleepalarm.ui.dday

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.data.db.entity.ProjectEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.ui.components.DeadlineDateTimeField
import com.personal.sleepalarm.ui.theme.AppAccentTone
import com.personal.sleepalarm.ui.theme.appAccents
import com.personal.sleepalarm.util.DeadlineLinks
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** The same editor creates a calendar deadline and changes an existing one without changing its identity. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeadlineEditor(
    initial: DDayEntity?,
    defaultDate: LocalDate,
    projects: List<ProjectEntity>,
    tasks: List<TaskEntity>,
    isSaving: Boolean = false,
    error: String? = null,
    onBack: () -> Unit,
    onSave: (DDayEntity, Long?) -> Unit,
    onDelete: ((DDayEntity) -> Unit)? = null,
    linkedDeadlines: List<DDayEntity> = emptyList(),
    onOpenExistingTaskDeadline: ((DDayEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val source = remember(initial?.id, defaultDate) { initial ?: DDayEntity(title = "", targetDate = defaultDate.toString()) }
    val initialLinks = remember(source.linksJson) { DeadlineLinks.decode(source.linksJson) }
    var title by rememberSaveable(source.id) { mutableStateOf(source.title) }
    var dateIso by rememberSaveable(source.id) { mutableStateOf(source.targetDate) }
    var notes by rememberSaveable(source.id) { mutableStateOf(source.notes) }
    var projectId by rememberSaveable(source.id) { mutableStateOf(source.projectId) }
    var taskId by rememberSaveable(source.id) { mutableStateOf(source.taskId) }
    val zone = ZoneId.systemDefault()
    val sourceTask = tasks.firstOrNull { it.id == source.taskId }
    val sourceDue = sourceTask?.dueAtMillis ?: defaultDate.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
    var taskDueMillis by rememberSaveable(source.id) { mutableStateOf<Long?>(if (source.taskId != null) sourceDue else null) }
    var pendingExistingId by rememberSaveable { mutableStateOf<Int?>(null) }
    var links by rememberSaveable(
        source.id,
        stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it })
    ) { mutableStateOf(initialLinks.ifEmpty { listOf("") }) }
    var submitted by rememberSaveable(source.id) { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showPlanPicker by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var browserError by remember(source.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val scheduleTone = MaterialTheme.appAccents.schedule
    val notesTone = MaterialTheme.appAccents.creative
    val linksTone = MaterialTheme.appAccents.info
    val planTone = MaterialTheme.appAccents.work
    val urgentTone = MaterialTheme.appAccents.urgent
    val formScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(source.id) { formScrollState.scrollTo(0) }
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(dateIso) { runCatching { LocalDate.parse(dateIso) }.getOrDefault(defaultDate) }
    val dateLabel = remember(date, locale) { date.format(DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", locale)) }
    val selectedTask = tasks.firstOrNull { it.id == taskId }
    val effectiveTitle = if (taskId != null) selectedTask?.primaryLabel() ?: title else title
    val hasChanges = title != source.title || dateIso != source.targetDate || notes != source.notes ||
        projectId != source.projectId || taskId != source.taskId || links.filter { it.isNotBlank() } != initialLinks ||
        (taskId != null && taskDueMillis != sourceDue)
    val invalidLinks = links.any { it.isNotBlank() && DeadlineLinks.normalize(it) == null }
    val validationError = if (submitted) {
        when {
            effectiveTitle.isBlank() -> stringResource(R.string.deadline_name_required)
            taskId != null && taskDueMillis == null -> stringResource(R.string.deadline_task_due_required)
            invalidLinks -> stringResource(R.string.deadline_links_check)
            else -> null
        }
    } else null
    val leaveEditor: () -> Unit = {
        if (!isSaving) {
            if (hasChanges) showDiscardDialog = true else onBack()
        }
    }
    val selectedPlanLabel = when {
        taskId != null -> tasks.firstOrNull { it.id == taskId }?.let { stringResource(R.string.deadline_task_named, it.primaryLabel()) }
        projectId != null -> projects.firstOrNull { it.id == projectId }?.let { stringResource(R.string.deadline_project_named, it.title) }
        else -> null
    } ?: stringResource(if (taskId != null || projectId != null) R.string.deadline_linked_unavailable else R.string.deadline_no_plan)
    BackHandler(onBack = leaveEditor)

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Surface(color = scheduleTone.container, contentColor = scheduleTone.onContainer) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = leaveEditor, enabled = !isSaving) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = scheduleTone.onContainer)
                }
                Text(
                    stringResource(if (initial == null) R.string.deadline_create else R.string.deadline_edit),
                    style = MaterialTheme.typography.titleLarge,
                    color = scheduleTone.onContainer,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Button(
                    onClick = {
                        submitted = true
                        val nonEmptyLinks = links.filter { it.isNotBlank() }
                        if (effectiveTitle.isNotBlank() && (taskId == null || taskDueMillis != null) && nonEmptyLinks.all { DeadlineLinks.normalize(it) != null }) {
                            onSave(
                                source.copy(
                                    title = effectiveTitle.trim(),
                                    targetDate = if (taskId != null && taskDueMillis != null) Instant.ofEpochMilli(taskDueMillis!!).atZone(zone).toLocalDate().toString() else date.toString(),
                                    notes = notes.trim(),
                                    projectId = projectId,
                                    taskId = taskId,
                                    linksJson = DeadlineLinks.encode(nonEmptyLinks)
                                ),
                                if (taskId != null) taskDueMillis else null
                            )
                        } else {
                            scope.launch { formScrollState.animateScrollTo(0) }
                        }
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheduleTone.action,
                        contentColor = scheduleTone.onAction,
                        disabledContainerColor = scheduleTone.action,
                        disabledContentColor = scheduleTone.onAction
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = scheduleTone.onAction, strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(formScrollState).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (error != null || validationError != null || browserError) {
                Surface(color = urgentTone.action, contentColor = urgentTone.onAction, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        error ?: validationError ?: stringResource(R.string.deadline_browser_unavailable),
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = urgentTone.onAction
                    )
                }
            }
            DeadlineEditorSection(stringResource(R.string.deadline_result_section), scheduleTone) {
                OutlinedTextField(
                    value = effectiveTitle,
                    onValueChange = { title = it },
                    readOnly = taskId != null,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    label = { Text(stringResource(if (taskId != null) R.string.deadline_task_name else R.string.deadline_name)) },
                    placeholder = { Text(stringResource(R.string.deadline_name_example)) },
                    isError = submitted && effectiveTitle.isBlank(),
                    supportingText = when {
                        submitted && effectiveTitle.isBlank() -> ({ Text(stringResource(R.string.deadline_name_required)) })
                        taskId != null -> ({ Text(stringResource(R.string.deadline_task_name_hint)) })
                        else -> null
                    },
                    minLines = 1,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                if (taskId != null) {
                    DeadlineDateTimeField(
                        value = taskDueMillis,
                        onValueChange = { taskDueMillis = it },
                        tone = scheduleTone,
                        allowClear = false,
                        enabled = !isSaving
                    )
                    Text(stringResource(R.string.deadline_task_single_due), style = MaterialTheme.typography.bodySmall, color = scheduleTone.onContainer)
                } else {
                    Surface(
                        onClick = { showDatePicker = true },
                        enabled = !isSaving,
                        color = scheduleTone.action,
                        contentColor = scheduleTone.onAction,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CalendarMonth, null, tint = scheduleTone.onAction)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(stringResource(R.string.deadline_date_label), style = MaterialTheme.typography.labelMedium, color = scheduleTone.onAction)
                                Text(dateLabel, style = MaterialTheme.typography.titleMedium, color = scheduleTone.onAction)
                            }
                            Icon(Icons.Default.ChevronRight, stringResource(R.string.deadline_choose_date), tint = scheduleTone.onAction)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf(
                            LocalDate.now() to R.string.deadline_due_today,
                            LocalDate.now().plusDays(1) to R.string.deadline_due_tomorrow,
                            LocalDate.now().plusWeeks(1) to R.string.deadline_in_week
                        ).forEach { (quickDate, label) ->
                            FilterChip(
                                selected = date == quickDate,
                                onClick = { dateIso = quickDate.toString() },
                                enabled = !isSaving,
                                label = { Text(stringResource(label)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = scheduleTone.container,
                                    labelColor = scheduleTone.onContainer,
                                    selectedContainerColor = scheduleTone.action,
                                    selectedLabelColor = scheduleTone.onAction
                                )
                            )
                        }
                    }
                    Text(stringResource(R.string.deadline_date_hint), style = MaterialTheme.typography.bodySmall, color = scheduleTone.onContainer)
                }
            }
            DeadlineEditorSection(stringResource(R.string.deadline_notes_section), notesTone) {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    label = { Text(stringResource(R.string.deadline_notes_optional)) },
                    placeholder = { Text(stringResource(R.string.deadline_notes_hint)) },
                    minLines = 2,
                    maxLines = 6
                )
            }
            DeadlineEditorSection(stringResource(R.string.deadline_links_section), linksTone) {
                Text(stringResource(R.string.deadline_links_hint), style = MaterialTheme.typography.bodySmall, color = linksTone.onContainer)
                links.forEachIndexed { index, link ->
                    val validLink = link.isBlank() || DeadlineLinks.normalize(link) != null
                    Column {
                        OutlinedTextField(
                            value = link,
                            onValueChange = { value -> links = links.toMutableList().also { it[index] = value }; browserError = false },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving,
                            label = { Text(stringResource(R.string.deadline_link_number, index + 1)) },
                            placeholder = { Text("https://example.com") },
                            isError = !validLink,
                            supportingText = if (!validLink) ({ Text(stringResource(R.string.deadline_link_invalid)) }) else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            singleLine = true
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            if (link.isNotBlank() && validLink) {
                                TextButton(
                                    onClick = { browserError = !openDeadlineLink(context, link) },
                                    enabled = !isSaving,
                                    colors = ButtonDefaults.textButtonColors(contentColor = linksTone.onContainer)
                                ) {
                                    Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp), tint = linksTone.onContainer)
                                    Text(stringResource(R.string.deadline_open_link), modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                            if (links.size > 1 || link.isNotEmpty()) {
                                IconButton(
                                    onClick = { links = links.filterIndexed { position, _ -> position != index }.ifEmpty { listOf("") } },
                                    enabled = !isSaving
                                ) {
                                    Icon(Icons.Default.Close, stringResource(R.string.deadline_remove_link, index + 1), tint = linksTone.onContainer)
                                }
                            }
                        }
                    }
                }
                TextButton(
                    onClick = { links = links + "" },
                    enabled = !isSaving && links.lastOrNull()?.isNotBlank() == true,
                    colors = ButtonDefaults.textButtonColors(contentColor = linksTone.onContainer)
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp), tint = linksTone.onContainer)
                    Text(stringResource(R.string.deadline_add_link), modifier = Modifier.padding(start = 6.dp))
                }
            }
            DeadlineEditorSection(stringResource(R.string.deadline_plan_section), planTone) {
                Text(stringResource(R.string.deadline_plan_hint), style = MaterialTheme.typography.bodySmall, color = planTone.onContainer)
                Surface(
                    onClick = { showPlanPicker = true },
                    enabled = !isSaving && source.taskId == null,
                    color = planTone.action,
                    contentColor = planTone.onAction,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Link, null, tint = planTone.onAction)
                        Text(selectedPlanLabel, style = MaterialTheme.typography.bodyMedium, color = planTone.onAction, modifier = Modifier.weight(1f))
                        if (source.taskId == null) Icon(Icons.Default.ChevronRight, stringResource(R.string.deadline_choose_plan), tint = planTone.onAction)
                    }
                }
                if (taskId != null || projectId != null) {
                    if (taskId != null) {
                        Text(
                            stringResource(R.string.deadline_linked_task_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = planTone.onContainer
                        )
                    }
                    if (source.taskId == null) TextButton(
                        onClick = { taskId = null; projectId = null },
                        enabled = !isSaving,
                        colors = ButtonDefaults.textButtonColors(contentColor = planTone.onContainer)
                    ) { Text(stringResource(R.string.deadline_unlink_plan)) }
                }
            }
            if (initial != null && onDelete != null && (source.taskId == null || sourceTask?.dueAtMillis != null)) {
                TextButton(
                    onClick = { showDeleteDialog = true },
                    enabled = !isSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = urgentTone.onContainer, containerColor = urgentTone.container),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp), tint = urgentTone.onContainer)
                    Text(stringResource(if (source.taskId != null) R.string.deadline_clear_task_due else R.string.deadline_delete), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (showDatePicker) {
        DeadlineToneTheme(scheduleTone) {
            val picker = rememberDatePickerState(
                initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                yearRange = minOf(1900, date.year)..maxOf(2100, date.year)
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        enabled = picker.selectedDateMillis != null,
                        onClick = {
                            picker.selectedDateMillis?.let { dateIso = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                            showDatePicker = false
                        }
                    ) { Text(stringResource(R.string.deadline_apply_date)) }
                },
                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } }
            ) { DatePicker(state = picker) }
        }
    }
    if (showPlanPicker) {
        DeadlinePlanPicker(
            projects = projects,
            tasks = tasks,
            projectId = projectId,
            taskId = taskId,
            onDismiss = { showPlanPicker = false },
            onSelect = { chosenProject, chosenTask ->
                showPlanPicker = false
                val existing = chosenTask?.let { chosen -> linkedDeadlines.firstOrNull { it.taskId == chosen && it.id != source.id } }
                if (existing != null && onOpenExistingTaskDeadline != null) {
                    if (hasChanges) pendingExistingId = existing.id else onOpenExistingTaskDeadline(existing)
                } else {
                    projectId = chosenProject
                    taskId = chosenTask
                    if (chosenTask != null) {
                        val task = tasks.firstOrNull { it.id == chosenTask }
                        title = task?.primaryLabel() ?: title
                        taskDueMillis = task?.dueAtMillis ?: date.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
                    }
                    scope.launch { formScrollState.animateScrollTo(0) }
                }
            }
        )
    }
    if (showDiscardDialog) {
        DeadlineToneTheme(scheduleTone) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = { Text(stringResource(R.string.deadline_discard_title)) },
                text = { Text(stringResource(R.string.deadline_discard_message)) },
                confirmButton = {
                    TextButton(onClick = { showDiscardDialog = false; onBack() }) { Text(stringResource(R.string.deadline_discard)) }
                },
                dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.deadline_keep_editing)) } }
            )
        }
    }
    if (showDeleteDialog && initial != null && onDelete != null) {
        DeadlineToneTheme(urgentTone) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(if (source.taskId != null) R.string.deadline_clear_task_due_title else R.string.deadline_delete_title)) },
                text = { Text(stringResource(if (source.taskId != null) R.string.deadline_clear_task_due_message else R.string.deadline_delete_message, effectiveTitle)) },
                confirmButton = {
                    TextButton(onClick = { showDeleteDialog = false; onDelete(initial) }, enabled = !isSaving) { Text(stringResource(if (source.taskId != null) R.string.deadline_clear_task_due else R.string.deadline_delete)) }
                },
                dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
            )
        }
    }
    if (pendingExistingId != null) {
        DeadlineToneTheme(scheduleTone) {
            AlertDialog(
                onDismissRequest = { pendingExistingId = null },
                title = { Text(stringResource(R.string.deadline_open_existing_title)) },
                text = { Text(stringResource(R.string.deadline_open_existing_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        val existing = linkedDeadlines.firstOrNull { it.id == pendingExistingId }
                        pendingExistingId = null
                        existing?.let { onOpenExistingTaskDeadline?.invoke(it) }
                    }) { Text(stringResource(R.string.deadline_open_existing)) }
                },
                dismissButton = { TextButton(onClick = { pendingExistingId = null }) { Text(stringResource(R.string.action_cancel)) } }
            )
        }
    }
}

@Composable
private fun DeadlineEditorSection(title: String, tone: AppAccentTone, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = tone.container, contentColor = tone.onContainer, shape = RoundedCornerShape(14.dp)) {
        DeadlineToneTheme(tone) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = tone.onContainer)
                content()
            }
        }
    }
}

@Composable
private fun DeadlinePlanPicker(
    projects: List<ProjectEntity>,
    tasks: List<TaskEntity>,
    projectId: Int?,
    taskId: Int?,
    onDismiss: () -> Unit,
    onSelect: (Int?, Int?) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filteredProjects = remember(projects, query) { projects.filter { it.title.contains(query.trim(), ignoreCase = true) } }
    val filteredTasks = remember(tasks, query) { tasks.filter { it.primaryLabel().contains(query.trim(), ignoreCase = true) } }
    val tone = MaterialTheme.appAccents.work
    DeadlineToneTheme(tone) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.deadline_choose_plan)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.deadline_search_plan)) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true
                    )
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item("none") {
                            DeadlinePlanOption(stringResource(R.string.deadline_no_plan), projectId == null && taskId == null, tone) { onSelect(null, null) }
                        }
                        if (filteredProjects.isNotEmpty()) {
                            item("projects") { Text(stringResource(R.string.deadline_projects), style = MaterialTheme.typography.labelLarge, color = tone.onContainer, modifier = Modifier.padding(top = 8.dp)) }
                            items(filteredProjects, key = { "project_${it.id}" }) { project ->
                                DeadlinePlanOption(project.title, projectId == project.id, tone) { onSelect(project.id, null) }
                            }
                        }
                        if (filteredTasks.isNotEmpty()) {
                            item("tasks") { Text(stringResource(R.string.deadline_tasks), style = MaterialTheme.typography.labelLarge, color = tone.onContainer, modifier = Modifier.padding(top = 8.dp)) }
                            items(filteredTasks, key = { "task_${it.id}" }) { task ->
                                DeadlinePlanOption(task.primaryLabel(), taskId == task.id, tone) { onSelect(null, task.id) }
                            }
                        }
                        if (filteredProjects.isEmpty() && filteredTasks.isEmpty()) {
                            item("empty") {
                                Text(stringResource(R.string.deadline_plan_empty), style = MaterialTheme.typography.bodySmall, color = tone.onContainer, modifier = Modifier.padding(vertical = 10.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
        )
    }
}

@Composable
private fun DeadlinePlanOption(label: String, selected: Boolean, tone: AppAccentTone, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) tone.action else tone.container,
        contentColor = if (selected) tone.onAction else tone.onContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Default.Check, stringResource(R.string.deadline_selected), modifier = Modifier.size(18.dp))
        }
    }
}
