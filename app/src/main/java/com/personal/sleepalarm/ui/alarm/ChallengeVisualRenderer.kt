package com.personal.sleepalarm.ui.alarm

import com.personal.sleepalarm.ui.theme.appAccents

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.personal.sleepalarm.domain.model.ChallengeVisual
import com.personal.sleepalarm.domain.model.VisualPoint
import kotlin.math.abs
import kotlin.math.max

/** Renders semantic challenge graphics entirely offline with Compose Canvas. */
@Composable
fun ChallengeVisualRenderer(
    visual: ChallengeVisual,
    modifier: Modifier = Modifier
) {
    val accents = MaterialTheme.appAccents
    val containerTone = when (visual) {
        is ChallengeVisual.FunctionGraph -> accents.progress
        is ChallengeVisual.NumberLine -> accents.info
        is ChallengeVisual.GeometryDiagram -> accents.creative
    }
    val primary = when (visual) {
        is ChallengeVisual.FunctionGraph -> accents.progress.fill
        is ChallengeVisual.NumberLine -> accents.info.fill
        is ChallengeVisual.GeometryDiagram -> accents.creative.fill
    }
    val secondary = when (visual) {
        is ChallengeVisual.FunctionGraph -> accents.creative.fill
        is ChallengeVisual.NumberLine -> accents.study.fill
        is ChallengeVisual.GeometryDiagram -> accents.study.fill
    }
    val foreground = containerTone.onContainer
    val grid = containerTone.onContainer.copy(alpha = 0.18f)

    Surface(
        modifier = modifier.semantics { contentDescription = visual.contentDescription },
        shape = RoundedCornerShape(20.dp),
        color = containerTone.container,
        contentColor = containerTone.onContainer
    ) {
        Canvas(
            modifier = Modifier
                .padding(12.dp)
                .aspectRatio(1.72f)
        ) {
            when (visual) {
                is ChallengeVisual.FunctionGraph -> drawFunctionGraph(
                    graph = visual,
                    primary = primary,
                    secondary = secondary,
                    foreground = foreground,
                    grid = grid
                )
                is ChallengeVisual.NumberLine -> drawNumberLine(
                    line = visual,
                    primary = primary,
                    secondary = secondary,
                    foreground = foreground,
                    grid = grid
                )
                is ChallengeVisual.GeometryDiagram -> drawGeometry(
                    diagram = visual,
                    primary = primary,
                    secondary = secondary,
                    foreground = foreground
                )
            }
        }
    }
}

private fun DrawScope.drawFunctionGraph(
    graph: ChallengeVisual.FunctionGraph,
    primary: Color,
    secondary: Color,
    foreground: Color,
    grid: Color
) {
    val inset = 18.dp.toPx()
    fun map(point: VisualPoint): Offset = Offset(
        x = inset + ((point.x - graph.xMin) / (graph.xMax - graph.xMin)).toFloat() * (size.width - inset * 2),
        y = size.height - inset - ((point.y - graph.yMin) / (graph.yMax - graph.yMin)).toFloat() * (size.height - inset * 2)
    )

    repeat(5) { step ->
        val fraction = step / 4f
        val x = inset + (size.width - inset * 2) * fraction
        val y = inset + (size.height - inset * 2) * fraction
        drawLine(grid, Offset(x, inset), Offset(x, size.height - inset), strokeWidth = 1.dp.toPx())
        drawLine(grid, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 1.dp.toPx())
    }

    if (0.0 in graph.xMin..graph.xMax) {
        val x = map(VisualPoint(0.0, graph.yMin)).x
        drawLine(foreground, Offset(x, inset), Offset(x, size.height - inset), strokeWidth = 1.5.dp.toPx())
    }
    if (0.0 in graph.yMin..graph.yMax) {
        val y = map(VisualPoint(graph.xMin, 0.0)).y
        drawLine(foreground, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 1.5.dp.toPx())
    }

    graph.series.forEachIndexed { index, series ->
        val path = Path()
        series.points.forEachIndexed { pointIndex, point ->
            val mapped = map(point)
            if (pointIndex == 0) path.moveTo(mapped.x, mapped.y) else path.lineTo(mapped.x, mapped.y)
        }
        drawPath(
            path = path,
            color = if (index % 2 == 0) primary else secondary,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (series.dashed) {
                    PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))
                } else null
            )
        )
    }

    graph.points.forEach { point ->
        val mapped = map(point)
        drawCircle(
            color = if (point.emphasized) secondary else primary,
            radius = if (point.emphasized) 5.dp.toPx() else 3.5.dp.toPx(),
            center = mapped
        )
        point.label?.let { drawCanvasLabel(it, mapped + Offset(6.dp.toPx(), -6.dp.toPx()), foreground) }
    }
}

