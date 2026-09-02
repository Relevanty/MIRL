package com.personal.sleepalarm.ui.home

import com.personal.sleepalarm.ui.theme.appAccents
import com.personal.sleepalarm.ui.theme.AppAccentTone

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.domain.calculator.DailyTaskFocusProgress
import com.personal.sleepalarm.domain.automation.isAutomationArmed
import com.personal.sleepalarm.domain.automation.isAutomationPausedForFocus
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.SleepPlan
import com.personal.sleepalarm.domain.model.ordinaryTasks
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.domain.model.remainingWorkMillisOrNull
import com.personal.sleepalarm.ui.components.PermissionBanners
import com.personal.sleepalarm.ui.components.WarningCard
import com.personal.sleepalarm.ui.dday.DDayViewModel
import com.personal.sleepalarm.ui.dday.NearestDDay
import com.personal.sleepalarm.ui.stats.StatsScreen
import com.personal.sleepalarm.ui.stats.StatsViewModel
import com.personal.sleepalarm.ui.theme.ThemedModalBottomSheet
import com.personal.sleepalarm.ui.mood.MorningCheckInDialog
import com.personal.sleepalarm.ui.mood.EnergyCheckInDialog
import com.personal.sleepalarm.util.PermissionChecker
import com.personal.sleepalarm.util.TimeFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.ceil
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenDiary: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenStats: (() -> Unit)? = null,
    onOpenDDay: () -> Unit = {},
    onOpenAssistant: () -> Unit = {},
    onOpenEnglishLearning: () -> Unit = {},
    onOpenMathPractice: () -> Unit = {},
    openTaskCount: Int = 0,
    upcomingTasks: List<TaskEntity> = emptyList(),
    onStartTaskFocus: (TaskEntity, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val quickNotes by viewModel.quickNotes.collectAsStateWithLifecycle()
    val adaptivePlan by viewModel.adaptivePlan.collectAsStateWithLifecycle()
    val dailyTaskProgress by viewModel.dailyTaskProgress.collectAsStateWithLifecycle()
    val statsViewModel: StatsViewModel = viewModel()
    val dDayViewModel: DDayViewModel = viewModel()
    val nearestDDay by dDayViewModel.nearest.collectAsStateWithLifecycle()
    val visibleDDay = nearestDDay?.takeIf(dDayViewModel::isBadgeVisible)
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var showStats by remember { mutableStateOf(false) }
    var showQuickNotes by remember { mutableStateOf(false) }
    var showHomeTools by remember { mutableStateOf(false) }
    var showMorningCheckIn by remember { mutableStateOf(false) }
    var showRecoveryCheckIn by remember { mutableStateOf(false) }
    val ordinaryUpcomingTasks = remember(adaptivePlan.orderedTasks, upcomingTasks) {
        (adaptivePlan.orderedTasks.ifEmpty { upcomingTasks }).ordinaryTasks()
    }

    if (showStats) {
        StatsScreen(
            viewModel = statsViewModel,
            onBack = { showStats = false },
            modifier = modifier
        )
        return
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.takeIf(String::isNotBlank)?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val compactHeight = maxHeight < 680.dp
            val sectionSpacing = if (compactHeight) 6.dp else 8.dp
            val taskCardHeight = if (compactHeight) 136.dp else 156.dp
            val contextStripHeight = if (compactHeight) 88.dp else 104.dp
            val sleepButtonHeight = if (compactHeight) 76.dp else 80.dp
            val activeSleepSession = state.activeSession
            val catState = when {
                activeSleepSession?.isAutomationPausedForFocus() == true -> SleepCatState.AWAKE
                activeSleepSession?.isAutomationArmed() == true -> SleepCatState.DETECTING
                activeSleepSession?.detectedSleepOnsetTime != null -> SleepCatState.SLEEPING
                activeSleepSession != null && state.now - activeSleepSession.bedTimePlanned < 10L * 60_000L ->
                    SleepCatState.PREPARING
                activeSleepSession != null -> SleepCatState.DETECTING
                state.latestCompletedSession?.actualWakeTime?.let { state.now - it < 6L * 60L * 60_000L } == true ->
                    SleepCatState.MORNING
                else -> SleepCatState.AWAKE
            }

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    SleepPlanWithCat(
                        plan = state.plan,
                        activeSession = activeSleepSession,
                        catState = catState,
                        compact = compactHeight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    Spacer(Modifier.height(sectionSpacing))
                    DayContextStrip(
                        adaptivePlan = adaptivePlan,
                        nowMillis = state.now,
                        compact = compactHeight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(contextStripHeight)
                    )
                    Spacer(Modifier.height(sectionSpacing))
                    TodayDynamicCard(
                        activeSession = activeSleepSession,
                        latestCompleted = state.latestCompletedSession,
                        now = state.now,
                        tasks = ordinaryUpcomingTasks,
                        dailyProgressByTask = dailyTaskProgress,
                        nearestDDay = visibleDDay,
                        adaptivePlan = adaptivePlan,
                        compact = compactHeight,
                        onOpenTasks = onOpenTasks,
                        onOpenDDay = onOpenDDay,
                        onStartFocus = onStartTaskFocus,
                        onMorningCheckIn = { showMorningCheckIn = true },
                        onRecoveryCheckIn = { showRecoveryCheckIn = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(taskCardHeight)
                    )
                }
                Spacer(Modifier.height(if (compactHeight) 10.dp else 12.dp))
                StartButtons(
                    activeSession = state.activeSession,
                    canStart = state.plan != null && state.permissions.exactAlarmsAllowed,
                    buttonHeight = sleepButtonHeight,
                    onStart = viewModel::startSleepSession,
                    onCancelActive = viewModel::cancelActiveSession
                )
                Spacer(Modifier.height(if (compactHeight) 14.dp else 18.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    QuickAccessPill(
                        label = stringResource(R.string.quick_notes_title),
                        onClick = { showQuickNotes = true },
                        containerColor = MaterialTheme.appAccents.creative.action,
                        contentColor = MaterialTheme.appAccents.creative.onAction,
                        modifier = Modifier
                            .weight(0.44f)
                            .height(48.dp)
                    )
                    Spacer(Modifier.weight(0.12f))
                    QuickAccessPill(
                        label = stringResource(R.string.diary_title),
                        onClick = onOpenDiary,
                        containerColor = MaterialTheme.appAccents.leisure.action,
                        contentColor = MaterialTheme.appAccents.leisure.onAction,
                        modifier = Modifier
                            .weight(0.44f)
                            .height(48.dp)
                    )
                }
            }

            IconButton(
                onClick = { showHomeTools = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = stringResource(R.string.home_action_sections),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
                )
            }
        }
    }

    if (showHomeTools) {
        ThemedModalBottomSheet(onDismissRequest = { showHomeTools = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_action_sections),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                state.activeSession?.let { active ->
                    ActiveSessionCard(
                        activeSession = active,
                        onCancel = viewModel::cancelActiveSession,
                        onSkipAutomation = viewModel::skipSleepAutomationTonight,
                        onRejectDetectedOnset = viewModel::rejectDetectedSleepOnset
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeToolTile(
                        icon = Icons.Default.BarChart,
                        label = stringResource(R.string.action_open_stats),
                        tone = MaterialTheme.appAccents.progress,
                        onClick = {
                            showHomeTools = false
                            onOpenStats?.invoke() ?: run { showStats = true }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    HomeToolTile(
                        icon = Icons.Default.SmartToy,
                        label = stringResource(R.string.misc_assistant),
                        tone = MaterialTheme.appAccents.info,
                        onClick = {
                            showHomeTools = false
                            onOpenAssistant()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeToolTile(
                        icon = Icons.Default.Translate,
                        label = stringResource(R.string.language_english),
                        tone = MaterialTheme.appAccents.study,
                        onClick = {
                            showHomeTools = false
                            onOpenEnglishLearning()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    HomeToolTile(
                        icon = Icons.Default.Calculate,
                        label = stringResource(R.string.math_practice_open),
                        tone = MaterialTheme.appAccents.creative,
                        onClick = {
                            showHomeTools = false
                            onOpenMathPractice()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                PermissionBanners(
                    state = state.permissions,
                    onOpenExactAlarmSettings = {
                        runCatching { context.startActivity(PermissionChecker.exactAlarmsIntent(context)) }
                    },
                    onOpenNotificationSettings = {
                        runCatching { context.startActivity(PermissionChecker.notificationsIntent(context)) }
                    },
                    onOpenBatterySettings = {
                        runCatching { context.startActivity(PermissionChecker.batteryOptimizationIntent(context)) }
                    },
                    onOpenFullScreenSettings = {
                        runCatching { context.startActivity(PermissionChecker.fullScreenIntentSettings(context)) }
                    },
                    onOpenNotificationPolicySettings = {
                        runCatching { context.startActivity(PermissionChecker.notificationPolicyIntent(context)) }
                    }
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showQuickNotes) {
        QuickNotesDialog(
            initialText = quickNotes,
            onSave = viewModel::updateQuickNotes,
            onDismiss = { showQuickNotes = false }
        )
    }
    if (showMorningCheckIn) {
        MorningCheckInDialog(
            onSubmit = { input ->
                viewModel.saveMorningRecheck(input)
                showMorningCheckIn = false
            },
            onSkip = { showMorningCheckIn = false }
        )
    }
    if (showRecoveryCheckIn) {
        EnergyCheckInDialog(
            title = stringResource(R.string.energy_recovery_title),
            supportingText = stringResource(R.string.energy_recovery_hint),
            onSubmit = { energy ->
                viewModel.saveRecoveryEnergy(
                    energy = energy,
                    taskId = adaptivePlan.recoveryTaskId,
                    focusProtocolSessionId = adaptivePlan.recoveryFocusSessionId
                )
                showRecoveryCheckIn = false
            },
            onSkip = { showRecoveryCheckIn = false }
        )
    }
}

@Composable
private fun FittedSingleLineText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    minFontSize: TextUnit = 9.sp,
    textAlign: TextAlign? = null
) {
    val baseFontSize = style.fontSize
    var fittedFontSize by remember(text, baseFontSize, minFontSize) {
        mutableStateOf(baseFontSize)
    }
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(fontSize = fittedFontSize),
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (result.didOverflowWidth && fittedFontSize.value > minFontSize.value) {
                fittedFontSize = (fittedFontSize.value - 0.75f)
                    .coerceAtLeast(minFontSize.value)
                    .sp
            }
        }
    )
}

@Composable
private fun TodayDynamicCard(
    activeSession: SleepSessionEntity?,
    latestCompleted: SleepSessionEntity?,
    now: Long,
    tasks: List<TaskEntity>,
    dailyProgressByTask: Map<Int, DailyTaskFocusProgress>,
    nearestDDay: NearestDDay?,
    adaptivePlan: AdaptiveHomePlan,
    compact: Boolean,
    onOpenTasks: () -> Unit,
    onOpenDDay: () -> Unit,
    onStartFocus: (TaskEntity, Int) -> Unit,
    onMorningCheckIn: () -> Unit,
    onRecoveryCheckIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val morningResult = latestCompleted?.takeIf {
        activeSession == null && it.actualWakeTime?.let { wake -> now - wake < 6L * 60L * 60_000L } == true
    }
    val task = tasks.firstOrNull { candidate ->
        homeNextFocusMinutes(candidate, dailyProgressByTask[candidate.id]) > 0
    }
    val allDailyTargetsComplete = tasks.isNotEmpty() && task == null
    val checkInAction = when {
        adaptivePlan.shouldOfferMorningCheckIn -> onMorningCheckIn
        adaptivePlan.shouldOfferRecoveryCheckIn -> onRecoveryCheckIn
        else -> null
    }
    val checkInDescription = when {
        adaptivePlan.shouldOfferMorningCheckIn -> stringResource(R.string.adaptive_morning_recheck_action)
        adaptivePlan.shouldOfferRecoveryCheckIn -> stringResource(R.string.energy_recovery_action)
        else -> null
    }
    val cardTone = when {
        activeSession != null -> MaterialTheme.appAccents.sleep
        morningResult != null -> MaterialTheme.appAccents.success
        task != null -> MaterialTheme.appAccents.focus
        else -> MaterialTheme.appAccents.schedule
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardTone.container,
            contentColor = cardTone.onContainer
        ),
        modifier = modifier.then(
            if (activeSession == null && morningResult == null) {
                Modifier.clickable(onClick = onOpenTasks)
            } else Modifier
        )
    ) {
        when {
            activeSession != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = if (compact) 6.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(
                    if (compact) 3.dp else 4.dp,
                    Alignment.CenterVertically
                )
            ) {
                val trackingPaused = activeSession.isAutomationPausedForFocus()
                val onsetDetected = activeSession.detectedSleepOnsetTime != null
                Text(
                    text = stringResource(
                        when {
                            trackingPaused -> R.string.home_sleep_tracking_paused_title
                            onsetDetected -> R.string.home_sleep_tracking_detected_title
                            else -> R.string.home_sleep_tracking_title
                        }
                    ),
                    style = if (compact) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.appAccents.sleep.color,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (trackingPaused) {
                        stringResource(
                            R.string.home_sleep_tracking_paused_status,
                            TimeFormatter.formatEpochMillis(
                                activeSession.estimatedWakeTime,
                                activeSession.zoneId
                            )
                        )
                    } else if (activeSession.isAutomationArmed()) {
                        stringResource(
                            R.string.sleep_automation_waiting_card,
                            TimeFormatter.formatEpochMillis(
                                activeSession.estimatedWakeTime,
                                activeSession.zoneId
                            )
                        )
                    } else {
                        stringResource(
                            R.string.active_session_text,
                            TimeFormatter.formatEpochMillis(
                                activeSession.estimatedWakeTime,
                                activeSession.zoneId
                            )
                        )
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = cardTone.onContainer
                )
                Text(
                    text = stringResource(
                        when {
                            trackingPaused -> R.string.home_sleep_tracking_paused_body
                            onsetDetected -> R.string.home_sleep_tracking_detected_body
                            else -> R.string.home_sleep_tracking_body
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = cardTone.onContainer.copy(alpha = 0.78f),
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            morningResult != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = if (compact) 6.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(
                    if (compact) 4.dp else 6.dp,
                    Alignment.CenterVertically
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.home_sleep_morning_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.appAccents.success.color,
                        fontWeight = FontWeight.SemiBold
                    )
                    checkInAction?.let { action ->
                        TextButton(onClick = action) {
                            FittedSingleLineText(
                                text = checkInDescription.orEmpty(),
                                style = MaterialTheme.typography.labelLarge,
                                minFontSize = 10.sp
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.home_sleep_morning_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cardTone.onContainer.copy(alpha = 0.78f),
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            task != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = if (compact) 6.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.adaptive_main_now),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.appAccents.focus.color,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    nearestDDay?.let { deadline ->
                        HomeDDayBadge(
                            nearest = deadline,
                            onClick = onOpenDDay,
                            compact = compact
                        )
                    }
                    checkInAction?.let { action ->
                        IconButton(
                            onClick = action,
                            modifier = Modifier.size(if (compact) 28.dp else 32.dp)
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = checkInDescription,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.appAccents.warning.color
                            )
                        }
                    }
                }
                Text(
                    task.primaryLabel(),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val progress = dailyProgressByTask[task.id]
                val targetMinutes = progress?.targetMinutes ?: task.plannedFocusMinutes.coerceAtLeast(0)
                val spentMinutes = progress?.spentMinutes ?: 0
                val remainingTodayMinutes = homeDailyRemainingMinutes(task, progress)
                val nextFocusMinutes = homeNextFocusMinutes(task, progress)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FittedSingleLineText(
                        text = if (remainingTodayMinutes <= 0) {
                            stringResource(
                                R.string.daily_focus_home_today_complete,
                                spentMinutes,
                                targetMinutes
                            )
                        } else {
                            stringResource(
                                R.string.daily_focus_home_today_progress,
                                spentMinutes,
                                targetMinutes,
                                remainingTodayMinutes
                            )
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.appAccents.focus.color,
                        minFontSize = 9.sp
                    )
                    task.dueAtMillis?.let { dueAt ->
                        TaskDeadlineBadge(dueAtMillis = dueAt, nowMillis = now)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        AdaptivePlanExplanation(adaptivePlan, task.id)
                    }
                    Button(
                        onClick = { onStartFocus(task, nextFocusMinutes) },
                        enabled = nextFocusMinutes > 0,
                        modifier = Modifier.height(if (compact) 36.dp else 40.dp)
                    ) {
                        Text(
                            stringResource(R.string.home_action_focus),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
            else -> Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FittedSingleLineText(
                        stringResource(
                            if (allDailyTargetsComplete) R.string.home_daily_targets_complete_title
                            else R.string.home_no_tasks_title
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = cardTone.onContainer,
                        minFontSize = 10.sp
                    )
                    FittedSingleLineText(
                        stringResource(
                            if (allDailyTargetsComplete) R.string.home_daily_targets_complete_body
                            else R.string.home_no_tasks_body
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = cardTone.onContainer.copy(alpha = 0.78f),
                        minFontSize = 9.sp
                    )
                }
                nearestDDay?.let { deadline ->
                    HomeDDayBadge(
                        nearest = deadline,
                        onClick = onOpenDDay,
                        compact = compact
                    )
                }
                checkInAction?.let { action ->
                    IconButton(onClick = action) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = checkInDescription,
                            tint = MaterialTheme.appAccents.warning.color
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdaptivePlanExplanation(plan: AdaptiveHomePlan, taskId: Int) {
    val state = plan.personalState ?: return
    val energy = state.estimatedEnergy.roundToInt().coerceIn(1, 10)
    val confidence = (state.confidence.value * 100).roundToInt().coerceIn(0, 100)
    val reason = when (plan.reasonByTaskId[taskId] ?: plan.topReason) {
        AdaptivePlanReason.DEADLINE -> stringResource(R.string.adaptive_reason_deadline)
        AdaptivePlanReason.REQUIRED -> stringResource(R.string.adaptive_reason_required)
        AdaptivePlanReason.ENERGY_MATCH -> stringResource(R.string.adaptive_reason_energy)
        AdaptivePlanReason.CAPACITY_MATCH -> stringResource(R.string.adaptive_reason_capacity)
        AdaptivePlanReason.DEFAULT_ORDER -> stringResource(R.string.adaptive_reason_default)
    }
    FittedSingleLineText(
        text = "$energy/10 · $confidence% · $reason",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        minFontSize = 8.sp
    )
}

internal enum class HomeDaylightPhase {
    BEFORE_SUNRISE,
    DAYLIGHT,
    AFTER_SUNSET,
    POLAR_DAY,
    POLAR_NIGHT,
    UNKNOWN
}

internal data class HomeDaylightProgress(
    val phase: HomeDaylightPhase,
    val elapsedMinutes: Int,
    val remainingMinutes: Int,
    val minutesUntilSunrise: Int,
    val fraction: Float
)

internal fun calculateHomeDaylightProgress(
    nowMillis: Long,
    sunriseMillis: Long?,
    sunsetMillis: Long?,
    daylightMinutes: Int
): HomeDaylightProgress {
    val safeTotal = daylightMinutes.coerceIn(0, 24 * 60)
    if (sunriseMillis == null || sunsetMillis == null || sunsetMillis <= sunriseMillis) {
        val phase = when (safeTotal) {
            0 -> HomeDaylightPhase.POLAR_NIGHT
            24 * 60 -> HomeDaylightPhase.POLAR_DAY
            else -> HomeDaylightPhase.UNKNOWN
        }
        return HomeDaylightProgress(
            phase = phase,
            elapsedMinutes = if (phase == HomeDaylightPhase.POLAR_DAY) safeTotal else 0,
            remainingMinutes = if (phase == HomeDaylightPhase.POLAR_DAY) 0 else safeTotal,
            minutesUntilSunrise = 0,
            fraction = if (phase == HomeDaylightPhase.POLAR_DAY) 1f else 0f
        )
    }

    val spanMillis = sunsetMillis - sunriseMillis
    val elapsedMillis = (nowMillis - sunriseMillis).coerceIn(0L, spanMillis)
    val fraction = (elapsedMillis.toDouble() / spanMillis.toDouble()).toFloat().coerceIn(0f, 1f)
    return when {
        nowMillis < sunriseMillis -> HomeDaylightProgress(
            phase = HomeDaylightPhase.BEFORE_SUNRISE,
            elapsedMinutes = 0,
            remainingMinutes = safeTotal,
            minutesUntilSunrise = ceil(
                (sunriseMillis - nowMillis) / HOME_MINUTE_MILLIS.toDouble()
            ).toInt(),
            fraction = 0f
        )
        nowMillis >= sunsetMillis -> HomeDaylightProgress(
            phase = HomeDaylightPhase.AFTER_SUNSET,
            elapsedMinutes = safeTotal,
            remainingMinutes = 0,
            minutesUntilSunrise = 0,
            fraction = 1f
        )
        else -> HomeDaylightProgress(
            phase = HomeDaylightPhase.DAYLIGHT,
            elapsedMinutes = (elapsedMillis / HOME_MINUTE_MILLIS).toInt().coerceAtMost(safeTotal),
            remainingMinutes = ceil(
                (sunsetMillis - nowMillis) / HOME_MINUTE_MILLIS.toDouble()
            ).toInt().coerceAtMost(safeTotal),
            minutesUntilSunrise = 0,
            fraction = fraction
        )
    }
}

@Composable
private fun DayContextStrip(
    adaptivePlan: AdaptiveHomePlan,
    nowMillis: Long,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val daylightMinutes = adaptivePlan.daylightMinutes
    val temperature = adaptivePlan.temperatureCelsius?.roundToInt()
    val hasWeather = temperature != null || adaptivePlan.weatherCode != null
    if (daylightMinutes == null && !hasWeather) return

    val progress = daylightMinutes?.let { totalMinutes ->
        calculateHomeDaylightProgress(
            nowMillis = nowMillis,
            sunriseMillis = adaptivePlan.sunriseMillis,
            sunsetMillis = adaptivePlan.sunsetMillis,
            daylightMinutes = totalMinutes
        )
    }
    val condition = homeWeatherCondition(adaptivePlan.weatherCode)
    val weatherSummary = listOfNotNull(
        temperature?.let(::formatHomeTemperature),
        condition
    ).joinToString(" · ")
    val headerSummary = weatherSummary.ifBlank {
        daylightMinutes?.let { TimeFormatter.formatMinutes(it.toLong()) }.orEmpty()
    }
    val metrics = mutableListOf<HomeContextMetric>()
    if (daylightMinutes != null && progress != null) {
        when (progress.phase) {
            HomeDaylightPhase.DAYLIGHT -> {
                metrics += HomeContextMetric(
                    stringResource(R.string.home_context_elapsed),
                    TimeFormatter.formatMinutes(progress.elapsedMinutes.toLong()),
                    HomeContextTone.DAYLIGHT
                )
                metrics += HomeContextMetric(
                    stringResource(R.string.home_context_until_sunset),
                    TimeFormatter.formatMinutes(progress.remainingMinutes.toLong()),
                    HomeContextTone.COUNTDOWN
                )
            }
            HomeDaylightPhase.BEFORE_SUNRISE -> {
                metrics += HomeContextMetric(
                    stringResource(R.string.home_context_until_sunrise),
                    TimeFormatter.formatMinutes(progress.minutesUntilSunrise.toLong()),
                    HomeContextTone.COUNTDOWN
                )
                metrics += HomeContextMetric(
                    stringResource(R.string.home_context_daylight_amount),
                    TimeFormatter.formatMinutes(daylightMinutes.toLong()),
                    HomeContextTone.DAYLIGHT
                )
            }
            else -> metrics += HomeContextMetric(
                stringResource(R.string.home_context_day_length),
                TimeFormatter.formatMinutes(daylightMinutes.toLong()),
                HomeContextTone.DAYLIGHT
            )
        }
    }
    adaptivePlan.relativeHumidityPercent?.let { humidity ->
        metrics += HomeContextMetric(
            stringResource(R.string.home_context_humidity_title),
            stringResource(R.string.home_context_humidity_value, humidity),
            HomeContextTone.HUMIDITY
        )
    }
    adaptivePlan.windSpeedKilometersPerHour?.roundToInt()?.let { wind ->
        metrics += HomeContextMetric(
            stringResource(R.string.home_context_wind_title),
            stringResource(R.string.home_context_wind_value, wind),
            HomeContextTone.WIND
        )
    }
    var apparentShownAsMetric = false
    if (metrics.size < 4) {
        adaptivePlan.apparentTemperatureCelsius?.roundToInt()?.let { feels ->
            metrics += HomeContextMetric(
                stringResource(R.string.home_context_apparent_title),
                formatHomeTemperature(feels),
                HomeContextTone.TEMPERATURE
            )
            apparentShownAsMetric = true
        }
    }
    var precipitationShownAsMetric = false
    if (metrics.size < 4) {
        adaptivePlan.precipitationMillimeters?.takeIf { it >= 0.1 }?.let { precipitation ->
            metrics += HomeContextMetric(
                stringResource(R.string.home_context_precipitation_title),
                stringResource(R.string.home_context_precipitation_value, precipitation),
                HomeContextTone.PRECIPITATION
            )
            precipitationShownAsMetric = true
        }
    }

    val safeZone = adaptivePlan.daylightZoneId ?: ZoneId.systemDefault().id
    val phaseStatus = when (progress?.phase) {
        HomeDaylightPhase.AFTER_SUNSET -> stringResource(R.string.home_context_after_sunset)
        HomeDaylightPhase.POLAR_DAY -> stringResource(R.string.home_context_polar_day)
        HomeDaylightPhase.POLAR_NIGHT -> stringResource(R.string.home_context_polar_night)
        else -> null
    }
    val sunWindow = if (adaptivePlan.sunriseMillis != null && adaptivePlan.sunsetMillis != null) {
        stringResource(
            R.string.home_context_sun_window,
            TimeFormatter.formatEpochMillis(adaptivePlan.sunriseMillis, safeZone),
            TimeFormatter.formatEpochMillis(adaptivePlan.sunsetMillis, safeZone)
        )
    } else null
    val feelsLike = if (apparentShownAsMetric) null else {
        adaptivePlan.apparentTemperatureCelsius?.roundToInt()?.let { value ->
            stringResource(R.string.home_context_feels_like_compact, formatHomeTemperature(value))
        }
    }
    val precipitation = if (precipitationShownAsMetric) null else {
        adaptivePlan.precipitationMillimeters
            ?.takeIf { it >= 0.1 }
            ?.let { value -> stringResource(R.string.home_context_precipitation_compact, value) }
    }
    val footer = listOfNotNull(phaseStatus, sunWindow, feelsLike, precipitation)
        .joinToString(" · ")

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.appAccents.info.container,
        contentColor = MaterialTheme.appAccents.info.onContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 7.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (daylightMinutes != null) Icons.Default.LightMode
                    else Icons.Default.Thermostat,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = if (daylightMinutes != null) MaterialTheme.appAccents.schedule.color
                    else MaterialTheme.appAccents.info.color
                )
                FittedSingleLineText(
                    text = stringResource(
                        if (daylightMinutes != null) R.string.home_context_daylight_title
                        else R.string.home_context_weather_today
                    ),
                    modifier = Modifier.padding(start = 6.dp).weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    minFontSize = 10.sp
                )
                FittedSingleLineText(
                    text = headerSummary,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (hasWeather) MaterialTheme.appAccents.info.color
                    else MaterialTheme.appAccents.schedule.color,
                    fontWeight = FontWeight.SemiBold,
                    minFontSize = 9.sp,
                    textAlign = TextAlign.End
                )
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.appAccents.schedule.color,
                    trackColor = MaterialTheme.appAccents.schedule.action
                )
            }
            if (metrics.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 28.dp else 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    metrics.take(4).forEachIndexed { index, metric ->
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(if (compact) 20.dp else 24.dp)
                                    .background(MaterialTheme.appAccents.info.color.copy(alpha = 0.42f))
                            )
                        }
                        HomeContextMetricCell(
                            metric = metric,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (footer.isNotBlank()) {
                FittedSingleLineText(
                    text = footer,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appAccents.info.onContainer.copy(alpha = 0.78f),
                    minFontSize = 8.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private data class HomeContextMetric(
    val label: String,
    val value: String,
    val tone: HomeContextTone
)

private enum class HomeContextTone {
    DAYLIGHT,
    COUNTDOWN,
    HUMIDITY,
    WIND,
    TEMPERATURE,
    PRECIPITATION
}

@Composable
private fun HomeContextMetricCell(
    metric: HomeContextMetric,
    modifier: Modifier = Modifier
) {
    val valueColor = when (metric.tone) {
        HomeContextTone.DAYLIGHT -> MaterialTheme.appAccents.schedule.color
        HomeContextTone.COUNTDOWN -> MaterialTheme.appAccents.progress.color
        HomeContextTone.HUMIDITY -> MaterialTheme.appAccents.info.color
        HomeContextTone.WIND -> MaterialTheme.appAccents.energy.color
        HomeContextTone.TEMPERATURE -> MaterialTheme.appAccents.calm.color
        HomeContextTone.PRECIPITATION -> MaterialTheme.appAccents.other.color
    }
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FittedSingleLineText(
            text = metric.label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = lerp(MaterialTheme.appAccents.info.onContainer, valueColor, 0.28f),
            minFontSize = 8.sp,
            textAlign = TextAlign.Center
        )
        FittedSingleLineText(
            text = metric.value,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
            minFontSize = 9.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun homeWeatherCondition(code: Int?): String? = code?.let { value ->
    stringResource(
        when (value) {
            0 -> R.string.home_weather_clear
            in 1..2 -> R.string.home_weather_partly_cloudy
            3 -> R.string.home_weather_cloudy
            in 45..48 -> R.string.home_weather_fog
            in 51..67, in 80..82 -> R.string.home_weather_rain
            in 71..77, in 85..86 -> R.string.home_weather_snow
            in 95..99 -> R.string.home_weather_thunderstorm
            else -> R.string.home_weather_variable
        }
    )
}

private fun formatHomeTemperature(value: Int): String = when {
    value > 0 -> "+$value°"
    value < 0 -> "−${-value}°"
    else -> "0°"
}

@Composable
private fun HomeDDayBadge(
    nearest: NearestDDay,
    onClick: () -> Unit,
    compact: Boolean
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.appAccents.urgent.container,
        contentColor = MaterialTheme.appAccents.urgent.onContainer
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = 3.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(13.dp))
            FittedSingleLineText(
                text = if (nearest.days == 0) {
                    stringResource(R.string.dday_today)
                } else {
                    stringResource(R.string.dday_days_left, nearest.days)
                },
                style = MaterialTheme.typography.labelSmall,
                minFontSize = 8.sp
            )
        }
    }
}

@Composable
private fun TaskDeadlineBadge(dueAtMillis: Long, nowMillis: Long) {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val dueDate = Instant.ofEpochMilli(dueAtMillis).atZone(zone).toLocalDate()
    val formatted = dueDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
    val overdue = dueDate.isBefore(today)
    val urgent = overdue || dueDate == today
    val text = when {
        overdue -> stringResource(R.string.home_task_deadline_overdue, formatted)
        dueDate == today -> stringResource(R.string.home_task_deadline_today)
        dueDate == today.plusDays(1) -> stringResource(R.string.home_task_deadline_tomorrow)
        else -> stringResource(R.string.home_task_deadline_date, formatted)
    }
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (urgent) MaterialTheme.appAccents.urgent.container
        else MaterialTheme.appAccents.schedule.container,
        contentColor = if (urgent) MaterialTheme.appAccents.urgent.onContainer
        else MaterialTheme.appAccents.schedule.onContainer
    ) {
        FittedSingleLineText(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            minFontSize = 8.sp
        )
    }
}

internal fun homeDailyRemainingMinutes(
    task: TaskEntity,
    progress: DailyTaskFocusProgress?
): Int {
    val remainingMillis = progress?.remainingMillis
        ?: task.plannedFocusMinutes.coerceAtLeast(0).toLong() * HOME_MINUTE_MILLIS
    return ceil(remainingMillis / HOME_MINUTE_MILLIS.toDouble()).toInt().coerceAtLeast(0)
}

internal fun homeNextFocusMinutes(
    task: TaskEntity,
    progress: DailyTaskFocusProgress?
): Int {
    val dailyRemainingMillis = progress?.remainingMillis
        ?: task.plannedFocusMinutes.coerceAtLeast(0).toLong() * HOME_MINUTE_MILLIS
    val totalRemainingMillis = task.remainingWorkMillisOrNull()?.let { remaining ->
        (remaining - (progress?.liveAddedMillis ?: 0L)).coerceAtLeast(0L)
    }
    val effectiveRemaining = totalRemainingMillis?.let { minOf(dailyRemainingMillis, it) }
        ?: dailyRemainingMillis
    if (effectiveRemaining <= 0L) return 0
    val remainingMinutes = ceil(effectiveRemaining / HOME_MINUTE_MILLIS.toDouble()).toInt()
    return minOf(task.estimatedMinutes.coerceAtLeast(1), remainingMinutes).coerceAtLeast(1)
}

private const val HOME_MINUTE_MILLIS = 60_000L

@Composable
private fun UpcomingTasksStrip(tasks: List<TaskEntity>, onOpenTasks: () -> Unit) {
    Card(
        onClick = onOpenTasks,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.appAccents.schedule.container,
            contentColor = MaterialTheme.appAccents.schedule.onContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_tasks_next),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.home_tasks_all),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appAccents.schedule.onContainer
                )
            }
            tasks.forEach { task ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        task.primaryLabel(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    task.dueAtMillis?.let {
                        Text(
                            TimeFormatter.formatEpochMillis(it, ZoneId.systemDefault().id),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appAccents.schedule.onContainer.copy(alpha = 0.76f),
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

internal enum class SleepCatState { AWAKE, PREPARING, DETECTING, SLEEPING, MORNING }

internal fun SleepCatState.showsSleepingPose(): Boolean = when (this) {
    SleepCatState.PREPARING,
    SleepCatState.DETECTING,
    SleepCatState.SLEEPING -> true
    SleepCatState.AWAKE,
    SleepCatState.MORNING -> false
}

@Composable
private fun SleepPlanWithCat(
    plan: SleepPlan?,
    activeSession: SleepSessionEntity?,
    catState: SleepCatState,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val isSleeping = catState.showsSleepingPose()
    BoxWithConstraints(modifier = modifier) {
        val densePlan = maxHeight < 300.dp
        val sleepPlanWidth = maxWidth
        val cardEdge = calculateSleepCatCardEdgeDp(maxWidth.value).dp
        val catOverlapInset = if (densePlan) {
            26.dp
        } else {
            (maxWidth * 0.10f).coerceIn(32.dp, 48.dp)
        }
        val catCanvasHeight = cardEdge + 48.dp

        var catPulse by remember { mutableStateOf(false) }
        val pulseScale by animateFloatAsState(
            targetValue = if (catPulse) 1.045f else 1f,
            animationSpec = tween(160),
            label = "sleep_cat_tap"
        )
        LaunchedEffect(catPulse) {
            if (catPulse) {
                kotlinx.coroutines.delay(160)
                catPulse = false
            }
        }

        // Keep the cat and its card as one bottom-anchored group so flexible
        // height is absorbed above them, directly against the context card below.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            PlanSummaryCard(
                plan = plan,
                activeSession = activeSession,
                compact = compact,
                dense = densePlan,
                catOverlapInset = catOverlapInset,
                modifier = Modifier.padding(top = cardEdge)
            )
            val catModifier = Modifier
                    .fillMaxWidth()
                    .height(catCanvasHeight + 8.dp)
                    .offset(y = (-8).dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                    .then(
                        if (catState == SleepCatState.AWAKE) Modifier
                        else Modifier.pointerInput(catState) {
                            detectTapGestures(onTap = { catPulse = true })
                        }
                    )
            GeometricCatBackdrop(
                modifier = catModifier,
                cardEdgeFromTop = cardEdge + 8.dp,
                isSleeping = isSleeping
            )

            if (isSleeping) SleepCatZzz(cardEdge = cardEdge, canvasWidth = sleepPlanWidth)
        }
    }
}

@Composable
private fun SleepCatZzz(
    cardEdge: androidx.compose.ui.unit.Dp,
    canvasWidth: androidx.compose.ui.unit.Dp
) {
    val transition = rememberInfiniteTransition(label = "sleep_cat_zzz")
    val specs = listOf(
        Triple(0, 18.sp, 0.0f),
        Triple(1, 14.sp, 0.46f),
        Triple(2, 11.sp, 0.82f)
    )
    specs.forEach { (index, size, phase) ->
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, delayMillis = (phase * 2400).toInt()),
                repeatMode = RepeatMode.Restart
            ),
            label = "sleep_cat_z_$index"
        )
        Text(
            text = "z",
            modifier = Modifier
                // Start at the mouth and drift up/right; no touch target or ripple.
                .offset(
                    x = canvasWidth * (0.68f + index * 0.055f) + (progress * 14f).dp,
                    y = cardEdge - canvasWidth * (0.15f + index * 0.045f) - (progress * 24f).dp
                )
                .graphicsLayer {
                    alpha = (1f - progress).coerceIn(0f, 1f) * 0.78f
                    scaleX = 0.88f + progress * 0.24f
                    scaleY = 0.88f + progress * 0.24f
                },
            style = MaterialTheme.typography.titleLarge.copy(fontSize = size),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
    }
}

internal fun calculateSleepCatCardEdgeDp(availableWidthDp: Float): Float =
    (availableWidthDp * 0.26f).coerceIn(84f, 108f)

@Composable
private fun GeometricCatBackdrop(
    modifier: Modifier = Modifier,
    cardEdgeFromTop: androidx.compose.ui.unit.Dp,
    isSleeping: Boolean = false
) {
    val focusTone = MaterialTheme.appAccents.focus
    val sleepTone = MaterialTheme.appAccents.sleep
    val studyTone = MaterialTheme.appAccents.study
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val backdrop = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onBackground
    val transition = rememberInfiniteTransition(label = "sleep_cat_breathing")
    val breathingOffset by transition.animateFloat(
        initialValue = if (isSleeping) -2.2f else -0.8f,
        targetValue = if (isSleeping) 2.2f else 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSleeping) 2600 else 4200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sleep_cat_breath"
    )
    val tailSwing by transition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSleeping) 3400 else 2200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sleep_cat_tail"
    )
    val blink by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(180, delayMillis = 2800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sleep_cat_blink"
    )
    Canvas(
        modifier.graphicsLayer { translationY = breathingOffset }
    ) {
        val cardEdge = cardEdgeFromTop.toPx()
        // Keep the head slightly forward of the reclining body.
        val center = Offset(size.width * 0.71f, cardEdge - size.width * 0.082f)
        val radius = size.width * 0.15f
        val featureColor = ink.copy(alpha = 0.34f)

        // The tail rests on the card edge first, then drops softly down its left side.
        val tail = Path().apply {
            moveTo(size.width * 0.235f + tailSwing, cardEdge + radius * 0.01f)
            cubicTo(
                size.width * 0.17f, cardEdge + radius * 0.01f,
                size.width * 0.055f, cardEdge - radius * 0.01f,
                size.width * 0.025f, cardEdge + radius * 0.08f
            )
            cubicTo(
                size.width * -0.005f, cardEdge + radius * 0.18f,
                size.width * 0.0f, cardEdge + radius * 0.42f,
                size.width * 0.015f, cardEdge + radius * 0.50f
            )
        }
        drawPath(
            path = tail,
            color = backdrop,
            style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = tail,
            color = studyTone.fill.copy(alpha = 0.29f),
            style = Stroke(width = 17.dp.toPx(), cap = StrokeCap.Round)
        )

        // Long, slightly asymmetric body inspired by the supplied reclining-cat
        // silhouette. Its lower contour disappears softly into the card.
        val body = Path().apply {
            moveTo(size.width * 0.18f, cardEdge - radius * 0.01f)
            cubicTo(
                size.width * 0.18f, cardEdge - radius * 0.56f,
                size.width * 0.35f, cardEdge - radius * 0.94f,
                size.width * 0.52f, cardEdge - radius * 0.80f
            )
            cubicTo(
                size.width * 0.61f, cardEdge - radius * 0.72f,
                size.width * 0.67f, cardEdge - radius * 0.40f,
                size.width * 0.67f, cardEdge - radius * 0.04f
            )
            cubicTo(
                size.width * 0.57f, cardEdge + radius * 0.09f,
                size.width * 0.31f, cardEdge + radius * 0.10f,
                size.width * 0.18f, cardEdge - radius * 0.01f
            )
            close()
        }
        drawPath(body, backdrop)
        drawPath(body, surface.copy(alpha = 0.28f))
        clipPath(body) {
            drawCircle(
                color = focusTone.fill.copy(alpha = 0.19f),
                radius = radius * 1.28f,
                center = Offset(size.width * 0.32f, cardEdge - radius * 0.28f)
            )
            drawCircle(
                color = sleepTone.fill.copy(alpha = 0.175f),
                radius = radius * 1.08f,
                center = Offset(size.width * 0.54f, cardEdge - radius * 0.20f)
            )
            val flankFacet = Path().apply {
                moveTo(size.width * 0.39f, cardEdge - radius * 0.86f)
                lineTo(size.width * 0.56f, cardEdge + radius * 0.10f)
                lineTo(size.width * 0.31f, cardEdge + radius * 0.10f)
                close()
            }
            drawPath(flankFacet, studyTone.fill.copy(alpha = 0.165f))
        }

        // A tucked hind paw peeks out under the left half of the body.
        val hindPaw = Path().apply {
            moveTo(size.width * 0.35f, cardEdge - radius * 0.18f)
            cubicTo(
                size.width * 0.30f, cardEdge - radius * 0.01f,
                size.width * 0.20f, cardEdge - radius * 0.02f,
                size.width * 0.17f, cardEdge + radius * 0.07f
            )
            cubicTo(
                size.width * 0.15f, cardEdge + radius * 0.15f,
                size.width * 0.20f, cardEdge + radius * 0.20f,
                size.width * 0.27f, cardEdge + radius * 0.18f
            )
            cubicTo(
                size.width * 0.36f, cardEdge + radius * 0.16f,
                size.width * 0.41f, cardEdge + radius * 0.08f,
                size.width * 0.35f, cardEdge - radius * 0.18f
            )
            close()
        }
        drawPath(hindPaw, backdrop)
        drawPath(hindPaw, focusTone.fill.copy(alpha = 0.30f))

        // The two forelegs point forward and overlap naturally. Drawing them
        // before the head makes their shoulders disappear beneath its outline.
        val farFrontLeg = Path().apply {
            moveTo(center.x + radius * 0.10f, center.y + radius * 0.42f)
            cubicTo(
                center.x + radius * 0.42f, center.y + radius * 0.64f,
                size.width * 0.82f, cardEdge - radius * 0.08f,
                size.width * 0.91f, cardEdge + radius * 0.02f
            )
            cubicTo(
                size.width * 0.945f, cardEdge + radius * 0.07f,
                size.width * 0.92f, cardEdge + radius * 0.15f,
                size.width * 0.875f, cardEdge + radius * 0.12f
            )
            cubicTo(
                size.width * 0.77f, cardEdge + radius * 0.04f,
                center.x + radius * 0.24f, center.y + radius * 0.82f,
                center.x - radius * 0.02f, center.y + radius * 0.62f
            )
            close()
        }
        drawPath(farFrontLeg, backdrop)
        drawPath(farFrontLeg, sleepTone.fill.copy(alpha = 0.28f))

        val nearFrontLeg = Path().apply {
            moveTo(center.x - radius * 0.20f, center.y + radius * 0.43f)
            cubicTo(
                center.x - radius * 0.29f, center.y + radius * 0.65f,
                size.width * 0.71f, cardEdge - radius * 0.02f,
                size.width * 0.84f, cardEdge + radius * 0.07f
            )
            cubicTo(
                size.width * 0.88f, cardEdge + radius * 0.12f,
                size.width * 0.865f, cardEdge + radius * 0.20f,
                size.width * 0.82f, cardEdge + radius * 0.18f
            )
            cubicTo(
                size.width * 0.71f, cardEdge + radius * 0.15f,
                center.x - radius * 0.43f, center.y + radius * 0.79f,
                center.x - radius * 0.48f, center.y + radius * 0.57f
            )
            close()
        }
        drawPath(nearFrontLeg, backdrop)
        drawPath(nearFrontLeg, studyTone.fill.copy(alpha = 0.32f))

        val head = Path().apply {
            moveTo(center.x - radius * 0.86f, center.y - radius * 0.36f)
            lineTo(center.x - radius * 0.78f, center.y - radius * 1.16f)
            lineTo(center.x - radius * 0.22f, center.y - radius * 0.72f)
            cubicTo(
                center.x - radius * 0.08f, center.y - radius * 0.78f,
                center.x + radius * 0.08f, center.y - radius * 0.78f,
                center.x + radius * 0.22f, center.y - radius * 0.72f
            )
            lineTo(center.x + radius * 0.78f, center.y - radius * 1.16f)
            lineTo(center.x + radius * 0.86f, center.y - radius * 0.36f)
            cubicTo(
                center.x + radius * 1.02f, center.y + radius * 0.26f,
                center.x + radius * 0.62f, center.y + radius * 0.88f,
                center.x, center.y + radius * 0.98f
            )
            cubicTo(
                center.x - radius * 0.62f, center.y + radius * 0.88f,
                center.x - radius * 1.02f, center.y + radius * 0.26f,
                center.x - radius * 0.86f, center.y - radius * 0.36f
            )
            close()
        }

        rotate(degrees = 9f, pivot = center) {
            drawPath(head, backdrop)
            drawPath(head, surface.copy(alpha = 0.29f))
            clipPath(head) {
            val leftPanel = Path().apply {
                moveTo(center.x - radius * 1.05f, center.y - radius * 1.22f)
                lineTo(center.x + radius * 0.06f, center.y - radius * 0.18f)
                lineTo(center.x - radius * 0.20f, center.y + radius * 1.05f)
                lineTo(center.x - radius * 1.08f, center.y + radius * 1.05f)
                close()
            }
            val rightPanel = Path().apply {
                moveTo(center.x + radius * 0.18f, center.y - radius * 0.92f)
                lineTo(center.x + radius * 1.08f, center.y - radius * 0.42f)
                lineTo(center.x + radius * 0.68f, center.y + radius * 0.78f)
                lineTo(center.x - radius * 0.10f, center.y + radius * 0.12f)
                close()
            }
            drawPath(leftPanel, focusTone.fill.copy(alpha = 0.22f))
            drawPath(rightPanel, sleepTone.fill.copy(alpha = 0.205f))

            // Thin origami seams are the new motif: quiet, geometric, and theme-aware.
            val seamStroke = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(
                focusTone.color.copy(alpha = 0.23f),
                Offset(center.x, center.y - radius * 0.72f),
                Offset(center.x, center.y + radius * 0.88f),
                seamStroke.width,
                StrokeCap.Round
            )
            drawLine(
                studyTone.color.copy(alpha = 0.21f),
                Offset(center.x - radius * 0.78f, center.y - radius * 0.36f),
                Offset(center.x, center.y + radius * 0.10f),
                seamStroke.width,
                StrokeCap.Round
            )
            drawLine(
                studyTone.color.copy(alpha = 0.21f),
                Offset(center.x + radius * 0.78f, center.y - radius * 0.36f),
                Offset(center.x, center.y + radius * 0.10f),
                seamStroke.width,
                StrokeCap.Round
            )
            }

        val featureStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        val leftEye = Path().apply {
            moveTo(center.x - radius * 0.58f, center.y - radius * 0.10f)
            quadraticTo(
                center.x - radius * 0.38f,
                center.y + radius * 0.05f,
                center.x - radius * 0.18f,
                center.y - radius * 0.10f
            )
        }
        val rightEye = Path().apply {
            moveTo(center.x + radius * 0.18f, center.y - radius * 0.10f)
            quadraticTo(
                center.x + radius * 0.38f,
                center.y + radius * 0.05f,
                center.x + radius * 0.58f,
                center.y - radius * 0.10f
            )
        }
        if (isSleeping) {
            drawPath(leftEye, featureColor, style = featureStroke)
            drawPath(rightEye, featureColor, style = featureStroke)
        } else {
            val eyeRadius = radius * (0.095f - 0.07f * blink).coerceAtLeast(0.02f)
            drawCircle(featureColor, eyeRadius, Offset(center.x - radius * 0.39f, center.y - radius * 0.06f))
            drawCircle(featureColor, eyeRadius, Offset(center.x + radius * 0.39f, center.y - radius * 0.06f))
        }

        val nose = Path().apply {
            moveTo(center.x - 6.dp.toPx(), center.y + radius * 0.20f)
            lineTo(center.x + 6.dp.toPx(), center.y + radius * 0.20f)
            lineTo(center.x, center.y + radius * 0.29f)
            close()
        }
        drawPath(nose, studyTone.color.copy(alpha = 0.40f))

        val mouth = Path().apply {
            moveTo(center.x, center.y + radius * 0.29f)
            quadraticTo(
                center.x - radius * 0.08f,
                center.y + radius * 0.42f,
                center.x - radius * 0.18f,
                center.y + radius * 0.36f
            )
            moveTo(center.x, center.y + radius * 0.29f)
            quadraticTo(
                center.x + radius * 0.08f,
                center.y + radius * 0.42f,
                center.x + radius * 0.18f,
                center.y + radius * 0.36f
            )
        }
        drawPath(mouth, featureColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))

            listOf(-0.02f, 0.13f).forEach { verticalOffset ->
                val y = center.y + radius * (0.34f + verticalOffset)
                drawLine(
                    featureColor,
                    Offset(center.x - radius * 0.92f, y - radius * verticalOffset),
                    Offset(center.x - radius * 0.23f, y),
                    2.dp.toPx(),
                    StrokeCap.Round
                )
                drawLine(
                    featureColor,
                    Offset(center.x + radius * 0.23f, y),
                    Offset(center.x + radius * 0.92f, y - radius * verticalOffset),
                    2.dp.toPx(),
                    StrokeCap.Round
                )
            }
        }

        // Tiny toe seams keep the long paws legible without turning them into
        // separate icon-like shapes.
        listOf(0.835f, 0.89f).forEach { pawCenterX ->
            drawLine(
                color = featureColor.copy(alpha = 0.72f),
                start = Offset(size.width * pawCenterX, cardEdge + radius * 0.115f),
                end = Offset(size.width * (pawCenterX + 0.018f), cardEdge + radius * 0.13f),
                strokeWidth = 1.4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

    }
}

@Composable
private fun SleepTopActions(
    isBriefingPlaying: Boolean,
    openTaskCount: Int,
    onTasks: () -> Unit,
    onBriefing: () -> Unit,
    onStats: () -> Unit,
    onMore: () -> Unit,
    onAssistant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.appAccents.work.container)
                .clickable(onClick = onTasks)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                Icons.Default.Checklist,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.appAccents.work.onContainer
            )
            Text(
                stringResource(R.string.home_tasks_button),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.appAccents.work.onContainer,
                fontWeight = FontWeight.SemiBold
            )
            if (openTaskCount > 0) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.appAccents.work.color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        openTaskCount.coerceAtMost(99).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appAccents.work.onColor
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            HeaderAction(
                icon = if (isBriefingPlaying) Icons.Default.Stop else Icons.Default.RecordVoiceOver,
                description = stringResource(R.string.content_description_briefing),
                selected = isBriefingPlaying,
                onClick = onBriefing
            )
            HeaderAction(
                icon = Icons.Default.BarChart,
                description = stringResource(R.string.action_open_stats),
                onClick = onStats
            )
            HeaderAction(
                icon = Icons.Default.SmartToy,
                description = stringResource(R.string.misc_assistant),
                onClick = onAssistant
            )
            HeaderAction(
                icon = Icons.Default.MoreHoriz,
                description = stringResource(R.string.tab_misc),
                onClick = onMore
            )
        }
    }
}

internal const val HOME_LEARNING_SHORTCUTS_TEST_TAG = "home_learning_shortcuts_overlay"
internal const val HOME_ENGLISH_SHORTCUT_TEST_TAG = "home_english_learning_shortcut"
internal const val HOME_MATH_SHORTCUT_TEST_TAG = "home_math_practice_shortcut"

internal data class HomeLearningShortcutsGeometry(
    val topOffsetDp: Float,
    val touchTargetDp: Float,
    val visualDiameterDp: Float,
    val horizontalGapDp: Float
) {
    val rowWidthDp: Float get() = touchTargetDp * 2f + horizontalGapDp
}

/** Pure geometry shared by the overlay and local layout tests. */
internal fun calculateHomeLearningShortcutsGeometry(): HomeLearningShortcutsGeometry =
    HomeLearningShortcutsGeometry(
        // 8dp screen content inset + 4dp header inset + 42dp action + 6dp gap.
        topOffsetDp = 60f,
        touchTargetDp = 48f,
        visualDiameterDp = 40f,
        horizontalGapDp = 0f
    )

@Composable
private fun LearningShortcutsOverlay(
    onOpenEnglishLearning: () -> Unit,
    onOpenMathPractice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val geometry = calculateHomeLearningShortcutsGeometry()
    Row(
        modifier = modifier
            .offset(y = geometry.topOffsetDp.dp)
            .testTag(HOME_LEARNING_SHORTCUTS_TEST_TAG),
        horizontalArrangement = Arrangement.spacedBy(geometry.horizontalGapDp.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LearningShortcut(
            icon = Icons.Default.Translate,
            description = stringResource(R.string.home_english_learning_open),
            testTag = HOME_ENGLISH_SHORTCUT_TEST_TAG,
            geometry = geometry,
            tone = MaterialTheme.appAccents.study,
            onClick = onOpenEnglishLearning
        )
        LearningShortcut(
            icon = Icons.Default.Calculate,
            description = stringResource(R.string.math_practice_open),
            testTag = HOME_MATH_SHORTCUT_TEST_TAG,
            geometry = geometry,
            tone = MaterialTheme.appAccents.creative,
            onClick = onOpenMathPractice
        )
    }
}

@Composable
private fun LearningShortcut(
    icon: ImageVector,
    description: String,
    testTag: String,
    geometry: HomeLearningShortcutsGeometry,
    tone: AppAccentTone,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(geometry.touchTargetDp.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(geometry.visualDiameterDp.dp)
                .clip(CircleShape)
                .background(tone.container),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(21.dp),
                tint = tone.onContainer
            )
        }
    }
}

@Composable
private fun HeaderAction(
    icon: ImageVector,
    description: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .size(42.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.appAccents.focus.container
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(21.dp),
            tint = if (selected) MaterialTheme.appAccents.focus.color
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActiveSessionCard(
    activeSession: SleepSessionEntity,
    onCancel: () -> Unit,
    onSkipAutomation: () -> Unit,
    onRejectDetectedOnset: () -> Unit
) {
    val automationArmed = activeSession.isAutomationArmed()
    val detected = activeSession.detectedSleepOnsetTime != null
    WarningCard(
        text = if (automationArmed) {
            stringResource(
                R.string.sleep_automation_waiting_card,
                TimeFormatter.formatEpochMillis(activeSession.estimatedWakeTime, activeSession.zoneId)
            )
        } else stringResource(
                R.string.active_session_text,
                TimeFormatter.formatEpochMillis(activeSession.estimatedWakeTime, activeSession.zoneId)
            ),
        isError = false,
        actionLabel = stringResource(
            when {
                detected -> R.string.sleep_onset_not_asleep
                automationArmed -> R.string.action_skip_sleep_automation_tonight
                else -> R.string.action_cancel_sleep
            }
        ),
        onAction = when {
            detected -> onRejectDetectedOnset
            automationArmed -> onSkipAutomation
            else -> onCancel
        }
    )
}

@Composable
private fun PlanSummaryCard(
    plan: SleepPlan?,
    activeSession: SleepSessionEntity?,
    compact: Boolean,
    dense: Boolean,
    catOverlapInset: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(30.dp)
    val sleepTone = MaterialTheme.appAccents.sleep
    if (plan == null && activeSession == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .background(sleepTone.container)
                .padding(
                    start = if (dense) 14.dp else if (compact) 16.dp else 20.dp,
                    end = if (dense) 10.dp else if (compact) 12.dp else 16.dp,
                    top = catOverlapInset,
                    bottom = if (dense) 4.dp else if (compact) 7.dp else 10.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.home_summary_no_plan),
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = sleepTone.onContainer,
                textAlign = TextAlign.Center,
                maxLines = if (compact) 3 else 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }

    val wakeText = activeSession?.let {
        TimeFormatter.formatEpochMillis(it.estimatedWakeTime, it.zoneId)
    } ?: TimeFormatter.formatZonedDateTime(requireNotNull(plan).estimatedWake)
    val sleepStartText = activeSession?.let {
        TimeFormatter.formatEpochMillis(
            it.detectedSleepOnsetTime ?: it.estimatedSleepStartTime,
            it.zoneId
        )
    } ?: TimeFormatter.formatZonedDateTime(requireNotNull(plan).estimatedSleepStart)
    val totalSleepMinutes = activeSession?.let {
        val sleepStart = it.detectedSleepOnsetTime ?: it.estimatedSleepStartTime
        (it.estimatedWakeTime - sleepStart).coerceAtLeast(0L) / HOME_MINUTE_MILLIS
    } ?: requireNotNull(plan).totalSleepMinutes
    val cycles = activeSession?.cyclesPlanned ?: requireNotNull(plan).cycles

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(sleepTone.container)
            .padding(
                start = if (dense) 14.dp else if (compact) 16.dp else 20.dp,
                end = if (dense) 10.dp else if (compact) 12.dp else 16.dp,
                top = catOverlapInset,
                bottom = if (dense) 4.dp else if (compact) 7.dp else 10.dp
            ),
        verticalArrangement = Arrangement.spacedBy(
            if (dense) 3.dp else if (compact) 4.dp else 6.dp,
            Alignment.CenterVertically
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp)
            ) {
                FittedSingleLineText(
                    text = stringResource(R.string.home_sleep_plan_title),
                    style = if (dense) MaterialTheme.typography.labelMedium
                    else if (compact) MaterialTheme.typography.labelLarge
                    else MaterialTheme.typography.titleSmall,
                    color = sleepTone.onContainer.copy(alpha = 0.78f),
                    minFontSize = 10.sp
                )
                FittedSingleLineText(
                    text = wakeText,
                    style = when {
                        dense -> MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp)
                        compact -> MaterialTheme.typography.displayMedium.copy(fontSize = 46.sp)
                        else -> MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp)
                    },
                    fontWeight = FontWeight.Bold,
                    color = sleepTone.onContainer,
                    minFontSize = if (dense) 32.sp else if (compact) 38.sp else 46.sp
                )
                FittedSingleLineText(
                    text = stringResource(R.string.home_sleep_wake_label),
                    style = if (dense) MaterialTheme.typography.labelSmall
                    else if (compact) MaterialTheme.typography.labelMedium
                    else MaterialTheme.typography.bodyMedium,
                    color = sleepTone.onContainer.copy(alpha = 0.78f),
                    minFontSize = 9.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PlanDetail(
                label = stringResource(R.string.home_summary_sleep_start),
                value = sleepStartText,
                compact = compact,
                dense = dense,
                modifier = Modifier.weight(1f)
            )
            PlanDetail(
                label = stringResource(R.string.home_summary_total),
                value = TimeFormatter.formatMinutes(totalSleepMinutes),
                compact = compact,
                dense = dense,
                modifier = Modifier.weight(1.3f)
            )
            PlanDetail(
                label = stringResource(R.string.stats_cycles_unit),
                value = cycles.toString(),
                compact = compact,
                dense = dense,
                modifier = Modifier.weight(0.7f)
            )
        }

        when {
            activeSession == null && plan?.cyclesDidNotFit == true && plan.cycles == 0 -> Text(
                text = stringResource(R.string.error_no_cycle_fits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appAccents.warning.color,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = if (dense) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
            activeSession == null && plan?.isCutByPreferredWake == true -> Text(
                text = stringResource(R.string.home_summary_cut_by_wake),
                style = MaterialTheme.typography.bodySmall,
                color = sleepTone.onContainer.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = if (dense) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlanDetail(
    label: String,
    value: String,
    compact: Boolean,
    dense: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.appAccents.sleep.action)
            .padding(
                horizontal = if (dense) 5.dp else if (compact) 6.dp else 10.dp,
                vertical = if (dense) 2.dp else if (compact) 3.dp else 5.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FittedSingleLineText(
            text = label,
            style = if (dense) MaterialTheme.typography.labelSmall
            else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.appAccents.sleep.onAction.copy(alpha = 0.78f),
            minFontSize = 8.sp
        )
        if (!compact && !dense) Spacer(Modifier.height(2.dp))
        FittedSingleLineText(
            text = value,
            style = if (dense) MaterialTheme.typography.labelLarge
            else if (compact) MaterialTheme.typography.titleSmall
            else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.appAccents.sleep.onAction,
            fontWeight = FontWeight.SemiBold,
            minFontSize = 9.sp
        )
    }
}

@Composable
private fun StartButtons(
    activeSession: SleepSessionEntity?,
    canStart: Boolean,
    buttonHeight: androidx.compose.ui.unit.Dp,
    onStart: () -> Unit,
    onCancelActive: () -> Unit
) {
    val automationArmed = activeSession?.isAutomationArmed() == true
    val cancellableSession = activeSession != null && !automationArmed
    val sleepContainerColor = MaterialTheme.appAccents.sleep.action
    val sleepContentColor = MaterialTheme.appAccents.sleep.onAction
    val disabledSleepContainer = lerp(
        MaterialTheme.colorScheme.surface,
        sleepContainerColor,
        0.46f
    )
    val disabledSleepContent = lerp(
        disabledSleepContainer,
        sleepContentColor,
        0.72f
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = if (cancellableSession) onCancelActive else onStart,
            enabled = cancellableSession || canStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(buttonHeight),
            shape = RoundedCornerShape(26.dp),
            colors = if (cancellableSession) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.appAccents.urgent.color,
                    contentColor = MaterialTheme.appAccents.urgent.onColor
                )
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = sleepContainerColor,
                    contentColor = sleepContentColor,
                    disabledContainerColor = disabledSleepContainer,
                    disabledContentColor = disabledSleepContent
                )
            }
        ) {
            Text(
                text = stringResource(
                    if (cancellableSession) R.string.action_cancel_sleep
                    else R.string.action_go_to_sleep_now
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HomeActionDock(
    isBriefingPlaying: Boolean,
    onQuickNotes: () -> Unit,
    onBriefing: () -> Unit,
    onDiary: () -> Unit,
    onTools: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HomeDockAction(
            icon = Icons.Default.EditNote,
            label = stringResource(R.string.quick_notes_title),
            onClick = onQuickNotes,
            containerColor = MaterialTheme.appAccents.creative.container,
            contentColor = MaterialTheme.appAccents.creative.onContainer,
            modifier = Modifier.weight(1f)
        )
        HomeDockAction(
            icon = if (isBriefingPlaying) Icons.Default.Stop else Icons.Default.RecordVoiceOver,
            label = stringResource(R.string.home_action_briefing),
            onClick = onBriefing,
            containerColor = MaterialTheme.appAccents.focus.container,
            contentColor = MaterialTheme.appAccents.focus.onContainer,
            modifier = Modifier.weight(1f)
        )
        HomeDockAction(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            label = stringResource(R.string.diary_title),
            onClick = onDiary,
            containerColor = MaterialTheme.appAccents.leisure.container,
            contentColor = MaterialTheme.appAccents.leisure.onContainer,
            modifier = Modifier.weight(1f)
        )
        HomeDockAction(
            icon = Icons.Default.Apps,
            label = stringResource(R.string.home_action_sections),
            onClick = onTools,
            containerColor = MaterialTheme.appAccents.other.container,
            contentColor = MaterialTheme.appAccents.other.onContainer,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HomeDockAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
            FittedSingleLineText(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                minFontSize = 8.sp
            )
        }
    }
}

@Composable
private fun HomeToolTile(
    icon: ImageVector,
    label: String,
    tone: AppAccentTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(18.dp),
        color = tone.container,
        contentColor = tone.onContainer
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickAccessPill(
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(containerColor)
                .padding(horizontal = 16.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            FittedSingleLineText(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                fontWeight = FontWeight.Medium,
                minFontSize = 11.sp
            )
        }
    }
}
