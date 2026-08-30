package com.personal.sleepalarm.ui.calendar

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import java.time.LocalTime
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import kotlin.math.abs
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.domain.calculator.CalendarActivityCalculator
import com.personal.sleepalarm.domain.calculator.effectiveActivityEndMillis
import com.personal.sleepalarm.ui.activity.ManualActivitySheet
import com.personal.sleepalarm.ui.theme.ThemedModalBottomSheet
import com.personal.sleepalarm.ui.theme.appAccents
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = viewModel(),
    onBack: (() -> Unit)? = null,
    onOpenTask: (Int) -> Unit = {},
    onStartFocus: (Int) -> Unit = {},
    openEventId: Int? = null,
    openOccurrenceStart: Long? = null,
    openRequestToken: Int = 0,
    modifier: Modifier = Modifier
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val studySessions by viewModel.studySessions.collectAsStateWithLifecycle()
    val actualActivities by viewModel.actualActivities.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val sleepSessions by viewModel.sleepSessions.collectAsStateWithLifecycle()
    val milestones by viewModel.milestones.collectAsStateWithLifecycle()
    val actualByDay = remember(actualActivities) {
        CalendarActivityCalculator.millisByDate(actualActivities, ZoneId.systemDefault())
    }

    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editorDate by remember { mutableStateOf<LocalDate?>(null) }
    var editingEvent by remember { mutableStateOf<CalendarEventEntity?>(null) }
    var manualStartMillis by remember { mutableStateOf<Long?>(null) }
    var editingActivity by remember { mutableStateOf<ActivityRecordEntity?>(null) }

    LaunchedEffect(openEventId, openOccurrenceStart, openRequestToken, events) {
        val event = events.firstOrNull { it.id == openEventId } ?: return@LaunchedEffect
        val occurrence = openOccurrenceStart ?: event.startMillis
        val date = Instant.ofEpochMilli(occurrence).atZone(ZoneId.systemDefault()).toLocalDate()
        month = YearMonth.from(date)
        selectedDate = date
    }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    if (showEditor) {
        EventEditor(
            initial = editingEvent,
            defaultDate = editorDate,
            tasks = tasks.filterNot { it.isDone },
            onBack = {
                showEditor = false
                editingEvent = null
            },
            onSave = { event ->
                if (editingEvent != null) viewModel.updateEvent(event)
                else viewModel.addEvent(event)
                showEditor = false
                editingEvent = null
            }
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
                    Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                }
                Text(
                    text = stringResource(R.string.task_open_calendar),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.Default.ChevronLeft, null, tint = MaterialTheme.appAccents.focus.color)
            }
            Text(
                text = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.appAccents.focus.color)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                tasks.filter { task ->
                                    !task.isDone &&
                                    task.dueAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() == day.date } == true
                                }.take(1).forEach { add("Срок задачи") }
                                sleepSessions.filter { session ->
                                    val wake = session.actualWakeTime ?: session.estimatedWakeTime
                                    Instant.ofEpochMilli(wake).atZone(ZoneId.systemDefault()).toLocalDate() == day.date
                                }.take(1).forEach { add("Сон") }
                                milestones.filter { it.targetDate == day.date.toString() }.take(1).forEach { add("Этап") }
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
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("EEE, dd.MM", Locale.getDefault())),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))

                val dayEvents = eventsOn(events, date)
                val dayActivities = activitiesOn(actualActivities, date)
                val dayDeadlines = tasks.filter { task ->
                    !task.isDone && task.dueAtMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() == date
                    } == true
                }
                val daySleeps = sleepSessions.filter { session ->
                    val wake = session.actualWakeTime ?: session.estimatedWakeTime
                    Instant.ofEpochMilli(wake).atZone(ZoneId.systemDefault()).toLocalDate() == date
                }
                val dayMilestones = milestones.filter { milestone ->
                    milestone.targetDate == date.toString() &&
                        (milestone.taskId == null || tasks.firstOrNull { it.id == milestone.taskId }?.isDone == false)
                }
                if (dayActivities.isNotEmpty()) {
                    Text(
                        stringResource(R.string.activity_history),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.appAccents.focus.color
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
                dayMilestones.forEach { milestone ->
                    Text(
                        "Этап · ${milestone.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appAccents.success.color,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                dayDeadlines.forEach { task ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.appAccents.urgent.container.copy(alpha = 0.82f),
                        contentColor = MaterialTheme.appAccents.urgent.onContainer
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Срок · ${task.primaryLabel()}",
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = { selectedDate = null; onOpenTask(task.id) }) {
                                Icon(Icons.Default.Checklist, "Открыть задачу")
                            }
                            IconButton(onClick = { selectedDate = null; onStartFocus(task.id) }) {
                                Icon(Icons.Default.PlayArrow, "Начать фокус")
                            }
                        }
                    }
                }
                if (dayEvents.isEmpty()) {
                    Text(
                        text = stringResource(R.string.calendar_no_events),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                } else {
                    dayEvents.forEach { ev ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!ev.allDay) {
                                val time = Instant.ofEpochMilli(ev.startMillis)
                                    .atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                                Text(
                                    text = time,
                                    modifier = Modifier.padding(end = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.appAccents.work.color,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = ev.title,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        editingEvent = ev
                                        editorDate = null
                                        selectedDate = null
                                        showEditor = true
                                    },
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(ev.title))
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.calendar_title_copied),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                editingEvent = ev
                                editorDate = null
                                selectedDate = null
                                showEditor = true
                            }) {
                                Icon(Icons.Default.Edit, null,
                                    tint = MaterialTheme.appAccents.work.color)
                            }
                            ev.taskId?.let { taskId -> tasks.firstOrNull { it.id == taskId && !it.isDone } }?.let { task ->
                                IconButton(onClick = {
                                    selectedDate = null
                                    onOpenTask(task.id)
                                }) {
                                    Icon(Icons.Default.Checklist, "Открыть задачу")
                                }
                                IconButton(onClick = {
                                    selectedDate = null
                                    onStartFocus(task.id)
                                }) {
                                    Icon(Icons.Default.PlayArrow, "Начать фокус")
                                }
                            }
                            IconButton(onClick = { viewModel.deleteEvent(ev.id) }) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.appAccents.urgent.color)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.activity_add_spent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.appAccents.work.color
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val now = ZonedDateTime.now()
                    val hours = (8..22 step 2).filter { hour -> date < now.toLocalDate() || hour <= now.hour }
                    items(hours) { hour ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                manualStartMillis = date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                selectedDate = null
                            },
                            label = { Text("%02d:00".format(hour)) }
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        val now = ZonedDateTime.now()
                        val hour = if (date == now.toLocalDate()) now.minusMinutes(25).hour else 12
                        manualStartMillis = date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        selectedDate = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Timer, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.activity_add_title))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        editingEvent = null
                        editorDate = date          // ← дата из календаря
                        selectedDate = null
                        showEditor = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.appAccents.focus.color,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.calendar_add_event))
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
    markers: List<String>,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(1.dp)
            .clip(shape)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = if (day.inMonth) 0.10f else 0.035f
                )
            )
            .border(
                width = if (isToday) 1.2.dp else 0.55.dp,
                color = if (isToday) {
                    MaterialTheme.appAccents.focus.color.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(
                        alpha = if (day.inMonth) 0.44f else 0.22f
                    )
                },
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    if (isToday) MaterialTheme.appAccents.focus.color else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    isToday -> MaterialTheme.appAccents.focus.onColor
                    day.inMonth -> MaterialTheme.colorScheme.onBackground
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )
        }

        if (actualMillis > 0) {
            ActivityDurationChip(actualMillis)
        }

        events.take(3).forEach { ev -> EventChip(ev.title) }
        markers.take((3 - events.size).coerceAtLeast(0)).forEach { EventChip(it) }
        if (events.size + markers.size > 3) {
            Text(
                "+${events.size + markers.size - 3}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            tint = tone.color
        )
        Spacer(Modifier.width(2.dp))
        Text(
            formatDuration(millis),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = tone.color,
            maxLines = 1
        )
    }
}

