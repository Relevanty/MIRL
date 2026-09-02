package com.personal.sleepalarm.ui.calendar

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.rememberModalBottomSheetState
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.CalendarEventEntity
import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.ui.dday.DDayViewModel
import com.personal.sleepalarm.ui.dday.DeadlineCard
import com.personal.sleepalarm.ui.dday.DeadlineEditor
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.domain.calculator.CalendarActivityCalculator
import com.personal.sleepalarm.domain.calculator.effectiveActivityEndMillis
import com.personal.sleepalarm.ui.activity.ManualActivitySheet
import com.personal.sleepalarm.ui.theme.ThemedModalBottomSheet
import com.personal.sleepalarm.ui.theme.AppAccentTone
import com.personal.sleepalarm.ui.theme.appAccents
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = viewModel(),
    deadlineViewModel: DDayViewModel = viewModel(),
    onBack: (() -> Unit)? = null,
    onOpenReminders: () -> Unit = {},
    onOpenTask: (Int) -> Unit = {},
    onStartFocus: (Int) -> Unit = {},
    openEventId: Int? = null,
    openOccurrenceStart: Long? = null,
    openRequestToken: Int = 0,
    openDeadlines: Boolean = false,
    onOpenDeadlinesConsumed: () -> Unit = {},
    onEditorActive: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val eventsLoaded by viewModel.eventsLoaded.collectAsStateWithLifecycle()
    val savingPlan by viewModel.saving.collectAsStateWithLifecycle()
    val planError by viewModel.saveError.collectAsStateWithLifecycle()
    val savedPlan by viewModel.savedEvent.collectAsStateWithLifecycle()
    val studySessions by viewModel.studySessions.collectAsStateWithLifecycle()
    val actualActivities by viewModel.actualActivities.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val sleepSessions by viewModel.sleepSessions.collectAsStateWithLifecycle()
    val deadlineState by deadlineViewModel.uiState.collectAsStateWithLifecycle()
    val savingDeadline by deadlineViewModel.saving.collectAsStateWithLifecycle()
    val deadlineError by deadlineViewModel.saveError.collectAsStateWithLifecycle()
    val deadlineMutation by deadlineViewModel.mutationResult.collectAsStateWithLifecycle()
    val milestones = deadlineState.events
    val activeMilestones = milestones.filter { milestone ->
        milestone.taskId == null || tasks.none { it.id == milestone.taskId && it.isDone }
    }
    val actualByDay = remember(actualActivities) {
        CalendarActivityCalculator.millisByDate(actualActivities, ZoneId.systemDefault())
    }

    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editorDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var editingEventId by rememberSaveable { mutableStateOf<Int?>(null) }
    val editingEvent = events.firstOrNull { it.id == editingEventId }
    var handledEventRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var manualStartMillis by remember { mutableStateOf<Long?>(null) }
    var editingActivity by remember { mutableStateOf<ActivityRecordEntity?>(null) }
    var showDeadlineList by rememberSaveable { mutableStateOf(false) }
    var showDeadlineEditor by rememberSaveable { mutableStateOf(false) }
    var editingDeadlineId by rememberSaveable { mutableStateOf<Int?>(null) }
    var deadlineDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }

    DisposableEffect(showDeadlineEditor, showEditor) {
        onEditorActive(showDeadlineEditor || showEditor)
        onDispose { onEditorActive(false) }
    }

    LaunchedEffect(savedPlan) {
        val saved = savedPlan ?: return@LaunchedEffect
        showEditor = false
        editingEventId = null
        val date = Instant.ofEpochMilli(saved.startMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        month = YearMonth.from(date)
        selectedDate = date
        viewModel.consumeSavedEvent(saved)
    }

    fun editPlan(event: CalendarEventEntity?, date: LocalDate? = null) {
        editingEventId = event?.id
        editorDate = date
        viewModel.clearSaveError()
        selectedDate = null
        showEditor = true
    }

    // Consume save/delete completion from the current composition, even after rotation.
    LaunchedEffect(deadlineMutation) {
        val result = deadlineMutation ?: return@LaunchedEffect
        showDeadlineEditor = false
        editingDeadlineId = null
        result.targetDate?.let { dateString ->
            val date = LocalDate.parse(dateString)
            month = YearMonth.from(date)
            if (!showDeadlineList) selectedDate = date
        }
        deadlineViewModel.consumeMutationResult(result)
    }

    fun editDeadline(event: DDayEntity?, date: LocalDate = LocalDate.now()) {
        editingDeadlineId = event?.id
        deadlineDate = if (event?.taskId != null) {
            deadlineState.tasks.firstOrNull { it.id == event.taskId }?.dueAtMillis?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
            } ?: date.toString()
        } else event?.targetDate ?: date.toString()
        deadlineViewModel.clearSaveError()
        selectedDate = null
        showDeadlineEditor = true
    }

    LaunchedEffect(openDeadlines) {
        if (openDeadlines) {
            showDeadlineList = true
            selectedDate = null
            onOpenDeadlinesConsumed()
        }
    }

    LaunchedEffect(openEventId, openOccurrenceStart, openRequestToken, events) {
        val requestKey = "$openRequestToken:$openEventId:$openOccurrenceStart"
        if (handledEventRequest == requestKey) return@LaunchedEffect
        val event = events.firstOrNull { it.id == openEventId } ?: return@LaunchedEffect
        handledEventRequest = requestKey
        val occurrence = openOccurrenceStart ?: event.startMillis
        val date = Instant.ofEpochMilli(occurrence).atZone(ZoneId.systemDefault()).toLocalDate()
        month = YearMonth.from(date)
        selectedDate = date
    }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scheduleTone = MaterialTheme.appAccents.schedule

    if (showDeadlineEditor) {
        val editingDeadline = milestones.firstOrNull { it.id == editingDeadlineId }
            ?: deadlineState.metadata.firstOrNull { it.id == editingDeadlineId }
        // Do not turn a restored edit into a new item while Room is loading.
        if (editingDeadlineId != null && editingDeadline == null) {
            BackHandler { showDeadlineEditor = false }
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (deadlineState.isLoaded && !savingDeadline) {
                    Text(stringResource(R.string.calendar_deadline_stale), color = scheduleTone.color)
                } else {
                    CircularProgressIndicator(color = scheduleTone.color)
                }
                TextButton(
                    onClick = { showDeadlineEditor = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = scheduleTone.color)
                ) {
                    Text(stringResource(R.string.action_back))
                }
            }
            return
        }
        DeadlineEditor(
            initial = editingDeadline,
            defaultDate = runCatching { LocalDate.parse(deadlineDate) }.getOrDefault(LocalDate.now()),
            projects = deadlineState.projects,
            tasks = deadlineState.tasks,
            linkedDeadlines = deadlineState.metadata,
            onOpenExistingTaskDeadline = { editDeadline(it) },
            isSaving = savingDeadline,
            error = deadlineError,
            onBack = { showDeadlineEditor = false },
            onSave = deadlineViewModel::saveEvent,
            onDelete = if (editingDeadline != null) { event ->
                deadlineViewModel.deleteEvent(event.id)
            } else null
        )
        return
    }

    if (showDeadlineList) {
        BackHandler { showDeadlineList = false }
        CalendarDeadlines(
            events = milestones,
            plans = deadlineState.plans,
            completedTaskIds = tasks.filter { it.isDone }.map { it.id }.toSet(),
            taskDueDates = tasks.mapNotNull { task -> task.dueAtMillis?.let { task.id to it } }.toMap(),
            onBack = { showDeadlineList = false },
            onCreate = { editDeadline(null) },
            onEdit = { editDeadline(it) },
            onOpenTask = onOpenTask,
            modifier = modifier
        )
        return
    }

    if (showEditor) {
        if (editingEventId != null && editingEvent == null) {
            BackHandler { showEditor = false }
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (eventsLoaded && !savingPlan) {
                    Text(stringResource(R.string.calendar_plan_missing), color = scheduleTone.color)
                } else CircularProgressIndicator(color = scheduleTone.color)
                TextButton(onClick = { showEditor = false }) { Text(stringResource(R.string.action_back)) }
            }
            return
        }
        PlannedActivityEditor(
            initial = editingEvent,
            defaultDate = editorDate,
            tasks = tasks.filter { !it.isDone || it.id == editingEvent?.taskId },
            isSaving = savingPlan,
            error = planError,
            onBack = {
                showEditor = false
                editingEventId = null
            },
            onSave = viewModel::savePlannedActivity
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
    ) {
        if (onBack != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        stringResource(R.string.action_back),
                        tint = scheduleTone.color
                    )
                }
                Text(
                    text = stringResource(R.string.task_open_calendar),
                    style = MaterialTheme.typography.headlineSmall,
                    color = scheduleTone.color
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.Default.ChevronLeft, null, tint = scheduleTone.color)
            }
            Text(
                text = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = scheduleTone.color,
                textAlign = TextAlign.Center
            )
            IconButton(onClick = onOpenReminders) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = stringResource(R.string.reminders_title),
                    tint = scheduleTone.color
                )
            }
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.Default.ChevronRight, null, tint = scheduleTone.color)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { showDeadlineList = true },
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, scheduleTone.color),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = scheduleTone.color)
            ) {
                Icon(Icons.Default.Flag, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.calendar_deadlines_count, milestones.size))
            }
            Button(
                onClick = { editDeadline(null) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheduleTone.action,
                    contentColor = scheduleTone.onAction
                )
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.calendar_create_deadline))
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(
                stringResource(R.string.day_mon),
                stringResource(R.string.day_tue),
                stringResource(R.string.day_wed),
                stringResource(R.string.day_thu),
                stringResource(R.string.day_fri),
                stringResource(R.string.day_sat),
                stringResource(R.string.day_sun)
            ).forEach { d ->
                Text(
                    text = d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheduleTone.color.copy(alpha = 0.72f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val grid = remember(month) { buildMonthGrid(month) }
        val today = LocalDate.now()

        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            grid.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    week.forEach { day ->
                        DayCell(
                            modifier = Modifier.weight(1f),
                            day = day,
                            isToday = day.date == today,
                            actualMillis = actualByDay[day.date] ?: 0L,
                            events = eventsOn(events, day.date),
                            markers = buildList {
                                val dateMilestones = activeMilestones.filter { it.targetDate == day.date.toString() }
                                dateMilestones.forEach {
                                    add(CalendarMarker(it.title, CalendarMarkerKind.DEADLINE))
                                }
                                tasks.filter { task ->
                                    !task.isDone &&
                                    dateMilestones.none { it.taskId == task.id } &&
                                    task.dueAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() == day.date } == true
                                }.take(1).forEach {
                                    add(CalendarMarker(it.primaryLabel(), CalendarMarkerKind.DEADLINE))
                                }
                                sleepSessions.filter { session ->
                                    val wake = session.actualWakeTime ?: session.estimatedWakeTime
                                    Instant.ofEpochMilli(wake).atZone(ZoneId.systemDefault()).toLocalDate() == day.date
                                }.take(1).forEach {
                                    add(CalendarMarker("Сон", CalendarMarkerKind.SLEEP))
                                }
                            },
                            onClick = { selectedDate = day.date }
                        )
                    }
                }
            }
        }
    }

    if (selectedDate != null) {
        val date = selectedDate!!
        ThemedModalBottomSheet(
            onDismissRequest = { selectedDate = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("EEE, dd.MM", Locale.getDefault())),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheduleTone.color
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { editDeadline(null, date) },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, scheduleTone.color),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = scheduleTone.color)
                ) {
                    Icon(Icons.Default.Flag, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.calendar_deadline_on_date))
                }

                val dayEvents = eventsOn(events, date)
                val dayActivities = activitiesOn(actualActivities, date)
                val dayMilestones = activeMilestones.filter { it.targetDate == date.toString() }
                val dayDeadlines = tasks.filter { task ->
                    !task.isDone && dayMilestones.none { it.taskId == task.id } && task.dueAtMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() == date
                    } == true
                }
                val daySleeps = sleepSessions.filter { session ->
                    val wake = session.actualWakeTime ?: session.estimatedWakeTime
                    Instant.ofEpochMilli(wake).atZone(ZoneId.systemDefault()).toLocalDate() == date
                }
                dayMilestones.forEach { milestone ->
                    DeadlineCard(
                        event = milestone,
                        plan = deadlineState.plans[milestone.id],
                        dueAtMillis = milestone.taskId?.let { taskId -> tasks.firstOrNull { it.id == taskId }?.dueAtMillis },
                        modifier = Modifier.padding(vertical = 4.dp),
                        onClick = { editDeadline(milestone) },
                        onOpenTask = { taskId -> selectedDate = null; onOpenTask(taskId) }
                    )
                }
                if (dayActivities.isNotEmpty()) {
                    Text(
                        stringResource(R.string.activity_history),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheduleTone.color
                    )
                    dayActivities.forEach { activity ->
                        val activityTone = when (activity.activityType) {
                            com.personal.sleepalarm.domain.model.FocusActivityType.STUDY -> MaterialTheme.appAccents.study
                            com.personal.sleepalarm.domain.model.FocusActivityType.WORK -> MaterialTheme.appAccents.work
                            com.personal.sleepalarm.domain.model.FocusActivityType.OTHER -> MaterialTheme.appAccents.other
                        }
                        Surface(
                            onClick = {
                                if (activity.source == "MANUAL") {
                                    editingActivity = activity
                                    selectedDate = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = activityTone.container.copy(alpha = 0.82f),
                            contentColor = activityTone.onContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Timer, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        activity.taskId?.let { taskId ->
                                            tasks.firstOrNull { it.id == taskId }?.primaryLabel()
                                        } ?: activity.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        activityTimeLabel(activity),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = activityTone.onContainer.copy(alpha = 0.76f)
                                    )
                                }
                                if (activity.source == "MANUAL") {
                                    Text(
                                        stringResource(R.string.activity_added_manually),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = activityTone.onContainer
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                daySleeps.forEach { sleep ->
                    val wake = sleep.actualWakeTime ?: sleep.estimatedWakeTime
                    val onset = sleep.detectedSleepOnsetTime ?: sleep.estimatedSleepStartTime
                    Text(
                        "Сон · ${formatDuration((wake - onset).coerceAtLeast(0L))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appAccents.sleep.color,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                dayDeadlines.forEach { task ->
                    val tone = deadlineTone(date)
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = tone.container.copy(alpha = 0.82f),
                        contentColor = tone.onContainer
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Срок · ${task.primaryLabel()}",
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = tone.onContainer
                            )
                            IconButton(onClick = { selectedDate = null; onOpenTask(task.id) }) {
                                Icon(
                                    Icons.Default.Checklist,
                                    "Открыть задачу",
                                    tint = tone.onContainer
                                )
                            }
                            IconButton(onClick = { selectedDate = null; onStartFocus(task.id) }) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    "Начать фокус",
                                    tint = tone.onContainer
                                )
                            }
                        }
                    }
                }
                if (dayEvents.isEmpty() && dayMilestones.isEmpty() && dayDeadlines.isEmpty() &&
                    dayActivities.isEmpty() && daySleeps.isEmpty()) {
                    Text(
                        text = stringResource(R.string.planned_activity_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheduleTone.color.copy(alpha = 0.76f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                } else {
                    if (dayEvents.isNotEmpty()) {
                        Text(
                            stringResource(R.string.planned_activity_day_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = scheduleTone.color,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    dayEvents.forEach { ev ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = scheduleTone.container,
                            contentColor = scheduleTone.onContainer
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = ev.title,
                                    modifier = Modifier.fillMaxWidth().clickable { editPlan(ev) },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    color = scheduleTone.onContainer
                                )
                                val rangeLabel = if (ev.allDay) {
                                    stringResource(R.string.calendar_all_day)
                                } else {
                                    val zone = ZoneId.systemDefault()
                                    val start = Instant.ofEpochMilli(ev.startMillis).atZone(zone)
                                    val end = Instant.ofEpochMilli(ev.endMillis).atZone(zone)
                                    val format = DateTimeFormatter.ofPattern("HH:mm")
                                    val extraDays = java.time.temporal.ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate())
                                    val endLabel = if (extraDays > 0) {
                                        stringResource(R.string.planned_activity_end_later, end.format(format), extraDays)
                                    } else end.format(format)
                                    stringResource(
                                        R.string.planned_activity_time_range,
                                        start.format(format),
                                        endLabel,
                                        ((ev.endMillis - ev.startMillis) / 60_000L).coerceAtLeast(1L)
                                    )
                                }
                                Text(rangeLabel, style = MaterialTheme.typography.bodySmall, color = scheduleTone.onContainer)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = {
                                        clipboard.setText(AnnotatedString(ev.title))
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.calendar_title_copied),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            stringResource(R.string.planned_activity_copy_title),
                                            tint = scheduleTone.onContainer
                                        )
                                    }
                                    IconButton(onClick = { editPlan(ev) }) {
                                        Icon(Icons.Default.Edit, stringResource(R.string.planned_activity_edit), tint = scheduleTone.onContainer)
                                    }
                                    ev.taskId?.let { taskId ->
                                        tasks.firstOrNull { it.id == taskId && !it.isDone }
                                    }?.let { task ->
                                        IconButton(onClick = {
                                            selectedDate = null
                                            onOpenTask(task.id)
                                        }) {
                                            Icon(
                                                Icons.Default.Checklist,
                                                stringResource(R.string.deadline_open_task),
                                                tint = scheduleTone.onContainer
                                            )
                                        }
                                        IconButton(onClick = {
                                            selectedDate = null
                                            onStartFocus(task.id)
                                        }) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                stringResource(R.string.task_start_focus),
                                                tint = scheduleTone.onContainer
                                            )
                                        }
                                    }
                                    IconButton(onClick = { viewModel.deleteEvent(ev.id) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            stringResource(R.string.planned_activity_delete),
                                            tint = MaterialTheme.appAccents.urgent.color
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!date.isAfter(LocalDate.now())) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.activity_add_spent),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.appAccents.work.color
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val now = ZonedDateTime.now()
                        val hours = (8..22 step 2).filter { hour ->
                            date.atTime(hour, 0).atZone(ZoneId.systemDefault()).isBefore(now.minusMinutes(1))
                        }
                        items(hours) { hour ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    manualStartMillis = date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    selectedDate = null
                                },
                                label = { Text("%02d:00".format(hour)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.appAccents.work.action,
                                    labelColor = MaterialTheme.appAccents.work.onAction,
                                    selectedContainerColor = MaterialTheme.appAccents.work.color,
                                    selectedLabelColor = MaterialTheme.appAccents.work.onColor
                                )
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val now = ZonedDateTime.now()
                            val start = if (date == now.toLocalDate()) now.minusMinutes(25)
                                else date.atTime(12, 0).atZone(ZoneId.systemDefault())
                            manualStartMillis = start.toInstant().toEpochMilli()
                            selectedDate = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.appAccents.work.color),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.appAccents.work.color
                        )
                    ) {
                        Icon(Icons.Default.Timer, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.activity_add_title))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { editPlan(null, date) },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, scheduleTone.color),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = scheduleTone.color)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = scheduleTone.color,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.planned_activity_create))
                }
                // Keep the primary action above the app navigation bar on devices
                // where a modal sheet and the host Scaffold share one window.
                Spacer(modifier = Modifier.height(104.dp))
            }
        }
    }

    if (manualStartMillis != null || editingActivity != null) {
        ManualActivitySheet(
            initialStartMillis = manualStartMillis,
            editing = editingActivity,
            onDismiss = {
                manualStartMillis = null
                editingActivity = null
            }
        )
    }
}

