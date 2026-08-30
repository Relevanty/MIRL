package com.personal.sleepalarm.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

internal const val MIN_TEXT_CONTRAST = 4.5f
internal const val MIN_UI_CONTRAST = 3.0f
internal const val MIN_DISABLED_CONTRAST = 2.0f
internal const val MATERIAL_DISABLED_ALPHA = 0.38f

internal fun contrastRatio(foreground: Color, background: Color): Float {
    val opaqueBackground = background.opaque()
    val renderedForeground = foreground.compositeOver(opaqueBackground)
    val lighter = maxOf(renderedForeground.luminance(), opaqueBackground.luminance())
    val darker = minOf(renderedForeground.luminance(), opaqueBackground.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Adjusts only as far toward black or white as needed. Starting hue and
 * saturation therefore survive whenever the requested contrast permits it.
 */
internal fun ensureContrast(
    foreground: Color,
    background: Color,
    minimumRatio: Float = MIN_TEXT_CONTRAST
): Color = ensureContrast(foreground, listOf(background), minimumRatio)

internal fun ensureContrast(
    foreground: Color,
    backgrounds: Iterable<Color>,
    minimumRatio: Float = MIN_TEXT_CONTRAST
): Color {
    val opaqueForeground = foreground.opaque()
    val opaqueBackgrounds = backgrounds.map { it.opaque() }.distinct()
    if (opaqueBackgrounds.isEmpty() ||
        opaqueBackgrounds.all { contrastRatio(opaqueForeground, it) >= minimumRatio }
    ) {
        return opaqueForeground
    }

    val candidates = listOf(Color.Black, Color.White).mapNotNull { target ->
        minimumBlendToward(
            foreground = opaqueForeground,
            target = target,
            backgrounds = opaqueBackgrounds,
            minimumRatio = minimumRatio
        )
    }
    return candidates.minByOrNull { colorDistance(opaqueForeground, it) }
        ?: listOf(Color.Black, Color.White).maxBy { candidate ->
            opaqueBackgrounds.minOf { contrastRatio(candidate, it) }
        }
}

/**
 * Normalizes the opaque source color while evaluating how Material actually
 * renders it with [renderedAlpha]. This keeps disabled labels visible without
 * replacing Material's standard disabled alpha throughout the UI.
 */
internal fun ensureContrastAtAlpha(
    foreground: Color,
    backgrounds: Iterable<Color>,
    renderedAlpha: Float,
    minimumRatio: Float
): Color {
    val alpha = renderedAlpha.coerceIn(0f, 1f)
    val opaqueForeground = foreground.opaque()
    val opaqueBackgrounds = backgrounds.map { it.opaque() }.distinct()
    if (opaqueBackgrounds.isEmpty() ||
        opaqueBackgrounds.all { contrastRatio(opaqueForeground.copy(alpha = alpha), it) >= minimumRatio }
    ) {
        return opaqueForeground
    }

    val candidates = listOf(Color.Black, Color.White).mapNotNull { target ->
        minimumBlendToward(
            foreground = opaqueForeground,
            target = target,
            backgrounds = opaqueBackgrounds,
            minimumRatio = minimumRatio,
            renderedAlpha = alpha
        )
    }
    return candidates.minByOrNull { colorDistance(opaqueForeground, it) }
        ?: listOf(Color.Black, Color.White).maxBy { candidate ->
            opaqueBackgrounds.minOf { contrastRatio(candidate.copy(alpha = alpha), it) }
        }
}

internal fun disabledContentColor(
    content: Color,
    background: Color
): Color = ensureContrast(
    foreground = lerp(content.opaque(), background.opaque(), 0.55f),
    background = background,
    minimumRatio = MIN_DISABLED_CONTRAST
)

private fun minimumBlendToward(
    foreground: Color,
    target: Color,
    backgrounds: List<Color>,
    minimumRatio: Float,
    renderedAlpha: Float = 1f
): Color? {
    fun passes(amount: Float): Boolean {
        val candidate = lerp(foreground, target, amount).opaque().copy(alpha = renderedAlpha)
        return backgrounds.all { contrastRatio(candidate, it) >= minimumRatio }
    }

    var firstPassingStep = -1
    for (step in 1..SEARCH_STEPS) {
        if (passes(step.toFloat() / SEARCH_STEPS)) {
            firstPassingStep = step
            break
        }
    }
    if (firstPassingStep < 0) return null

    var low = (firstPassingStep - 1).toFloat() / SEARCH_STEPS
    var high = firstPassingStep.toFloat() / SEARCH_STEPS
    repeat(REFINEMENT_STEPS) {
        val middle = (low + high) / 2f
        if (passes(middle)) high = middle else low = middle
    }
    return lerp(foreground, target, high).opaque()
}

private fun colorDistance(first: Color, second: Color): Float {
    val red = first.red - second.red
    val green = first.green - second.green
    val blue = first.blue - second.blue
    return red * red + green * green + blue * blue
}

private fun Color.opaque(): Color = copy(alpha = 1f)

private const val SEARCH_STEPS = 96
private const val REFINEMENT_STEPS = 12
