package com.personal.sleepalarm.ui.stats

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.sleepalarm.util.TimeFormatter

// =====================================================================
// Цвета графиков теперь берутся из MaterialTheme.colorScheme,
// поэтому графики подстраиваются под любую из 14 тем.
// =====================================================================

@Composable
fun DurationBarChart(
    days: List<DayStat>,
    popupText: (Long) -> String,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // === Цвета из текущей темы ===
    val cs = MaterialTheme.colorScheme
    val barTop = cs.primary
    val barBottom = lerp(cs.primary, cs.background, 0.35f)
    val barSelectedTop = lerp(cs.primary, cs.onBackground, 0.4f)
    val barSelectedBottom = cs.primary
    val gridColor = cs.outline.copy(alpha = 0.4f)
    val axisTextColor = cs.onSurfaceVariant
    val emptyBarColor = cs.surfaceVariant
    val popupBg = cs.primary
    val popupText = cs.onPrimary

    var widthPx by remember { mutableIntStateOf(0) }
    var heightPx by remember { mutableIntStateOf(0) }

    val padLeftPx = with(density) { 30.dp.toPx() }
    val padRightPx = with(density) { 8.dp.toPx() }
    val padTopPx = with(density) { 28.dp.toPx() }
    val padBottomPx = with(density) { 22.dp.toPx() }

    var target by remember { mutableStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 1_100),
        label = "barGrow"
    )
    LaunchedEffect(days.size) { target = 1f }

    var selected by remember { mutableIntStateOf(-1) }

    val dataMax = days.maxOfOrNull { it.durationMinutes } ?: 0L
    val axisMaxMinutes = ((maxOf(dataMax, 6 * 60) + 59) / 60) * 60
    val axisMidMinutes = axisMaxMinutes / 2

    val count = days.size

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .onSizeChanged { size ->
                widthPx = size.width
                heightPx = size.height
            }
            .pointerInput(count, widthPx) {
                detectTapGestures { offset ->
                    val index = barIndexAt(
                        xPx = offset.x,
                        widthPx = widthPx.toFloat(),
                        padLeftPx = padLeftPx,
                        padRightPx = padRightPx,
                        count = count
                    )
                    selected = if (index == selected) -1 else index
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val plotTop = padTopPx
        val plotBottom = h - padBottomPx
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
        val plotLeft = padLeftPx
        val plotRight = w - padRightPx
        val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)

        drawAxisGuide(
            y = plotTop, left = plotLeft, right = plotRight,
            label = hoursLabel(axisMaxMinutes),
            textMeasurer = textMeasurer,
            gridColor = gridColor, textColor = axisTextColor
        )
        drawAxisGuide(
            y = plotTop + plotHeight / 2f, left = plotLeft, right = plotRight,
            label = hoursLabel(axisMidMinutes),
            textMeasurer = textMeasurer,
            gridColor = gridColor, textColor = axisTextColor
        )

        if (count == 0) return@Canvas

        val slot = plotWidth / count
        val barWidth = (slot * 0.56f).coerceAtLeast(2f)
        val stagger = 3f

        days.forEachIndexed { index, day ->
            val centerX = plotLeft + slot * index + slot / 2f

            val localProgress = ((progress * (count + stagger) - index) / stagger)
                .coerceIn(0f, 1f)

            if (!day.hasData) {
                drawRoundRect(
                    color = emptyBarColor,
                    topLeft = Offset(centerX - barWidth / 2f, plotBottom - 4f),
                    size = Size(barWidth, 4f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f)
                )
            } else {
                val fullBarHeight = (day.durationMinutes.toFloat() / axisMaxMinutes.toFloat()) * plotHeight
                val barHeight = fullBarHeight * localProgress
                val top = plotBottom - barHeight

                val isSelected = index == selected

                val brush = Brush.verticalGradient(
                    colors = if (isSelected) {
                        listOf(barSelectedTop, barSelectedBottom)
                    } else {
                        listOf(barTop, barBottom)
                    },
                    startY = top,
                    endY = plotBottom
                )

                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(centerX - barWidth / 2f, top),
                    size = Size(barWidth, barHeight.coerceAtLeast(0f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(x = 4f, y = 4f)
                )

                if (isSelected) {
                    drawLine(
                        color = barSelectedTop.copy(alpha = 0.35f),
                        start = Offset(centerX, plotTop),
                        end = Offset(centerX, plotBottom),
                        strokeWidth = 1f
                    )
                }
            }

            val dayLayout = textMeasurer.measure(
                text = day.dayLabel,
                style = TextStyle(
                    fontSize = 9.sp,
                    color = if (index == selected) barTop else axisTextColor,
                    fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal
                )
            )
            drawText(
                textLayoutResult = dayLayout,
                topLeft = Offset(
                    x = centerX - dayLayout.size.width / 2f,
                    y = plotBottom + 6f
                )
            )
        }

        if (selected in days.indices && days[selected].hasData) {
            val centerX = plotLeft + slot * selected + slot / 2f
            val fullBarHeight = (days[selected].durationMinutes.toFloat() / axisMaxMinutes.toFloat()) * plotHeight
            val top = plotBottom - fullBarHeight * localProgressFor(selected, progress, count, stagger)

            val popup = popupText(days[selected].durationMinutes)
            val popupLayout = textMeasurer.measure(
                text = popup,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = popupText
                )
            )

            val popupPadH = 8f
            val popupPadV = 4f
            val popupW = popupLayout.size.width + popupPadH * 2
            val popupH = popupLayout.size.height + popupPadV * 2

            var popupLeft = centerX - popupW / 2f
            popupLeft = popupLeft.coerceIn(plotLeft, plotRight - popupW)
            val popupTop = (top - popupH - 6f).coerceAtLeast(0f)

            drawRoundRect(
                color = popupBg,
                topLeft = Offset(popupLeft, popupTop),
                size = Size(popupW, popupH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f)
            )
            drawText(
                textLayoutResult = popupLayout,
                topLeft = Offset(
                    x = popupLeft + popupPadH,
                    y = popupTop + popupPadV
                )
            )
        }
    }
}

