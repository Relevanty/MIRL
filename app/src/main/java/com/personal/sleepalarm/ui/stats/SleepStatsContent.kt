package com.personal.sleepalarm.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.domain.calculator.ActivityDayBoundary
import com.personal.sleepalarm.domain.model.DismissType
import com.personal.sleepalarm.util.TimeFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

private enum class SleepStatsPeriod { DAY, WEEK, MONTH }
private enum class SleepOutcome { NORMAL, SNOOZE, MISSED, ACTIVE }

private data class SleepNight(
    val date: LocalDate,
    val sessions: List<SleepSessionEntity>,
    val startMillis: Long,
    val endMillis: Long,
    val durationMillis: Long,
    val plannedDurationMillis: Long,
    val bedTimeMillis: Long,
    val latencyMinutes: Int?,
    val cuesPlayed: Int,
    val cuesScheduled: Int,
    val outcome: SleepOutcome
)

@Composable
fun SleepStatsContent(
    state: StatsUiState,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zone = ZoneId.systemDefault()
    val locale = Locale.getDefault()
    val initialDate = remember(zone, state.snapshotTimeMillis) {
        ActivityDayBoundary.dateFor(state.snapshotTimeMillis, zone)
    }
    var period by remember { mutableStateOf(SleepStatsPeriod.DAY) }
    var anchorDate by remember { mutableStateOf(initialDate) }
    val nights = remember(state.allSessions, state.snapshotTimeMillis, zone) {
        buildSleepNights(state.allSessions, state.snapshotTimeMillis, zone)
    }
    val periodDates = remember(period, anchorDate) {
        when (period) {
            SleepStatsPeriod.DAY -> anchorDate to anchorDate.plusDays(1)
            SleepStatsPeriod.WEEK -> {
                val monday = anchorDate.minusDays((anchorDate.dayOfWeek.value - 1).toLong())
                monday to monday.plusWeeks(1)
            }
            SleepStatsPeriod.MONTH -> {
                val month = YearMonth.from(anchorDate)
                month.atDay(1) to month.plusMonths(1).atDay(1)
            }
        }
    }
    val selectedNights = remember(nights, periodDates) {
        nights.filter { it.date >= periodDates.first && it.date < periodDates.second }
            .sortedBy { it.date }
    }
    val periodLabel = remember(period, periodDates, locale) {
        sleepPeriodLabel(period, periodDates.first, periodDates.second, locale)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = period == SleepStatsPeriod.DAY,
                onClick = { period = SleepStatsPeriod.DAY },
                label = { Text(stringResource(R.string.study_period_day)) }
            )
            FilterChip(
                selected = period == SleepStatsPeriod.WEEK,
                onClick = { period = SleepStatsPeriod.WEEK },
                label = { Text(stringResource(R.string.study_period_week)) }
            )
            FilterChip(
                selected = period == SleepStatsPeriod.MONTH,
                onClick = { period = SleepStatsPeriod.MONTH },
                label = { Text(stringResource(R.string.study_period_month)) }
            )
        }

        SleepPeriodNavigator(
            label = periodLabel,
            onPrevious = {
                anchorDate = when (period) {
                    SleepStatsPeriod.DAY -> anchorDate.minusDays(1)
                    SleepStatsPeriod.WEEK -> anchorDate.minusWeeks(1)
                    SleepStatsPeriod.MONTH -> anchorDate.minusMonths(1)
                }
            },
            onNext = {
                anchorDate = when (period) {
                    SleepStatsPeriod.DAY -> anchorDate.plusDays(1)
                    SleepStatsPeriod.WEEK -> anchorDate.plusWeeks(1)
                    SleepStatsPeriod.MONTH -> anchorDate.plusMonths(1)
                }
            }
        )

        SleepSummaryGrid(selectedNights, zone)

        if (selectedNights.isEmpty()) {
            SleepEmptyCard()
        } else {
            if (period == SleepStatsPeriod.DAY) {
                SelectedNightCard(selectedNights.last(), zone)
            }
            DurationComparisonChart(selectedNights)
            SleepScheduleChart(selectedNights, zone, locale)
            BedWakeTrendChart(selectedNights, zone)
            LatencyChart(selectedNights)
            OutcomeChart(selectedNights)
            CueEfficiencyCard(selectedNights)
            if (period == SleepStatsPeriod.MONTH) {
                SleepHeatmap(YearMonth.from(periodDates.first), selectedNights, locale)
            }
        }

        SleepHistory(
            nights = selectedNights.sortedByDescending { it.endMillis }.take(12),
            zone = zone,
            canExport = state.allSessions.isNotEmpty(),
            onExport = onExport
        )
    }
}

