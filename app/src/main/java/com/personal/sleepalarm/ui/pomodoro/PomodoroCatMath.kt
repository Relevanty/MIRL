package com.personal.sleepalarm.ui.pomodoro

val SUBJECT_COLORS = listOf(
    0xFF9E9E9E,
    0xFF9575CD,
    0xFF2E7D32,
    0xFFE57373,
    0xFF4DB6AC,
    0xFFFFB74D,
    0xFF64B5F6,
    0xFFF06292
).map { it.toInt() }

internal fun calculateMeasuredBigCatScale(
    measuredWidthPx: Float,
    measuredHeightPx: Float,
    availableWidthPx: Float,
    availableHeightPx: Float,
    animationReserve: Float
): Float {
    if (
        measuredWidthPx <= 0f || measuredHeightPx <= 0f ||
        availableWidthPx <= 0f || availableHeightPx <= 0f
    ) return 0.12f
    val reserve = animationReserve.coerceAtLeast(1f)
    return minOf(
        availableWidthPx * BIG_CAT_CONTENT_FRACTION / (measuredWidthPx * reserve),
        availableHeightPx * BIG_CAT_CONTENT_FRACTION / (measuredHeightPx * reserve)
    ).coerceAtLeast(0.01f)
}

internal fun calculateCatHorizontalTravel(
    sceneWidthDp: Float,
    visibleHalfWidthDp: Float
): Float = (
    sceneWidthDp / 2f -
        visibleHalfWidthDp.coerceAtLeast(0f) -
        CAT_SCENE_EDGE_PADDING_DP
    ).coerceAtLeast(0f)

internal fun calculateParabolicHopOffset(progress: Float, heightDp: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return -4f * heightDp.coerceAtLeast(0f) * p * (1f - p)
}

/** Keeps legacy animation frames measurable on one stable monospace canvas. */
internal fun normalizeAnimatedCatFrame(frame: String, bodyAxis: Int = 3): String {
    val sourceLines = frame
        .trim('\n', '\r')
        .split("\n")
        .map { it.trimEnd('\r', ' ') }
    val canvasCentre = ANIMATED_CAT_CANVAS_COLUMNS / 2
    val leadingPadding = (canvasCentre - bodyAxis).coerceAtLeast(0)
    val topPadding = ((ANIMATED_CAT_CANVAS_LINES - sourceLines.size) / 2).coerceAtLeast(0)
    return List(ANIMATED_CAT_CANVAS_LINES) { index ->
        val sourceIndex = index - topPadding
        val line = if (sourceIndex in sourceLines.indices) sourceLines[sourceIndex] else ""
        (" ".repeat(leadingPadding) + line)
            .padEnd(ANIMATED_CAT_CANVAS_COLUMNS, '\u00A0')
    }.joinToString("\n")
}

private const val BIG_CAT_CONTENT_FRACTION = 0.92f
private const val CAT_SCENE_EDGE_PADDING_DP = 8f
private const val ANIMATED_CAT_CANVAS_COLUMNS = 15
private const val ANIMATED_CAT_CANVAS_LINES = 6