@Composable
fun WakeTimeLineChart(
    days: List<DayStat>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    // === Цвета из текущей темы ===
    val cs = MaterialTheme.colorScheme
    val lineColor = cs.secondary
    val gridColor = cs.outline.copy(alpha = 0.4f)
    val axisTextColor = cs.onSurfaceVariant

    var target by remember { mutableStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 1_300),
        label = "lineDraw"
    )
    LaunchedEffect(days.size) { target = 1f }

    val pulse = rememberInfiniteTransition(label = "wakePulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val values = days.mapNotNull { it.wakeMinutesOfDay }
    val rawMin = values.minOrNull() ?: (7 * 60)
    val rawMax = values.maxOrNull() ?: (8 * 60)
    val axisMin = ((rawMin - 30).coerceAtLeast(0) / 30) * 30
    val axisMax = ((rawMax + 30) / 30) * 30
    val axisSpan = (axisMax - axisMin).coerceAtLeast(30)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val w = size.width
        val h = size.height
        val padLeft = 40f
        val padRight = 12f
        val padTop = 12f
        val padBottom = 22f
        val plotTop = padTop
        val plotBottom = h - padBottom
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
        val plotLeft = padLeft
        val plotRight = w - padRight
        val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)

        drawAxisGuideTime(
            y = plotTop, left = plotLeft, right = plotRight,
            minutes = axisMin,
            textMeasurer = textMeasurer,
            gridColor = gridColor, textColor = axisTextColor
        )
        drawAxisGuideTime(
            y = plotBottom, left = plotLeft, right = plotRight,
            minutes = axisMax,
            textMeasurer = textMeasurer,
            gridColor = gridColor, textColor = axisTextColor
        )

        val count = days.size
        if (count < 2) {
            drawEmptyHint(textMeasurer, plotLeft, plotTop, plotHeight, axisTextColor)
            return@Canvas
        }

        val stepX = plotWidth / (count - 1)

        fun yFor(minutes: Int): Float {
            val t = (minutes - axisMin).toFloat() / axisSpan.toFloat()
            return plotTop + t.coerceIn(0f, 1f) * plotHeight
        }

        data class Pt(val index: Int, val x: Float, val y: Float)
        val points = mutableListOf<Pt>()
        days.forEachIndexed { index, day ->
            val wake = day.wakeMinutesOfDay ?: return@forEachIndexed
            points += Pt(
                index = index,
                x = plotLeft + stepX * index,
                y = yFor(wake)
            )
        }

        if (points.isEmpty()) {
            drawEmptyHint(textMeasurer, plotLeft, plotTop, plotHeight, axisTextColor)
            return@Canvas
        }

        val drawnFloat = progress * (points.size - 1).coerceAtLeast(1)
        val fullCount = drawnFloat.toInt()
        val frac = drawnFloat - fullCount

        val head: Offset = if (fullCount >= points.size - 1) {
            Offset(points.last().x, points.last().y)
        } else {
            val a = points[fullCount]
            val b = points[fullCount + 1]
            Offset(
                x = a.x + (b.x - a.x) * frac,
                y = a.y + (b.y - a.y) * frac
            )
        }

        val areaPath = Path().apply {
            moveTo(points[0].x, plotBottom)
            for (i in 0..fullCount.coerceAtMost(points.size - 1)) {
                lineTo(points[i].x, points[i].y)
            }
            lineTo(head.x, head.y)
            lineTo(head.x, plotBottom)
            close()
        }
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent),
                startY = plotTop,
                endY = plotBottom
            )
        )

        val linePath = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1..fullCount.coerceAtMost(points.size - 1)) {
                lineTo(points[i].x, points[i].y)
            }
            lineTo(head.x, head.y)
        }
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
        )

        for (i in 0..fullCount.coerceAtMost(points.size - 1)) {
            drawCircle(
                color = lineColor,
                radius = 3.5f,
                center = Offset(points[i].x, points[i].y)
            )
        }

        drawCircle(
            color = lineColor.copy(alpha = pulseAlpha),
            radius = 8f,
            center = head
        )
        drawCircle(
            color = lineColor,
            radius = 4f,
            center = head
        )

        days.forEachIndexed { index, day ->
            val x = plotLeft + stepX * index
            val layout = textMeasurer.measure(
                text = day.dayLabel,
                style = TextStyle(fontSize = 9.sp, color = axisTextColor)
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(x - layout.size.width / 2f, plotBottom + 6f)
            )
        }
    }
}

