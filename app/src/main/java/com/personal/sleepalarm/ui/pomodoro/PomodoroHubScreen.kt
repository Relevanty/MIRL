package com.personal.sleepalarm.ui.pomodoro

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.OtherActivityEntity
import com.personal.sleepalarm.data.db.entity.SubjectEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.ui.focusprotocol.EnergyPatternCard
import com.personal.sleepalarm.ui.focusprotocol.CompletedFocusBlocksCard
import com.personal.sleepalarm.ui.focusprotocol.FocusProtocolActiveScreen
import com.personal.sleepalarm.ui.focusprotocol.FocusProtocolSetupSheet
import com.personal.sleepalarm.ui.focusprotocol.FocusProtocolTarget
import com.personal.sleepalarm.ui.focusprotocol.FocusProtocolViewModel
import com.personal.sleepalarm.ui.focusprotocol.formatCompactDuration
import com.personal.sleepalarm.ui.theme.ThemedModalBottomSheet
import com.personal.sleepalarm.ui.activity.ManualActivitySheet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class HubActivityItem(
    val id: Int,
    val name: String,
    val color: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(
    modifier: Modifier = Modifier,
    viewModel: PomodoroViewModel = viewModel()
) {
    val protocolViewModel: FocusProtocolViewModel = viewModel()
    val activeProtocol by protocolViewModel.activeSession.collectAsStateWithLifecycle()
    val latestProtocol by protocolViewModel.latestSession.collectAsStateWithLifecycle()
    val protocolRemaining by protocolViewModel.remainingMillis.collectAsStateWithLifecycle()
    val energyPattern by protocolViewModel.energyPattern.collectAsStateWithLifecycle()
    val recentCompletedBlocks by protocolViewModel.recentCompletedBlocks.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val workTasks by viewModel.workTasks.collectAsStateWithLifecycle()
    val otherActivities by viewModel.otherActivities.collectAsStateWithLifecycle()
    val currentDaySessions by viewModel.currentDayFocusSessions.collectAsStateWithLifecycle()
    val currentDayRange by viewModel.currentDayRange.collectAsStateWithLifecycle()
    val activityType by viewModel.activityType.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedItemId.collectAsStateWithLifecycle()
    val fallbackFocusDuration by viewModel.focusDuration.collectAsStateWithLifecycle()
    val fallbackBreakDuration by viewModel.breakDuration.collectAsStateWithLifecycle()
    val notificationSoundUri by viewModel.notificationSoundUri.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModel.setNotificationSound(uri)
    }

    val studyItems = remember(subjects) {
        subjects.map { HubActivityItem(it.id, it.name, it.color) }
    }
    val workItems = remember(workTasks) {
        workTasks.filterNot { it.isDone }.map {
            HubActivityItem(
                it.id,
                it.title.ifBlank { it.description.ifBlank { it.nextAction.ifBlank { "Задача #${it.id}" } } },
                0xFF5C6BC0.toInt()
            )
        }
    }
    val otherItems = remember(otherActivities) {
        otherActivities.map { HubActivityItem(it.id, it.name, it.color) }
    }
    fun itemsFor(type: FocusActivityType): List<HubActivityItem> = when (type) {
        FocusActivityType.STUDY -> studyItems
        FocusActivityType.WORK -> workItems
        FocusActivityType.OTHER -> otherItems
    }

    activeProtocol?.let { session ->
        val targets = itemsFor(session.activityType).map {
            FocusProtocolTarget(it.id, it.name, it.color)
        }
        FocusProtocolActiveScreen(
            session = session,
            remainingMillis = protocolRemaining,
            availableTargets = targets,
            onSkipReset = { protocolViewModel.skipReset(session.id) },
            onStartFocus = { protocolViewModel.startFocus(session.id) },
            onPause = { protocolViewModel.pauseFocus(session.id) },
            onResume = { protocolViewModel.resumeFocus(session.id) },
            onFinishFocus = { protocolViewModel.finishFocus(session.id) },
            onFinishRecovery = { protocolViewModel.finishRecovery(session.id) },
            onDistraction = { protocolViewModel.markDistraction(session.id) },
            onRepeatCycle = { protocolViewModel.repeatCycle(session.id) },
            onSwitchTarget = { target, outcome ->
                protocolViewModel.switchTargetAndRepeat(
                    session.id,
                    session.activityType,
                    target.id,
                    target.name,
                    outcome
                )
            },
            onFinishBlock = { protocolViewModel.finishBlock(session.id) },
            onCancel = { protocolViewModel.cancel(session.id, it) },
            onCompleteReview = { protocolViewModel.completeReview(session.id, it) },
            modifier = modifier
        )
        return
    }

    val activityItems = itemsFor(activityType)
    val selectedItem = activityItems.firstOrNull { it.id == selectedId }
    val (dayStart, dayEnd) = currentDayRange
    val totalsByItem = remember(currentDaySessions, activityType, currentDayRange) {
        currentDaySessions.asSequence()
            .filter { it.activityType == activityType }
            .mapNotNull { session ->
                val itemId = when (activityType) {
                    FocusActivityType.STUDY -> session.subjectId
                    FocusActivityType.WORK -> session.taskId
                    FocusActivityType.OTHER -> session.otherActivityId
                } ?: return@mapNotNull null
                val actualEnd = session.startedAt + session.actualDurationMillis
                val end = minOf(session.completedAt ?: actualEnd, actualEnd, dayEnd)
                val start = maxOf(session.startedAt, dayStart)
                itemId to (end - start).coerceAtLeast(0L)
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, durations) -> durations.sum() }
    }
    val totalToday = totalsByItem.values.sum()
    val cyclesToday = currentDaySessions.count {
        it.activityType == activityType && !it.isBreak && it.actualDurationMillis > 0L && it.recordSource == "TIMER"
    }

    LaunchedEffect(activityType, activityItems, selectedId) {
        if (activityItems.isNotEmpty() && activityItems.none { it.id == selectedId }) {
            viewModel.selectItem(activityItems.first().id)
        }
    }

    var showSetup by remember { mutableStateOf(false) }
    var showInsights by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HubActivityItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .padding(top = 30.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.pomodoro_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.focus_hub_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ActivityTypeStrip(
            selected = activityType,
            onSelected = viewModel::selectActivityType
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HubQuickAction(
                title = "Звук",
                subtitle = if (notificationSoundUri == null) "системный" else "выбран",
                onClick = { soundPicker.launch(arrayOf("audio/*")) },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.weight(1f)
            )
            HubQuickAction(
                title = "Добавить",
                subtitle = "время",
                onClick = { showManualEntry = true },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.weight(1f)
            )
            HubQuickAction(
                title = "История",
                subtitle = "$cyclesToday циклов",
                onClick = { showInsights = true },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = categoryTitle(activityType),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.focus_hub_hold_to_edit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ActivityCarousel(
            items = activityItems,
            selectedId = selectedId,
            totals = totalsByItem,
            onSelect = { viewModel.selectItem(it.id) },
            onEdit = {
                editing = it
                showEditor = true
            },
            onAdd = {
                editing = null
                showEditor = true
            }
        )

        FocusLaunchCard(
            modifier = Modifier.weight(1f),
            item = selectedItem,
            outcome = latestProtocol
                ?.takeIf { it.activityType == activityType && it.itemId == selectedItem?.id }
                ?.outcome
                .orEmpty(),
            focusMinutes = latestProtocol?.focusDurationMinutes
                ?: (fallbackFocusDuration / 60_000L).toInt(),
            recoveryMinutes = latestProtocol?.recoveryDurationMinutes
                ?: (fallbackBreakDuration / 60_000L).toInt(),
            cyclesToday = cyclesToday,
            totalToday = totalToday,
            onStart = { showSetup = true }
        )
    }

    if (showSetup) {
        val previousForItem = latestProtocol?.takeIf {
            it.activityType == activityType && it.itemId == selectedItem?.id
        }
        FocusProtocolSetupSheet(
            activityType = activityType,
            targets = activityItems.map { FocusProtocolTarget(it.id, it.name, it.color) },
            selectedTargetId = selectedItem?.id,
            initialOutcome = previousForItem?.outcome.orEmpty(),
            initialResetMinutes = latestProtocol?.resetDurationMinutes ?: 5,
            initialFocusMinutes = latestProtocol?.focusDurationMinutes
                ?: (fallbackFocusDuration / 60_000L).toInt(),
            initialRecoveryMinutes = latestProtocol?.recoveryDurationMinutes
                ?: (fallbackBreakDuration / 60_000L).toInt(),
            bedtimeRisk = protocolViewModel::isBedtimeRisk,
            onStart = { target, outcome, reset, focus, recovery, energy ->
                protocolViewModel.start(
                    activityType = activityType,
                    itemId = target.id,
                    itemName = target.name,
                    outcome = outcome,
                    resetMinutes = reset,
                    focusMinutes = focus,
                    recoveryMinutes = recovery,
                    energyBefore = energy
                )
                showSetup = false
            },
            onDismiss = { showSetup = false }
        )
    }

    if (showInsights) {
        ThemedModalBottomSheet(onDismissRequest = { showInsights = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.focus_hub_insights),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                DailySummaryCard(cyclesToday, totalToday)
                CompletedFocusBlocksCard(
                    blocks = recentCompletedBlocks.filter { it.activityType == activityType }
                )
                EnergyPatternCard(points = energyPattern)
                Spacer(Modifier.height(26.dp))
            }
        }
    }

    if (showManualEntry) {
        ManualActivitySheet(
            onDismiss = { showManualEntry = false },
            initialTaskId = selectedItem?.id.takeIf { activityType == FocusActivityType.WORK },
            initialActivityType = activityType,
            initialSubjectId = selectedItem?.id.takeIf { activityType == FocusActivityType.STUDY },
            initialOtherActivityId = selectedItem?.id.takeIf { activityType == FocusActivityType.OTHER },
            initialTitle = selectedItem?.name.orEmpty()
        )
    }

    if (showEditor) {
        ActivityEditorDialog(
            initial = editing,
            activityType = activityType,
            onSave = { name, color ->
                val current = editing
                when (activityType) {
                    FocusActivityType.STUDY -> if (current == null) {
                        viewModel.addSubject(name, color)
                    } else {
                        val source = subjects.firstOrNull { it.id == current.id }
                        if (source != null) {
                            viewModel.updateSubject(source.copy(name = name.trim(), color = color))
                        }
                    }
                    FocusActivityType.WORK -> if (current == null) {
                        viewModel.addWorkTask(name)
                    } else {
                        val source = workTasks.firstOrNull { it.id == current.id }
                        if (source != null) viewModel.updateWorkTask(source.copy(title = name.trim()))
                    }
                    FocusActivityType.OTHER -> if (current == null) {
                        viewModel.addOtherActivity(name, color)
                    } else {
                        val source = otherActivities.firstOrNull { it.id == current.id }
                        if (source != null) {
                            viewModel.updateOtherActivity(source.copy(name = name.trim(), color = color))
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
}

@Composable
private fun HubQuickAction(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor.copy(alpha = 0.72f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualFocusDialog(
    onDismiss: () -> Unit,
    onSave: (Long, Int) -> Unit
) {
    val zone = ZoneId.systemDefault()
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var timeText by remember { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var durationText by remember { mutableStateOf("25") }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.focus_manual_entry_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.focus_manual_entry_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy")))
                }
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it },
                    label = { Text(stringResource(R.string.focus_manual_time)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.focus_manual_duration)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val time = runCatching {
                    LocalTime.parse(timeText.trim(), DateTimeFormatter.ofPattern("H:mm"))
                }.getOrNull() ?: return@TextButton
                val minutes = durationText.toIntOrNull()?.coerceIn(1, PomodoroViewModel.MAX_FOCUS_MINUTES.toInt())
                    ?: return@TextButton
                val start = LocalDateTime.of(selectedDate, time).atZone(zone).toInstant().toEpochMilli()
                val boundedStart = minOf(start, System.currentTimeMillis() - minutes * 60_000L)
                onSave(boundedStart, minutes)
            }) { Text(stringResource(R.string.task_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        selectedDate = java.time.Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.task_date_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun ActivityTypeStrip(
    selected: FocusActivityType,
    onSelected: (FocusActivityType) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(FocusActivityType.entries) { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelected(type) },
                label = { Text(activityTypeTitle(type)) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActivityCarousel(
    items: List<HubActivityItem>,
    selectedId: Int?,
    totals: Map<Int, Long>,
    onSelect: (HubActivityItem) -> Unit,
    onEdit: (HubActivityItem) -> Unit,
    onAdd: () -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.id }) { item ->
            val selected = item.id == selectedId
            val width by animateDpAsState(if (selected) 148.dp else 132.dp, label = "subjectWidth")
            val background by animateColorAsState(
                if (selected) Color(item.color).copy(alpha = 0.24f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                label = "subjectColor"
            )
            Column(
                modifier = Modifier
                    .width(width)
                    .height(92.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(background)
                    .then(
                        if (selected) Modifier.border(
                            1.5.dp,
                            Color(item.color),
                            RoundedCornerShape(22.dp)
                        ) else Modifier
                    )
                    .combinedClickable(
                        onClick = { onSelect(item) },
                        onLongClick = { onEdit(item) }
                    )
                    .padding(13.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(Color(item.color))
                    )
                    Text(
                        text = if (selected) "ฅ" else "·",
                        color = Color(item.color),
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = item.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
                Text(
                    text = formatCompactDuration(totals[item.id] ?: 0L),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(92.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(22.dp)
                    )
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+\nฅ",
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FocusLaunchCard(
    modifier: Modifier = Modifier,
    item: HubActivityItem?,
    outcome: String,
    focusMinutes: Int,
    recoveryMinutes: Int,
    cyclesToday: Int,
    totalToday: Long,
    onStart: () -> Unit
) {
    var catMood by remember { mutableIntStateOf(0) }
    val catLine = when (catMood % 3) {
        1 -> stringResource(R.string.focus_cat_hub_pet)
        2 -> stringResource(R.string.focus_cat_hub_ready)
        else -> stringResource(R.string.focus_cat_hub_hint)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item?.name ?: stringResource(R.string.focus_hub_choose_item),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (outcome.isBlank()) catLine else outcome,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            AnimatedFocusCat(
                mood = FocusCatMood.IDLE,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onInteract = { catMood++ }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill(stringResource(R.string.focus_block_focus_pill, focusMinutes))
                InfoPill(stringResource(R.string.focus_block_rest_pill, recoveryMinutes))
            }

            Button(
                onClick = onStart,
                enabled = item != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.focus_block_begin))
            }

            Text(
                text = stringResource(
                    R.string.focus_hub_today_summary,
                    cyclesToday,
                    formatCompactDuration(totalToday)
                ),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
private fun DailySummaryCard(cycles: Int, total: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$cycles", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.focus_block_cycles_label), style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatCompactDuration(total),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(stringResource(R.string.focus_block_focus_time_label), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ActivityEditorDialog(
    initial: HubActivityItem?,
    activityType: FocusActivityType,
    onSave: (String, Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableIntStateOf(initial?.color ?: SUBJECT_COLORS[1]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.focus_hub_new_item) else initial.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.library_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (activityType != FocusActivityType.WORK) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(SUBJECT_COLORS) { itemColor ->
                            Box(
                                modifier = Modifier
                                    .size(if (itemColor == color) 34.dp else 30.dp)
                                    .clip(CircleShape)
                                    .background(Color(itemColor))
                                    .then(
                                        if (itemColor == color) Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.onSurface,
                                            CircleShape
                                        ) else Modifier
                                    )
                                    .clickable { color = itemColor }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, color) }) {
                Text(stringResource(R.string.library_save))
            }
        },
        dismissButton = {
            Row {
                onDelete?.let {
                    TextButton(onClick = it) {
                        Text(
                            stringResource(R.string.library_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}

@Composable
private fun categoryTitle(type: FocusActivityType): String = stringResource(
    when (type) {
        FocusActivityType.STUDY -> R.string.pomodoro_subjects
        FocusActivityType.WORK -> R.string.pomodoro_tasks
        FocusActivityType.OTHER -> R.string.pomodoro_other_items
    }
)

@Composable
private fun activityTypeTitle(type: FocusActivityType): String = stringResource(
    when (type) {
        FocusActivityType.STUDY -> R.string.pomodoro_activity_study
        FocusActivityType.WORK -> R.string.pomodoro_activity_work
        FocusActivityType.OTHER -> R.string.pomodoro_activity_other
    }
)
