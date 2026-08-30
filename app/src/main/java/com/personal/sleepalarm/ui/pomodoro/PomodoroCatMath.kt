package com.personal.sleepalarm.ui.pomodoro

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.personal.sleepalarm.ui.theme.appAccents

/**
 * Stable compatibility tokens persisted by existing SubjectEntity/OtherActivityEntity rows.
 * They must not be rendered as ARGB directly: [pomodoroColorForToken] resolves each token
 * into the active theme's semantic palette.
 */
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

/** Stable tokens used by task-backed hub items; kept compatible with existing sessions. */
internal val STUDY_TASK_COLOR_TOKEN = 0xFF42A5F5.toInt()
internal val WORK_TASK_COLOR_TOKEN = 0xFF5C6BC0.toInt()
internal val OTHER_TASK_COLOR_TOKEN = 0xFFAB47BC.toInt()

private enum class PomodoroColorRole {
    WORK,
    SLEEP,
    SUCCESS,
    URGENT,
    CALM,
    WARNING,
    STUDY,
    OTHER
}

private val subjectColorRoles = listOf(
    PomodoroColorRole.WORK,
    PomodoroColorRole.SLEEP,
    PomodoroColorRole.SUCCESS,
    PomodoroColorRole.URGENT,
    PomodoroColorRole.CALM,
    PomodoroColorRole.WARNING,
    PomodoroColorRole.STUDY,
    PomodoroColorRole.OTHER
)

/** Resolves persisted legacy ARGB values as stable semantic tokens for the current theme. */
@Composable
internal fun pomodoroColorForToken(token: Int): Color {
    val accents = MaterialTheme.appAccents
    return when (pomodoroColorRoleForToken(token)) {
        PomodoroColorRole.WORK -> accents.work.color
        PomodoroColorRole.SLEEP -> accents.sleep.color
        PomodoroColorRole.SUCCESS -> accents.success.color
        PomodoroColorRole.URGENT -> accents.urgent.color
        PomodoroColorRole.CALM -> accents.calm.color
        PomodoroColorRole.WARNING -> accents.warning.color
        PomodoroColorRole.STUDY -> accents.study.color
        PomodoroColorRole.OTHER -> accents.other.color
    }
}

private fun pomodoroColorRoleForToken(token: Int): PomodoroColorRole = when (token) {
    STUDY_TASK_COLOR_TOKEN -> PomodoroColorRole.STUDY
    WORK_TASK_COLOR_TOKEN -> PomodoroColorRole.WORK
    OTHER_TASK_COLOR_TOKEN -> PomodoroColorRole.OTHER
    else -> {
        val knownIndex = SUBJECT_COLORS.indexOf(token)
        subjectColorRoles[
            if (knownIndex >= 0) knownIndex
            else Math.floorMod(token, subjectColorRoles.size)
        ]
    }
}

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
