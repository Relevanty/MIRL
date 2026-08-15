package com.personal.sleepalarm.ui.stats

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.domain.model.DismissType
import com.personal.sleepalarm.util.CsvExporter
import com.personal.sleepalarm.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// =====================================================================
// Палитра — ВСЕ цвета из MaterialTheme.colorScheme (адаптивны к теме).
// =====================================================================

private val TextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onBackground
private val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val Amber: Color @Composable get() = MaterialTheme.colorScheme.primary
private val AmberSoft: Color @Composable get() =
    lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onBackground, 0.4f)
private val Teal: Color @Composable get() = MaterialTheme.colorScheme.secondary
private val SoftRed: Color @Composable get() = MaterialTheme.colorScheme.error
private val InfoBlue: Color @Composable get() = MaterialTheme.colorScheme.tertiary
private val CardBorder: Color @Composable get() = MaterialTheme.colorScheme.outline
private val TrackRing: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val DotEmpty: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant

private val EyebrowStyle: TextStyle
    @Composable get() = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.8.sp,
        color = Amber
    )

// =====================================================================
// Главный экран статистики
// =====================================================================

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // === Переключатель Сон / Учёба ===
    var statsMode by remember { mutableStateOf("sleep") }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val exportScope = rememberCoroutineScope()

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            exportScope.launch {
                val ok = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        withContext(Dispatchers.IO) {
                            CsvExporter.writeSessionsCsv(
                                context = context,
                                sessions = state.allSessions,
                                outputStream = out
                            )
                        }
                    }
                    true
                }.getOrDefault(false)

                snackbarHostState.showSnackbar(
                    if (ok) context.getString(R.string.stats_export_done)
                    else context.getString(R.string.stats_export_empty)
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 28.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            }

            // === Переключатель ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = statsMode == "sleep",
                    onClick = { statsMode = "sleep" },
                    label = { Text(stringResource(R.string.tab_home)) }
                )
                FilterChip(
                    selected = statsMode == "study",
                    onClick = { statsMode = "study" },
                    label = { Text(stringResource(R.string.stats_tab_study)) }
                )
            }

            // === Контент по режиму ===
            if (statsMode == "study") {
                StudyStatsContent()
            } else {
                SleepStatsContent(
                    state = state,
                    exportCsvLauncher = exportCsvLauncher,
                    snackbarHostState = snackbarHostState
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

// =====================================================================
// Блок статистики сна (вынесен в отдельную функцию для чистоты)
// =====================================================================

@Composable
private fun SleepStatsContent(
    state: StatsUiState,
    exportCsvLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    snackbarHostState: SnackbarHostState
) {
    HeaderBlock()

    if (state.sessions.isEmpty()) {
        Reveal(delayMs = 100) {
            EmptyState()
        }
    } else {
        Reveal(delayMs = 0) {
            LastNightCard(state = state)
        }

        Reveal(delayMs = 140) {
            AggregatesBlock(state = state)
        }

        Reveal(delayMs = 200) {
            ChartCard(
                title = stringResource(R.string.stats_chart_duration_title),
                subtitle = stringResource(R.string.stats_chart_duration_hint)
            ) {
                DurationBarChart(
                    days = state.chartDays,
                    popupText = ::durationPopupText
                )
            }
        }

        Reveal(delayMs = 280) {
            ChartCard(
                title = stringResource(R.string.stats_chart_wake_title),
                subtitle = null
            ) {
                WakeTimeLineChart(days = state.chartDays)
            }
        }

        Reveal(delayMs = 340) {
            HistoryHeader(
                count = state.sessions.size,
                canExport = state.allSessions.isNotEmpty(),
                onExport = {
                    exportCsvLauncher.launch("sleep_sessions.csv")
                }
            )
        }

        state.sessions.forEachIndexed { index, session ->
            Reveal(delayMs = 400L + index * 70L) {
                SessionRow(session = session)
            }
        }
    }
}

// =====================================================================
// Карточка-обёртка графика
// =====================================================================

@Composable
private fun ChartCard(
    title: String,
    subtitle: String?,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .nightCard(shape = RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
        )

        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = TextStyle(fontSize = 12.sp, color = Muted)
            )
        }

        content()
    }
}

// =====================================================================
// Пульсирующая точка-маркер
// =====================================================================

@Composable
private fun PulseDot(
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "pulseDot")
    val halo by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo"
    )

    Box(
        modifier = modifier.size(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = halo))
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

// =====================================================================
// Каскадное появление блоков
// =====================================================================

@Composable
private fun Reveal(
    delayMs: Long,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delayMs)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(420)) + slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight / 10 },
            animationSpec = tween(420)
        )
    ) {
        content()
    }
}

// =====================================================================
// Заголовок
// =====================================================================

