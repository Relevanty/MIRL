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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.FocusProtocolSessionEntity
import com.personal.sleepalarm.domain.calculator.ActivityDayBoundary
import com.personal.sleepalarm.domain.calculator.ActivityPeriodCalculator
import com.personal.sleepalarm.domain.calculator.ActivityPeriodTotals
import com.personal.sleepalarm.domain.calculator.effectiveActivityEndMillis
import com.personal.sleepalarm.domain.calculator.TrackedActivityType
import com.personal.sleepalarm.domain.calculator.TrackedInterval
import com.personal.sleepalarm.domain.model.DismissType
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.domain.model.projectActivityRecords
import com.personal.sleepalarm.ui.theme.appAccents
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

private enum class ActivityStatsPeriod { DAY, WEEK, MONTH }

private data class ActivityRecord(
    val type: TrackedActivityType,
    val startMillis: Long,
    val endMillis: Long,
    val label: String
) {
    fun asInterval() = TrackedInterval(type, startMillis, endMillis)
}

private data class CategoryVisual(
    val type: TrackedActivityType,
    val label: String,
    val color: Color
)

private data class ActivityBucket(
    val label: String,
    val totals: ActivityPeriodTotals
)

@Composable
fun ActivityStatsContent(
    modifier: Modifier = Modifier,
    viewModel: ActivityStatsViewModel = viewModel(),
    onAddActivity: () -> Unit = {}
) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val locale = Locale.getDefault()
    val initialDate = remember(zone) {
        ActivityDayBoundary.dateFor(source.snapshotTimeMillis, zone)
    }
    var period by remember { mutableStateOf(ActivityStatsPeriod.DAY) }
    var anchorDate by remember { mutableStateOf(initialDate) }

    val periodDates = remember(period, anchorDate) {
        when (period) {
            ActivityStatsPeriod.DAY -> anchorDate to anchorDate.plusDays(1)
            ActivityStatsPeriod.WEEK -> {
                val monday = anchorDate.minusDays((anchorDate.dayOfWeek.value - 1).toLong())
                monday to monday.plusWeeks(1)
            }
            ActivityStatsPeriod.MONTH -> {
                val month = YearMonth.from(anchorDate)
                month.atDay(1) to month.plusMonths(1).atDay(1)
            }
        }
    }
    val bounds = remember(periodDates, zone) {
        ActivityDayBoundary.boundsFor(periodDates.first, periodDates.second, zone)
    }
    val records = remember(source) { source.toActivityRecords() }
    val intervals = remember(records) { records.map(ActivityRecord::asInterval) }
    val totals = remember(bounds, intervals) {
        ActivityPeriodCalculator.calculate(bounds.first, bounds.second, intervals)
    }
    val categories = categoryVisuals()
    val periodLabel = remember(period, periodDates, locale) {
        formatPeriodLabel(period, periodDates.first, periodDates.second, locale)
    }
    val completedStudyBlocks = remember(source.completedFocusBlocks, bounds) {
        source.completedFocusBlocks.filter { block ->
            block.activityType == FocusActivityType.STUDY &&
                (block.completedAt ?: 0L) in bounds.first until bounds.second
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedButton(onClick = onAddActivity, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.activity_add_title))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = period == ActivityStatsPeriod.DAY,
                onClick = { period = ActivityStatsPeriod.DAY },
                label = { Text(stringResource(R.string.study_period_day)) }
            )
            FilterChip(
                selected = period == ActivityStatsPeriod.WEEK,
                onClick = { period = ActivityStatsPeriod.WEEK },
                label = { Text(stringResource(R.string.study_period_week)) }
            )
            FilterChip(
                selected = period == ActivityStatsPeriod.MONTH,
                onClick = { period = ActivityStatsPeriod.MONTH },
                label = { Text(stringResource(R.string.study_period_month)) }
            )
        }

        PeriodNavigator(
            label = periodLabel,
            onPrevious = {
                anchorDate = when (period) {
                    ActivityStatsPeriod.DAY -> anchorDate.minusDays(1)
                    ActivityStatsPeriod.WEEK -> anchorDate.minusWeeks(1)
                    ActivityStatsPeriod.MONTH -> anchorDate.minusMonths(1)
                }
            },
            onNext = {
                anchorDate = when (period) {
                    ActivityStatsPeriod.DAY -> anchorDate.plusDays(1)
                    ActivityStatsPeriod.WEEK -> anchorDate.plusWeeks(1)
                    ActivityStatsPeriod.MONTH -> anchorDate.plusMonths(1)
                }
            }
        )

        SummaryGrid(totals, categories)

        CompletedStudyBlocksCard(completedStudyBlocks, locale)

        DistributionChart(totals, categories)
        ComparisonChart(totals, categories)

        when (period) {
            ActivityStatsPeriod.DAY -> {
                DayTimelineChart(bounds.first, bounds.second, records, categories, zone)
                val hourly = remember(bounds, intervals, zone, locale) {
                    hourlyBuckets(bounds.first, bounds.second, intervals, zone, locale)
                }
                StackedBarChart(
                    title = stringResource(R.string.activity_chart_hourly),
                    buckets = hourly,
                    categories = categories,
                    labelCount = 5
                )
            }
            ActivityStatsPeriod.WEEK,
            ActivityStatsPeriod.MONTH -> {
                val daily = remember(periodDates, intervals, zone, locale) {
                    dailyBuckets(periodDates.first, periodDates.second, intervals, zone, locale)
                }
                StackedBarChart(
                    title = stringResource(R.string.activity_chart_daily),
                    buckets = daily,
                    categories = categories,
                    labelCount = if (period == ActivityStatsPeriod.WEEK) 7 else 6
                )
                TrendChart(daily, MaterialTheme.appAccents.focus.color)
                if (period == ActivityStatsPeriod.MONTH) {
                    ActivityHeatmap(
                        month = YearMonth.from(periodDates.first),
                        buckets = daily,
                        categories = categories,
                        locale = locale
                    )
                }
            }
        }

        TopActivitiesChart(
            records = records,
            periodStart = bounds.first,
            periodEnd = bounds.second,
            categories = categories
        )
    }
}