private fun activitiesOn(records: List<ActivityRecordEntity>, date: LocalDate): List<ActivityRecordEntity> {
    val zone = ZoneId.systemDefault()
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return records.filter { it.startedAt < end && it.effectiveActivityEndMillis() > start }
        .sortedBy(ActivityRecordEntity::startedAt)
}

private fun activityTimeLabel(record: ActivityRecordEntity): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(record.startedAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    val end = Instant.ofEpochMilli(record.endedAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    return "$start–$end · ${record.durationMillis / 60_000L} мин"
}

// =====================================================================
// Ячейка дня
// =====================================================================

private data class MonthDay(val date: LocalDate, val inMonth: Boolean)

private enum class CalendarMarkerKind { DEADLINE, SLEEP }

private data class CalendarMarker(
    val label: String,
    val kind: CalendarMarkerKind
)

private fun buildMonthGrid(month: YearMonth): List<MonthDay> {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    var d = first.minusDays(offset.toLong())
    return List(42) {
        MonthDay(d, d.month == month.month).also { d = d.plusDays(1) }
    }
}

@Composable
private fun DayCell(
    modifier: Modifier = Modifier,
    day: MonthDay,
    isToday: Boolean,
    actualMillis: Long,
    events: List<CalendarEventEntity>,
    markers: List<CalendarMarker>,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val scheduleTone = MaterialTheme.appAccents.schedule
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .padding(1.dp)
            .clip(shape)
            .background(
                scheduleTone.container.copy(
                    alpha = if (day.inMonth) 0.10f else 0.035f
                )
            )
            .border(
                width = if (isToday) 1.2.dp else 0.55.dp,
                color = if (isToday) {
                    scheduleTone.color.copy(alpha = 0.75f)
                } else {
                    scheduleTone.onContainer.copy(
                        alpha = if (day.inMonth) 0.44f else 0.22f
                    )
                },
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        val fontScale = LocalDensity.current.fontScale
        val dateSize = if (maxHeight < 70.dp) 22.dp else 26.dp
        val chipHeight = 16f * fontScale + 2f
        val showActivity = actualMillis > 0 && maxHeight.value >= dateSize.value + chipHeight
        val slots = ((maxHeight.value - dateSize.value - if (showActivity) chipHeight else 0f) / chipHeight)
            .toInt().coerceIn(0, 3)
        // Prioritize deadlines and account for hidden items even on a compact screen.
        val deadlineMarkers = markers.filter { it.kind == CalendarMarkerKind.DEADLINE }
        val otherMarkers = markers.filter { it.kind != CalendarMarkerKind.DEADLINE }
        val shownDeadlines = deadlineMarkers.take(if (events.isEmpty()) slots else (slots - 1).coerceAtLeast(1).coerceAtMost(slots))
        val shownEvents = events.take(slots - shownDeadlines.size)
        val shownOthers = otherMarkers.take(slots - shownDeadlines.size - shownEvents.size)
        val hiddenCount = events.size + markers.size - shownDeadlines.size - shownEvents.size - shownOthers.size
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(dateSize)
                        .clip(CircleShape)
                        .background(if (isToday) scheduleTone.color else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day.date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            isToday -> scheduleTone.onColor
                            day.inMonth -> scheduleTone.onContainer
                            else -> scheduleTone.onContainer.copy(alpha = 0.5f)
                        }
                    )
                }
                if (hiddenCount > 0) {
                    Text(
                        "+$hiddenCount",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        maxLines = 1,
                        color = if (deadlineMarkers.isNotEmpty()) deadlineTone(day.date).color else scheduleTone.color
                    )
                }
            }
            if (showActivity) ActivityDurationChip(actualMillis)
            shownDeadlines.forEach { MarkerChip(marker = it, date = day.date) }
            shownEvents.forEach { EventChip(it.title) }
            shownOthers.forEach { MarkerChip(marker = it, date = day.date) }
        }
    }
}