// =====================================================================
// Вспомогательные функции рисования (цвета теперь передаются параметрами)
// =====================================================================

private fun DrawScope.drawAxisGuide(
    y: Float,
    left: Float,
    right: Float,
    label: String,
    textMeasurer: TextMeasurer,
    gridColor: Color,
    textColor: Color
) {
    drawLine(
        color = gridColor,
        start = Offset(left, y),
        end = Offset(right, y),
        strokeWidth = 1f,
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
            intervals = floatArrayOf(4f, 4f),
            phase = 0f
        )
    )
    val layout = textMeasurer.measure(
        text = label,
        style = TextStyle(fontSize = 9.sp, color = textColor)
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(x = 0f, y = y - layout.size.height / 2f)
    )
}

private fun DrawScope.drawAxisGuideTime(
    y: Float,
    left: Float,
    right: Float,
    minutes: Int,
    textMeasurer: TextMeasurer,
    gridColor: Color,
    textColor: Color
) {
    drawLine(
        color = gridColor,
        start = Offset(left, y),
        end = Offset(right, y),
        strokeWidth = 1f,
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
            intervals = floatArrayOf(4f, 4f),
            phase = 0f
        )
    )
    val layout = textMeasurer.measure(
        text = formatMinutesOfDay(minutes),
        style = TextStyle(fontSize = 9.sp, color = textColor)
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(x = 0f, y = y - layout.size.height / 2f)
    )
}

private fun DrawScope.drawEmptyHint(
    textMeasurer: TextMeasurer,
    left: Float,
    top: Float,
    height: Float,
    textColor: Color
) {
    val layout = textMeasurer.measure(
        text = "—",
        style = TextStyle(fontSize = 14.sp, color = textColor)
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            x = left + 8f,
            y = top + height / 2f - layout.size.height / 2f
        )
    )
}

// =====================================================================
// Геометрия / форматирование (без изменений)
// =====================================================================

private fun barIndexAt(
    xPx: Float,
    widthPx: Float,
    padLeftPx: Float,
    padRightPx: Float,
    count: Int
): Int {
    if (count <= 0 || widthPx <= 0f) return -1
    val plotWidth = (widthPx - padLeftPx - padRightPx).coerceAtLeast(1f)
    val slot = plotWidth / count
    val raw = ((xPx - padLeftPx) / slot).toInt()
    return raw.coerceIn(0, count - 1)
}

private fun localProgressFor(
    index: Int,
    progress: Float,
    count: Int,
    stagger: Float
): Float = ((progress * (count + stagger) - index) / stagger).coerceIn(0f, 1f)

private fun hoursLabel(minutes: Long): String {
    val h = (minutes / 60).toInt()
    return if (java.util.Locale.getDefault().language == "en") "$h hr" else "$h ч"
}

private fun formatMinutesOfDay(totalMinutes: Int): String {
    val h = (totalMinutes / 60).coerceIn(0, 23)
    val m = (totalMinutes % 60).coerceIn(0, 59)
    return "%02d:%02d".format(h, m)
}

fun durationPopupText(minutes: Long): String =
    TimeFormatter.formatMinutes(minutes)
