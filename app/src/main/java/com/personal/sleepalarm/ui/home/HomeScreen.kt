package com.personal.sleepalarm.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.domain.model.SleepPlan
import com.personal.sleepalarm.ui.components.PermissionBanners
import com.personal.sleepalarm.ui.components.WarningCard
import com.personal.sleepalarm.ui.dday.DDayBadge
import com.personal.sleepalarm.ui.stats.StatsScreen
import com.personal.sleepalarm.ui.stats.StatsViewModel
import com.personal.sleepalarm.util.PermissionChecker
import com.personal.sleepalarm.util.TimeFormatter

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenDiary: () -> Unit = {},
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
                    onBriefing = viewModel::playBriefing,
                    onStats = { showStats = true }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SleepPlanWithCat(plan = state.plan)
                        StartButtons(
                            activeSession = state.activeSession,
                            canStart = state.plan != null && state.permissions.exactAlarmsAllowed,
                            onStart = viewModel::startSleepSession,
                            onCancelActive = viewModel::cancelActiveSession
                        )
                    }
                }
            }

            // Transient cards share one overlay stack. Neither an active sleep
            // session nor a permission warning can remeasure and move the plan.
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp)
                    .padding(top = 60.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.activeSession?.let { active ->
                    ActiveSessionCard(activeSession = active, onCancel = viewModel::cancelActiveSession)
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

            QuickAccessPill(
                label = stringResource(R.string.quick_notes_title),
                onClick = { showQuickNotes = true },
                accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
            QuickAccessPill(
                label = stringResource(R.string.diary_title),
                onClick = onOpenDiary,
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
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
private fun SleepPlanWithCat(plan: SleepPlan?) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardEdge = calculateSleepCatCardEdgeDp(maxWidth.value).dp
        val catCanvasHeight = cardEdge + 56.dp

        PlanSummaryCard(
            plan = plan,
            modifier = Modifier.padding(top = cardEdge)
        )
        // The cat and the plan card now share the same coordinate system. Any
        // content above this box moves them together instead of separating them.
        GeometricCatBackdrop(
            modifier = Modifier
                .fillMaxWidth()
                .height(catCanvasHeight),
            cardEdgeFromTop = cardEdge
        )
    }
}

internal fun calculateSleepCatCardEdgeDp(availableWidthDp: Float): Float =
    (availableWidthDp * 0.30f).coerceIn(96f, 124f)

@Composable
private fun GeometricCatBackdrop(
    modifier: Modifier = Modifier,
    cardEdgeFromTop: androidx.compose.ui.unit.Dp
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val backdrop = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onBackground
    Canvas(modifier) {
        val cardEdge = cardEdgeFromTop.toPx()
        // Keep the head slightly forward of the reclining body.
        val center = Offset(size.width * 0.71f, cardEdge - size.width * 0.082f)
        val radius = size.width * 0.15f
        val featureColor = ink.copy(alpha = 0.34f)

        // The tail rests on the card edge first, then drops softly down its left side.
        val tail = Path().apply {
            moveTo(size.width * 0.235f, cardEdge + radius * 0.01f)
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
            color = tertiary.copy(alpha = 0.29f),
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
                color = primary.copy(alpha = 0.19f),
                radius = radius * 1.28f,
                center = Offset(size.width * 0.32f, cardEdge - radius * 0.28f)
            )
            drawCircle(
                color = secondary.copy(alpha = 0.175f),
                radius = radius * 1.08f,
                center = Offset(size.width * 0.54f, cardEdge - radius * 0.20f)
            )
            val flankFacet = Path().apply {
                moveTo(size.width * 0.39f, cardEdge - radius * 0.86f)
                lineTo(size.width * 0.56f, cardEdge + radius * 0.10f)
                lineTo(size.width * 0.31f, cardEdge + radius * 0.10f)
                close()
            }
            drawPath(flankFacet, tertiary.copy(alpha = 0.165f))
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
        drawPath(hindPaw, primary.copy(alpha = 0.30f))

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
        drawPath(farFrontLeg, secondary.copy(alpha = 0.28f))

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
        drawPath(nearFrontLeg, tertiary.copy(alpha = 0.32f))

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
            drawPath(leftPanel, primary.copy(alpha = 0.22f))
            drawPath(rightPanel, secondary.copy(alpha = 0.205f))

            // Thin origami seams are the new motif: quiet, geometric, and theme-aware.
            val seamStroke = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(
                primary.copy(alpha = 0.23f),
                Offset(center.x, center.y - radius * 0.72f),
                Offset(center.x, center.y + radius * 0.88f),
                seamStroke.width,
                StrokeCap.Round
            )
            drawLine(
                tertiary.copy(alpha = 0.21f),
                Offset(center.x - radius * 0.78f, center.y - radius * 0.36f),
                Offset(center.x, center.y + radius * 0.10f),
                seamStroke.width,
                StrokeCap.Round
            )
            drawLine(
                tertiary.copy(alpha = 0.21f),
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
        drawPath(leftEye, featureColor, style = featureStroke)
        drawPath(rightEye, featureColor, style = featureStroke)

        val nose = Path().apply {
            moveTo(center.x - 6.dp.toPx(), center.y + radius * 0.20f)
            lineTo(center.x + 6.dp.toPx(), center.y + radius * 0.20f)
            lineTo(center.x, center.y + radius * 0.29f)
            close()
        }
        drawPath(nose, tertiary.copy(alpha = 0.40f))

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
    onBriefing: () -> Unit,
    onStats: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
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
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(21.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActiveSessionCard(activeSession: SleepSessionEntity, onCancel: () -> Unit) {
    WarningCard(
        text = stringResource(
            R.string.active_session_text,
            TimeFormatter.formatEpochMillis(activeSession.estimatedWakeTime, activeSession.zoneId)
        ),
        isError = false,
        actionLabel = stringResource(R.string.action_cancel_sleep),
        onAction = onCancel
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
                    color = MaterialTheme.colorScheme.primary
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
                color = MaterialTheme.colorScheme.error,
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
    val isActive = activeSession != null
    Button(
        onClick = if (isActive) onCancelActive else onStart,
        enabled = isActive || canStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp),
        shape = RoundedCornerShape(22.dp),
        colors = if (isActive) {
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.buttonColors()
        }
    ) {
        Text(
            text = stringResource(if (isActive) R.string.action_cancel_sleep else R.string.action_go_to_sleep_now),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
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
    val contentColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color.White.copy(alpha = 0.82f)
    } else {
        Color.Black.copy(alpha = 0.82f)
    }
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