@Composable
private fun ActivityDurationChip(millis: Long) {
    val tone = MaterialTheme.appAccents.work
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(tone.container)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Timer, null,
            modifier = Modifier.size(9.dp),
            tint = tone.onContainer
        )
        Spacer(Modifier.width(2.dp))
        Text(
            formatDuration(millis),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = tone.onContainer,
            maxLines = 1
        )
    }
}

@Composable
private fun EventChip(title: String) {
    val tone = MaterialTheme.appAccents.schedule
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(tone.container)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
        color = tone.onContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun MarkerChip(marker: CalendarMarker, date: LocalDate) {
    val tone = when (marker.kind) {
        CalendarMarkerKind.DEADLINE -> deadlineTone(date)
        CalendarMarkerKind.SLEEP -> MaterialTheme.appAccents.sleep
    }
    Text(
        text = marker.label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(tone.container)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
        color = tone.onContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun deadlineTone(date: LocalDate): AppAccentTone {
    val today = LocalDate.now()
    return when {
        !date.isAfter(today) -> MaterialTheme.appAccents.urgent
        !date.isAfter(today.plusDays(7)) -> MaterialTheme.appAccents.warning
        else -> MaterialTheme.appAccents.schedule
    }
}

private fun formatDuration(ms: Long): String {
    val t = ms / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    return if (h > 0) "%d:%02d".format(h, m) else "%d:%02d".format(m, t % 60)
}