@Composable
private fun CompletedStudyBlocksCard(
    blocks: List<FocusProtocolSessionEntity>,
    locale: Locale
) {
    ChartCard(stringResource(R.string.study_completed_blocks)) {
        if (blocks.isEmpty()) {
            Text(
                text = stringResource(R.string.study_completed_blocks_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@ChartCard
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StudyMetric(
                value = blocks.size.toString(),
                label = stringResource(R.string.study_blocks_label),
                modifier = Modifier.weight(1f)
            )
            StudyMetric(
                value = blocks.sumOf { it.completedCycles }.toString(),
                label = stringResource(R.string.focus_block_cycles_label),
                modifier = Modifier.weight(1f)
            )
            StudyMetric(
                value = formatDuration(blocks.sumOf { it.totalFocusMillis }),
                label = stringResource(R.string.focus_block_focus_time_label),
                modifier = Modifier.weight(1f)
            )
        }

        blocks.take(6).forEach { block ->
            val date = remember(block.completedAt, locale) {
                Instant.ofEpochMilli(block.completedAt ?: block.createdAt)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", locale))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.48f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = block.itemName,
                        modifier = Modifier.weight(1f),
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.study_block_result,
                        block.completedCycles,
                        formatDuration(block.totalFocusMillis)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appAccents.work.color
                )
            }
        }
    }
}

@Composable
private fun StudyMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.appAccents.work.container)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun categoryVisuals(): List<CategoryVisual> = listOf(
    CategoryVisual(
        TrackedActivityType.SLEEP,
        stringResource(R.string.activity_sleep),
        MaterialTheme.appAccents.sleep.color
    ),
    CategoryVisual(
        TrackedActivityType.STUDY,
        stringResource(R.string.activity_study),
        MaterialTheme.appAccents.study.color
    ),
    CategoryVisual(
        TrackedActivityType.WORK,
        stringResource(R.string.activity_work),
        MaterialTheme.appAccents.work.color
    ),
    CategoryVisual(
        TrackedActivityType.OTHER,
        stringResource(R.string.activity_other),
        MaterialTheme.appAccents.other.color
    )
)

@Composable
private fun PeriodNavigator(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.activity_previous_period))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.activity_next_period))
        }
    }
}

