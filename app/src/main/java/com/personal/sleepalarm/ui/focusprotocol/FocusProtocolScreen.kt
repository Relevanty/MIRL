package com.personal.sleepalarm.ui.focusprotocol

import com.personal.sleepalarm.ui.theme.appAccents

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.FocusProtocolSessionEntity
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.domain.model.FocusProtocolPhase
import com.personal.sleepalarm.domain.calculator.DailyTaskFocusProgress
import com.personal.sleepalarm.ui.components.DailyFocusProgressCard
import com.personal.sleepalarm.ui.focusaudio.ActiveFocusSoundButton
import com.personal.sleepalarm.ui.focusaudio.FocusSoundscapeSetupRow
import com.personal.sleepalarm.ui.focusaudio.FocusSoundscapeUiState
import com.personal.sleepalarm.ui.pomodoro.AnimatedFocusCat
import com.personal.sleepalarm.ui.pomodoro.FocusCatMood
import com.personal.sleepalarm.ui.pomodoro.pomodoroColorForToken
import com.personal.sleepalarm.ui.theme.ThemedModalBottomSheet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

data class FocusProtocolTarget(
    val id: Int,
    val name: String,
    /** Persisted compatibility token; resolve through pomodoroColorForToken before drawing. */
    val color: Int,
    /** Null for standalone activities; zero means an exhausted task budget. */
    val maximumFocusMinutes: Int? = null,
    val dailyProgress: DailyTaskFocusProgress? = null,
    val boutMinutes: Int? = null,
    val isDailyRequired: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusProtocolSetupSheet(
    activityType: FocusActivityType,
    targets: List<FocusProtocolTarget>,
    selectedTargetId: Int? = null,
    initialOutcome: String = "",
    initialResetMinutes: Int = 5,
    initialFocusMinutes: Int,
    initialRecoveryMinutes: Int,
    bedtimeRisk: (Int) -> Boolean,
    startInProgress: Boolean,
    soundscapeLoading: Boolean = false,
    startError: String?,
    soundscapeState: FocusSoundscapeUiState,
    onOpenSoundscape: () -> Unit,
    onToggleSoundscape: () -> Unit,
    onTargetSelected: (FocusProtocolTarget) -> Unit,
    onStart: (
        target: FocusProtocolTarget,
        outcome: String,
        resetMinutes: Int,
        focusMinutes: Int,
        recoveryMinutes: Int,
        energy: Int
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedId by rememberSaveable {
        mutableIntStateOf(selectedTargetId ?: targets.firstOrNull()?.id ?: 0)
    }
    val selected = targets.firstOrNull { it.id == selectedId }
    val maximumFocusMinutes = (selected?.maximumFocusMinutes ?: 180).coerceIn(1, 180)
    val minimumFocusMinutes = if (maximumFocusMinutes < 5) 1 else 5
    var outcome by rememberSaveable { mutableStateOf(initialOutcome) }
    var resetMinutes by rememberSaveable { mutableIntStateOf(initialResetMinutes.coerceIn(0, 20)) }
    var focusMinutes by rememberSaveable {
        mutableIntStateOf(initialFocusMinutes.coerceIn(1, 180))
    }
    var recoveryMinutes by rememberSaveable {
        mutableIntStateOf(initialRecoveryMinutes.coerceIn(1, 30))
    }
    var energy by rememberSaveable { mutableIntStateOf(6) }
    LaunchedEffect(selectedId, minimumFocusMinutes, maximumFocusMinutes) {
        focusMinutes = (selected?.boutMinutes ?: focusMinutes)
            .coerceIn(minimumFocusMinutes, maximumFocusMinutes)
    }
    val totalMinutes = resetMinutes + focusMinutes + recoveryMinutes

    ThemedModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = stringResource(R.string.focus_block_setup_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.focus_block_setup_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.focus_settings_total,
                        totalMinutes,
                        focusMinutes,
                        recoveryMinutes
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.appAccents.focus.color
                )
            }

            TargetRibbon(
                targets = targets,
                selectedId = selectedId,
                onSelected = {
                    selectedId = it.id
                    onTargetSelected(it)
                }
            )

            selected?.dailyProgress?.let { progress ->
                DailyFocusProgressCard(
                    progress = progress,
                    boutElapsedMillis = 0L,
                    boutMinutes = selected.boutMinutes ?: focusMinutes,
                    requiredToday = selected.isDailyRequired
                )
            }

            OutlinedTextField(
                value = outcome,
                onValueChange = { outcome = it },
                label = { Text(stringResource(R.string.focus_protocol_outcome)) },
                placeholder = { Text(stringResource(R.string.focus_protocol_outcome_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            FocusSoundscapeSetupRow(
                state = soundscapeState,
                onOpenPicker = onOpenSoundscape,
                onTogglePlayback = onToggleSoundscape,
                enabled = !startInProgress && !soundscapeLoading
            )

            MinuteSlider(
                title = stringResource(R.string.focus_protocol_reset_duration),
                description = stringResource(R.string.focus_settings_reset_hint),
                value = resetMinutes,
                minimum = 0,
                maximum = 20,
                step = 5,
                onValueChange = { resetMinutes = it }
            )
            MinuteSlider(
                title = stringResource(R.string.focus_protocol_focus_duration),
                description = stringResource(R.string.focus_settings_focus_hint),
                value = focusMinutes,
                minimum = minimumFocusMinutes,
                maximum = maximumFocusMinutes,
                step = if (maximumFocusMinutes < 5) 1 else 5,
                onValueChange = { focusMinutes = it }
            )
            MinuteSlider(
                title = stringResource(R.string.focus_protocol_recovery_duration),
                description = stringResource(R.string.focus_settings_recovery_hint),
                value = recoveryMinutes,
                minimum = 1,
                maximum = 30,
                step = 1,
                onValueChange = { recoveryMinutes = it }
            )

            Text(
                text = stringResource(R.string.focus_protocol_energy_before, energy),
                style = MaterialTheme.typography.titleSmall
            )
            Slider(
                value = energy.toFloat(),
                onValueChange = { energy = it.roundToInt().coerceIn(1, 10) },
                valueRange = 1f..10f,
                steps = 8,
                modifier = Modifier.fillMaxWidth()
            )

            if (bedtimeRisk(totalMinutes)) {
                Text(
                    text = stringResource(R.string.focus_protocol_bedtime_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appAccents.urgent.color
                )
            }

            startError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appAccents.urgent.color
                )
            }

            Button(
                onClick = {
                    selected?.let {
                        onStart(
                            it,
                            outcome.trim(),
                            resetMinutes,
                            focusMinutes,
                            recoveryMinutes,
                            energy
                        )
                    }
                },
                enabled = selected != null &&
                    selected.maximumFocusMinutes != 0 &&
                    outcome.isNotBlank() &&
                    !soundscapeLoading &&
                    !startInProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (startInProgress) R.string.focus_block_starting
                        else R.string.focus_block_begin
                    )
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TargetRibbon(
    targets: List<FocusProtocolTarget>,
    selectedId: Int,
    onSelected: (FocusProtocolTarget) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        items(targets, key = { it.id }) { target ->
            val selected = target.id == selectedId
            val targetColor = pomodoroColorForToken(target.color)
            val background by animateColorAsState(
                if (selected) targetColor.copy(alpha = 0.24f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                label = "targetBackground"
            )
            Column(
                modifier = Modifier
                    .width(126.dp)
                    .height(if (target.dailyProgress == null) 68.dp else 82.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(background)
                    .then(
                        if (selected) Modifier.border(
                            1.5.dp,
                            targetColor,
                            RoundedCornerShape(18.dp)
                        ) else Modifier
                    )
                    .clickable { onSelected(target) }
                    .padding(11.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(targetColor)
                )
                Text(
                    text = target.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
                target.dailyProgress?.let { progress ->
                    Text(
                        stringResource(
                            R.string.daily_focus_today_value,
                            progress.spentMinutes,
                            progress.targetMinutes
                        ),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MinuteSlider(
    title: String,
    description: String,
    value: Int,
    minimum: Int,
    maximum: Int,
    step: Int,
    onValueChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.focus_protocol_minutes_value, value),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.appAccents.focus.color,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { raw ->
                onValueChange(snapFocusSetting(raw, minimum, maximum, step))
            },
            valueRange = minimum.toFloat()..maximum.toFloat(),
            steps = ((maximum - minimum) / step - 1).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun FocusProtocolActiveScreen(
    session: FocusProtocolSessionEntity,
    remainingMillis: Long,
    dailyProgress: DailyTaskFocusProgress? = null,
    boutElapsedMillis: Long = 0L,
    dailyRequired: Boolean = false,
    onSkipReset: () -> Unit,
    onStartFocus: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinishFocus: () -> Unit,
    onFinishRecovery: () -> Unit,
    onDistraction: () -> Unit,
    onCancel: (String) -> Unit,
    onCompleteReview: (Int) -> Unit,
    modifier: Modifier = Modifier,
    availableTargets: List<FocusProtocolTarget> = emptyList(),
    onRepeatCycle: () -> Unit = {},
    onSwitchTarget: (FocusProtocolTarget, String) -> Unit = { _, _ -> },
    onFinishBlock: () -> Unit = {},
    soundscapeState: FocusSoundscapeUiState? = null,
    onOpenSoundscape: () -> Unit = {},
    onToggleSoundscape: () -> Unit = {}
) {
    var showCancelDialog by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var energyAfter by rememberSaveable(session.id) { mutableStateOf<Int?>(null) }
    BackHandler(enabled = session.phase != FocusProtocolPhase.REVIEW) {
        if (session.phase == FocusProtocolPhase.CYCLE_READY) onFinishBlock()
        else showCancelDialog = true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        BlockProgress(
            session = session,
            soundscapeState = soundscapeState,
            onOpenSoundscape = onOpenSoundscape,
            onToggleSoundscape = onToggleSoundscape
        )

        CatCompanion(
            phase = session.phase,
            cycle = session.completedCycles,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        AnimatedContent(
            targetState = session.phase,
            label = "focusPhase"
        ) { phase ->
            Text(
                text = phaseDescription(phase),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        if (session.phase.hasCountdown || session.phase == FocusProtocolPhase.FOCUS_PAUSED) {
            Text(
                text = formatFocusClock(remainingMillis),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.appAccents.focus.color
            )
        }

        if (session.phase != FocusProtocolPhase.REVIEW) {
            GoalChip(session.itemName, session.outcome)
        }

        dailyProgress?.let { progress ->
            DailyFocusProgressCard(
                progress = progress,
                boutElapsedMillis = boutElapsedMillis,
                boutMinutes = session.focusDurationMinutes,
                requiredToday = dailyRequired
            )
        }

        when (session.phase) {
            FocusProtocolPhase.RESET -> {
                OutlinedButton(onClick = onSkipReset, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.focus_protocol_skip_reset))
                }
            }
            FocusProtocolPhase.ACTIVATE -> {
                Button(onClick = onStartFocus, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.focus_protocol_begin_focus))
                }
            }
            FocusProtocolPhase.FOCUS -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    OutlinedButton(onClick = onDistraction, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.focus_block_distraction_short))
                    }
                    OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.focus_protocol_pause))
                    }
                }
                TextButton(onClick = onFinishFocus) {
                    Text(stringResource(R.string.focus_protocol_finish_focus))
                }
            }
            FocusProtocolPhase.FOCUS_PAUSED -> {
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.focus_protocol_resume))
                }
                TextButton(onClick = onFinishFocus) {
                    Text(stringResource(R.string.focus_protocol_finish_focus))
                }
            }
            FocusProtocolPhase.RECOVERY -> {
                RecoveryPrompt(session.completedCycles)
                OutlinedButton(onClick = onFinishRecovery, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.focus_protocol_finish_recovery))
                }
            }
            FocusProtocolPhase.CYCLE_READY -> {
                Button(onClick = onRepeatCycle, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.focus_block_one_more_cycle))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    OutlinedButton(
                        onClick = { showTargetPicker = true },
                        enabled = availableTargets.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.focus_block_switch_target))
                    }
                    OutlinedButton(onClick = onFinishBlock, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.focus_block_finish))
                    }
                }
            }
            FocusProtocolPhase.REVIEW -> {
                BlockSummary(session)
                Text(
                    text = energyAfter?.let {
                        stringResource(R.string.focus_protocol_energy_after, it)
                    } ?: stringResource(R.string.focus_protocol_energy_after_prompt),
                    style = MaterialTheme.typography.titleMedium
                )
                listOf(1..5, 6..10).forEach { range ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        range.forEach { value ->
                            FilterChip(
                                selected = energyAfter == value,
                                onClick = { energyAfter = value },
                                label = {
                                    Text(
                                        text = value.toString(),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                energyAfter?.let { after ->
                    val delta = after - session.energyBefore
                    Text(
                        text = stringResource(
                            R.string.focus_protocol_energy_change,
                            session.energyBefore,
                            after,
                            formatSignedEnergyDelta(delta)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                Button(
                    onClick = { energyAfter?.let(onCompleteReview) },
                    enabled = energyAfter != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.focus_protocol_complete))
                }
            }
            else -> Unit
        }

        if (session.phase != FocusProtocolPhase.REVIEW &&
            session.phase != FocusProtocolPhase.CYCLE_READY
        ) {
            TextButton(onClick = { showCancelDialog = true }) {
                Text(stringResource(R.string.focus_block_stop))
            }
        }
    }

    if (showTargetPicker) {
        SwitchTargetSheet(
            session = session,
            targets = availableTargets,
            onSelect = { target, outcome ->
                showTargetPicker = false
                onSwitchTarget(target, outcome)
            },
            onDismiss = { showTargetPicker = false }
        )
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(R.string.focus_protocol_cancel_title)) },
            text = { Text(stringResource(R.string.focus_protocol_cancel_message)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { onCancel("tired") }) {
                        Text(stringResource(R.string.focus_protocol_cancel_tired))
                    }
                    TextButton(onClick = { onCancel("interrupted") }) {
                        Text(stringResource(R.string.focus_protocol_cancel_interrupted))
                    }
                    TextButton(onClick = { onCancel("distracted") }) {
                        Text(stringResource(R.string.focus_protocol_cancel_distracted))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun CatCompanion(
    phase: FocusProtocolPhase,
    cycle: Int,
    modifier: Modifier = Modifier
) {
    var interaction by remember(phase) { mutableIntStateOf(0) }
    var dragTotal by remember(phase) { mutableFloatStateOf(0f) }
    val mood = when (phase) {
        FocusProtocolPhase.RESET -> FocusCatMood.RESET
        FocusProtocolPhase.ACTIVATE -> FocusCatMood.READY
        FocusProtocolPhase.FOCUS -> FocusCatMood.FOCUS
        FocusProtocolPhase.FOCUS_PAUSED -> FocusCatMood.PAUSED
        FocusProtocolPhase.RECOVERY -> FocusCatMood.REST
        FocusProtocolPhase.CYCLE_READY -> FocusCatMood.READY
        FocusProtocolPhase.REVIEW -> FocusCatMood.CELEBRATE
        else -> FocusCatMood.IDLE
    }
    val message = when (phase) {
        FocusProtocolPhase.FOCUS -> if (interaction == 0) {
            stringResource(R.string.focus_cat_focus_message)
        } else {
            stringResource(R.string.focus_cat_focus_tap)
        }
        FocusProtocolPhase.RECOVERY -> when (interaction % 3) {
            1 -> stringResource(R.string.focus_cat_petted)
            2 -> stringResource(R.string.focus_cat_stretch)
            else -> stringResource(R.string.focus_cat_rest_message)
        }
        FocusProtocolPhase.CYCLE_READY -> stringResource(R.string.focus_cat_ready_message, cycle)
        FocusProtocolPhase.REVIEW -> stringResource(R.string.focus_cat_review_message)
        else -> if (interaction == 0) stringResource(R.string.focus_cat_tap_hint)
        else stringResource(R.string.focus_cat_petted)
    }

    Card(
        modifier = modifier
            .pointerInput(phase) {
                detectHorizontalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = {
                        if (kotlin.math.abs(dragTotal) > 45f) interaction++
                        dragTotal = 0f
                    }
                ) { change, amount ->
                    change.consume()
                    dragTotal += amount
                }
            },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.appAccents.calm.container,
            contentColor = MaterialTheme.appAccents.calm.onContainer
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedFocusCat(
                mood = mood,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 30.dp),
                onInteract = { interaction++ }
            )
            Text(
                text = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.appAccents.calm.onContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BlockProgress(
    session: FocusProtocolSessionEntity,
    soundscapeState: FocusSoundscapeUiState?,
    onOpenSoundscape: () -> Unit,
    onToggleSoundscape: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = phaseTitle(session.phase),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = session.itemName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.focus_block_cycles_count, session.completedCycles),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.appAccents.focus.container)
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.appAccents.focus.color
                )
                soundscapeState?.let { state ->
                    ActiveFocusSoundButton(
                        state = state,
                        onOpenPicker = onOpenSoundscape,
                        onTogglePlayback = onToggleSoundscape,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        PhaseRail(session.phase)
    }
}

@Composable
private fun PhaseRail(phase: FocusProtocolPhase) {
    val selected = when (phase) {
        FocusProtocolPhase.RESET -> 0
        FocusProtocolPhase.ACTIVATE -> 1
        FocusProtocolPhase.FOCUS, FocusProtocolPhase.FOCUS_PAUSED -> 2
        FocusProtocolPhase.RECOVERY, FocusProtocolPhase.CYCLE_READY -> 3
        else -> 4
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        repeat(5) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (index <= selected) MaterialTheme.appAccents.focus.color
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
}

@Composable
private fun GoalChip(itemName: String, outcome: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = itemName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.appAccents.focus.color
        )
        Text(
            text = outcome,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecoveryPrompt(cycle: Int) {
    val message = when (cycle % 3) {
        1 -> stringResource(R.string.focus_rest_look_away)
        2 -> stringResource(R.string.focus_rest_water)
        else -> stringResource(R.string.focus_rest_shoulders)
    }
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.appAccents.calm.container)
            .padding(13.dp),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun BlockSummary(session: FocusProtocolSessionEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.appAccents.success.container,
            contentColor = MaterialTheme.appAccents.success.onContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryValue(
                value = session.completedCycles.toString(),
                label = stringResource(R.string.focus_block_cycles_label)
            )
            SummaryValue(
                value = formatCompactDuration(session.totalFocusMillis),
                label = stringResource(R.string.focus_block_focus_time_label)
            )
            SummaryValue(
                value = session.distractionCount.toString(),
                label = stringResource(R.string.focus_block_distractions_label)
            )
        }
    }
}

@Composable
private fun SummaryValue(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwitchTargetSheet(
    session: FocusProtocolSessionEntity,
    targets: List<FocusProtocolTarget>,
    onSelect: (FocusProtocolTarget, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedId by rememberSaveable { mutableIntStateOf(session.itemId) }
    var outcome by rememberSaveable { mutableStateOf(session.outcome) }
    val selected = targets.firstOrNull { it.id == selectedId }
    ThemedModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.focus_block_switch_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            TargetRibbon(targets, selectedId) { selectedId = it.id }
            OutlinedTextField(
                value = outcome,
                onValueChange = { outcome = it },
                label = { Text(stringResource(R.string.focus_protocol_outcome)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { selected?.let { onSelect(it, outcome.trim()) } },
                enabled = selected != null && outcome.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.focus_block_start_next))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun CompletedFocusBlocksCard(
    blocks: List<FocusProtocolSessionEntity>,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.focus_history_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (blocks.isEmpty()) {
                Text(
                    text = stringResource(R.string.focus_history_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                blocks.take(8).forEach { block ->
                    val date = remember(block.completedAt, locale) {
                        Instant.ofEpochMilli(block.completedAt ?: block.createdAt)
                            .atZone(ZoneId.systemDefault())
                            .format(
                                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                                    .withLocale(locale)
                            )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(15.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = block.itemName,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = date,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = block.outcome,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(
                                R.string.focus_history_result,
                                block.completedCycles,
                                formatCompactDuration(block.totalFocusMillis)
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.appAccents.focus.color
                        )
                        block.energyAfter?.let { after ->
                            Text(
                                text = stringResource(
                                    R.string.focus_history_energy,
                                    block.energyBefore,
                                    after,
                                    formatSignedEnergyDelta(after - block.energyBefore)
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EnergyPatternCard(
    points: List<EnergyHourPoint>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.focus_protocol_energy_chart),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (points.isEmpty()) {
                Text(
                    text = stringResource(R.string.focus_protocol_energy_chart_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val values = remember(points) { points.associateBy { it.hour } }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    repeat(24) { hour ->
                        val point = values[hour]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(1.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                EnergyPatternBar(
                                    value = point?.averageBefore,
                                    color = MaterialTheme.appAccents.focus.color,
                                    modifier = Modifier.weight(1f)
                                )
                                EnergyPatternBar(
                                    value = point?.averageAfter,
                                    color = MaterialTheme.appAccents.success.color,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("0", "6", "12", "18", "23").forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(
                    text = stringResource(R.string.focus_protocol_energy_chart_legend),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.focus_protocol_energy_samples,
                        points.sumOf { it.sampleCount }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EnergyPatternBar(
    value: Float?,
    color: Color,
    modifier: Modifier = Modifier
) {
    val safeValue = value?.coerceIn(0f, 10f) ?: 0f
    Box(
        modifier = modifier
            .height((safeValue / 10f * 64f).coerceAtLeast(if (value == null) 0f else 2f).dp)
            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
            .background(if (value != null) color else Color.Transparent)
    )
}

private fun formatSignedEnergyDelta(delta: Int): String = when {
    delta > 0 -> "+$delta"
    else -> delta.toString()
}

@Composable
private fun phaseTitle(phase: FocusProtocolPhase): String = stringResource(
    when (phase) {
        FocusProtocolPhase.RESET -> R.string.focus_protocol_phase_reset
        FocusProtocolPhase.ACTIVATE -> R.string.focus_protocol_phase_activate
        FocusProtocolPhase.FOCUS -> R.string.focus_protocol_phase_focus
        FocusProtocolPhase.FOCUS_PAUSED -> R.string.focus_protocol_phase_paused
        FocusProtocolPhase.RECOVERY -> R.string.focus_protocol_phase_recovery
        FocusProtocolPhase.CYCLE_READY -> R.string.focus_protocol_phase_cycle_ready
        else -> R.string.focus_protocol_phase_review
    }
)

@Composable
private fun phaseDescription(phase: FocusProtocolPhase): String = stringResource(
    when (phase) {
        FocusProtocolPhase.RESET -> R.string.focus_protocol_reset_description
        FocusProtocolPhase.ACTIVATE -> R.string.focus_protocol_activate_description
        FocusProtocolPhase.FOCUS -> R.string.focus_protocol_focus_description
        FocusProtocolPhase.FOCUS_PAUSED -> R.string.focus_protocol_paused_description
        FocusProtocolPhase.RECOVERY -> R.string.focus_protocol_recovery_description
        FocusProtocolPhase.CYCLE_READY -> R.string.focus_block_cycle_ready_description
        else -> R.string.focus_protocol_phase_review
    }
)

internal fun formatFocusClock(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) + 999L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

internal fun formatCompactDuration(millis: Long): String {
    val minutes = millis.coerceAtLeast(0L) / 60_000L
    return if (minutes >= 60L) "%d:%02d".format(minutes / 60L, minutes % 60L)
    else "${minutes}м"
}

internal fun snapFocusSetting(raw: Float, minimum: Int, maximum: Int, step: Int): Int {
    val safeStep = step.coerceAtLeast(1)
    val snapped = ((raw - minimum) / safeStep).roundToInt() * safeStep + minimum
    return snapped.coerceIn(minimum, maximum)
}