@Composable
private fun HeaderBlock() {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.stats_label_statistics).uppercase(),
            style = EyebrowStyle
        )

        Text(
            text = stringResource(R.string.stats_title),
            style = TextStyle(
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )
        )

        Text(
            text = stringResource(R.string.stats_subtitle),
            style = TextStyle(
                fontSize = 14.sp,
                color = Muted
            )
        )
    }
}

// =====================================================================
// Последняя ночь
// =====================================================================

@Composable
private fun LastNightCard(state: StatsUiState) {
    val session = state.lastSession ?: return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .nightCard(shape = RoundedCornerShape(26.dp))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.stats_label_last_night).uppercase(),
                style = EyebrowStyle
            )

            val minutes = state.lastSessionSleepMinutes

            if (minutes != null) {
                AnimatedDuration(minutes = minutes)
            } else {
                Text(
                    text = "—",
                    style = TextStyle(
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                )
            }

            Text(
                text = stringResource(
                    R.string.stats_last_night_range,
                    TimeFormatter.formatEpochMillis(
                        session.bedTimePlanned,
                        session.zoneId
                    ),
                    TimeFormatter.formatEpochMillis(
                        session.estimatedWakeTime,
                        session.zoneId
                    )
                ),
                style = TextStyle(fontSize = 13.sp, color = Muted)
            )

            Text(
                text = stringResource(
                    R.string.stats_cycles_info,
                    session.cyclesPlanned,
                    session.cycleLengthMinutes
                ),
                style = TextStyle(fontSize = 13.sp, color = Muted)
            )

            if (session.cuesEnabled && session.cuesScheduledCount > 0) {
                Text(
                    text = stringResource(
                        R.string.stats_cues_info,
                        state.lastNightCuesPlayed,
                        state.lastNightCuesScheduled
                    ),
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AmberSoft
                    )
                )
            } else {
                Text(
                    text = stringResource(R.string.stats_cues_off),
                    style = TextStyle(fontSize = 13.sp, color = Muted)
                )
            }

            session.detectedOnsetLatencyMinutes?.let { detected ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PulseDot(color = Teal)
                    Text(
                        text = stringResource(R.string.stats_detected_onset, detected),
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Teal
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        CycleRing(cycles = session.cyclesPlanned)
    }
}

@Composable
private fun CycleRing(
    cycles: Int,
    modifier: Modifier = Modifier
) {
    var targetProgress by remember { mutableStateOf(0f) }

    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 1_100),
        label = "ringProgress"
    )

    LaunchedEffect(Unit) {
        targetProgress = 1f
    }

    val trackColor = TrackRing
    val arcColor = Amber

    Box(
        modifier = modifier.size(118.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f
            val center = Offset(
                x = size.width / 2f,
                y = size.height / 2f
            )

            drawCircle(
                color = trackColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth)
            )

            val safeCycles = cycles.coerceAtLeast(1)
            val segment = 360f / safeCycles
            val gap = 8f
            val sweep = (segment - gap).coerceAtLeast(0f) * progress

            repeat(safeCycles) { index ->
                drawArc(
                    color = arcColor,
                    startAngle = -90f + index * segment,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(
                        x = center.x - radius,
                        y = center.y - radius
                    ),
                    size = Size(
                        width = radius * 2f,
                        height = radius * 2f
                    ),
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = cycles.toString(),
                style = TextStyle(
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
            )

            Text(
                text = stringResource(R.string.stats_cycles_unit),
                style = TextStyle(fontSize = 11.sp, color = Muted)
            )
        }
    }
}

// =====================================================================
// Агрегаты
// =====================================================================

@Composable
private fun AggregatesBlock(state: StatsUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1.6f)
                .height(152.dp)
                .nightCard(shape = RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.stats_label_avg_7d).uppercase(),
                    style = EyebrowStyle
                )

                val avg = state.avgSleepMinutes7d

                if (avg != null) {
                    AnimatedDuration(minutes = avg)
                } else {
                    Text(
                        text = "—",
                        style = TextStyle(
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                    )
                }

                Text(
                    text = stringResource(
                        R.string.stats_sessions_count,
                        state.sessionsCount7d
                    ),
                    style = TextStyle(fontSize = 12.sp, color = Muted)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SmallStatCard(
                label = stringResource(R.string.stats_label_no_snooze),
                value = state.noSnoozePercent?.let { "$it%" } ?: "—",
                valueColor = Teal
            )

            SmallStatCard(
                label = stringResource(R.string.stats_label_cues),
                value = "${state.lastNightCuesPlayed}/${state.lastNightCuesScheduled}",
                valueColor = AmberSoft
            )
        }
    }
}

@Composable
private fun SmallStatCard(
    label: String,
    value: String,
    valueColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(69.dp)
            .nightCard(shape = RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
                color = Muted
            )
        )

        Text(
            text = value,
            style = TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor
            )
        )
    }
}