@Composable
private fun SummaryGrid(totals: ActivityPeriodTotals, categories: List<CategoryVisual>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { category ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(category.color.copy(alpha = 0.12f))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(category.color)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(category.label, style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = formatDuration(totals.value(category.type)),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DistributionChart(totals: ActivityPeriodTotals, categories: List<CategoryVisual>) {
    val total = categories.sumOf { totals.value(it.type) }
    ChartCard(stringResource(R.string.activity_category_ratio)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(142.dp)) {
                val emptyColor = MaterialTheme.colorScheme.surfaceVariant
                Canvas(Modifier.size(142.dp)) {
                    val stroke = 18.dp.toPx()
                    if (total <= 0L) {
                        drawCircle(emptyColor, style = Stroke(stroke))
                    } else {
                        var start = -90f
                        categories.forEach { category ->
                            val sweep = totals.value(category.type).toFloat() / total * 360f
                            if (sweep > 0f) {
                                drawArc(
                                    color = category.color,
                                    startAngle = start,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    style = Stroke(stroke, cap = StrokeCap.Butt)
                                )
                                start += sweep
                            }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatDuration(total),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.activity_total_tracked),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                categories.forEach { category ->
                    val value = totals.value(category.type)
                    val percent = if (total == 0L) 0 else (value * 100 / total).toInt()
                    LegendRow(category, formatDuration(value), "$percent%")
                }
            }
        }
    }
}

@Composable
private fun ComparisonChart(totals: ActivityPeriodTotals, categories: List<CategoryVisual>) {
    val maximum = categories.maxOfOrNull { totals.value(it.type) }?.coerceAtLeast(1L) ?: 1L
    ChartCard(stringResource(R.string.activity_chart_comparison)) {
        categories.forEach { category ->
            val value = totals.value(category.type)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category.label, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(value.toFloat() / maximum)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(category.color)
                    )
                }
                Text(
                    text = formatDuration(value),
                    modifier = Modifier.width(76.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun DayTimelineChart(
    periodStart: Long,
    periodEnd: Long,
    records: List<ActivityRecord>,
    categories: List<CategoryVisual>,
    zone: ZoneId
) {
    val periodDuration = (periodEnd - periodStart).coerceAtLeast(1L)
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant
    ChartCard(stringResource(R.string.activity_chart_timeline)) {
        categories.forEach { category ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category.label, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall)
                val track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(track)
                ) {
                    records.asSequence()
                        .filter { it.type == category.type }
                        .forEach { record ->
                            val start = maxOf(record.startMillis, periodStart)
                            val end = minOf(record.endMillis, periodEnd)
                            if (end > start) {
                                val left = size.width * (start - periodStart).toFloat() / periodDuration
                                val width = size.width * (end - start).toFloat() / periodDuration
                                drawRoundRect(
                                    color = category.color,
                                    topLeft = Offset(left, 0f),
                                    size = Size(width.coerceAtLeast(2.dp.toPx()), size.height),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx())
                                )
                            }
                        }
                    (1..3).forEach { index ->
                        val x = size.width * index / 4f
                        drawLine(gridLineColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(72.dp))
            val labels = (0..4).map { index ->
                val millis = periodStart + periodDuration * index / 4
                Instant.ofEpochMilli(millis).atZone(zone).format(timeFormatter)
            }
            labels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = if (label == labels.last()) TextAlign.End else TextAlign.Start,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StackedBarChart(
    title: String,
    buckets: List<ActivityBucket>,
    categories: List<CategoryVisual>,
    labelCount: Int
) {
    val maxValue = buckets.maxOfOrNull { it.totals.total() }?.coerceAtLeast(1L) ?: 1L
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    ChartCard(title) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
        ) {
            repeat(3) { line ->
                val y = size.height * line / 2f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            if (buckets.isNotEmpty()) {
                val slot = size.width / buckets.size
                val barWidth = (slot * 0.62f).coerceAtLeast(2.dp.toPx())
                buckets.forEachIndexed { index, bucket ->
                    var bottom = size.height
                    categories.forEach { category ->
                        val value = bucket.totals.value(category.type)
                        val height = size.height * value.toFloat() / maxValue
                        if (height > 0f) {
                            drawRoundRect(
                                color = category.color,
                                topLeft = Offset(index * slot + (slot - barWidth) / 2, bottom - height),
                                size = Size(barWidth, height.coerceAtLeast(1.dp.toPx())),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                            )
                            bottom -= height
                        }
                    }
                }
            }
        }
        BucketAxisLabels(buckets, labelCount)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            categories.forEach { category -> MiniLegend(category) }
        }
    }
}

@Composable
private fun TrendChart(buckets: List<ActivityBucket>, color: Color) {
    val values = buckets.map { it.totals.total() }
    val maximum = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    ChartCard(stringResource(R.string.activity_chart_trend)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp)
        ) {
            repeat(3) { line ->
                val y = size.height * line / 2f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            if (values.isNotEmpty()) {
                val denominator = (values.size - 1).coerceAtLeast(1)
                val points = values.mapIndexed { index, value ->
                    Offset(
                        x = size.width * index / denominator,
                        y = size.height - size.height * value.toFloat() / maximum
                    )
                }
                points.zipWithNext().forEach { (start, end) ->
                    drawLine(color, start, end, 3.dp.toPx(), cap = StrokeCap.Round)
                }
                points.forEach { point ->
                    drawCircle(color, radius = 3.5.dp.toPx(), center = point)
                }
            }
        }
        BucketAxisLabels(buckets, 6)
    }
}

@Composable
private fun ActivityHeatmap(
    month: YearMonth,
    buckets: List<ActivityBucket>,
    categories: List<CategoryVisual>,
    locale: Locale
) {
    val byDay = buckets.mapIndexed { index, bucket -> month.atDay(index + 1) to bucket.totals }.toMap()
    val maxTotal = byDay.values.maxOfOrNull { it.total() }?.coerceAtLeast(1L) ?: 1L
    val offset = month.atDay(1).dayOfWeek.value - 1
    val cells = ceil((offset + month.lengthOfMonth()) / 7f).toInt() * 7

    ChartCard(stringResource(R.string.activity_chart_heatmap)) {
        Row(Modifier.fillMaxWidth()) {
            (1..7).forEach { day ->
                Text(
                    text = java.time.DayOfWeek.of(day).getDisplayName(TextStyle.NARROW, locale),
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
                    val dayNumber = week * 7 + weekday - offset + 1
                    if (dayNumber in 1..month.lengthOfMonth()) {
                        val totals = byDay[month.atDay(dayNumber)] ?: emptyTotals()
                        val dominant = categories.maxByOrNull { totals.value(it.type) }
                        val strength = (totals.total().toFloat() / maxTotal).coerceIn(0f, 1f)
                        val cellColor = if (totals.total() == 0L) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                        } else {
                            dominant!!.color.copy(alpha = 0.20f + strength * 0.72f)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(cellColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(dayNumber.toString(), style = MaterialTheme.typography.labelMedium)
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
private fun TopActivitiesChart(
    records: List<ActivityRecord>,
    periodStart: Long,
    periodEnd: Long,
    categories: List<CategoryVisual>
) {
    val sleepLabel = categories.first { it.type == TrackedActivityType.SLEEP }.label
    val rows = remember(records, periodStart, periodEnd, sleepLabel) {
        records
            .filter { it.endMillis > periodStart && it.startMillis < periodEnd }
            .groupBy { record ->
                record.type to if (record.type == TrackedActivityType.SLEEP) sleepLabel
                else record.label.ifBlank { "—" }
            }
            .map { (key, group) ->
                val duration = ActivityPeriodCalculator.calculate(
                    periodStart,
                    periodEnd,
                    group.map(ActivityRecord::asInterval)
                ).value(key.first)
                Triple(key.first, key.second, duration)
            }
            .filter { it.third > 0L }
            .sortedByDescending { it.third }
            .take(8)
    }
    val maximum = rows.maxOfOrNull { it.third }?.coerceAtLeast(1L) ?: 1L
    ChartCard(stringResource(R.string.activity_top_items)) {
        if (rows.isEmpty()) {
            Text(
                text = stringResource(R.string.activity_no_data),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            rows.forEach { (type, label, value) ->
                val category = categories.first { it.type == type }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(category.color))
                        Spacer(Modifier.width(7.dp))
                        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(formatDuration(value), style = MaterialTheme.typography.labelMedium)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(value.toFloat() / maximum)
                                .height(6.dp)
                                .background(category.color)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BucketAxisLabels(buckets: List<ActivityBucket>, labelCount: Int) {
    if (buckets.isEmpty()) return
    val count = labelCount.coerceAtMost(buckets.size).coerceAtLeast(1)
    val indexes = if (count == 1) listOf(0) else (0 until count).map { it * (buckets.lastIndex) / (count - 1) }
    Row(Modifier.fillMaxWidth()) {
        indexes.forEachIndexed { position, index ->
            Text(
                text = buckets[index].label,
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
private fun LegendRow(category: CategoryVisual, value: String, percent: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(category.color))
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(category.label, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(percent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MiniLegend(category: CategoryVisual) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(category.color))
        Spacer(Modifier.width(4.dp))
        Text(category.label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable ColumnScope.() -> Unit) {
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

private fun ActivityStatsSource.toActivityRecords(): List<ActivityRecord> {
    val focus = if (activityRecords.isNotEmpty()) projectActivityRecords(
        records = activityRecords,
        currentTasks = currentTasks
    ).mapNotNull { projection ->
        val record = projection.record
        val end = record.effectiveActivityEndMillis()
        if (end <= record.startedAt) return@mapNotNull null
        ActivityRecord(
            type = when (projection.activityType) {
                FocusActivityType.STUDY -> TrackedActivityType.STUDY
                FocusActivityType.WORK -> TrackedActivityType.WORK
                FocusActivityType.OTHER -> TrackedActivityType.OTHER
            },
            startMillis = record.startedAt,
            endMillis = end,
            label = projection.label
        )
    } else focusSessions.mapNotNull { session ->
        val naturalEnd = session.startedAt + session.actualDurationMillis
        val end = minOf(session.completedAt ?: naturalEnd, naturalEnd)
        if (end <= session.startedAt) return@mapNotNull null
        ActivityRecord(
            type = when (session.activityType) {
                FocusActivityType.STUDY -> TrackedActivityType.STUDY
                FocusActivityType.WORK -> TrackedActivityType.WORK
                FocusActivityType.OTHER -> TrackedActivityType.OTHER
            },
            startMillis = session.startedAt,
            endMillis = end,
            label = session.itemName
        )
    }
    val sleep = sleepSessions.mapNotNull { session ->
        if (!session.isActive && session.dismissType == DismissType.CANCELLED) return@mapNotNull null
        val start = session.detectedSleepOnsetTime ?: session.estimatedSleepStartTime
        val end = session.actualWakeTime
            ?: if (session.isActive) snapshotTimeMillis else session.estimatedWakeTime
        if (end <= start) return@mapNotNull null
        ActivityRecord(TrackedActivityType.SLEEP, start, end, "")
    }
    return focus + sleep
}

private fun dailyBuckets(
    startDate: LocalDate,
    endDate: LocalDate,
    intervals: List<TrackedInterval>,
    zone: ZoneId,
    locale: Locale
): List<ActivityBucket> {
    val formatter = DateTimeFormatter.ofPattern("d MMM", locale)
    return generateSequence(startDate) { date -> date.plusDays(1).takeIf { it < endDate } }
        .map { date ->
            val bounds = ActivityDayBoundary.boundsFor(date, date.plusDays(1), zone)
            ActivityBucket(
                label = date.format(formatter),
                totals = ActivityPeriodCalculator.calculate(bounds.first, bounds.second, intervals)
            )
        }
        .toList()
}

private fun hourlyBuckets(
    periodStart: Long,
    periodEnd: Long,
    intervals: List<TrackedInterval>,
    zone: ZoneId,
    locale: Locale
): List<ActivityBucket> {
    val formatter = DateTimeFormatter.ofPattern("HH:mm", locale)
    val result = mutableListOf<ActivityBucket>()
    var start = periodStart
    while (start < periodEnd) {
        val end = minOf(start + 60 * 60 * 1000L, periodEnd)
        result += ActivityBucket(
            label = Instant.ofEpochMilli(start).atZone(zone).format(formatter),
            totals = ActivityPeriodCalculator.calculate(start, end, intervals)
        )
        start = end
    }
    return result
}

private fun formatPeriodLabel(
    period: ActivityStatsPeriod,
    start: LocalDate,
    endExclusive: LocalDate,
    locale: Locale
): String = when (period) {
    ActivityStatsPeriod.DAY -> start.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
    ActivityStatsPeriod.WEEK -> {
        val formatter = DateTimeFormatter.ofPattern("d MMM", locale)
        "${start.format(formatter)} — ${endExclusive.minusDays(1).format(formatter)}"
    }
    ActivityStatsPeriod.MONTH -> YearMonth.from(start)
        .format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

private fun ActivityPeriodTotals.value(type: TrackedActivityType): Long = when (type) {
    TrackedActivityType.SLEEP -> sleepMillis
    TrackedActivityType.STUDY -> studyMillis
    TrackedActivityType.WORK -> workMillis
    TrackedActivityType.OTHER -> otherMillis
}

private fun ActivityPeriodTotals.total(): Long = sleepMillis + studyMillis + workMillis + otherMillis

private fun emptyTotals() = ActivityPeriodTotals(0L, 0L)

@Composable
private fun formatDuration(millis: Long): String {
    val minutes = millis.coerceAtLeast(0L) / 60_000L
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours > 0 && rest > 0 -> stringResource(R.string.activity_duration_hours_minutes, hours, rest)
        hours > 0 -> stringResource(R.string.activity_duration_hours, hours)
        else -> stringResource(R.string.activity_duration_minutes, rest)
    }
}
