package com.personal.sleepalarm.ui.stats

import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.StudySessionEntity
import com.personal.sleepalarm.data.db.entity.SubjectEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// =====================================================================
// Статистика учёбы: вид «День» (heatmap, сводка, donut, таймлайн)
// =====================================================================

@Composable
fun StudyStatsContent(
    viewModel: StudyStatsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    var period by remember { mutableStateOf("day") }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var weekStart by remember {
        mutableStateOf(
            LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        )
    }

    var monthStats by remember { mutableStateOf(YearMonth.now()) }

    val subjectById = remember(subjects) { subjects.associateBy { it.id } }
    val zone = ZoneId.systemDefault()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            mapOf(
                "day" to stringResource(R.string.study_period_day),
                "week" to stringResource(R.string.study_period_week),
                "month" to stringResource(R.string.study_period_month)
            ).forEach { (k, v) ->
                FilterChip(selected = period == k, onClick = { period = k }, label = { Text(v) })
            }
        }

        when (period) {
            "day" -> DayView(
                sessions = sessions,
                subjectById = subjectById,
                month = month,
                onMonth = { month = it },
                selectedDate = selectedDate,
                onSelect = { selectedDate = it },
                zone = zone
            )
            "week" -> WeekView(
                sessions = sessions,
                subjectById = subjectById,
                weekStart = weekStart,
                onWeek = { weekStart = it },
                zone = zone
            )
            "month" -> MonthView(
                sessions = sessions,
                subjectById = subjectById,
                month = monthStats,
                onMonth = { monthStats = it },
                zone = zone
            )
        }
    }
}

// =====================================================================
// День
// =====================================================================

@Composable
private fun DayView(
    sessions: List<StudySessionEntity>,
    subjectById: Map<Int, SubjectEntity>,
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    zone: ZoneId
) {
    val totalsByDay = remember(sessions) {
        sessions.groupBy { it.dateKey }.mapValues { (_, v) -> v.sumOf { it.durationMillis } }
    }
    val daySessions = sessions.filter { it.dateKey == selectedDate.toString() }.sortedBy { it.startMillis }
    val dayTotal = daySessions.sumOf { it.durationMillis }
    val maxSession = daySessions.maxOfOrNull { it.durationMillis } ?: 0L
    val firstStart = daySessions.firstOrNull()?.startMillis
    val lastEnd = daySessions.lastOrNull()?.endMillis

    StudyCard {
        MonthHeatmap(month, onMonth, totalsByDay, selectedDate, onSelect)
    }

    StudyCard {
        Text(
            selectedDate.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())),
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Row {
            StatCell(stringResource(R.string.study_total_time_multiline), fmtHMS(dayTotal), Modifier.weight(1f))
            StatCell(stringResource(R.string.study_max_focus_multiline), fmtHMS(maxSession), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row {
            StatCell(stringResource(R.string.study_start_time), firstStart?.let { fmtClock(it) } ?: "—", Modifier.weight(1f))
            StatCell(stringResource(R.string.study_end_time), lastEnd?.let { fmtClock(it) } ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
        Spacer(Modifier.height(12.dp))

        val yesterday = selectedDate.minusDays(1)
        val yTotal = totalsByDay[yesterday.toString()] ?: 0L
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.study_today), color = MaterialTheme.colorScheme.onBackground)
                Text("+" + fmtHMS(dayTotal), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.study_yesterday), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmtHMS(yTotal), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DayLineChart(daySessions, Modifier.weight(1.2f).height(110.dp))
        }
    }

    StudyCard {
        val donut = daySessions.groupBy { it.subjectId }.map { (id, list) ->
            val s = subjectById[id]
            DonutSlice(s?.name ?: stringResource(R.string.study_general), Color(s?.color ?: 0xFF9E9E9E.toInt()), list.sumOf { it.durationMillis })
        }
        if (donut.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(170.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                DonutBlock(donut)
            }
        }

        val span = if (firstStart != null && lastEnd != null) (lastEnd - firstStart) else 0L
        val other = (span - dayTotal).coerceAtLeast(0L)
        Box(
            modifier = Modifier.fillMaxWidth().height(170.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            DonutBlock(
                listOf(
                    DonutSlice(stringResource(R.string.study_label), MaterialTheme.colorScheme.primary, dayTotal),
                    DonutSlice(stringResource(R.string.study_other), MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), other)
                )
            )
        }
    }
    StudyCard {
        Text(
            selectedDate.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Person, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(120.dp))
                Spacer(Modifier.height(8.dp))
                Text(fmtHMS(dayTotal), style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            HourGrid(Modifier.weight(1f).height(260.dp))
        }
    }

    StudyCard {
        Text(stringResource(R.string.study_timeline), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(12.dp))
        StudyTimeline(daySessions, subjectById, selectedDate, zone)
    }
}