private fun DrawScope.drawNumberLine(
    line: ChallengeVisual.NumberLine,
    primary: Color,
    secondary: Color,
    foreground: Color,
    grid: Color
) {
    val horizontalInset = 24.dp.toPx()
    val centerY = size.height * 0.52f
    fun x(value: Double): Float = horizontalInset +
        ((value - line.min) / (line.max - line.min)).toFloat() * (size.width - horizontalInset * 2)

    drawLine(
        color = foreground,
        start = Offset(horizontalInset, centerY),
        end = Offset(size.width - horizontalInset, centerY),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )
    val arrow = 7.dp.toPx()
    drawLine(foreground, Offset(horizontalInset, centerY), Offset(horizontalInset + arrow, centerY - arrow / 2), 2.dp.toPx())
    drawLine(foreground, Offset(horizontalInset, centerY), Offset(horizontalInset + arrow, centerY + arrow / 2), 2.dp.toPx())
    drawLine(foreground, Offset(size.width - horizontalInset, centerY), Offset(size.width - horizontalInset - arrow, centerY - arrow / 2), 2.dp.toPx())
    drawLine(foreground, Offset(size.width - horizontalInset, centerY), Offset(size.width - horizontalInset - arrow, centerY + arrow / 2), 2.dp.toPx())

    repeat(7) { step ->
        val value = line.min + (line.max - line.min) * step / 6.0
        val tickX = x(value)
        drawLine(grid, Offset(tickX, centerY - 5.dp.toPx()), Offset(tickX, centerY + 5.dp.toPx()), 1.dp.toPx())
    }

    line.intervals.forEachIndexed { index, interval ->
        val startX = interval.start?.let(::x) ?: horizontalInset
        val endX = interval.end?.let(::x) ?: size.width - horizontalInset
        val color = if (index % 2 == 0) primary else secondary
        drawLine(color, Offset(startX, centerY), Offset(endX, centerY), 7.dp.toPx(), StrokeCap.Round)
        interval.start?.let {
            drawEndpoint(Offset(startX, centerY), interval.startInclusive, color)
        }
        interval.end?.let {
            drawEndpoint(Offset(endX, centerY), interval.endInclusive, color)
        }
    }

    line.points.forEach { point ->
        val center = Offset(x(point.value), centerY)
        drawEndpoint(center, point.filled, secondary)
        point.label?.let { drawCanvasLabel(it, center + Offset(0f, 20.dp.toPx()), foreground, centered = true) }
    }
}

private fun DrawScope.drawEndpoint(center: Offset, filled: Boolean, color: Color) {
    drawCircle(
        color = color,
        radius = 6.dp.toPx(),
        center = center,
        style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(2.5.dp.toPx())
    )
}

private fun DrawScope.drawGeometry(
    diagram: ChallengeVisual.GeometryDiagram,
    primary: Color,
    secondary: Color,
    foreground: Color
) {
    if (diagram.points.isEmpty()) return
    val minX = diagram.points.minOf { it.x }
    val maxX = diagram.points.maxOf { it.x }
    val minY = diagram.points.minOf { it.y }
    val maxY = diagram.points.maxOf { it.y }
    val rangeX = max(abs(maxX - minX), 1.0)
    val rangeY = max(abs(maxY - minY), 1.0)
    val inset = 24.dp.toPx()
    val scaleX = (size.width - inset * 2) / rangeX.toFloat()
    val scaleY = (size.height - inset * 2) / rangeY.toFloat()
    fun map(point: VisualPoint): Offset = Offset(
        inset + ((point.x - minX) * scaleX).toFloat(),
        size.height - inset - ((point.y - minY) * scaleY).toFloat()
    )

    diagram.polygons.forEach { polygon ->
        val validPoints = polygon.pointIndices.mapNotNull(diagram.points::getOrNull)
        if (validPoints.size >= 3) {
            val path = Path()
            validPoints.forEachIndexed { index, point ->
                val mapped = map(point)
                if (index == 0) path.moveTo(mapped.x, mapped.y) else path.lineTo(mapped.x, mapped.y)
            }
            path.close()
            if (polygon.filled) drawPath(path, primary.copy(alpha = 0.14f))
            drawPath(path, primary, style = Stroke(2.dp.toPx()))
        }
    }

    diagram.circles.forEach { circle ->
        val centerPoint = diagram.points.getOrNull(circle.centerPointIndex) ?: return@forEach
        drawCircle(
            color = secondary,
            radius = (circle.radius * (scaleX + scaleY) / 2f).toFloat(),
            center = map(centerPoint),
            style = Stroke(2.dp.toPx())
        )
    }

    diagram.segments.forEach { segment ->
        val from = diagram.points.getOrNull(segment.fromPointIndex) ?: return@forEach
        val to = diagram.points.getOrNull(segment.toPointIndex) ?: return@forEach
        drawLine(
            color = foreground,
            start = map(from),
            end = map(to),
            strokeWidth = 2.5.dp.toPx(),
            pathEffect = if (segment.dashed) {
                PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))
            } else null
        )
    }

    diagram.points.forEach { point ->
        val mapped = map(point)
        drawCircle(if (point.emphasized) secondary else primary, if (point.emphasized) 5.dp.toPx() else 3.5.dp.toPx(), mapped)
        point.label?.let { drawCanvasLabel(it, mapped + Offset(7.dp.toPx(), -7.dp.toPx()), foreground) }
    }
}

private fun DrawScope.drawCanvasLabel(
    text: String,
    position: Offset,
    color: Color,
    centered: Boolean = false
) {
    val paint = Paint().apply {
        isAntiAlias = true
        textSize = 12.dp.toPx()
        this.color = color.toArgb()
        textAlign = if (centered) Paint.Align.CENTER else Paint.Align.LEFT
    }
    drawContext.canvas.nativeCanvas.drawText(text, position.x, position.y, paint)
}