@Composable
private fun AnimatedDuration(
    minutes: Long,
    modifier: Modifier = Modifier
) {
    val animated by animateIntAsState(
        targetValue = minutes.toInt(),
        animationSpec = tween(durationMillis = 900),
        label = "durationCounter"
    )

    Text(
        text = TimeFormatter.formatMinutes(animated.toLong()),
        style = TextStyle(
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            color = TextPrimary
        ),
        modifier = modifier
    )
}

// =====================================================================
// История сессий
// =====================================================================

@Composable
private fun HistoryHeader(
    count: Int,
    canExport: Boolean,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.stats_label_history).uppercase(),
            style = EyebrowStyle
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(CardBorder.copy(alpha = 0.5f))
        )

        TextButton(
            onClick = onExport,
            enabled = canExport
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.stats_action_export_csv),
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            )
        }

        Text(
            text = stringResource(R.string.stats_last_sessions, count),
            style = TextStyle(fontSize = 12.sp, color = Muted)
        )
    }
}

@Composable
private fun SessionRow(session: SleepSessionEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .nightCard(shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = sessionDateText(session),
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                )

                Text(
                    text = stringResource(
                        R.string.stats_last_night_range,
                        TimeFormatter.formatEpochMillis(
                            session.bedTimePlanned,
                            session.zoneId
                        ),
                        TimeFormatter.formatEpochMillis(
                            session.estimatedWakeTime,
                            session.zoneId
                        )
                    ),
                    style = TextStyle(fontSize = 12.sp, color = Muted)
                )
            }

            Text(
                text = TimeFormatter.formatMinutes(
                    sessionDurationMinutes(session)
                ),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Amber
                )
            )

            Spacer(modifier = Modifier.width(12.dp))

            DismissBadge(session = session)
        }

        session.detectedOnsetLatencyMinutes?.let { detected ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PulseDot(color = Teal)
                Text(
                    text = stringResource(R.string.stats_detected_onset, detected),
                    style = TextStyle(fontSize = 11.sp, color = Teal)
                )
            }
        }

        if (session.cuesEnabled && session.cuesScheduledCount > 0) {
            CueDots(
                played = session.cuesPlayedCount,
                scheduled = session.cuesScheduledCount
            )
        }
    }
}

@Composable
private fun DismissBadge(session: SleepSessionEntity) {
    val text: String
    val color: Color

    when {
        session.isActive && session.dismissType == null -> {
            text = stringResource(R.string.stats_badge_active)
            color = InfoBlue
        }

        session.dismissType == DismissType.NORMAL -> {
            text = stringResource(R.string.stats_badge_normal)
            color = Teal
        }

        session.dismissType == DismissType.SNOOZE -> {
            text = stringResource(R.string.stats_badge_snooze)
            color = Amber
        }

        session.dismissType == DismissType.MISSED -> {
            text = stringResource(R.string.stats_badge_missed)
            color = SoftRed
        }

        else -> {
            text = stringResource(R.string.stats_badge_cancelled)
            color = Muted
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        )
    }
}

@Composable
private fun CueDots(
    played: Int,
    scheduled: Int
) {
    val shown = scheduled.coerceAtMost(12)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(shown) { index ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < played) Amber else DotEmpty
                    )
            )
        }

        if (scheduled > 12) {
            Text(
                text = "+",
                style = TextStyle(fontSize = 10.sp, color = Muted)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = "$played/$scheduled",
            style = TextStyle(fontSize = 11.sp, color = Muted)
        )
    }
}

// =====================================================================
// Пустое состояние
// =====================================================================

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .nightCard(shape = RoundedCornerShape(22.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "☾",
            style = TextStyle(
                fontSize = 46.sp,
                color = Amber
            )
        )

        Text(
            text = stringResource(R.string.stats_empty_title),
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )

        Text(
            text = stringResource(R.string.stats_empty_text),
            style = TextStyle(
                fontSize = 13.sp,
                color = Muted
            )
        )
    }
}

// =====================================================================
// Helpers
// =====================================================================

@Composable
private fun Modifier.nightCard(
    shape: Shape = RoundedCornerShape(20.dp)
): Modifier {
    return this
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.surfaceVariant
                )
            ),
            shape = shape
        )
        .border(
            width = 1.dp,
            color = CardBorder.copy(alpha = 0.5f),
            shape = shape
        )
}

private fun sessionDurationMinutes(session: SleepSessionEntity): Long {
    val end = session.actualWakeTime ?: session.estimatedWakeTime
    val start = session.detectedSleepOnsetTime ?: session.estimatedSleepStartTime
    val minutes = (end - start) / (60L * 1000L)
    return minutes.coerceAtLeast(0)
}

private fun sessionDateText(session: SleepSessionEntity): String {
    val zone = runCatching { ZoneId.of(session.zoneId) }
        .getOrDefault(ZoneId.systemDefault())

    val zonedDateTime = Instant.ofEpochMilli(session.bedTimePlanned).atZone(zone)

    return DateTimeFormatter
        .ofPattern("d MMM · EEE", Locale("ru"))
        .format(zonedDateTime)
}