// =====================================================================
// Компоненты
// =====================================================================

@Composable
private fun StudyCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun MonthHeatmap(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    totalsByDay: Map<String, Long>,
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMonth(month.minusMonths(1)) }) {
            Icon(Icons.Default.ChevronLeft, null, tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground)
        IconButton(onClick = { onMonth(month.plusMonths(1)) }) {
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onBackground)
        }
    }
    Row {
        listOf(
            stringResource(R.string.day_mon),
            stringResource(R.string.day_tue),
            stringResource(R.string.day_wed),
            stringResource(R.string.day_thu),
            stringResource(R.string.day_fri),
            stringResource(R.string.day_sat),
            stringResource(R.string.day_sun)
        ).forEach {
            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val first = month.atDay(1)
    var d = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val grid = List(42) { val x = d; d = d.plusDays(1); x }
    grid.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            week.forEach { day ->
                val minutes = (totalsByDay[day.toString()] ?: 0L) / 60000
                val alpha = when {
                    minutes <= 0 -> 0f; minutes < 30 -> 0.3f; minutes < 60 -> 0.55f
                    minutes < 120 -> 0.8f; else -> 1f
                }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                        .background(
                            if (alpha == 0f) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            else primary.copy(alpha = alpha)
                        )
                        .clickable { onSelect(day) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (alpha > 0.5f) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf("0+", "4+", "7+", "10+", "12+").forEachIndexed { i, t ->
            Box(Modifier.clip(RoundedCornerShape(3.dp))
                .background(primary.copy(alpha = listOf(0.1f, 0.3f, 0.55f, 0.8f, 1f)[i]))
                .padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text(t, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onBackground)
            }
        }
        Spacer(Modifier.weight(1f))
        val monthTotal = totalsByDay.entries.sumOf { it.value }
        Text(stringResource(R.string.study_average_prefix) + " " + fmtHMS(monthTotal), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayLineChart(sessions: List<StudySessionEntity>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        drawLine(axis.copy(alpha = 0.4f), Offset(0f, h - 1f), Offset(w, h - 1f), strokeWidth = 1f)
        if (sessions.isEmpty()) return@Canvas
        val dayStart = sessions.first().startMillis
        val dayEnd = (sessions.last().endMillis).coerceAtLeast(dayStart + 1)
        var cum = 0L
        var prevX = 0f; var prevY = h - 2f
        sessions.forEach { s ->
            val x = ((s.startMillis - dayStart).toFloat() / (dayEnd - dayStart)) * (w - 8f)
            cum += s.durationMillis
            val y = h - 2f - (cum.toFloat() / (cum + 60_000f)) * (h - 12f)
            drawLine(color, Offset(prevX, prevY), Offset(x, prevY), strokeWidth = 3f)
            drawLine(color, Offset(x, prevY), Offset(x, y), strokeWidth = 3f)
            prevX = x; prevY = y
        }
        drawCircle(color, radius = 5f, center = Offset(prevX, prevY))
    }
}

@Composable
private fun HourGrid(modifier: Modifier = Modifier) {
    val hours = listOf(5,6,7,8,9,10,11,12,1,2,3,4,5,6,7,8,9,10,11,12)
    Column(modifier = modifier) {
        hours.forEach { h ->
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(h.toString(), style = MaterialTheme.typography.labelSmall, fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(16.dp))
                Box(Modifier.weight(1f).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)))
            }
        }
    }
}

@Composable
private fun DonutBlock(data: List<DonutSlice>) {
    val total = data.sumOf { it.value }
    Row(verticalAlignment = Alignment.CenterVertically) {
        DonutChart(data, modifier = Modifier.size(120.dp))
        Spacer(Modifier.width(24.dp))
        Column {
            data.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
                    Spacer(Modifier.width(8.dp))
                    Text(s.label, color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f))
                    val pct = if (total > 0) (s.value * 100 / total) else 0L
                    Text("${fmtHMS(s.value)} · $pct%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

private data class DonutSlice(val label: String, val color: Color, val value: Long)

@Composable
private fun DonutChart(data: List<DonutSlice>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val total = data.sumOf { it.value }.toFloat()
        if (total <= 0f) return@Canvas
        var start = -90f
        val stroke = Stroke(width = size.minDimension / 5f)
        data.forEach { s ->
            val sweep = s.value / total * 360f
            drawArc(s.color, start, sweep, useCenter = false, style = stroke)
            start += sweep
        }
    }
}

// =====================================================================
// Таймлайн
// =====================================================================

private sealed class TlEntry {
    data class Gap(val from: Long, val to: Long) : TlEntry()
    data class Session(val s: StudySessionEntity) : TlEntry()
}

@Composable
private fun StudyTimeline(
    sessions: List<StudySessionEntity>,
    subjectById: Map<Int, SubjectEntity>,
    currentDate: LocalDate,
    zone: ZoneId
) {
    val dayStart = currentDate.atTime(5, 0).atZone(zone).toInstant().toEpochMilli()
    val dayEnd = currentDate.plusDays(1).atTime(5, 0).atZone(zone).toInstant().toEpochMilli()

    val entries = mutableListOf<TlEntry>()
    var cursor = dayStart
    sessions.forEach { s ->
        if (s.startMillis > cursor) entries += TlEntry.Gap(cursor, s.startMillis)
        entries += TlEntry.Session(s)
        cursor = s.endMillis
    }
    if (cursor < dayEnd) entries += TlEntry.Gap(cursor, dayEnd)

    entries.forEach { e ->
        when (e) {
            is TlEntry.Gap -> Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${fmtClock(e.from)} ~ ${fmtClock(e.to)}   ${fmtGap(e.to - e.from)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp))
            }
            is TlEntry.Session -> {
                val subj = subjectById[e.s.subjectId]
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(fmtClock(e.s.startMillis), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(52.dp))
                    Box(Modifier.size(12.dp).clip(CircleShape)
                        .background(Color(subj?.color ?: 0xFF9E9E9E.toInt())))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(subj?.name ?: stringResource(R.string.study_general), color = MaterialTheme.colorScheme.onBackground)
                        Text(fmtGap(e.s.durationMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${fmtClock(e.s.startMillis)} ~ ${fmtClock(e.s.endMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.MoreVert, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// =====================================================================
// Форматирование
// =====================================================================

private fun fmtClock(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("a h:mm", Locale.ENGLISH))

private fun fmtHMS(ms: Long): String {
    val t = ms / 1000
    val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun fmtGap(ms: Long): String {
    val t = ms / 1000
    val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    val isEnglish = Locale.getDefault().language == "en"
    return when {
        h > 0 && isEnglish -> "$h hr $m min"
        h > 0 -> "$h ч. $m м."
        m > 0 && isEnglish -> "$m min $s sec"
        m > 0 -> "$m м. $s с."
        isEnglish -> "$s sec"
        else -> "$s с."
    }
}

// =====================================================================
// Неделя
// =====================================================================

@Composable
private fun WeekView(
    sessions: List<StudySessionEntity>,
    subjectById: Map<Int, SubjectEntity>,
    weekStart: LocalDate,
    onWeek: (LocalDate) -> Unit,
    zone: ZoneId
) {
    val totalsByDay = remember(sessions) {
        sessions.groupBy { it.dateKey }.mapValues { (_, v) -> v.sumOf { it.durationMillis } }
    }

    val weekDays = List(7) { weekStart.plusDays(it.toLong()) }
    val perDay = weekDays.map { totalsByDay[it.toString()] ?: 0L }
    val weekTotal = perDay.sum()
    val avg = weekTotal / 7

    val lastWeekStart = weekStart.minusWeeks(1)
    val lastWeekTotal = List(7) { lastWeekStart.plusDays(it.toLong()) }
        .sumOf { totalsByDay[it.toString()] ?: 0L }

    val weekSessions = sessions.filter { s ->
        val d = LocalDate.parse(s.dateKey)
        !d.isBefore(weekStart) && d.isBefore(weekStart.plusWeeks(1))
    }

    StudyCard { QuarterGrid(weekStart, onWeek, totalsByDay) }

    StudyCard {
        Text(
            stringResource(
                R.string.study_week_range,
                weekStart.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())),
                weekStart.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
            ),
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Row {
            StatCell(stringResource(R.string.study_total_time), fmtHMS(weekTotal), Modifier.weight(1f))
            StatCell(stringResource(R.string.study_average_day), fmtHMS(avg), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.study_until_friday), color = MaterialTheme.colorScheme.onBackground)
                Text("+" + fmtHMS(weekTotal), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.study_today), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.study_previous_week, fmtHMS(lastWeekTotal)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            WeekLineChart(perDay, List(7) { lastWeekStart.plusDays(it.toLong()) }
                .map { totalsByDay[it.toString()] ?: 0L },
                Modifier.weight(1.2f).height(110.dp))
        }
    }

    StudyCard {
        Text(stringResource(R.string.study_start_end), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        StartEndBarChart(weekDays, sessions, Modifier.fillMaxWidth().height(180.dp))
    }

    StudyCard {
        Text(stringResource(R.string.study_subject_ratio), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        val donut = weekSessions.groupBy { it.subjectId }.map { (id, list) ->
            val s = subjectById[id]
            DonutSlice(s?.name ?: stringResource(R.string.study_general), Color(s?.color ?: 0xFF9E9E9E.toInt()), list.sumOf { it.durationMillis })
        }
        if (donut.isNotEmpty()) DonutBlock(donut)
    }

    StudyCard {
        Text(stringResource(R.string.study_time_by_subject), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        StackedBarChart(weekDays, sessions, subjectById, Modifier.fillMaxWidth().height(180.dp))
    }

    StudyCard {
        Text(stringResource(R.string.study_cumulative_time), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        CumulativeAreaChart(perDay, Modifier.fillMaxWidth().height(160.dp))
    }

    StudyCard {
        Text(stringResource(R.string.study_break_ratio), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        val span = if (weekSessions.isNotEmpty())
            (weekSessions.maxOf { it.endMillis } - weekSessions.minOf { it.startMillis }) else 0L
        DonutBlock(
            listOf(
                DonutSlice(stringResource(R.string.study_label), MaterialTheme.colorScheme.primary, weekTotal),
                DonutSlice(stringResource(R.string.study_other), MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    (span - weekTotal).coerceAtLeast(0L))
            )
        )
    }
}

// =====================================================================
// Сетка недель квартала
// =====================================================================

@Composable
private fun QuarterGrid(
    weekStart: LocalDate,
    onWeek: (LocalDate) -> Unit,
    totalsByDay: Map<String, Long>
) {
    val quarter = (weekStart.monthValue - 1) / 3 + 1
    val qStart = LocalDate.of(weekStart.year, (quarter - 1) * 3 + 1, 1)
    val qEnd = qStart.plusMonths(3)
    var w = qStart.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val weeks = mutableListOf<LocalDate>()
    while (w.isBefore(qEnd)) { weeks += w; w = w.plusWeeks(1) }

    val primary = MaterialTheme.colorScheme.primary

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onWeek(weekStart.minusWeeks(13)) }) {
            Icon(Icons.Default.ChevronLeft, null, tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(stringResource(R.string.study_quarter_format, weekStart.year, quarter),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground)
        IconButton(onClick = { onWeek(weekStart.plusWeeks(13)) }) {
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onBackground)
        }
    }

    weeks.chunked(5).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { wk ->
                val total = List(7) { wk.plusDays(it.toLong()) }.sumOf { totalsByDay[it.toString()] ?: 0L }
                val selected = wk == weekStart
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                        .background(
                            if (total > 0) primary.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                        .then(if (selected) Modifier.background(primary.copy(alpha = 0.15f)) else Modifier)
                        .clickable { onWeek(wk) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(wk.format(DateTimeFormatter.ofPattern("dd.MM", Locale.getDefault())) + " ~",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground)
                    if (total > 0) {
                        Text(fmtHMS(total), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
            // добить пустые ячейки до 5
            repeat(5 - row.size) { Box(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// =====================================================================
// Графики недели
// =====================================================================

@Composable
private fun WeekLineChart(thisWeek: List<Long>, lastWeek: List<Long>, modifier: Modifier = Modifier) {
    val orange = MaterialTheme.colorScheme.primary
    val gray = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val max = (thisWeek.sum().coerceAtLeast(lastWeek.sum())).coerceAtLeast(1L).toFloat()
        fun drawLineCum(vals: List<Long>, color: Color) {
            var cum = 0f
            var prev = Offset(0f, h - 2f)
            vals.forEachIndexed { i, v ->
                cum += v
                val x = (i.toFloat() / 6f) * (w - 8f)
                val y = h - 2f - (cum / max) * (h - 12f)
                val cur = Offset(x, y)
                drawLine(color, prev, cur, strokeWidth = 3f)
                prev = cur
            }
            drawCircle(color, radius = 5f, center = prev)
        }
        drawLineCum(lastWeek, gray)
        drawLineCum(thisWeek, orange)
    }
}

@Composable
private fun StartEndBarChart(days: List<LocalDate>, sessions: List<StudySessionEntity>, modifier: Modifier = Modifier) {
    val orange = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        drawLine(axis.copy(alpha = 0.4f), Offset(0f, h - 1f), Offset(w, h - 1f), strokeWidth = 1f)
        val slot = w / 7f
        days.forEachIndexed { i, d ->
            val ds = sessions.filter { LocalDate.parse(it.dateKey) == d }
            if (ds.isNotEmpty()) {
                val startMin = Instant.ofEpochMilli(ds.minOf { it.startMillis })
                    .atZone(ZoneId.systemDefault()).toLocalTime().toSecondOfDay() / 60
                val endMin = Instant.ofEpochMilli(ds.maxOf { it.endMillis })
                    .atZone(ZoneId.systemDefault()).toLocalTime().toSecondOfDay() / 60
                val y1 = (startMin / 1440f) * (h - 20f)
                val y2 = (endMin / 1440f) * (h - 20f)
                val cx = slot * i + slot / 2
                val bw = slot * 0.5f
                drawRect(orange, topLeft = Offset(cx - bw / 2, y1),
                    size = androidx.compose.ui.geometry.Size(bw, (y2 - y1).coerceAtLeast(4f)))
            }
            val lbl = d.format(DateTimeFormatter.ofPattern("EE", Locale("ru")))
            // подпись дня
            drawLine(axis.copy(alpha = 0f), Offset(0f, 0f), Offset(0f, 0f))
        }
    }
}

@Composable
private fun StackedBarChart(
    days: List<LocalDate>,
    sessions: List<StudySessionEntity>,
    subjectById: Map<Int, SubjectEntity>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val slot = w / 7f
        val max = days.maxOf { d ->
            sessions.filter { LocalDate.parse(it.dateKey) == d }.sumOf { it.durationMillis }
        }.coerceAtLeast(1L).toFloat()
        days.forEachIndexed { i, d ->
            val ds = sessions.filter { LocalDate.parse(it.dateKey) == d }
            var yBottom = h - 20f
            val cx = slot * i + slot / 2
            val bw = slot * 0.5f
            ds.groupBy { it.subjectId }.forEach { (id, list) ->
                val v = list.sumOf { it.durationMillis }
                val bh = (v / max) * (h - 40f)
                val color = Color(subjectById[id]?.color ?: 0xFF9E9E9E.toInt())
                drawRect(color, topLeft = Offset(cx - bw / 2, yBottom - bh),
                    size = androidx.compose.ui.geometry.Size(bw, bh))
                yBottom -= bh
            }
        }
    }
}

@Composable
private fun CumulativeAreaChart(values: List<Long>, modifier: Modifier = Modifier) {
    val green = MaterialTheme.colorScheme.secondary
    val gray = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val max = values.sum().coerceAtLeast(1L).toFloat()
        var cum = 0f
        val pts = mutableListOf<Offset>(Offset(0f, h - 2f))
        values.forEachIndexed { i, v ->
            cum += v
            pts += Offset((i.toFloat() / 6f) * (w - 8f), h - 2f - (cum / max) * (h - 16f))
        }
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, h - 2f)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(w - 8f, h - 2f)
            close()
        }
        drawPath(path, green)
    }
}
// =====================================================================
// Месяц
// =====================================================================

@Composable
private fun MonthView(
    sessions: List<StudySessionEntity>,
    subjectById: Map<Int, SubjectEntity>,
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    zone: ZoneId
) {
    val totalsByDay = remember(sessions) {
        sessions.groupBy { it.dateKey }.mapValues { (_, v) -> v.sumOf { it.durationMillis } }
    }

    val days = List(month.lengthOfMonth()) { month.atDay(it + 1) }
    val perDay = days.map { totalsByDay[it.toString()] ?: 0L }
    val monthTotal = perDay.sum()
    val avg = monthTotal / days.size

    val lastMonth = month.minusMonths(1)
    val lastDays = List(lastMonth.lengthOfMonth()) { lastMonth.atDay(it + 1) }
    val lastTotal = lastDays.sumOf { totalsByDay[it.toString()] ?: 0L }

    val monthSessions = sessions.filter { s ->
        val d = LocalDate.parse(s.dateKey)
        YearMonth.from(d) == month
    }

    StudyCard { MonthGrid(month, onMonth, totalsByDay) }

    StudyCard {
        Text(stringResource(
                R.string.study_month_year_format,
                month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                month.year
            ),
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(12.dp))
        Row {
            StatCell(stringResource(R.string.study_total_time), fmtHMS(monthTotal), Modifier.weight(1f))
            StatCell(stringResource(R.string.study_average_day), fmtHMS(avg), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.study_today), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.study_previous_month), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MonthStepChart(perDay, lastDays.map { totalsByDay[it.toString()] ?: 0L },
                Modifier.weight(1.4f).height(120.dp))
        }
    }

    StudyCard {
        Text(stringResource(R.string.study_start_end), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        MonthStartEndBar(days, sessions, Modifier.fillMaxWidth().height(180.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("01.${"%02d".format(month.monthValue)}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("16.${"%02d".format(month.monthValue)}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${month.lengthOfMonth()}.${"%02d".format(month.monthValue)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    StudyCard {
        Text(stringResource(R.string.study_subject_ratio), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        val donut = monthSessions.groupBy { it.subjectId }.map { (id, list) ->
            val s = subjectById[id]
            DonutSlice(s?.name ?: stringResource(R.string.study_general), Color(s?.color ?: 0xFF9E9E9E.toInt()), list.sumOf { it.durationMillis })
        }
        if (donut.isNotEmpty()) DonutBlock(donut)
    }

    StudyCard {
        Text(stringResource(R.string.study_time_by_subject), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        MonthStackedBar(days, sessions, subjectById, Modifier.fillMaxWidth().height(180.dp))
    }

    StudyCard {
        Text(stringResource(R.string.study_cumulative_time), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        MonthCumulativeArea(perDay, Modifier.fillMaxWidth().height(160.dp))
    }

    StudyCard {
        Text(stringResource(R.string.study_break_ratio), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        val span = if (monthSessions.isNotEmpty())
            (monthSessions.maxOf { it.endMillis } - monthSessions.minOf { it.startMillis }) else 0L
        DonutBlock(
            listOf(
                DonutSlice(stringResource(R.string.study_label), MaterialTheme.colorScheme.primary, monthTotal),
                DonutSlice(stringResource(R.string.study_other), MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    (span - monthTotal).coerceAtLeast(0L))
            )
        )
    }
}

// =====================================================================
// Сетка года (12 месяцев)
// =====================================================================

@Composable
private fun MonthGrid(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    totalsByDay: Map<String, Long>
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMonth(month.minusYears(1)) }) {
            Icon(Icons.Default.ChevronLeft, null, tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(month.year.toString(), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground)
        IconButton(onClick = { onMonth(month.plusYears(1)) }) {
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onBackground)
        }
    }

    val months = List(12) { YearMonth.of(month.year, it + 1) }
    months.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { m ->
                val mDays = List(m.lengthOfMonth()) { m.atDay(it + 1) }
                val total = mDays.sumOf { totalsByDay[it.toString()] ?: 0L }
                val selected = m == month
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                        .background(
                            if (total > 0) primary.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                        .clickable { onMonth(m) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(m.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground)
                    if (total > 0) {
                        Text(fmtHMS(total), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
            repeat(4 - row.size) { Box(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// =====================================================================
// Графики месяца
// =====================================================================

@Composable
private fun MonthStepChart(thisMonth: List<Long>, lastMonth: List<Long>, modifier: Modifier = Modifier) {
    val orange = MaterialTheme.colorScheme.primary
    val gray = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val max = (thisMonth.sum().coerceAtLeast(lastMonth.sum())).coerceAtLeast(1L).toFloat()

        // прошлый месяц — серая ступенчатая область
        var cum = 0f
        val lastPts = mutableListOf<Offset>(Offset(0f, h - 2f))
        lastMonth.forEachIndexed { i, v ->
            cum += v
            lastPts += Offset((i.toFloat() / (lastMonth.size - 1).coerceAtLeast(1)) * (w - 8f),
                h - 2f - (cum / max) * (h - 12f))
        }
        val area = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, h - 2f)
            lastPts.forEach { lineTo(it.x, it.y) }
            lineTo(w - 8f, h - 2f); close()
        }
        drawPath(area, gray)

        // этот месяц — оранжевая линия
        cum = 0f
        var prev = Offset(0f, h - 2f)
        thisMonth.forEachIndexed { i, v ->
            cum += v
            val cur = Offset((i.toFloat() / (thisMonth.size - 1).coerceAtLeast(1)) * (w - 8f),
                h - 2f - (cum / max) * (h - 12f))
            drawLine(orange, prev, cur, strokeWidth = 3f)
            prev = cur
        }
        drawCircle(orange, radius = 5f, center = prev)
    }
}

@Composable
private fun MonthStartEndBar(days: List<LocalDate>, sessions: List<StudySessionEntity>, modifier: Modifier = Modifier) {
    val orange = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val slot = w / days.size
        days.forEachIndexed { i, d ->
            val ds = sessions.filter { LocalDate.parse(it.dateKey) == d }
            if (ds.isNotEmpty()) {
                val startMin = Instant.ofEpochMilli(ds.minOf { it.startMillis })
                    .atZone(ZoneId.systemDefault()).toLocalTime().toSecondOfDay() / 60
                val endMin = Instant.ofEpochMilli(ds.maxOf { it.endMillis })
                    .atZone(ZoneId.systemDefault()).toLocalTime().toSecondOfDay() / 60
                val y1 = (startMin / 1440f) * (h - 20f)
                val y2 = (endMin / 1440f) * (h - 20f)
                val cx = slot * i + slot / 2
                val bw = (slot * 0.6f).coerceAtMost(8f)
                drawRect(orange, topLeft = Offset(cx - bw / 2, y1),
                    size = androidx.compose.ui.geometry.Size(bw, (y2 - y1).coerceAtLeast(4f)))
            }
        }
    }
}

@Composable
private fun MonthStackedBar(
    days: List<LocalDate>,
    sessions: List<StudySessionEntity>,
    subjectById: Map<Int, SubjectEntity>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val slot = w / days.size
        val max = days.maxOf { d ->
            sessions.filter { LocalDate.parse(it.dateKey) == d }.sumOf { it.durationMillis }
        }.coerceAtLeast(1L).toFloat()
        days.forEachIndexed { i, d ->
            val ds = sessions.filter { LocalDate.parse(it.dateKey) == d }
            var yBottom = h - 20f
            val cx = slot * i + slot / 2
            val bw = (slot * 0.6f).coerceAtMost(10f)
            ds.groupBy { it.subjectId }.forEach { (id, list) ->
                val v = list.sumOf { it.durationMillis }
                val bh = (v / max) * (h - 40f)
                drawRect(Color(subjectById[id]?.color ?: 0xFF9E9E9E.toInt()),
                    topLeft = Offset(cx - bw / 2, yBottom - bh),
                    size = androidx.compose.ui.geometry.Size(bw, bh))
                yBottom -= bh
            }
        }
    }
}

@Composable
private fun MonthCumulativeArea(values: List<Long>, modifier: Modifier = Modifier) {
    val green = MaterialTheme.colorScheme.secondary
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val max = values.sum().coerceAtLeast(1L).toFloat()
        var cum = 0f
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(0f, h - 2f) }
        values.forEachIndexed { i, v ->
            cum += v
            path.lineTo(
                (i.toFloat() / (values.size - 1).coerceAtLeast(1)) * (w - 8f),
                h - 2f - (cum / max) * (h - 16f)
            )
        }
        path.lineTo(w - 8f, h - 2f)
        path.close()          // ← вот тут: close() вызывается НА path
        drawPath(path, green)
    }
}