@Composable
private fun SleepPeriodNavigator(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.ChevronLeft, stringResource(R.string.activity_previous_period))
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, stringResource(R.string.activity_next_period))
        }
    }
}

@Composable
private fun SleepSummaryGrid(nights: List<SleepNight>, zone: ZoneId) {
    val total = nights.sumOf { it.durationMillis }
    val average = if (nights.isEmpty()) 0L else total / nights.size
    val bedTime = averageClock(nights.map { it.bedTimeMillis }, zone, wrapAfterNoon = true)
    val wakeTime = averageClock(nights.map { it.endMillis }, zone, wrapAfterNoon = false)
    val colors = listOf(
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error
    )
    val values = listOf(
        Triple(stringResource(R.string.sleep_stats_total), TimeFormatter.formatMinutes(total / MINUTE_MS), colors[0]),
        Triple(stringResource(R.string.sleep_stats_average), TimeFormatter.formatMinutes(average / MINUTE_MS), colors[1]),
        Triple(stringResource(R.string.sleep_stats_avg_bedtime), bedTime, colors[2]),
        Triple(stringResource(R.string.sleep_stats_avg_wake), wakeTime, colors[3])
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.chunked(2).forEach { rowValues ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowValues.forEach { (label, value, accent) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(accent.copy(alpha = 0.12f))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
                            Spacer(Modifier.width(7.dp))
                            Text(label, style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedNightCard(night: SleepNight, zone: ZoneId) {
    SleepChartCard(stringResource(R.string.sleep_stats_selected_night)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    TimeFormatter.formatMinutes(night.durationMillis / MINUTE_MS),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(
                        R.string.stats_last_night_range,
                        formatClock(night.startMillis, zone),
                        formatClock(night.endMillis, zone)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(
                        R.string.sleep_stats_planned_value,
                        TimeFormatter.formatMinutes(night.plannedDurationMillis / MINUTE_MS)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SleepOutcomeBadge(night.outcome)
        }
        night.latencyMinutes?.let { latency ->
            Text(
                stringResource(R.string.stats_detected_onset, latency),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun DurationComparisonChart(nights: List<SleepNight>) {
    val actualColor = MaterialTheme.colorScheme.tertiary
    val plannedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val visible = nights.takeLast(MAX_GRAPH_NIGHTS)
    val maxValue = visible.maxOfOrNull { maxOf(it.durationMillis, it.plannedDurationMillis) }
        ?.coerceAtLeast(1L) ?: 1L
    SleepChartCard(stringResource(R.string.sleep_stats_duration_chart)) {
        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
            repeat(3) { index ->
                val y = size.height * index / 2f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            if (visible.isNotEmpty()) {
                val slot = size.width / visible.size
                val width = (slot * 0.27f).coerceAtLeast(2.dp.toPx())
                visible.forEachIndexed { index, night ->
                    val center = index * slot + slot / 2f
                    val actualHeight = size.height * night.durationMillis.toFloat() / maxValue
                    val plannedHeight = size.height * night.plannedDurationMillis.toFloat() / maxValue
                    drawRoundRect(
                        actualColor,
                        Offset(center - width - 1.dp.toPx(), size.height - actualHeight),
                        Size(width, actualHeight.coerceAtLeast(1.dp.toPx())),
                        CornerRadius(3.dp.toPx())
                    )
                    drawRoundRect(
                        plannedColor,
                        Offset(center + 1.dp.toPx(), size.height - plannedHeight),
                        Size(width, plannedHeight.coerceAtLeast(1.dp.toPx())),
                        CornerRadius(3.dp.toPx())
                    )
                }
            }
        }
        SleepDateAxis(visible)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SleepLegend(actualColor, stringResource(R.string.sleep_stats_actual))
            SleepLegend(plannedColor, stringResource(R.string.sleep_stats_planned))
        }
    }
}

@Composable
private fun SleepScheduleChart(nights: List<SleepNight>, zone: ZoneId, locale: Locale) {
    val visible = nights.takeLast(10)
    val accent = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    SleepChartCard(stringResource(R.string.sleep_stats_schedule_chart)) {
        visible.forEach { night ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    night.date.format(formatter),
                    modifier = Modifier.width(56.dp),
                    style = MaterialTheme.typography.labelSmall
                )
                val start = scheduleMinute(night.startMillis, zone)
                val end = scheduleMinute(night.endMillis, zone).let { if (it < start) it + DAY_MINUTES else it }
                Canvas(
                    Modifier
                        .weight(1f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(track)
                ) {
                    val left = size.width * ((start - SCHEDULE_START).toFloat() / SCHEDULE_SPAN).coerceIn(0f, 1f)
                    val right = size.width * ((end - SCHEDULE_START).toFloat() / SCHEDULE_SPAN).coerceIn(0f, 1f)
                    if (right > left) {
                        drawRoundRect(
                            accent,
                            Offset(left, 0f),
                            Size((right - left).coerceAtLeast(2.dp.toPx()), size.height),
                            CornerRadius(7.dp.toPx())
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(56.dp))
            listOf("18:00", "00:00", "06:00", "12:00").forEachIndexed { index, label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = if (index == 3) TextAlign.End else TextAlign.Start,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BedWakeTrendChart(nights: List<SleepNight>, zone: ZoneId) {
    val visible = nights.takeLast(MAX_GRAPH_NIGHTS)
    val bedColor = MaterialTheme.colorScheme.primary
    val wakeColor = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    SleepChartCard(stringResource(R.string.sleep_stats_regularity_chart)) {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            repeat(4) { index ->
                val y = size.height * index / 3f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            if (visible.isNotEmpty()) {
                val denominator = (visible.size - 1).coerceAtLeast(1)
                fun points(selector: (SleepNight) -> Long): List<Offset> = visible.mapIndexed { index, night ->
                    val minute = scheduleMinute(selector(night), zone)
                    Offset(
                        size.width * index / denominator,
                        size.height * ((minute - SCHEDULE_START).toFloat() / SCHEDULE_SPAN).coerceIn(0f, 1f)
                    )
                }
                listOf(bedColor to points { it.bedTimeMillis }, wakeColor to points { it.endMillis })
                    .forEach { (color, points) ->
                        points.zipWithNext().forEach { (start, end) ->
                            drawLine(color, start, end, 3.dp.toPx(), cap = StrokeCap.Round)
                        }
                        points.forEach { drawCircle(color, 3.5.dp.toPx(), it) }
                    }
            }
        }
        SleepDateAxis(visible)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SleepLegend(bedColor, stringResource(R.string.sleep_stats_bedtime))
            SleepLegend(wakeColor, stringResource(R.string.sleep_stats_wake_time))
        }
    }
}

@Composable
private fun LatencyChart(nights: List<SleepNight>) {
    val values = nights.mapNotNull { night -> night.latencyMinutes?.let { night to it } }.takeLast(10)
    val maxValue = values.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val accent = MaterialTheme.colorScheme.secondary
    SleepChartCard(stringResource(R.string.sleep_stats_latency_chart)) {
        if (values.isEmpty()) {
            Text(
                stringResource(R.string.sleep_stats_no_latency),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            values.forEach { (night, minutes) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(night.date.dayOfMonth.toString(), modifier = Modifier.width(28.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(minutes.toFloat() / maxValue)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(accent)
                        )
                    }
                    Text(
                        stringResource(R.string.minutes_format, minutes),
                        modifier = Modifier.width(58.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun OutcomeChart(nights: List<SleepNight>) {
    val visuals = listOf(
        Triple(SleepOutcome.NORMAL, stringResource(R.string.stats_badge_normal), MaterialTheme.colorScheme.secondary),
        Triple(SleepOutcome.SNOOZE, stringResource(R.string.stats_badge_snooze), MaterialTheme.colorScheme.primary),
        Triple(SleepOutcome.MISSED, stringResource(R.string.stats_badge_missed), MaterialTheme.colorScheme.error),
        Triple(SleepOutcome.ACTIVE, stringResource(R.string.stats_badge_active), MaterialTheme.colorScheme.tertiary)
    )
    SleepChartCard(stringResource(R.string.sleep_stats_outcomes_chart)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(138.dp), contentAlignment = Alignment.Center) {
                val emptyColor = MaterialTheme.colorScheme.surfaceVariant
                Canvas(Modifier.size(138.dp)) {
                    val stroke = 18.dp.toPx()
                    if (nights.isEmpty()) {
                        drawCircle(emptyColor, style = Stroke(stroke))
                    } else {
                        var start = -90f
                        visuals.forEach { (outcome, _, color) ->
                            val count = nights.count { it.outcome == outcome }
                            val sweep = count.toFloat() / nights.size * 360f
                            if (sweep > 0f) {
                                drawArc(color, start, sweep, false, style = Stroke(stroke))
                                start += sweep
                            }
                        }
                    }
                }
                Text(nights.size.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                visuals.forEach { (outcome, label, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(7.dp))
                        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(nights.count { it.outcome == outcome }.toString(), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CueEfficiencyCard(nights: List<SleepNight>) {
    val played = nights.sumOf { it.cuesPlayed }
    val scheduled = nights.sumOf { it.cuesScheduled }
    val ratio = if (scheduled == 0) 0f else (played.toFloat() / scheduled).coerceIn(0f, 1f)
    val accent = MaterialTheme.colorScheme.primary
    SleepChartCard(stringResource(R.string.sleep_stats_cues_chart)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (scheduled == 0) "—" else "${(ratio * 100).toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.sleep_stats_cues_value, played, scheduled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(ratio)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent)
            )
        }
    }
}

@Composable
private fun SleepHeatmap(month: YearMonth, nights: List<SleepNight>, locale: Locale) {
    val durationByDate = nights.groupBy { it.date }.mapValues { (_, value) -> value.sumOf { it.durationMillis } }
    val maximum = durationByDate.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val offset = month.atDay(1).dayOfWeek.value - 1
    val cells = ceil((offset + month.lengthOfMonth()) / 7f).toInt() * 7
    val accent = MaterialTheme.colorScheme.tertiary
    SleepChartCard(stringResource(R.string.sleep_stats_calendar_chart)) {
        Row(Modifier.fillMaxWidth()) {
            (1..7).forEach { day ->
                Text(
                    java.time.DayOfWeek.of(day).getDisplayName(TextStyle.NARROW, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        repeat(cells / 7) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { weekday ->
                    val day = week * 7 + weekday - offset + 1
                    if (day in 1..month.lengthOfMonth()) {
                        val value = durationByDate[month.atDay(day)] ?: 0L
                        val strength = (value.toFloat() / maximum).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (value == 0L) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                                    else accent.copy(alpha = 0.20f + strength * 0.72f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(day.toString(), style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepHistory(
    nights: List<SleepNight>,
    zone: ZoneId,
    canExport: Boolean,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.stats_label_history),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        TextButton(onClick = onExport, enabled = canExport) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text(stringResource(R.string.stats_action_export_csv))
        }
    }
    if (nights.isEmpty()) {
        Text(
            stringResource(R.string.activity_no_data),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        nights.forEach { night -> SleepHistoryRow(night, zone) }
    }
}

@Composable
private fun SleepHistoryRow(night: SleepNight, zone: ZoneId) {
    val locale = Locale.getDefault()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    night.date.format(DateTimeFormatter.ofPattern("d MMMM, EEE", locale)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${formatClock(night.startMillis, zone)} — ${formatClock(night.endMillis, zone)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                TimeFormatter.formatMinutes(night.durationMillis / MINUTE_MS),
                modifier = Modifier.padding(horizontal = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary
            )
            SleepOutcomeBadge(night.outcome)
        }
        if (night.cuesScheduled > 0) {
            Text(
                stringResource(R.string.sleep_stats_cues_value, night.cuesPlayed, night.cuesScheduled),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SleepOutcomeBadge(outcome: SleepOutcome) {
    val (text, color) = when (outcome) {
        SleepOutcome.NORMAL -> stringResource(R.string.stats_badge_normal) to MaterialTheme.colorScheme.secondary
        SleepOutcome.SNOOZE -> stringResource(R.string.stats_badge_snooze) to MaterialTheme.colorScheme.primary
        SleepOutcome.MISSED -> stringResource(R.string.stats_badge_missed) to MaterialTheme.colorScheme.error
        SleepOutcome.ACTIVE -> stringResource(R.string.stats_badge_active) to MaterialTheme.colorScheme.tertiary
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SleepEmptyCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("☾", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.tertiary)
        Text(stringResource(R.string.stats_empty_title), fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.sleep_stats_empty_period),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SleepDateAxis(nights: List<SleepNight>) {
    if (nights.isEmpty()) return
    val count = minOf(6, nights.size)
    val indexes = if (count == 1) listOf(0) else (0 until count).map { it * nights.lastIndex / (count - 1) }
    Row(Modifier.fillMaxWidth()) {
        indexes.forEachIndexed { position, index ->
            Text(
                nights[index].date.dayOfMonth.toString(),
                modifier = Modifier.weight(1f),
                textAlign = when (position) {
                    0 -> TextAlign.Start
                    indexes.lastIndex -> TextAlign.End
                    else -> TextAlign.Center
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SleepLegend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SleepChartCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

private fun buildSleepNights(
    allSessions: List<SleepSessionEntity>,
    snapshotTimeMillis: Long,
    zone: ZoneId
): List<SleepNight> {
    val byId = allSessions.associateBy { it.id }
    fun rootId(session: SleepSessionEntity): Int {
        var current = session
        val seen = mutableSetOf<Int>()
        while (current.parentSessionId != null && seen.add(current.id)) {
            current = byId[current.parentSessionId] ?: break
        }
        return current.id
    }

    return allSessions
        .filter { it.isActive || it.dismissType != DismissType.CANCELLED }
        .groupBy(::rootId)
        .values
        .mapNotNull { chain ->
            val actualIntervals = chain.mapNotNull { session ->
                val start = session.detectedSleepOnsetTime ?: session.estimatedSleepStartTime
                val end = session.actualWakeTime
                    ?: if (session.isActive) snapshotTimeMillis else session.estimatedWakeTime
                if (end > start) start to end else null
            }
            val plannedIntervals = chain.mapNotNull { session ->
                if (session.estimatedWakeTime > session.estimatedSleepStartTime) {
                    session.estimatedSleepStartTime to session.estimatedWakeTime
                } else null
            }
            val fallback = chain.minOfOrNull { it.bedTimePlanned } ?: return@mapNotNull null
            val start = actualIntervals.minOfOrNull { it.first } ?: fallback
            val end = actualIntervals.maxOfOrNull { it.second } ?: snapshotTimeMillis
            val latest = chain.maxByOrNull { it.actualWakeTime ?: it.updatedAt }
            val outcome = when {
                chain.any { it.isActive } -> SleepOutcome.ACTIVE
                chain.any { it.dismissType == DismissType.SNOOZE } -> SleepOutcome.SNOOZE
                latest?.dismissType == DismissType.MISSED -> SleepOutcome.MISSED
                else -> SleepOutcome.NORMAL
            }
            SleepNight(
                date = ActivityDayBoundary.dateFor(end, zone),
                sessions = chain,
                startMillis = start,
                endMillis = end,
                durationMillis = mergedDuration(actualIntervals),
                plannedDurationMillis = mergedDuration(plannedIntervals),
                bedTimeMillis = chain.minOf { it.bedTimePlanned },
                latencyMinutes = chain.firstNotNullOfOrNull { it.detectedOnsetLatencyMinutes },
                cuesPlayed = chain.sumOf { it.cuesPlayedCount },
                cuesScheduled = chain.sumOf { it.cuesScheduledCount },
                outcome = outcome
            )
        }
        .sortedBy { it.endMillis }
}

private fun mergedDuration(intervals: List<Pair<Long, Long>>): Long {
    if (intervals.isEmpty()) return 0L
    val sorted = intervals.sortedBy { it.first }
    var total = 0L
    var start = sorted.first().first
    var end = sorted.first().second
    sorted.drop(1).forEach { (nextStart, nextEnd) ->
        if (nextStart <= end) {
            end = maxOf(end, nextEnd)
        } else {
            total += end - start
            start = nextStart
            end = nextEnd
        }
    }
    return total + end - start
}

private fun averageClock(values: List<Long>, zone: ZoneId, wrapAfterNoon: Boolean): String {
    if (values.isEmpty()) return "—"
    val minutes = values.map { millis ->
        val time = Instant.ofEpochMilli(millis).atZone(zone)
        val raw = time.hour * 60 + time.minute
        if (wrapAfterNoon && raw < 12 * 60) raw + DAY_MINUTES else raw
    }.average().toInt().mod(DAY_MINUTES)
    return "%02d:%02d".format(Locale.ROOT, minutes / 60, minutes % 60)
}

private fun scheduleMinute(millis: Long, zone: ZoneId): Int {
    val time = Instant.ofEpochMilli(millis).atZone(zone)
    val raw = time.hour * 60 + time.minute
    return if (raw < 12 * 60) raw + DAY_MINUTES else raw
}

private fun formatClock(millis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))

private fun sleepPeriodLabel(
    period: SleepStatsPeriod,
    start: LocalDate,
    endExclusive: LocalDate,
    locale: Locale
): String = when (period) {
    SleepStatsPeriod.DAY -> start.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
    SleepStatsPeriod.WEEK -> {
        val formatter = DateTimeFormatter.ofPattern("d MMM", locale)
        "${start.format(formatter)} — ${endExclusive.minusDays(1).format(formatter)}"
    }
    SleepStatsPeriod.MONTH -> YearMonth.from(start)
        .format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

private const val MINUTE_MS = 60_000L
private const val DAY_MINUTES = 24 * 60
private const val SCHEDULE_START = 18 * 60
private const val SCHEDULE_SPAN = 18 * 60f
private const val MAX_GRAPH_NIGHTS = 31