@Composable
private fun EventChip(title: String) {
    val tone = MaterialTheme.appAccents.focus
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

private fun formatDuration(ms: Long): String {
    val t = ms / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    return if (h > 0) "%d:%02d".format(h, m) else "%d:%02d".format(m, t % 60)
}

// =====================================================================
// Редактор события
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditor(
    initial: CalendarEventEntity?,
    defaultDate: LocalDate?,
    tasks: List<TaskEntity>,
    onBack: () -> Unit,
    onSave: (CalendarEventEntity) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val nowZ = ZonedDateTime.now(zone)
    // Дата: из события → из выбранного дня календаря → сегодня
    val baseDate = initial?.let { Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDate() }
        ?: defaultDate
        ?: nowZ.toLocalDate()
    val defaultStart = if (baseDate == nowZ.toLocalDate()) roundUpDateTime(nowZ)
    else baseDate.atTime(9, 0).atZone(zone)
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var allDay by remember { mutableStateOf(initial?.allDay ?: false) }
    var start by remember {
        mutableStateOf(
            initial?.let { Instant.ofEpochMilli(it.startMillis).atZone(zone) } ?: defaultStart
        )
    }
    var end by remember {
        mutableStateOf(
            initial?.let { Instant.ofEpochMilli(it.endMillis).atZone(zone) }
                ?: defaultStart.plusHours(1)
        )
    }
    var repeat by remember { mutableStateOf(initial?.repeatRule ?: "none") }
    // Новый event получает удобный default, но существующий event без
    // напоминания должен оставаться без него после обычного редактирования.
    var reminder by remember {
        mutableStateOf<Int?>(if (initial == null) 30 else initial.reminderMinutes)
    }
    var selectedTaskId by remember { mutableStateOf(initial?.taskId) }

    var showStart by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    var showRepeat by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }

    val dtf = DateTimeFormatter.ofPattern("EEE, dd.MM HH:mm", Locale.getDefault())
    val repeatLabel = when (repeat) {
        "daily" -> stringResource(R.string.calendar_repeat_daily)
        "weekly" -> stringResource(R.string.calendar_repeat_weekly)
        else -> stringResource(R.string.calendar_repeat_none)
    }
    val reminderLabel = when (reminder) {
        null -> stringResource(R.string.calendar_reminder_none)
        0 -> stringResource(R.string.calendar_reminder_on_time)
        else -> stringResource(R.string.calendar_reminder_minutes, reminder ?: 0)
    }

    val startMillis = start.toInstant().toEpochMilli()
    val endMillis = if (allDay) {
        start.toLocalDate().atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
    } else {
        end.toInstant().toEpochMilli()
    }
    val isRangeValid = endMillis > startMillis
    val saveEvent = {
        if (isRangeValid) {
            onSave(
                CalendarEventEntity(
                    id = initial?.id ?: 0,
                    title = title.trim(),
                    startMillis = startMillis,
                    endMillis = endMillis,
                    allDay = allDay,
                    repeatRule = repeat,
                    reminderMinutes = reminder,
                    taskId = selectedTaskId,
                    projectId = tasks.firstOrNull { it.id == selectedTaskId }?.projectId,
                    eventKind = "PLANNED"
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.appAccents.work.color
                )
            }
        }

        OutlinedTextField(
            placeholder = { Text(stringResource(R.string.calendar_title_placeholder)) },
            value = title, onValueChange = { title = it },
            label = { Text(stringResource(R.string.calendar_field_title)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            singleLine = true
        )

        Text("Связанная задача", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selectedTaskId == null,
                    onClick = { selectedTaskId = null },
                    label = { Text("Без задачи") }
                )
            }
            items(tasks, key = { it.id }) { task ->
                FilterChip(
                    selected = selectedTaskId == task.id,
                    onClick = { selectedTaskId = task.id },
                    label = {
                        Text(
                            task.primaryLabel(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        EditorCard {
            SwitchRow(stringResource(R.string.calendar_all_day), allDay) { allDay = it }
            EditorDivider()
            EditorRow(stringResource(R.string.calendar_start), start.format(dtf)) { showStart = true }
            if (!allDay) {
                EditorRow(stringResource(R.string.calendar_end), end.format(dtf)) { showEnd = true }
            }
            EditorDivider()
            EditorRow(stringResource(R.string.calendar_repeat), repeatLabel, chevron = true) {
                showRepeat = true
            }
        }

        EditorCard {
            EditorRow(stringResource(R.string.calendar_reminder), reminderLabel, chevron = true) {
                showReminder = true
            }
        }

        if (!isRangeValid) {
            Text(
                text = stringResource(R.string.calendar_error_end_after_start),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appAccents.warning.color,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Button(
            onClick = saveEvent,
            enabled = title.isNotBlank() && isRangeValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                text = stringResource(R.string.calendar_save_event),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(8.dp))
    }

    if (showStart) {
        TimeSheet(initial = start, onApply = { nt ->
            start = nt
            if (end.isBefore(nt)) end = nt.plusHours(1)
        }, onDismiss = { showStart = false })
    }
    if (showEnd) {
        TimeSheet(initial = end, onApply = { end = it }, onDismiss = { showEnd = false })
    }
    if (showRepeat) {
        OptionSheet(stringResource(R.string.calendar_repeat),
            listOf(
                "none" to stringResource(R.string.calendar_repeat_none),
                "daily" to stringResource(R.string.calendar_repeat_daily),
                "weekly" to stringResource(R.string.calendar_repeat_weekly)
            ),
            repeat, { repeat = it; showRepeat = false }, { showRepeat = false })
    }
    if (showReminder) {
        OptionSheet(stringResource(R.string.calendar_reminder),
            listOf(
                null to stringResource(R.string.calendar_reminder_none),
                0 to stringResource(R.string.calendar_reminder_on_time),
                5 to stringResource(R.string.calendar_reminder_minutes, 5),
                10 to stringResource(R.string.calendar_reminder_minutes, 10),
                15 to stringResource(R.string.calendar_reminder_minutes, 15),
                30 to stringResource(R.string.calendar_reminder_minutes, 30),
                60 to stringResource(R.string.calendar_reminder_minutes, 60),
                120 to stringResource(R.string.calendar_reminder_hours, 2)
            ),
            reminder, { reminder = it; showReminder = false }, { showReminder = false })
    }
}

// =====================================================================
// Строки-карточки
// =====================================================================

@Composable
private fun EditorCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f))
            .border(
                width = 0.75.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(horizontal = 18.dp),
        content = content
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun EditorDivider() {
    Box(
        Modifier.fillMaxWidth().height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    )
}

@Composable
private fun EditorRow(label: String, value: String, chevron: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (chevron) {
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

// =====================================================================
// КОЛЁСИКИ (wheel picker)
// =====================================================================


private const val WHEEL_ITEM_HEIGHT = 52
private const val WHEEL_VISIBLE_COUNT = 5
private const val WHEEL_PAD_ITEMS = (WHEEL_VISIBLE_COUNT - 1) / 2 // невидимые распорки

@Composable
private fun NumberWheel(
    items: List<String>,
    externalIndex: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val itemHeightPx = with(density) { WHEEL_ITEM_HEIGHT.dp.toPx() }
    val containerHeight = (WHEEL_ITEM_HEIGHT * WHEEL_VISIBLE_COUNT).dp
    val containerCenterPx = with(density) { containerHeight.toPx() } / 2f

    val listState = rememberLazyListState()
    var initialized by remember { mutableStateOf(false) }

    // === Внешняя синхронизация ===
    // scrollToItem(N) ставит N-й элемент списка СВЕРХУ,
    // а с учётом распорки N-е реальное значение оказывается В ЦЕНТРЕ.
    LaunchedEffect(externalIndex) {
        val target = externalIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        if (!initialized) {
            listState.scrollToItem(target)          // мгновенно, без прокрутки с нуля
            initialized = true
        } else {
            listState.animateScrollToItem(target)   // плавно
        }
    }

    // === Выбранный = элемент, ближайший к центру контейнера ===
    val selectedIndex by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
                .minByOrNull { abs(it.offset + it.size / 2f - containerCenterPx) }
                ?.index
                ?.minus(WHEEL_PAD_ITEMS)
                ?.coerceIn(0, (items.size - 1).coerceAtLeast(0))
                ?: 0
        }
    }

    // === После отпускания пальца: точный snap в центр + уведомление ===
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val target = selectedIndex
            try {
                listState.animateScrollToItem(target)
            } catch (_: CancellationException) {
            }
            onIndexChange(target)
        }
    }

    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    Box(
        modifier = modifier
            .height(containerHeight),
        contentAlignment = Alignment.Center
    ) {
        // Линии-разделители вокруг среднего ряда
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(lineColor)
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(lineColor)
            )
        }

        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            modifier = Modifier.fillMaxSize()
        ) {
            // Невидимые распорки сверху и снизу — чтобы крайние значения
            // могли встать ровно в центр
            items(WHEEL_PAD_ITEMS) {
                Spacer(Modifier.height(WHEEL_ITEM_HEIGHT.dp))
            }

            items(items.size) { index ->
                val itemInfo = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == index + WHEEL_PAD_ITEMS }
                val itemCenter = (itemInfo?.offset ?: 0) +
                        (itemInfo?.size ?: WHEEL_ITEM_HEIGHT) / 2f
                val fraction = ((itemCenter - containerCenterPx) / itemHeightPx)
                    .coerceIn(-2f, 2f)

                val scale = (1f - abs(fraction) * 0.15f).coerceIn(0.7f, 1f)
                // Полностью прозрачно на краях, непрозрачно только в центре
                val itemAlpha = (1f - abs(fraction) / 1.8f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WHEEL_ITEM_HEIGHT.dp)
                        .graphicsLayer {
                            rotationX = fraction * 35f
                            alpha = itemAlpha
                        }
                        .scale(scale),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index],
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 26.sp,
                            fontWeight = if (abs(fraction) < 0.5f) FontWeight.SemiBold
                            else FontWeight.Normal
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                }
            }

            items(WHEEL_PAD_ITEMS) {
                Spacer(Modifier.height(WHEEL_ITEM_HEIGHT.dp))
            }
        }
    }
}

