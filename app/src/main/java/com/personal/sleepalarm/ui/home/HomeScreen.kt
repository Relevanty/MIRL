package com.personal.sleepalarm.ui.home

import com.personal.sleepalarm.ui.dday.DDayBadge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.personal.sleepalarm.ui.stats.StatsScreen
import com.personal.sleepalarm.ui.stats.StatsViewModel
import com.personal.sleepalarm.util.PermissionChecker
import com.personal.sleepalarm.util.TimeFormatter
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable

/**
 * Главный экран (упрощённый).
 *
 * Только: кнопка-иконка статистики, баннеры разрешений, карточка активной
 * сессии, сводка расчёта и две большие кнопки по центру.
 * FAB в правом нижнем углу открывает дневник.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenDiary: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val statsViewModel: StatsViewModel = viewModel()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Локальная навигация: статистика открывается поверх главного экрана.
    var showStats by remember { mutableStateOf(false) }

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
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(errorMessage) {
        val message = errorMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            // ИСПРАВЛЕНО: Плашка вместо FAB
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { onOpenDiary() }
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.diary_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Кнопка-иконка статистики справа вверху.
            val isBriefingPlaying by viewModel.isBriefingPlaying.collectAsStateWithLifecycle()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { viewModel.playBriefing() }) {
                    Icon(
                        imageVector = if (isBriefingPlaying) Icons.Default.Stop else Icons.Default.RecordVoiceOver,
                        contentDescription = stringResource(R.string.content_description_briefing),
                        tint = if (isBriefingPlaying) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = { showStats = true }) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = stringResource(R.string.action_open_stats)
                    )
                }
            }

            // Баннеры разрешений (если есть проблемы).
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

            if (state.activeSession != null) {
                Spacer(modifier = Modifier.height(8.dp))
                ActiveSessionCard(
                    activeSession = state.activeSession,
                    onCancel = { viewModel.cancelActiveSession() }
                )
            }

            // Центр: сводка + две большие кнопки.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PlanSummaryCard(plan = state.plan)

                    StartButtons(
                        activeSession = state.activeSession,
                        canStart = state.plan != null && state.permissions.exactAlarmsAllowed,
                        onStart = { viewModel.startSleepSession() },
                        onCancelActive = { viewModel.cancelActiveSession() }
                    )
                }
            }
        }
    }
}

// =====================================================================
// Карточка активной сессии
// =====================================================================

@Composable
private fun ActiveSessionCard(
    activeSession: SleepSessionEntity?,
    onCancel: () -> Unit
) {
    if (activeSession == null) return

    WarningCard(
        text = stringResource(
            R.string.active_session_text,
            TimeFormatter.formatEpochMillis(
                activeSession.estimatedWakeTime,
                activeSession.zoneId
            )
        ),
        isError = false,
        actionLabel = stringResource(R.string.action_cancel_sleep),
        onAction = onCancel
    )
}

// =====================================================================
// Сводка расчёта
// =====================================================================

@Composable
private fun PlanSummaryCard(plan: SleepPlan?) {
    if (plan == null) {
        Text(
            text = stringResource(R.string.home_summary_no_plan),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        return
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(
                R.string.home_summary_line,
                TimeFormatter.formatZonedDateTime(plan.estimatedSleepStart),
                TimeFormatter.formatZonedDateTime(plan.estimatedWake),
                plan.cycles
            ),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        // ДОБАВЛЕНО (v5): бейдж ближайшего D-Day.
        DDayBadge(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 8.dp)
        )

        if (plan.cyclesDidNotFit && plan.cycles == 0) {
            Text(
                text = stringResource(R.string.error_no_cycle_fits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        } else if (plan.isCutByPreferredWake) {
            Text(
                text = stringResource(R.string.home_summary_cut_by_wake),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// =====================================================================
// Кнопки по центру
// =====================================================================

@Composable
private fun StartButtons(
    activeSession: SleepSessionEntity?,
    canStart: Boolean,
    onStart: () -> Unit,
    onCancelActive: () -> Unit
) {
    if (activeSession != null) {
        Button(
            onClick = onCancelActive,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text(
                text = stringResource(R.string.action_cancel_sleep),
                style = MaterialTheme.typography.titleMedium
            )
        }
    } else {
        Button(
            onClick = onStart,
            enabled = canStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Text(
                text = stringResource(R.string.action_go_to_sleep_now),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
