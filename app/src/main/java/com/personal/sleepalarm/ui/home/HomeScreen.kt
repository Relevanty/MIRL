package com.personal.sleepalarm.ui.home

import com.personal.sleepalarm.ui.theme.appAccents

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.domain.automation.isAutomationArmed
import com.personal.sleepalarm.domain.automation.isAutomationPausedForFocus
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.SleepPlan
import com.personal.sleepalarm.domain.model.ordinaryTasks
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.domain.model.effectiveWorkBudgetMinutes
import com.personal.sleepalarm.domain.model.nextFocusDurationMinutes
import com.personal.sleepalarm.domain.model.remainingWorkMillisOrNull
import com.personal.sleepalarm.ui.components.PermissionBanners
import com.personal.sleepalarm.ui.components.WarningCard
import com.personal.sleepalarm.ui.dday.DDayBadge
import com.personal.sleepalarm.ui.stats.StatsScreen
import com.personal.sleepalarm.ui.stats.StatsViewModel
import com.personal.sleepalarm.ui.theme.ensureContrast
import com.personal.sleepalarm.util.PermissionChecker
import com.personal.sleepalarm.util.TimeFormatter
import java.time.ZoneId

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenDiary: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenStats: (() -> Unit)? = null,
    onOpenMore: () -> Unit = {},
    onOpenAssistant: () -> Unit = {},
    onOpenEnglishLearning: () -> Unit = {},
    onOpenMathPractice: () -> Unit = {},
    openTaskCount: Int = 0,
    upcomingTasks: List<TaskEntity> = emptyList(),
    onStartTaskFocus: (TaskEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isBriefingPlaying by viewModel.isBriefingPlaying.collectAsStateWithLifecycle()
    val quickNotes by viewModel.quickNotes.collectAsStateWithLifecycle()
    val statsViewModel: StatsViewModel = viewModel()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var showStats by remember { mutableStateOf(false) }
    var showQuickNotes by remember { mutableStateOf(false) }
    val ordinaryUpcomingTasks = remember(upcomingTasks) { upcomingTasks.ordinaryTasks() }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SleepTopActions(
                    isBriefingPlaying = isBriefingPlaying,
                    openTaskCount = openTaskCount,
                    onTasks = onOpenTasks,
                    onBriefing = viewModel::playBriefing,
                    onStats = { onOpenStats?.invoke() ?: run { showStats = true } },
                    onMore = onOpenMore,
                    onAssistant = onOpenAssistant
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
                    ) {
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
                        SleepPlanWithCat(plan = state.plan, catState = catState)
                        TodayDynamicCard(
                            activeSession = activeSleepSession,
                            latestCompleted = state.latestCompletedSession,
                            now = state.now,
                            tasks = ordinaryUpcomingTasks,
                            onOpenTasks = onOpenTasks,
                            onStartFocus = onStartTaskFocus
                        )
                        StartButtons(
                            activeSession = state.activeSession,
                            canStart = state.plan != null && state.permissions.exactAlarmsAllowed,
                            onStart = viewModel::startSleepSession,
                            onCancelActive = viewModel::cancelActiveSession
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickAccessPill(
                        label = stringResource(R.string.quick_notes_title),
                        onClick = { showQuickNotes = true },
                        accent = MaterialTheme.appAccents.calm.color,
                        modifier = Modifier.weight(1f)
                    )
                    QuickAccessPill(
                        label = stringResource(R.string.diary_title),
                        onClick = onOpenDiary,
                        accent = MaterialTheme.appAccents.other.color,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // These shortcuts are siblings of the content column, not children of
            // either the header row or the scroll container. They therefore never
            // add a row, change the cat's measurement, or create scroll range.
            LearningShortcutsOverlay(
                onOpenEnglishLearning = onOpenEnglishLearning,
                onOpenMathPractice = onOpenMathPractice,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
            )

            // Transient cards share one overlay stack. Neither an active sleep
            // session nor a permission warning can remeasure and move the plan.
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp)
                    .padding(top = 108.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.activeSession?.let { active ->
                    ActiveSessionCard(
                        activeSession = active,
                        onCancel = viewModel::cancelActiveSession,
                        onSkipAutomation = viewModel::skipSleepAutomationTonight,
                        onRejectDetectedOnset = viewModel::rejectDetectedSleepOnset
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
}

@Composable
private fun TodayDynamicCard(
    activeSession: SleepSessionEntity?,
    latestCompleted: SleepSessionEntity?,
    now: Long,
    tasks: List<TaskEntity>,
    onOpenTasks: () -> Unit,
    onStartFocus: (TaskEntity) -> Unit
) {
    val morningResult = latestCompleted?.takeIf {
        activeSession == null && it.actualWakeTime?.let { wake -> now - wake < 6L * 60L * 60_000L } == true
    }
    val task = tasks.firstOrNull { it.nextFocusDurationMinutes() > 0 } ?: tasks.firstOrNull()
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        when {
            activeSession != null -> Column(Modifier.padding(14.dp)) {
                Text(
                    if (activeSession.detectedSleepOnsetTime == null) "Телефон наблюдает за засыпанием"
                    else "Сон определён",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.appAccents.sleep.color
                )
                Text(
                    if (activeSession.detectedSleepOnsetTime == null)
                        "Учитываются покой, экран, зарядка и воспроизведение. Утром MIRL попросит подтверждение."
                    else "Время можно будет подтвердить или исправить утром.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            morningResult != null -> Column(Modifier.padding(14.dp)) {
                Text("Доброе утро", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.appAccents.success.color)
                Text(
                    "Проверьте результат сна выше — после подтверждения он попадёт в аналитику дня.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            task != null -> Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Главное сейчас", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.appAccents.focus.color)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onOpenTasks) { Text("Открыть") }
                }
                Text(
                    task.primaryLabel(),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val budgetMinutes = task.effectiveWorkBudgetMinutes()
                    val remaining = task.remainingWorkMillisOrNull()
                    val nextFocusMinutes = task.nextFocusDurationMinutes()
                    val remainingText = if (nextFocusMinutes <= 0) {
                        stringResource(R.string.daily_focus_home_budget_exhausted)
                    } else if (budgetMinutes > 0) {
                        val finiteRemaining = remaining ?: 0L
                        val hours = finiteRemaining / 3_600_000L
                        val minutes = finiteRemaining / 60_000L % 60
                        val duration = "${if (hours > 0) "$hours ч " else ""}$minutes мин"
                        stringResource(
                            R.string.daily_focus_home_remaining_bout,
                            duration,
                            nextFocusMinutes
                        )
                    } else stringResource(R.string.daily_focus_home_bout, nextFocusMinutes)
                    Text(
                        remainingText,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Button(
                        onClick = { onStartFocus(task) },
                        enabled = nextFocusMinutes > 0
                    ) { Text("Фокус") }
                }
            }
            else -> Column(Modifier.padding(14.dp)) {
                Text("День свободен для следующего шага", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onOpenTasks) { Text("Добавить задачу") }
            }
        }
    }
}

@Composable
private fun UpcomingTasksStrip(tasks: List<TaskEntity>, onOpenTasks: () -> Unit) {
    Card(
        onClick = onOpenTasks,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
                    color = MaterialTheme.appAccents.work.color
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

private enum class SleepCatState { AWAKE, PREPARING, DETECTING, SLEEPING, MORNING }

@Composable
private fun SleepPlanWithCat(plan: SleepPlan?, catState: SleepCatState) {
    val isSleeping = catState == SleepCatState.DETECTING || catState == SleepCatState.SLEEPING
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardEdge = calculateSleepCatCardEdgeDp(maxWidth.value).dp
        val catCanvasHeight = cardEdge + 56.dp

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

        PlanSummaryCard(
            plan = plan,
            modifier = Modifier.padding(top = cardEdge)
        )
        // The cat and the plan card now share the same coordinate system. Any
        // content above this box moves them together instead of separating them.
        val catModifier = Modifier
                .fillMaxWidth()
                .height(catCanvasHeight)
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
            cardEdgeFromTop = cardEdge,
            isSleeping = isSleeping
        )

        if (isSleeping) SleepCatZzz(cardEdge = cardEdge, canvasWidth = maxWidth)
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
    (availableWidthDp * 0.30f).coerceIn(96f, 124f)

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
            onClick = onOpenEnglishLearning
        )
        LearningShortcut(
            icon = Icons.Default.Calculate,
            description = stringResource(R.string.math_practice_open),
            testTag = HOME_MATH_SHORTCUT_TEST_TAG,
            geometry = geometry,
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
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(21.dp),
                tint = MaterialTheme.colorScheme.onSurface
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
private fun PlanSummaryCard(plan: SleepPlan?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(30.dp)
    if (plan == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.home_summary_no_plan),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.home_sleep_plan_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = TimeFormatter.formatZonedDateTime(plan.estimatedWake),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.appAccents.sleep.color
                )
                Text(
                    text = stringResource(R.string.home_sleep_wake_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PlanDetail(
                label = stringResource(R.string.home_summary_sleep_start),
                value = TimeFormatter.formatZonedDateTime(plan.estimatedSleepStart),
                modifier = Modifier.weight(1f)
            )
            PlanDetail(
                label = stringResource(R.string.home_summary_total),
                value = TimeFormatter.formatMinutes(plan.totalSleepMinutes),
                modifier = Modifier.weight(1f)
            )
            PlanDetail(
                label = stringResource(R.string.stats_cycles_unit),
                value = plan.cycles.toString(),
                modifier = Modifier.weight(0.8f)
            )
        }

        DDayBadge(modifier = Modifier.align(Alignment.CenterHorizontally))

        when {
            plan.cyclesDidNotFit && plan.cycles == 0 -> Text(
                text = stringResource(R.string.error_no_cycle_fits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appAccents.warning.color,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            plan.isCutByPreferredWake -> Text(
                text = stringResource(R.string.home_summary_cut_by_wake),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PlanDetail(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.48f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StartButtons(
    activeSession: SleepSessionEntity?,
    canStart: Boolean,
    onStart: () -> Unit,
    onCancelActive: () -> Unit
) {
    val automationArmed = activeSession?.isAutomationArmed() == true
    val cancellableSession = activeSession != null && !automationArmed
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = if (cancellableSession) onCancelActive else onStart,
            enabled = cancellableSession || canStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
            shape = RoundedCornerShape(22.dp),
            colors = if (cancellableSession) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.appAccents.urgent.color,
                    contentColor = MaterialTheme.appAccents.urgent.onColor
                )
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            Text(
                text = stringResource(
                    if (cancellableSession) R.string.action_cancel_sleep
                    else R.string.action_go_to_sleep_now
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun QuickAccessPill(
    label: String,
    onClick: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val containerColor = lerp(MaterialTheme.colorScheme.surface, accent, 0.72f)
    val contentColor = ensureContrast(
        foreground = MaterialTheme.colorScheme.onSurface,
        background = containerColor
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            fontWeight = FontWeight.Medium
        )
    }
}
