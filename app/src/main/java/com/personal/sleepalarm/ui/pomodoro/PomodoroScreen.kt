package com.personal.sleepalarm.ui.pomodoro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.ui.components.NumberWheel
import com.personal.sleepalarm.ui.theme.ThemedModalBottomSheet
import kotlinx.coroutines.delay

val SUBJECT_COLORS = listOf(
    0xFF9E9E9E, 0xFF9575CD, 0xFF2E7D32, 0xFFE57373,
    0xFF4DB6AC, 0xFFFFB74D, 0xFF64B5F6, 0xFFF06292
).map { it.toInt() }

private data class ActivityItemUi(
    val id: Int,
    val name: String,
    val color: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(
    viewModel: PomodoroViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val workTasks by viewModel.workTasks.collectAsStateWithLifecycle()
    val otherActivities by viewModel.otherActivities.collectAsStateWithLifecycle()
    val currentDaySessions by viewModel.currentDayFocusSessions.collectAsStateWithLifecycle()
    val currentDayRange by viewModel.currentDayRange.collectAsStateWithLifecycle()
    val remaining by viewModel.remaining.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val activityType by viewModel.activityType.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedItemId.collectAsStateWithLifecycle()
    val focusDuration by viewModel.focusDuration.collectAsStateWithLifecycle()
    val breakDuration by viewModel.breakDuration.collectAsStateWithLifecycle()

    val activityItems = remember(activityType, subjects, workTasks, otherActivities) {
        when (activityType) {
            FocusActivityType.STUDY -> subjects.map { ActivityItemUi(it.id, it.name, it.color) }
            FocusActivityType.WORK -> workTasks.map {
                ActivityItemUi(it.id, it.title, 0xFF5C6BC0.toInt())
            }
            FocusActivityType.OTHER -> otherActivities.map {
                ActivityItemUi(it.id, it.name, it.color)
            }
        }
    }
    val totalsByItem = remember(currentDaySessions, activityType, currentDayRange) {
        val (from, to) = currentDayRange
        currentDaySessions.asSequence()
            .filter { it.activityType == activityType }
            .mapNotNull { session ->
                val itemId = when (activityType) {
                    FocusActivityType.STUDY -> session.subjectId
                    FocusActivityType.WORK -> session.taskId
                    FocusActivityType.OTHER -> session.otherActivityId
                } ?: return@mapNotNull null
                val actualEnd = session.startedAt + session.actualDurationMillis
                val end = minOf(session.completedAt ?: actualEnd, actualEnd, to)
                val start = maxOf(session.startedAt, from)
                itemId to (end - start).coerceAtLeast(0L)
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, durations) -> durations.sum() }
    }
    val totalLast24Hours = totalsByItem.values.sum()
    val currentItemName = activityItems.firstOrNull { it.id == selectedId }?.name

    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ActivityItemUi?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showActivityPanel by remember { mutableStateOf(false) }

    val modeLabel = when (mode) {
        TimerMode.IDLE -> stringResource(R.string.pomodoro_mode_idle)
        TimerMode.FOCUS -> stringResource(R.string.pomodoro_mode_focus)
        TimerMode.FOCUS_PAUSED -> stringResource(R.string.pomodoro_mode_focus_paused)
        TimerMode.BREAK -> stringResource(R.string.pomodoro_mode_break)
        TimerMode.BREAK_PAUSED -> stringResource(R.string.pomodoro_mode_break_paused)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
                .padding(top = 40.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(vertical = 20.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BigCatFigure(
                        mode = mode,
                        subjectName = currentItemName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clickable { showSettings = true }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = formatClock(remaining),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )

                    if (mode != TimerMode.IDLE) {
                        Text(
                            text = currentItemName.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = modeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = when (activityType) {
                    FocusActivityType.STUDY -> stringResource(R.string.pomodoro_subjects)
                    FocusActivityType.WORK -> stringResource(R.string.pomodoro_tasks)
                    FocusActivityType.OTHER -> stringResource(R.string.pomodoro_other_items)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.pomodoro_total_activity_day),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = formatDuration(totalLast24Hours),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(activityItems, key = { it.id }) { item ->
                    val isCurrentItem = item.id == selectedId
                    val rowMode = if (!isCurrentItem) TimerMode.IDLE else mode
                    ActivityItemRow(
                        item = item,
                        durationMillis = totalsByItem[item.id] ?: 0L,
                        currentMode = rowMode,
                        onPlay = {
                            when {
                                mode == TimerMode.FOCUS && isCurrentItem -> viewModel.toggle()
                                (mode == TimerMode.BREAK || mode == TimerMode.BREAK_PAUSED) && isCurrentItem ->
                                    viewModel.endBreakToIdle()
                                else -> viewModel.start(item.id, item.name)
                            }
                        },
                        onEdit = {
                            editing = item
                            showEditor = true
                        }
                    )
                }

                if (activityItems.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.pomodoro_empty_category),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Text(
            text = "^+^",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clickable {
                    editing = null
                    showEditor = true
                },
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        )

        AnimatedVisibility(
            visible = showActivityPanel,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 8.dp)
        ) {
            ActivityTypePanel(
                selected = activityType,
                enabled = mode == TimerMode.IDLE,
                onDismiss = { showActivityPanel = false },
                onSelect = {
                    viewModel.selectActivityType(it)
                    showActivityPanel = false
                }
            )
        }

        if (!showActivityPanel) {
            ActivityDrawerHandle(
                onOpen = { showActivityPanel = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 8.dp)
            )
        }
    }

    if (showEditor) {
        ActivityItemEditorDialog(
            initial = editing,
            activityType = activityType,
            onSave = { name, color ->
                val current = editing
                when (activityType) {
                    FocusActivityType.STUDY -> if (current == null) {
                        viewModel.addSubject(name, color)
                    } else {
                        subjects.firstOrNull { it.id == current.id }?.let {
                            viewModel.updateSubject(it.copy(name = name, color = color))
                        }
                    }
                    FocusActivityType.WORK -> if (current == null) {
                        viewModel.addWorkTask(name)
                    } else {
                        workTasks.firstOrNull { it.id == current.id }?.let {
                            viewModel.updateWorkTask(it.copy(title = name))
                        }
                    }
                    FocusActivityType.OTHER -> if (current == null) {
                        viewModel.addOtherActivity(name, color)
                    } else {
                        otherActivities.firstOrNull { it.id == current.id }?.let {
                            viewModel.updateOtherActivity(it.copy(name = name, color = color))
                        }
                    }
                }
                showEditor = false
            },
            onDelete = editing?.let { item ->
                {
                    when (activityType) {
                        FocusActivityType.STUDY -> viewModel.deleteSubject(item.id)
                        FocusActivityType.WORK -> viewModel.deleteWorkTask(item.id)
                        FocusActivityType.OTHER -> viewModel.deleteOtherActivity(item.id)
                    }
                    showEditor = false
                }
            },
            onDismiss = { showEditor = false }
        )
    }

    if (showSettings) {
        TimerSettingsSheet(
            focusDurationMinutes = focusDuration / 60000,
            breakDurationMinutes = breakDuration / 60000,
            onFocusDurationChange = { viewModel.setFocusDuration(it) },
            onBreakDurationChange = { viewModel.setBreakDuration(it) },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun ActivityDrawerHandle(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragX by remember { mutableFloatStateOf(0f) }
    val description = stringResource(R.string.pomodoro_activity_arrow_description)

    Box(
        modifier = modifier
            .size(width = 34.dp, height = 46.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragX = 0f },
                    onDragEnd = {
                        if (dragX < -24f) onOpen()
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f }
                ) { change, amount ->
                    change.consume()
                    dragX += amount.x
                }
            }
            .clickable(onClick = onOpen),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "‹",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            fontSize = 29.sp,
            fontWeight = FontWeight.Light
        )
    }
}

@Composable
private fun ActivityTypePanel(
    selected: FocusActivityType,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSelect: (FocusActivityType) -> Unit
) {
    Column(
        modifier = Modifier
            .width(216.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.pomodoro_activity_question),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        FocusActivityType.entries.forEach { type ->
            val label = when (type) {
                FocusActivityType.STUDY -> stringResource(R.string.pomodoro_activity_study)
                FocusActivityType.WORK -> stringResource(R.string.pomodoro_activity_work)
                FocusActivityType.OTHER -> stringResource(R.string.pomodoro_activity_other)
            }
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                enabled = enabled,
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (!enabled) {
            Text(
                text = stringResource(R.string.pomodoro_activity_change_after_timer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerSettingsSheet(
    focusDurationMinutes: Long,
    breakDurationMinutes: Long,
    onFocusDurationChange: (Long) -> Unit,
    onBreakDurationChange: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val focusOptions = remember { (5..180 step 5).toList() }
    val breakOptions = remember { (1..30).toList() }

    var focusMinutes by remember { mutableIntStateOf(focusDurationMinutes.toInt()) }
    var breakMinutes by remember { mutableIntStateOf(breakDurationMinutes.toInt()) }

    LaunchedEffect(focusMinutes) {
        onFocusDurationChange(focusMinutes.toLong())
    }
    LaunchedEffect(breakMinutes) {
        onBreakDurationChange(breakMinutes.toLong())
    }

    ThemedModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "=^..^=",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 16.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    NumberWheel(
                        items = focusOptions.map { "$it" },
                        externalIndex = focusOptions.indexOf(focusMinutes).coerceAtLeast(0),
                        onIndexChange = { focusMinutes = focusOptions[it] },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(R.string.pomodoro_focus),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(R.string.minutes_format, focusMinutes),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    NumberWheel(
                        items = breakOptions.map { "$it" },
                        externalIndex = breakOptions.indexOf(breakMinutes).coerceAtLeast(0),
                        onIndexChange = { breakMinutes = breakOptions[it] },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(R.string.pomodoro_break),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        stringResource(R.string.minutes_format, breakMinutes),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun CatFigure(
    mode: TimerMode,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val text = when (mode) {
        TimerMode.FOCUS, TimerMode.FOCUS_PAUSED ->
            " /\\_/\\\n( o.o )\n /| |\\"
        TimerMode.BREAK, TimerMode.BREAK_PAUSED ->
            "  _  _\n ( =.= )\n /| |\\\n(_| |_)~"
        TimerMode.IDLE ->
            " /\\_/\\\n( -.- ) zZ\n \\___/"
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold
        ),
        color = tint,
        maxLines = 5,
        modifier = modifier
    )
}

@Composable
private fun ActivityItemRow(
    item: ActivityItemUi,
    durationMillis: Long,
    currentMode: TimerMode,
    onPlay: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            CatFigure(mode = currentMode, tint = Color(item.color))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = formatDuration(durationMillis),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        IconButton(onClick = onEdit) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActivityItemEditorDialog(
    initial: ActivityItemUi?,
    activityType: FocusActivityType,
    onSave: (String, Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableStateOf(initial?.color ?: SUBJECT_COLORS.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when (activityType) {
                        FocusActivityType.STUDY -> if (initial == null) {
                            R.string.pomodoro_new_subject
                        } else {
                            R.string.pomodoro_subject
                        }
                        FocusActivityType.WORK -> if (initial == null) {
                            R.string.pomodoro_new_task
                        } else {
                            R.string.pomodoro_task
                        }
                        FocusActivityType.OTHER -> if (initial == null) {
                            R.string.pomodoro_new_other
                        } else {
                            R.string.pomodoro_other_item
                        }
                    }
                )
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.library_field_title)) },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (activityType != FocusActivityType.WORK) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SUBJECT_COLORS.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .then(
                                        if (c == color) Modifier.background(
                                            MaterialTheme.colorScheme.onBackground,
                                            CircleShape
                                        ) else Modifier
                                    )
                            ) {
                                IconButton(onClick = { color = c }, modifier = Modifier.size(28.dp)) { }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, color) }
            ) { Text(stringResource(R.string.library_save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.library_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}

private fun formatClock(millis: Long): String {
    val total = millis / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun BigCatFigure(
    mode: TimerMode,
    subjectName: String?,
    modifier: Modifier = Modifier
) {
    val isFocusing = mode == TimerMode.FOCUS || mode == TimerMode.FOCUS_PAUSED
    val isBreak = mode == TimerMode.BREAK || mode == TimerMode.BREAK_PAUSED

    // Only the large figure is sized responsively. Its animation frames keep
    // the original renderer and alignment, so switching a frame cannot move
    // the cat's visual anchor.
    BoxWithConstraints(modifier = modifier) {
        val isSleeping = !isFocusing && !isBreak
        val catModifier = Modifier.fillMaxSize()
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val referenceText = remember(isSleeping) {
            if (isSleeping) {
                centerCat(SLEEPING_CAT_SIZE_REFERENCE.trimIndent(), axis = 3f)
            } else {
                normalizeAnimatedCatFrame(ANIMATED_CAT_SIZE_REFERENCE.trimIndent())
            }
        }
        val lineHeightMultiplier = if (isSleeping) 58f / 52f else 46f / 40f
        val referenceStyle = MaterialTheme.typography.displayLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = BIG_CAT_MEASUREMENT_FONT_SP.sp,
            lineHeight = (BIG_CAT_MEASUREMENT_FONT_SP * lineHeightMultiplier).sp,
            fontWeight = FontWeight.Bold
        )
        val catFontSizeSp = remember(
            referenceText,
            referenceStyle,
            maxWidth,
            maxHeight,
            isSleeping,
            textMeasurer
        ) {
            val lineCount = referenceText.count { it == '\n' } + 1
            val measured = textMeasurer.measure(
                text = AnnotatedString(referenceText),
                style = referenceStyle,
                softWrap = false,
                overflow = TextOverflow.Clip,
                maxLines = lineCount,
                constraints = Constraints()
            )
            val scale = calculateMeasuredBigCatScale(
                measuredWidthPx = measured.size.width.toFloat(),
                measuredHeightPx = measured.size.height.toFloat(),
                availableWidthPx = with(density) { maxWidth.toPx() },
                availableHeightPx = with(density) { maxHeight.toPx() },
                animationReserve = if (isSleeping) 1.06f else 1f
            )
            BIG_CAT_MEASUREMENT_FONT_SP * scale
        }
        val glyphStyle = MaterialTheme.typography.displayLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = catFontSizeSp.sp,
            fontWeight = FontWeight.Bold
        )
        val catCharacterWidthDp = remember(
            catFontSizeSp,
            textMeasurer,
            density,
            glyphStyle
        ) {
            val glyph = textMeasurer.measure(
                text = AnnotatedString("M"),
                style = glyphStyle,
                softWrap = false,
                maxLines = 1,
                constraints = Constraints()
            )
            with(density) { glyph.size.width.toDp().value }
        }
        when {
            isFocusing || isBreak -> {
                if (isFocusing) {
                    AwakeCat(catModifier, catFontSizeSp, catCharacterWidthDp)
                } else {
                    PlayingCat(catModifier, catFontSizeSp, catCharacterWidthDp)
                }
            }
            else -> SleepingCat(
                modifier = catModifier,
                catFontSizeSp = catFontSizeSp
            )
        }
    }
}


@Composable
private fun SleepingCat(modifier: Modifier, catFontSizeSp: Float) {
    val catText = """
 /\\_/\    
( -.- )    
 > ^ <     
""".trimIndent()

    val infiniteTransition = rememberInfiniteTransition(label = "sleep")
    val breathProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    // === Переливающаяся растущая z ===
    val c1 = MaterialTheme.colorScheme.primary
    val c2 = MaterialTheme.colorScheme.tertiary

    val zProgress = remember { Animatable(0f) }
    var zBig by remember { mutableStateOf<Boolean>(false) }
    var zBase by remember { mutableFloatStateOf(16f) }
    var zAmp by remember { mutableFloatStateOf(1.0f) }

    LaunchedEffect(Unit) {
        while (true) {
            zProgress.snapTo(0f)
            zProgress.animateTo(1f, tween(3200, easing = LinearEasing))
            delay(kotlin.random.Random.nextLong(4000L, 10001L))
            zBig = !zBig
            zBase = if (zBase >= 30f) 16f else zBase + 3f
            zAmp = 0.85f + kotlin.random.Random.nextFloat() * 0.3f
        }
    }

    val p = zProgress.value
    val smoothP = p * p * (3f - 2f * p)
    val fadeIn = (p / 0.12f).coerceIn(0f, 1f)
    val fadeOut = ((1f - p) / 0.55f).coerceIn(0f, 1f)
    val zAlpha = minOf(fadeIn, fadeOut)
    val zScale = 0.78f + smoothP * 0.35f
    // Сон всегда «уходит» от правой стороны головы: так буква не пересекает мордочку и уши.
    val zX = 44.dp + (38.dp * zAmp) * smoothP
    val zY = 12.dp - 110.dp * smoothP
    val zColor = if (p < 0.5f) lerp(c1, c2, p * 2f)
    else lerp(c2, c1, (p - 0.5f) * 2f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = centerCat(catText, axis = 3f),
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = catFontSizeSp.sp,
                lineHeight = (catFontSizeSp * (58f / 52f)).sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleX = 1f + breathProgress * 0.008f
                scaleY = 1f + breathProgress * 0.024f
            }
        )

        // Одна z за раз
        Text(
            text = if (zBig) "Z" else "z",
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = zBase.sp
            ),
            color = zColor,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = zX, y = zY)
                .graphicsLayer {
                    alpha = zAlpha
                    scaleX = zScale
                    scaleY = zScale
                }
        )
    }
}


@Composable
private fun AwakeCat(
    modifier: Modifier,
    catFontSizeSp: Float,
    catCharacterWidthDp: Float
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
    // ===== БАЗОВЫЕ КАДРЫ (медленное виляние хвостом) =====
    val tailUp = """
 /\\_/\    
( o.o )   
 > ^ < \   
(_| |_)/  
"""
    val tailMid = """
 /\\_/\    
( o.o )    
 > ^ <  /  
(_| |_)/  
"""
    val tailDown = """
 /\\_/\    
( _._ )    
 > ^ <  /  
(_| |_)/  
"""
    val blink = """
 /\\_/\    
( -.- )    
 >/| < \   
(_| |_)/  
"""
    val readPage = """
 /\\_/\
( o.o )
 >[=]< /
(_| |_)/
"""
    val readDown = """
 /\\_/\
( ._. )
 >[|]< /
(_| |_)/
"""
    val turnPage = """
 /\\_/\
( o.o )
 >[>]< /
(_| |_)/
"""

    // Смотрит влево
    val lookLeft = """
 /\\_/\    
(o.o  )    
 > ^ <  /  
(_| |_)/  
"""

    // Смотрит вправо
    val lookRight = """
 /\\_/\    
(  o.o)    
 > ^ <  /  
(_| |_)/  
"""

    // Чешет за ухом
    val scratchEar1 = """
 /\\_/\    
( o.o )    
 >/| <  /  
(_| |_)/  
"""
    val scratchEar2 = """
 /\\_/\    
( o.o )    
 > |\<  /
(_| |_)/  
"""

    // Облизывается (язык высовывается)
    val lick1 = """
 /\\_/\    
( o.o )    
 > ^ <  /  
(_| |_)/  
"""
    val lick2 = """
 /\\_/\    
( o.o )    
 > p <  /  
(_| |_)/  
"""
    val lick3 = """
 /\\_/\    
( o.o )    
 > ^ <  /  
(_| |_)/  
"""

    // Потягивается (вытягивается вперёд)
    val stretch1 = """
 /\\_/\    
( o.o )    
 > ^ <  /  
(_| |_)/  
"""
    val stretch2 = """
 /\\_/\        
( o.o )        
 > ^ <   /  
(_| |_)_/      
"""
    val stretch3 = """
 /\\_/\    
( o.o )    
 > ^ <  /  
(_| |_)/  
"""

    var currentText by remember { mutableStateOf(tailMid) }
    val horizontalMotion = remember { Animatable(0f) }
    val maxTravelX = calculateCatHorizontalTravel(
        sceneWidthDp = maxWidth.value,
        visibleHalfWidthDp = catCharacterWidthDp * 4.5f
    )

    LaunchedEffect(maxTravelX) {
        var nextRoutineAt = System.currentTimeMillis() +
            kotlin.random.Random.nextLong(18_000L, 30_001L)
        var nextRoamAt = System.currentTimeMillis() +
            kotlin.random.Random.nextLong(35_000L, 55_001L)
        var lastRoutine = -1
        var roamIndex = 2
        var roamDirection = if (kotlin.random.Random.nextBoolean()) 1 else -1
        while (true) {
            // Рабочий кот остаётся спокойным: один медленный цикл хвоста и
            // редкое одиночное моргание не перетягивают внимание с таймера.
            currentText = tailMid
            delay(2_600)
            currentText = tailUp
            delay(1_700)
            currentText = tailMid
            delay(2_200)
            currentText = tailDown
            delay(1_700)
            currentText = tailMid
            delay(2_800)
            currentText = blink
            delay(180)
            currentText = tailMid
            delay(1_800)

            val now = System.currentTimeMillis()
            if (now >= nextRoamAt && maxTravelX > 0f) {
                val roamStops = floatArrayOf(-1f, -0.55f, 0f, 0.55f, 1f)
                roamIndex += roamDirection
                if (roamIndex == 0 || roamIndex == roamStops.lastIndex) {
                    roamDirection *= -1
                }
                val walkStart = horizontalMotion.value
                val walkTarget = roamStops[roamIndex] * maxTravelX
                repeat(4) { step ->
                    currentText = if (step % 2 == 0) tailUp else tailDown
                    horizontalMotion.animateTo(
                        targetValue = walkStart + (walkTarget - walkStart) * (step + 1) / 4f,
                        animationSpec = tween(
                            durationMillis = kotlin.random.Random.nextInt(650, 901),
                            easing = FastOutSlowInEasing
                        )
                    )
                }
                currentText = tailMid
                nextRoamAt = System.currentTimeMillis() +
                    kotlin.random.Random.nextLong(35_000L, 65_001L)
            }

            if (now >= nextRoutineAt) {
                nextRoutineAt = System.currentTimeMillis() +
                    kotlin.random.Random.nextLong(28_000L, 52_001L)
                var routine: Int
                do {
                    routine = kotlin.random.Random.nextInt(6)
                } while (routine == lastRoutine)
                lastRoutine = routine
                when (routine) {
                        0 -> { // Ненадолго проверяет обстановку
                            currentText = lookLeft
                            delay(1_400)
                            currentText = tailMid
                            delay(700)
                            currentText = lookRight
                            delay(1_400)
                            currentText = tailMid
                            delay(900)
                        }
                        1 -> { // Спокойно читает и переворачивает страницу
                            currentText = readPage
                            delay(1_800)
                            currentText = readDown
                            delay(1_600)
                            currentText = turnPage
                            delay(900)
                            currentText = readPage
                            delay(1_400)
                            currentText = tailMid
                        }
                        2 -> { // Тихо двигает лапой
                            currentText = scratchEar1
                            delay(650)
                            currentText = scratchEar2
                            delay(650)
                            currentText = scratchEar1
                            delay(650)
                            currentText = tailMid
                            delay(900)
                        }
                        3 -> { // Умывается
                            currentText = lick1
                            delay(800)
                            currentText = lick2
                            delay(900)
                            currentText = lick3
                            delay(800)
                            currentText = tailMid
                            delay(900)
                        }
                        4 -> { // Небольшая разминка
                            currentText = stretch1
                            delay(800)
                            currentText = stretch2
                            delay(1_400)
                            currentText = stretch3
                            delay(800)
                            currentText = tailMid
                            delay(900)
                        }
                        else -> { // Длинное спокойное моргание
                            currentText = blink
                            delay(420)
                            currentText = tailMid
                            delay(1_200)
                        }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = normalizeAnimatedCatFrame(currentText, bodyAxis = 3),
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = catFontSizeSp.sp,
                lineHeight = (catFontSizeSp * (46f / 40f)).sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.offset(
                x = horizontalMotion.value.dp
            )
        )
    }
}
}
@Composable
private fun PlayingCat(
    modifier: Modifier,
    catFontSizeSp: Float,
    catCharacterWidthDp: Float
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
    // ===== БАЗОВЫЕ КАДРЫ (игра с клубком) =====
    val yarnLeft = """
  _____     
 ( o.o )o
 /|   |\_/  
(_|   |_)   
"""
    val yarnCenter = """
  _____     
 ( o.o ) o
 /|   |\_/  
(_|   |_)   
"""
    val yarnRight = """
  _____     
 ( o.o )  o
 /|   |\_/  
(_|   |_)   
"""
    val yarnBlink = """
  _____
 ( -.- ) o
 /|   |\_/
(_|   |_)
"""
    val hopCrouch = """
  _____
 ( o.o ) o
 /| _ |\_/
(_|_|_)
"""
    val hopAir = """
  _____
 ( o.o ) o
 /| ^ |\
(_/   \_)
"""
    val hopLand = """
  _____
 ( >.< ) o
 /| _ |\_/
(_|_|_)
"""

    // ===== РЕДКИЕ СОБЫТИЯ =====

    // Засыпает (глаза закрываются)
    val doze1 = """
  _____     
 ( -.- )  o 
 /|   |\_/  
(_|   |_)   
"""
    val doze2 = """
  _____   o
 ( -.- )    
 /|   |\_/  
(_|   |_)   
          
"""
    val dozeWake = """
  _____     
 ( O.O )!   
 /|   |\_/  
(_|   |_)   
"""

    // Потягивается (вытягивает лапы)
    val stretch1 = """
  _____   o 
 ( -.O )    
 /|   |\_/  
(_|   |_)   
"""
    val stretch2 = """
  _____     
 ( ~o~ )
 /|   |\_/  
(_| | |_)   
"""
    val stretch3 = """
  _____   o
 ( o.o )    
 /|   |\_/  
(_|   |_)   
"""

    // Ловит муху
    val flyLeft = """
  _____   o 
 ( o.o )    
 /|   |\_/  
(_|   |_)   
"""
    val flyCenter = """
  _____     
 ( o o )  o 
 /|   |\_/  
(_|   |_)   
"""
    val flyCatch = """
  _____     
 ( >w< )  o
 /|   |\_/  
(_|   |_)   
"""

    // Чешет ухо задней лапой
    val scratchEar1 = """
  _____     
 ( o.o )  o  
 /|   |\_/  
(_|   |_)   
"""
    val scratchEar2 = """
  _____     
 ( -._ )    
 /|   |\_/  
(_|   |_)  o 
"""

    // Два клубка сразу
    val twoYarns1 = """
  _____     
 ( o.o ) o o
 /|   |\_/  
(_|   |_)   
"""
    val twoYarns2 = """
  _____     
 ( o.o )o  o
 /|   |\_/  
(_|   |_)   
"""

    // Удивляется когда клубок улетает
    val surprised = """
  _____     
 ( O_O )    
 /|   |\_/  
(_|o  |_)  o
"""

    // Мурлычет (вибрирует)
    val purr1 = """
  _____     
 ( ~.~ )    
 /|   |\_/  
(_|   |_)   
"""
    val purr2 = """
  _____     
 ( ~.~ )    
 /|   |\_/  
(_|   |_)   
"""

    // Зевает от удовольствия
    val yawn1 = """
  _____     
 ( o.o )    
 /|   |\_/  
(_|   |_)   
"""
    val yawn2 = """
  _____     
 ( -.- )    
 /| O |\_/  
(_|   |_)   
"""
    val yawn3 = """
  _____     
 ( ~.~ )    
 /| o |\_/  
(_|   |_)   
"""

    // Переворачивается на другой бок
    val rollOver1 = """
  _____     
 ( o.o )    
 /|   |\_/  
(_|   |_)   
"""
    val rollOver2 = """
 _____      
(o.o )     
/|   |\_    
|_)   |_)   
"""
    val rollOver3 = """
  _____     
 ( o.o )    
 /|   |\_/  
(_|   |_)   
"""

    // Смотрит по сторонам
    val lookLeft = """
  _____     
(o.o  )  o
 /|   |\_/  
(_|   |_)   
"""
    val lookRight = """
  _____     
(  o.o)  o
 /|   |\_/  
(_|   |_)   
"""

    var currentText by remember { mutableStateOf(yarnLeft) }
    var hopFromX by remember { mutableFloatStateOf(0f) }
    var hopToX by remember { mutableFloatStateOf(0f) }
    val hopProgress = remember { Animatable(1f) }
    var lureGlyph by remember { mutableStateOf<String?>(null) }
    var lureX by remember { mutableFloatStateOf(0f) }
    var lureY by remember { mutableFloatStateOf(0f) }
    var lureFloats by remember { mutableStateOf(false) }
    val lureAlpha = remember { Animatable(0f) }
    val lureTransition = rememberInfiniteTransition(label = "lureFloat")
    val lureBob by lureTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lureBob"
    )
    val maxTravelX = calculateCatHorizontalTravel(
        sceneWidthDp = maxWidth.value,
        visibleHalfWidthDp = catCharacterWidthDp * 6.5f
    )
    val currentHopX = hopFromX + (hopToX - hopFromX) * hopProgress.value
    val hopHeight = minOf(
        maxHeight.value * 0.22f,
        18f + kotlin.math.abs(hopToX - hopFromX) * 0.2f
    )
    val currentHopY = calculateParabolicHopOffset(hopProgress.value, hopHeight)

    LaunchedEffect(maxTravelX) {
        var nextEventAt = System.currentTimeMillis() +
            kotlin.random.Random.nextLong(12_000L, 24_001L)
        var nextHopAt = System.currentTimeMillis() +
            kotlin.random.Random.nextLong(18_000L, 36_001L)
        var lastEvent = -1
        var roamIndex = 2
        while (true) {
            val ballSequence = listOf(
                yarnLeft, yarnCenter, yarnRight, yarnCenter,
                yarnLeft, yarnCenter, yarnBlink, yarnCenter,
                yarnRight, yarnCenter
            )
            for (frame in ballSequence) {
                currentText = frame
                delay(kotlin.random.Random.nextLong(240L, 361L))
            }

            // Между действиями кот остаётся на поверхности и не теряет клубок.
            currentText = yarnCenter
            delay(650)

            val hopNow = System.currentTimeMillis()
            if (hopNow >= nextHopAt && maxTravelX > 0f) {
                val roamStops = floatArrayOf(-1f, -0.5f, 0f, 0.5f, 1f)
                val possibleStops = roamStops.indices.filter {
                    kotlin.math.abs(it - roamIndex) >= 2
                }
                val nextRoamIndex = possibleStops.random()
                val roamTarget = roamStops[nextRoamIndex] * maxTravelX

                val lureIndex = kotlin.random.Random.nextInt(4)
                lureGlyph = listOf("✣", "⋈", "●", "✦")[lureIndex]
                lureX = roamTarget
                lureY = when (lureIndex) {
                    0 -> 26f   // жук у поверхности
                    1 -> -42f  // бабочка
                    2 -> 30f   // мяч
                    else -> -54f // светящаяся точка
                }
                lureFloats = lureIndex == 1 || lureIndex == 3
                lureAlpha.snapTo(0f)
                lureAlpha.animateTo(1f, tween(240, easing = FastOutSlowInEasing))

                currentText = if (roamTarget < hopToX) lookLeft else lookRight
                delay(kotlin.random.Random.nextLong(750L, 1_301L))
                currentText = hopCrouch
                delay(220)
                hopFromX = hopToX
                hopToX = roamTarget
                hopProgress.snapTo(0f)
                currentText = hopAir
                hopProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = kotlin.random.Random.nextInt(820, 1_181),
                        easing = LinearEasing
                    )
                )
                currentText = hopLand
                delay(240)
                currentText = yarnCenter
                lureAlpha.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                lureGlyph = null
                roamIndex = nextRoamIndex
                nextHopAt = System.currentTimeMillis() +
                    kotlin.random.Random.nextLong(20_000L, 40_001L)
            } else {
                if (hopNow >= nextHopAt) {
                    nextHopAt = hopNow + kotlin.random.Random.nextLong(20_000L, 40_001L)
                }
                delay(kotlin.random.Random.nextLong(650L, 1_401L))
            }

            val now = System.currentTimeMillis()
            if (now >= nextEventAt) {
                nextEventAt = now + kotlin.random.Random.nextLong(16_000L, 36_001L)
                var event: Int
                do {
                    event = kotlin.random.Random.nextInt(10)
                } while (event == lastEvent)
                lastEvent = event
                when (event) {
                        0 -> { // Засыпает
                            currentText = doze1
                            delay(2500)
                            currentText = doze2
                            delay(2000)
                            currentText = dozeWake
                            delay(600)
                            delay(1000)
                        }
                        1 -> { // Потягивается
                            currentText = stretch1
                            delay(800)
                            currentText = stretch2
                            delay(1800)
                            currentText = stretch3
                            delay(800)
                            delay(1000)
                        }
                        2 -> { // Ловит муху
                            currentText = flyLeft
                            delay(600)
                            currentText = flyCenter
                            delay(600)
                            currentText = flyCatch
                            delay(800)
                            delay(1000)
                        }
                        3 -> { // Чешет ухо
                            currentText = scratchEar1
                            delay(500)
                            currentText = scratchEar2
                            delay(500)
                            currentText = scratchEar1
                            delay(500)
                            currentText = scratchEar2
                            delay(500)
                            delay(1000)
                        }
                        4 -> { // Два клубка
                            currentText = twoYarns1
                            delay(800)
                            currentText = twoYarns2
                            delay(800)
                            currentText = twoYarns1
                            delay(800)
                            currentText = twoYarns2
                            delay(800)
                            delay(1000)
                        }
                        5 -> { // Удивляется
                            currentText = surprised
                            delay(1500)
                            delay(1000)
                        }
                        6 -> { // Мурлычет
                            currentText = purr1
                            delay(600)
                            currentText = purr2
                            delay(600)
                            currentText = purr1
                            delay(600)
                            currentText = purr2
                            delay(600)
                            delay(1000)
                        }
                        7 -> { // Зевает
                            currentText = yawn1
                            delay(800)
                            currentText = yawn2
                            delay(1200)
                            currentText = yawn3
                            delay(1000)
                            delay(1000)
                        }
                        8 -> { // Переворачивается
                            currentText = rollOver1
                            delay(800)
                            currentText = rollOver2
                            delay(1200)
                            currentText = rollOver3
                            delay(800)
                            delay(1000)
                        }
                        9 -> { // Смотрит по сторонам
                            currentText = lookLeft
                            delay(2000)
                            currentText = lookRight
                            delay(2000)
                            delay(1000)
                        }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        lureGlyph?.let { glyph ->
            Text(
                text = glyph,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = (catFontSizeSp * 0.46f).sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .offset(
                        x = lureX.dp,
                        y = (lureY + if (lureFloats) lureBob else 0f).dp
                    )
                    .graphicsLayer {
                        alpha = lureAlpha.value
                        val scale = 0.78f + lureAlpha.value * 0.22f
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
        Text(
            text = normalizeAnimatedCatFrame(currentText, bodyAxis = 4),
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = catFontSizeSp.sp,
                lineHeight = (catFontSizeSp * (46f / 40f)).sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.offset(
                x = currentHopX.dp,
                y = currentHopY.dp
            )
        )
    }
}
}

internal fun calculateMeasuredBigCatScale(
    measuredWidthPx: Float,
    measuredHeightPx: Float,
    availableWidthPx: Float,
    availableHeightPx: Float,
    animationReserve: Float
): Float {
    if (
        measuredWidthPx <= 0f || measuredHeightPx <= 0f ||
        availableWidthPx <= 0f || availableHeightPx <= 0f
    ) return 0.12f
    val reserve = animationReserve.coerceAtLeast(1f)
    return minOf(
        availableWidthPx * BIG_CAT_CONTENT_FRACTION / (measuredWidthPx * reserve),
        availableHeightPx * BIG_CAT_CONTENT_FRACTION / (measuredHeightPx * reserve)
    ).coerceAtLeast(0.01f)
}

internal fun calculateCatHorizontalTravel(
    sceneWidthDp: Float,
    visibleHalfWidthDp: Float
): Float = (
    sceneWidthDp / 2f -
        visibleHalfWidthDp.coerceAtLeast(0f) -
        CAT_SCENE_EDGE_PADDING_DP
    ).coerceAtLeast(0f)

internal fun calculateParabolicHopOffset(progress: Float, heightDp: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return -4f * heightDp.coerceAtLeast(0f) * p * (1f - p)
}

private const val BIG_CAT_CONTENT_FRACTION = 0.92f
private const val BIG_CAT_MEASUREMENT_FONT_SP = 100f
private const val CAT_SCENE_EDGE_PADDING_DP = 8f
private const val ANIMATED_CAT_CANVAS_COLUMNS = 15
private const val ANIMATED_CAT_CANVAS_LINES = 6
private const val SLEEPING_CAT_SIZE_REFERENCE = """
 /\\_/\\
( -.- )
 > ^ <
"""
private const val ANIMATED_CAT_SIZE_REFERENCE = """
 /\\_/\\
( o.o )
 > ^ <  /
(_| |_)/
"""

/**
 * Places every animation frame on one fixed monospace canvas and pins the
 * anatomical centre of the body to the canvas centre. NBSP padding is
 * intentional: unlike regular trailing spaces it participates in measurement.
 */
internal fun normalizeAnimatedCatFrame(frame: String, bodyAxis: Int = 3): String {
    val sourceLines = frame
        .trim('\n', '\r')
        .split("\n")
        .map { it.trimEnd('\r', ' ') }
    val canvasCentre = ANIMATED_CAT_CANVAS_COLUMNS / 2
    val leadingPadding = (canvasCentre - bodyAxis).coerceAtLeast(0)
    val topPadding = ((ANIMATED_CAT_CANVAS_LINES - sourceLines.size) / 2).coerceAtLeast(0)
    return List(ANIMATED_CAT_CANVAS_LINES) { index ->
        val sourceIndex = index - topPadding
        val line = if (sourceIndex in sourceLines.indices) sourceLines[sourceIndex] else ""
        (" ".repeat(leadingPadding) + line)
            .padEnd(ANIMATED_CAT_CANVAS_COLUMNS, '\u00A0')
    }.joinToString("\n")
}
/**
 * Центрирует кадр кота по заданной оси (может быть дробной —
 * например 3.5f, чтобы ось прошла МЕЖДУ двумя пробелами).
 */
private fun centerCat(frame: String, axis: Float = 1f): String {
    val lines = frame.trimEnd().split("\n").map { it.trimEnd() }
    val maxRight = lines.maxOf { it.length }
    val lead = (maxRight - 1 - 2 * axis).roundToInt().coerceAtLeast(0)
    return lines.joinToString("\n") { " ".repeat(lead) + it }
}
