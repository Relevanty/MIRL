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
import androidx.compose.ui.unit.TextUnit
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

    ResponsiveAsciiArt(
        text = text,
        color = tint,
        maxFontSize = 11.sp,
        lineHeightMultiplier = 13f / 11f,
        modifier = modifier.fillMaxSize()
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

    when {
        isFocusing -> AwakeCat(modifier)
        isBreak -> PlayingCat(modifier)
        else -> SleepingCat(modifier)
    }
}


@Composable
private fun SleepingCat(modifier: Modifier) {
    val catText = """
 /\\_/\    
( -.- )    
 > ^ <     
""".trimIndent()

    val infiniteTransition = rememberInfiniteTransition(label = "sleep")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
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
    var zDir by remember { mutableFloatStateOf(1f) }      // +1 вправо, -1 влево
    var zAmp by remember { mutableFloatStateOf(1.0f) }    // коэф. при x³ (0.8..1.2)

    LaunchedEffect(Unit) {
        while (true) {
            zProgress.snapTo(0f)                        // z появилась у головы
            zProgress.animateTo(1f, tween(3500, easing = LinearEasing)) // летит дольше
            // случайная пауза 4–10 секунд до следующего вылета
            delay(kotlin.random.Random.nextLong(4000L, 10001L))
            zBig = !zBig
            zBase = if (zBase >= 30f) 16f else zBase + 3f
            zDir = if (kotlin.random.Random.nextBoolean()) 1f else -1f
            zAmp = 0.8f + kotlin.random.Random.nextFloat() * 0.4f   // 0.8..1.2
        }
    }

    val p = zProgress.value
    val zAlpha = 1f - p                                  // тает по мере полёта
    val zScale = 0.7f + p * 0.9f                         // растёт в полёте
    // Случайное направление + узкий разброс x³
    // Горизонталь: быстрый старт, быстрое замедление
    val pFast = 1f - (1f - p) * (1f - p) * (1f - p)     // быстро выходит на плато
    val zX = 16.dp * zDir + (50.dp * zAmp) * pFast * zDir
    val zY = 40.dp - 200.dp * (p * p * p)                // вверх КУДА круче
    val zColor = if (p < 0.5f) lerp(c1, c2, p * 2f)
    else lerp(c2, c1, (p - 0.5f) * 2f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        ResponsiveAsciiArt(
            text = centerCat(catText, axis = 3f),
            color = MaterialTheme.colorScheme.primary,
            maxFontSize = 52.sp,
            lineHeightMultiplier = 58f / 52f,
            renderScale = breathScale,
            scaleReserve = 1.06f,
            modifier = Modifier.fillMaxSize()
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
private fun AwakeCat(modifier: Modifier) {
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
    val tailCurl = """
 /\\_/\    
(  o.o)    
 > ^ <  /  
(_| |_)/  
"""
    val blink = """
 /\\_/\    
( -.- )    
 >/| < \   
(_| |_)/  
"""

    // ===== РЕДКИЕ СОБЫТИЯ =====

    // Чихание
    val sneeze1 = """
 /\\_/\    
( O_O )!   
 > o <  /  
(_| |_)/  
"""
    val sneeze2 = """
 /\\_/\    
( >_< )    
 > O <  /  
(_| |_)/  
"""

    // Прыжок
    val jump = """
 /\\_/\    
( o.o )    
 > ^ <     
(_| |_)    
          
          
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

    // Отбивает мяч
    val ballApproach1 = """
 /\\_/\         o
( o.o )    
 > ^ <  /  
(_| |_)/  
"""
    val ballApproach2 = """
 /\\_/\       o  
( o.o )    
 > ^ <  /  
(_| |_)/  
"""
    val ballApproach3 = """
 /\\_/\     o    
( o.o )    
 > ^ <  /  
(_| |_)/  
"""
    val ballHit = """
 /\\_/\  o       
( o.o )\   
 > ^ <  /  
(_| |_)/  
"""
    val ballGone = """
 /\\_/\    
( o.o )    
 > ^ <  /  
(_| |_)/  
"""

    // Зевает (широко открывает рот)
    val yawn1 = """
 /\\_/\    
( o.o )    
 > ^ <  /  
(_| |_)/  
"""
    val yawn2 = """
 /\\_/\    
( -.- )    
 > O <  /  
(_| |_)/  
"""
    val yawn3 = """
 /\\_/\    
( ~.~ )    
 > o <  /  
(_| |_)/  
"""

    // Ловит муху (глаза следят)
    val flyLeft = """
 /\\_/\  o 
( o.o )    
 > ^ <  /  
(_| |_)/  
"""
    val flyCenter = """
 /\\_/\    
( o o )  o 
 > ^ <  /  
(_| |_)/  
"""
    val flyRight = """
 /\\_/\    
( o.o )   o
 > ^ <  /  
(_| |_)/  
"""
    val flyCatch = """
 /\\_/\    
( >w< )    
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
 >/| <  /  
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

    // Кратко засыпает (кивает)
    val doze1 = """
 /\\_/\    
( -.- )    
 > ^ <  /  
(_| |_)/  
"""
    val doze2 = """
 /\\_/\    
( -.- )    
 > ^ <  /  
(_| |_)/  
          
"""
    val dozeWake = """
 /\\_/\    
( O.O )!   
 > ^ <  /  
(_| |_)/  
"""

    var currentText by remember { mutableStateOf(tailMid) }
    var lastEventTime by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            // ===== ОСНОВНОЙ ЦИКЛ: медленное виляние хвостом =====
            currentText = tailMid
            delay(3500)
            currentText = tailUp
            delay(4000)
            currentText = tailMid
            delay(3500)
            currentText = tailDown
            delay(4000)
            currentText = tailMid
            delay(3500)

            // Моргание
            currentText = blink
            delay(400)
            currentText = tailMid
            delay(2500)

            currentText = tailCurl
            delay(4000)
            currentText = tailMid
            delay(3500)

            // ===== РЕДКИЕ СОБЫТИЯ (раз в 1-3 минуты) =====
            val now = System.currentTimeMillis()
            if (now - lastEventTime > 60000) {
                if (kotlin.random.Random.nextInt(100) < 30) { // 30% шанс
                    lastEventTime = now

                    when (kotlin.random.Random.nextInt(10)) {
                        0 -> { // Чихание
                            currentText = sneeze1
                            delay(300)
                            currentText = sneeze2
                            delay(500)
                            currentText = tailMid
                            delay(1000)
                        }
                        1 -> { // Прыжок
                            currentText = jump
                            delay(600)
                            currentText = tailMid
                            delay(1000)
                        }
                        2 -> { // Смотрит по сторонам
                            currentText = lookLeft
                            delay(2000)
                            currentText = tailMid
                            delay(1000)
                            currentText = lookRight
                            delay(2000)
                            currentText = tailMid
                            delay(1000)
                        }
                        3 -> { // Отбивает мяч
                            currentText = ballApproach1
                            delay(400)
                            currentText = ballApproach2
                            delay(400)
                            currentText = ballApproach3
                            delay(400)
                            currentText = ballHit
                            delay(500)
                            currentText = ballGone
                            delay(1000)
                        }
                        4 -> { // Зевает
                            currentText = yawn1
                            delay(800)
                            currentText = yawn2
                            delay(1200)
                            currentText = yawn3
                            delay(1000)
                            currentText = tailMid
                            delay(1500)
                        }
                        5 -> { // Ловит муху
                            currentText = flyLeft
                            delay(500)
                            currentText = flyCenter
                            delay(500)
                            currentText = flyRight
                            delay(500)
                            currentText = flyCatch
                            delay(700)
                            currentText = tailMid
                            delay(1000)
                        }
                        6 -> { // Чешет за ухом
                            currentText = scratchEar1
                            delay(400)
                            currentText = scratchEar2
                            delay(400)
                            currentText = scratchEar1
                            delay(400)
                            currentText = scratchEar2
                            delay(400)
                            currentText = tailMid
                            delay(1000)
                        }
                        7 -> { // Облизывается
                            currentText = lick1
                            delay(600)
                            currentText = lick2
                            delay(800)
                            currentText = lick3
                            delay(600)
                            currentText = tailMid
                            delay(1000)
                        }
                        8 -> { // Потягивается
                            currentText = stretch1
                            delay(600)
                            currentText = stretch2
                            delay(1500)
                            currentText = stretch3
                            delay(600)
                            currentText = tailMid
                            delay(1000)
                        }
                        9 -> { // Кратко засыпает
                            currentText = doze1
                            delay(2000)
                            currentText = doze2
                            delay(1500)
                            currentText = dozeWake
                            delay(500)
                            currentText = tailMid
                            delay(1000)
                        }
                    }
                }
            }
        }
    }

    ResponsiveAsciiArt(
        text = centerCat(currentText, axis = 3f),
        color = MaterialTheme.colorScheme.primary,
        maxFontSize = 40.sp,
        lineHeightMultiplier = 46f / 40f,
        modifier = modifier
    )
}
@Composable
private fun PlayingCat(modifier: Modifier) {
    // ===== БАЗОВЫЕ КАДРЫ (игра с клубком) =====
    val yarnLeft = """
  _____     
 ( o.o )  o 
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
 ( o.o ) o  
 /|   |\_/  
(_|   |_)   
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
  _____     
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
 ( ~.~ )    
 /|   |\_/  
(_| | |_)   
"""
    val stretch3 = """
  _____     
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
 ( >w< )    
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
    var lastEventTime by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            // ===== ОСНОВНОЙ ЦИКЛ: игра с клубком =====
            for (i in 1..16) {
                currentText = when (i % 4) {
                    0 -> yarnLeft
                    1 -> yarnCenter
                    2 -> yarnRight
                    else -> yarnCenter
                }
                delay(400)
            }

            // Клубок исчезает
            currentText = """
  _____     
 ( o.o )    
 /|   |\_/  
(_|   |_)   
"""
            delay(1200)

            // ===== РЕДКИЕ СОБЫТИЯ (раз в 1-3 минуты) =====
            val now = System.currentTimeMillis()
            if (now - lastEventTime > 60000) {
                if (kotlin.random.Random.nextInt(100) < 35) { // 35% шанс
                    lastEventTime = now

                    when (kotlin.random.Random.nextInt(10)) {
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
    }

    ResponsiveAsciiArt(
        text = centerCat(currentText, axis = 4f),
        color = MaterialTheme.colorScheme.primary,
        maxFontSize = 40.sp,
        lineHeightMultiplier = 46f / 40f,
        modifier = modifier
    )
}

@Composable
private fun ResponsiveAsciiArt(
    text: String,
    color: Color,
    maxFontSize: TextUnit,
    lineHeightMultiplier: Float,
    modifier: Modifier = Modifier,
    renderScale: Float = 1f,
    scaleReserve: Float = 1f
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val maximumFontSizePx = with(density) { maxFontSize.toPx() }
        val maximumLineHeightPx = maximumFontSizePx * lineHeightMultiplier
        val maximumStyle = MaterialTheme.typography.displayLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = with(density) { maximumFontSizePx.toSp() },
            lineHeight = with(density) { maximumLineHeightPx.toSp() },
            fontWeight = FontWeight.Bold
        )
        val lineCount = text.count { it == '\n' } + 1
        val measured = remember(text, maximumStyle, textMeasurer) {
            textMeasurer.measure(
                text = AnnotatedString(text),
                style = maximumStyle,
                softWrap = false,
                overflow = TextOverflow.Clip,
                maxLines = lineCount,
                constraints = Constraints()
            )
        }
        val availableWidthPx = with(density) { maxWidth.toPx() }
        val availableHeightPx = with(density) { maxHeight.toPx() }
        val fitScale = calculateAsciiFitScale(
            measuredWidthPx = measured.size.width.toFloat(),
            measuredHeightPx = measured.size.height.toFloat(),
            availableWidthPx = availableWidthPx,
            availableHeightPx = availableHeightPx,
            scaleReserve = scaleReserve
        )
        val fittedFontSizePx = maximumFontSizePx * fitScale

        Text(
            text = text,
            style = maximumStyle.copy(
                fontSize = with(density) { fittedFontSizePx.toSp() },
                lineHeight = with(density) {
                    (fittedFontSizePx * lineHeightMultiplier).toSp()
                }
            ),
            color = color,
            maxLines = lineCount,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                scaleX = renderScale
                scaleY = renderScale
            }
        )
    }
}

internal fun calculateAsciiFitScale(
    measuredWidthPx: Float,
    measuredHeightPx: Float,
    availableWidthPx: Float,
    availableHeightPx: Float,
    scaleReserve: Float = 1f
): Float {
    if (
        measuredWidthPx <= 0f || measuredHeightPx <= 0f ||
        availableWidthPx <= 0f || availableHeightPx <= 0f
    ) return 1f

    val safeReserve = scaleReserve.coerceAtLeast(1f)
    val safeWidth = availableWidthPx * ASCII_CONTENT_FRACTION
    val safeHeight = availableHeightPx * ASCII_CONTENT_FRACTION
    return minOf(
        safeWidth / (measuredWidthPx * safeReserve),
        safeHeight / (measuredHeightPx * safeReserve),
        1f
    ).coerceIn(MIN_ASCII_SCALE, 1f)
}

private const val ASCII_CONTENT_FRACTION = 0.94f
private const val MIN_ASCII_SCALE = 0.05f

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