/**

 * Округляет минуты вверх до ближайшего кратного 5.
 */
private fun roundUpTo5(minutes: Int, hour: Int): Pair<Int, Int> {
    val remainder = minutes % 5
    val rounded = if (remainder == 0) minutes else minutes + (5 - remainder)
    return if (rounded >= 60) {
        0 to (hour + 1)
    } else {
        rounded to hour
    }
}

private fun roundUpDateTime(dt: ZonedDateTime): ZonedDateTime {
    val (roundedMin, hourDelta) = roundUpTo5(dt.minute, 0)
    var result = dt.withMinute(roundedMin).withSecond(0).withNano(0)
    if (hourDelta > 0) {
        result = result.plusHours(hourDelta.toLong())
    }
    return result
}

/**
 * Шторка только со временем (час + минуты).
 * Дата фиксирована — она уже выбрана в календаре.
 * Сохранение автоматическое при вращении колёс.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSheet(
    initial: ZonedDateTime,
    onApply: (ZonedDateTime) -> Unit,
    onDismiss: () -> Unit
) {
    val zone = ZoneId.systemDefault()
    val now = ZonedDateTime.now(zone)
    val date = initial.toLocalDate()
    val isToday = date == now.toLocalDate()

    // Если сегодня — не даём выбрать прошедшее время
    val safeInitial = if (isToday && initial.isBefore(now)) roundUpDateTime(now) else initial

    var hour by remember { mutableIntStateOf(safeInitial.hour) }
    var minute by remember { mutableIntStateOf(safeInitial.minute) }

    val minHour = if (isToday) now.hour else 0
    val isCurrentHour = isToday && hour == now.hour
    val minMinuteValue = if (isCurrentHour) {
        val (rounded, _) = roundUpTo5(now.minute, 0)
        rounded
    } else 0

    LaunchedEffect(hour) {
        if (isCurrentHour && minute < minMinuteValue) minute = minMinuteValue
    }

    val hourRange = minHour..23
    val minuteRange = (minMinuteValue..55 step 5).toList()

    // Автосохранение при каждом изменении
    LaunchedEffect(hour, minute) {
        val m = if (isCurrentHour) minute.coerceAtLeast(minMinuteValue) else minute
        onApply(ZonedDateTime.of(date, LocalTime.of(hour, m), zone))
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = available
        }
    }

    ThemedModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {}
    ) {
        Column(

            modifier = Modifier

                .fillMaxWidth()
                .nestedScroll(nestedScrollConnection)
                // Гасим вертикальные свайпы в зонах без скролла (между колёсиками),
                // чтобы шторка не закрывалась. Колёсики крутятся как раньше.
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                    }
                }
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "=^..^=",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 16.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.appAccents.focus.color
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                NumberWheel(
                    items = hourRange.map { "%02d".format(it) },
                    externalIndex = (hour - minHour).coerceAtLeast(0),
                    onIndexChange = { idx -> hour = minHour + idx },
                    modifier = Modifier.weight(1f)
                )
                NumberWheel(
                    items = minuteRange.map { "%02d".format(it) },
                    externalIndex = ((minute - minMinuteValue) / 5).coerceAtLeast(0),
                    onIndexChange = { idx -> minute = minMinuteValue + idx * 5 },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}


@Composable
private fun WheelLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )
}

// =====================================================================
// Option sheet (для повторения и напоминания)
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionSheet(
    title: String,
    options: List<Pair<T, String>>,
    current: T,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit
) {
    ThemedModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(12.dp))
            options.forEach { (v, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(v) }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f))
                    if (v == current) {
                        Text("✓", color = MaterialTheme.appAccents.success.color,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
