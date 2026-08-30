package com.personal.sleepalarm.ui.stats

import com.personal.sleepalarm.ui.theme.appAccents

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.domain.calculator.CorrelationCalculator

/**
 * Секция «Сон vs Задачи vs Настроение» для StatsScreen.
 *
 * Все цвета берутся из MaterialTheme.colorScheme, поэтому секция
 * подстраивается под любую из 14 тем. Три серии различимы:
 * сон = primary, задачи = secondary, настроение = их смесь (lerp).
 */
@Composable
fun CorrelationSection(
    viewModel: CorrelationViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.corr_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (!state.enoughData) {
            Text(
                text = stringResource(R.string.corr_not_enough),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        CorrRow(label = stringResource(R.string.corr_sleep_mood), r = state.rSleepMood)
        CorrRow(label = stringResource(R.string.corr_tasks_mood), r = state.rTasksMood)
        CorrRow(label = stringResource(R.string.corr_sleep_tasks), r = state.rSleepTasks)

        Spacer(modifier = Modifier.height(4.dp))

        CorrelationChart(points = state.points)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(color = sleepColor(), text = stringResource(R.string.corr_legend_sleep))
            LegendDot(color = tasksColor(), text = stringResource(R.string.corr_legend_tasks))
            LegendDot(color = moodColor(), text = stringResource(R.string.corr_legend_mood))
        }
    }
}

// =====================================================================
// Строка коэффициента
// =====================================================================

@Composable
private fun CorrRow(label: String, r: Double?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (r == null) {
            Text(
                text = stringResource(R.string.corr_no_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "%.2f · %s".format(r, strengthText(r)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appAccents.focus.color
            )
        }
    }
}

@Composable
private fun strengthText(r: Double): String =
    when (CorrelationCalculator.strength(r)) {
        CorrelationCalculator.Strength.NONE -> stringResource(R.string.corr_strength_none)
        CorrelationCalculator.Strength.WEAK -> stringResource(R.string.corr_strength_weak)
        CorrelationCalculator.Strength.MODERATE -> stringResource(R.string.corr_strength_moderate)
        CorrelationCalculator.Strength.STRONG -> stringResource(R.string.corr_strength_strong)
        CorrelationCalculator.Strength.VERY_STRONG -> stringResource(R.string.corr_strength_very)
    }

// =====================================================================
// Line chart (3 нормированные серии)
// =====================================================================

@Composable
private fun CorrelationChart(points: List<DayPoint>) {
    val sleep = sleepColor()
    val tasks = tasksColor()
    val mood = moodColor()
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        if (points.size < 2) return@Canvas

        // Тонкая базовая линия внизу для ориентира.
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)

        val slot = size.width / (points.size - 1).coerceAtLeast(1)

        fun drawSeries(values: List<Double?>, color: Color) {
            val present = values.mapIndexedNotNull { i, v ->
                v?.let { i to it }
            }
            if (present.isEmpty()) return

            val max = present.maxOf { it.second }.coerceAtLeast(1.0)
            val min = present.minOf { it.second }
            val range = (max - min).coerceAtLeast(1.0)

            var prev: Offset? = null
            present.forEach { (i, v) ->
                val x: Float = i.toFloat() * slot
                val norm = (v - min) / range
                val y: Float = size.height - (norm * (size.height - 8f) + 4f).toFloat()
                val cur = Offset(x = x, y = y)
                prev?.let { p ->
                    drawLine(color, p, cur, strokeWidth = 3f)
                }
                prev = cur
            }
        }

        drawSeries(points.map { it.sleepMinutes }, sleep)
        drawSeries(points.map { it.tasksDone }, tasks)
        drawSeries(points.map { it.mood }, mood)
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(color)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// =====================================================================
// Цвета серий — все из темы, гарантированно различимы
// =====================================================================

@Composable
private fun sleepColor(): Color = MaterialTheme.appAccents.sleep.color

@Composable
private fun tasksColor(): Color = MaterialTheme.appAccents.work.color

/** Настроение = смесь primary и secondary, чтобы отличаться от обеих серий. */
@Composable
private fun moodColor(): Color {
    val cs = MaterialTheme.colorScheme
    return lerp(cs.primary, cs.secondary, 0.5f)
}
