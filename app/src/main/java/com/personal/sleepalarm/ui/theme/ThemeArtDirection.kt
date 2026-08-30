package com.personal.sleepalarm.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The visual recipe behind a preset.
 *
 * A preset intentionally stores only five stable seed colours. This recipe
 * turns those seeds into a recognisable atmosphere: paper is warm and quiet,
 * neon is deep and electric, nature is organic, AMOLED stays genuinely black,
 * and every generated treatment has its own character instead of being a
 * simple brightness variant.
 */
internal data class ThemeArtDirection(
    val treatment: ThemeTreatment,
    val fingerprint: Float,
    val hueOffset: Float,
    val tertiaryAnchors: List<Float>,
    val backgroundTint: Float,
    val surfacePrimaryTint: Float,
    val surfaceSecondaryTint: Float,
    val navigationDepth: Float,
    val variantTint: Float,
    val surfaceRange: Float,
    val accentSaturationFloor: Float,
    val accentSaturationScale: Float,
    val semanticSpread: Float,
    val semanticHarmonisation: Float,
    val statusHarmonisation: Float,
    val containerBlend: Float,
    val accentToneDelta: Float
)

internal enum class ThemeTreatment {
    LEGACY,
    ORIGINAL,
    DEPTH,
    VELVET,
    GLOW,
    DUSK
}

private data class CategoryRecipe(
    val tertiaryAnchors: List<Float>,
    val backgroundTintDark: Float,
    val backgroundTintLight: Float,
    val surfacePrimaryTint: Float,
    val surfaceSecondaryTint: Float,
    val navigationDepthDark: Float,
    val navigationDepthLight: Float,
    val variantTint: Float,
    val surfaceRange: Float,
    val accentSaturationFloor: Float,
    val accentSaturationScale: Float,
    val semanticSpread: Float,
    val semanticHarmonisation: Float,
    val statusHarmonisation: Float,
    val containerBlendDark: Float,
    val containerBlendLight: Float
)

private data class TreatmentRecipe(
    val hueOffset: Float = 0f,
    val backgroundTintScale: Float = 1f,
    val surfacePrimaryDelta: Float = 0f,
    val surfaceSecondaryDelta: Float = 0f,
    val navigationDepthDelta: Float = 0f,
    val variantTintDelta: Float = 0f,
    val surfaceRangeDelta: Float = 0f,
    val saturationScale: Float = 1f,
    val semanticSpreadScale: Float = 1f,
    val harmonisationDelta: Float = 0f,
    val statusHarmonisationDelta: Float = 0f,
    val containerBlendDelta: Float = 0f,
    val accentToneDelta: Float = 0f
)

private val categoryRecipes = mapOf(
    ThemeCategory.BASIC to CategoryRecipe(
        tertiaryAnchors = listOf(42f, 176f, 286f, 326f),
        backgroundTintDark = 0.018f, backgroundTintLight = 0.055f,
        surfacePrimaryTint = 0.045f, surfaceSecondaryTint = 0.018f,
        navigationDepthDark = 0.27f, navigationDepthLight = 0.15f,
        variantTint = 0.085f, surfaceRange = 0.070f,
        accentSaturationFloor = 0.56f, accentSaturationScale = 1.00f,
        semanticSpread = 66f, semanticHarmonisation = 0.34f,
        statusHarmonisation = 0.14f,
        containerBlendDark = 0.27f, containerBlendLight = 0.20f
    ),
    ThemeCategory.AMOLED to CategoryRecipe(
        tertiaryAnchors = listOf(42f, 172f, 286f, 334f),
        backgroundTintDark = 0.004f, backgroundTintLight = 0.025f,
        surfacePrimaryTint = 0.025f, surfaceSecondaryTint = 0.012f,
        navigationDepthDark = 0.43f, navigationDepthLight = 0.18f,
        variantTint = 0.060f, surfaceRange = 0.055f,
        accentSaturationFloor = 0.62f, accentSaturationScale = 1.08f,
        semanticSpread = 82f, semanticHarmonisation = 0.27f,
        statusHarmonisation = 0.10f,
        containerBlendDark = 0.24f, containerBlendLight = 0.19f
    ),
    ThemeCategory.NATURE to CategoryRecipe(
        tertiaryAnchors = listOf(30f, 78f, 174f, 210f, 326f),
        backgroundTintDark = 0.035f, backgroundTintLight = 0.070f,
        surfacePrimaryTint = 0.070f, surfaceSecondaryTint = 0.035f,
        navigationDepthDark = 0.25f, navigationDepthLight = 0.14f,
        variantTint = 0.125f, surfaceRange = 0.075f,
        accentSaturationFloor = 0.48f, accentSaturationScale = 0.90f,
        semanticSpread = 52f, semanticHarmonisation = 0.44f,
        statusHarmonisation = 0.16f,
        containerBlendDark = 0.29f, containerBlendLight = 0.22f
    ),
    ThemeCategory.OCEAN to CategoryRecipe(
        tertiaryAnchors = listOf(16f, 42f, 276f, 322f),
        backgroundTintDark = 0.050f, backgroundTintLight = 0.070f,
        surfacePrimaryTint = 0.085f, surfaceSecondaryTint = 0.045f,
        navigationDepthDark = 0.32f, navigationDepthLight = 0.17f,
        variantTint = 0.140f, surfaceRange = 0.085f,
        accentSaturationFloor = 0.62f, accentSaturationScale = 1.00f,
        semanticSpread = 74f, semanticHarmonisation = 0.40f,
        statusHarmonisation = 0.14f,
        containerBlendDark = 0.30f, containerBlendLight = 0.22f
    ),
    ThemeCategory.SPACE to CategoryRecipe(
        tertiaryAnchors = listOf(38f, 168f, 314f, 342f),
        backgroundTintDark = 0.052f, backgroundTintLight = 0.060f,
        surfacePrimaryTint = 0.095f, surfaceSecondaryTint = 0.050f,
        navigationDepthDark = 0.37f, navigationDepthLight = 0.18f,
        variantTint = 0.145f, surfaceRange = 0.095f,
        accentSaturationFloor = 0.62f, accentSaturationScale = 1.06f,
        semanticSpread = 88f, semanticHarmonisation = 0.34f,
        statusHarmonisation = 0.13f,
        containerBlendDark = 0.30f, containerBlendLight = 0.21f
    ),
    ThemeCategory.NEON to CategoryRecipe(
        tertiaryAnchors = listOf(34f, 104f, 190f, 276f, 348f),
        backgroundTintDark = 0.035f, backgroundTintLight = 0.055f,
        surfacePrimaryTint = 0.105f, surfaceSecondaryTint = 0.060f,
        navigationDepthDark = 0.45f, navigationDepthLight = 0.20f,
        variantTint = 0.155f, surfaceRange = 0.105f,
        accentSaturationFloor = 0.76f, accentSaturationScale = 1.16f,
        semanticSpread = 104f, semanticHarmonisation = 0.24f,
        statusHarmonisation = 0.10f,
        containerBlendDark = 0.30f, containerBlendLight = 0.22f
    ),
    ThemeCategory.INDUSTRIAL to CategoryRecipe(
        tertiaryAnchors = listOf(12f, 38f, 188f, 214f),
        backgroundTintDark = 0.020f, backgroundTintLight = 0.045f,
        surfacePrimaryTint = 0.040f, surfaceSecondaryTint = 0.030f,
        navigationDepthDark = 0.34f, navigationDepthLight = 0.17f,
        variantTint = 0.075f, surfaceRange = 0.060f,
        accentSaturationFloor = 0.38f, accentSaturationScale = 0.76f,
        semanticSpread = 56f, semanticHarmonisation = 0.42f,
        statusHarmonisation = 0.16f,
        containerBlendDark = 0.25f, containerBlendLight = 0.19f
    ),
    ThemeCategory.RETRO to CategoryRecipe(
        tertiaryAnchors = listOf(28f, 118f, 184f, 332f),
        backgroundTintDark = 0.035f, backgroundTintLight = 0.060f,
        surfacePrimaryTint = 0.075f, surfaceSecondaryTint = 0.025f,
        navigationDepthDark = 0.36f, navigationDepthLight = 0.18f,
        variantTint = 0.110f, surfaceRange = 0.080f,
        accentSaturationFloor = 0.64f, accentSaturationScale = 1.04f,
        semanticSpread = 72f, semanticHarmonisation = 0.36f,
        statusHarmonisation = 0.14f,
        containerBlendDark = 0.28f, containerBlendLight = 0.20f
    ),
    ThemeCategory.ELEGANT to CategoryRecipe(
        tertiaryAnchors = listOf(38f, 164f, 302f, 334f),
        backgroundTintDark = 0.045f, backgroundTintLight = 0.060f,
        surfacePrimaryTint = 0.090f, surfaceSecondaryTint = 0.050f,
        navigationDepthDark = 0.30f, navigationDepthLight = 0.15f,
        variantTint = 0.120f, surfaceRange = 0.080f,
        accentSaturationFloor = 0.54f, accentSaturationScale = 0.92f,
        semanticSpread = 58f, semanticHarmonisation = 0.48f,
        statusHarmonisation = 0.17f,
        containerBlendDark = 0.30f, containerBlendLight = 0.22f
    ),
    ThemeCategory.SYSTEM to CategoryRecipe(
        tertiaryAnchors = listOf(28f, 138f, 190f, 270f, 332f),
        backgroundTintDark = 0.025f, backgroundTintLight = 0.050f,
        surfacePrimaryTint = 0.050f, surfaceSecondaryTint = 0.025f,
        navigationDepthDark = 0.29f, navigationDepthLight = 0.15f,
        variantTint = 0.085f, surfaceRange = 0.065f,
        accentSaturationFloor = 0.52f, accentSaturationScale = 0.90f,
        semanticSpread = 64f, semanticHarmonisation = 0.40f,
        statusHarmonisation = 0.15f,
        containerBlendDark = 0.26f, containerBlendLight = 0.20f
    ),
    ThemeCategory.PAPER to CategoryRecipe(
        tertiaryAnchors = listOf(20f, 82f, 146f, 206f),
        backgroundTintDark = 0.025f, backgroundTintLight = 0.075f,
        surfacePrimaryTint = 0.032f, surfaceSecondaryTint = 0.022f,
        navigationDepthDark = 0.25f, navigationDepthLight = 0.13f,
        variantTint = 0.065f, surfaceRange = 0.050f,
        accentSaturationFloor = 0.38f, accentSaturationScale = 0.72f,
        semanticSpread = 44f, semanticHarmonisation = 0.52f,
        statusHarmonisation = 0.18f,
        containerBlendDark = 0.25f, containerBlendLight = 0.18f
    ),
    ThemeCategory.PASTEL to CategoryRecipe(
        tertiaryAnchors = listOf(18f, 154f, 250f, 322f),
        backgroundTintDark = 0.030f, backgroundTintLight = 0.070f,
        surfacePrimaryTint = 0.042f, surfaceSecondaryTint = 0.032f,
        navigationDepthDark = 0.25f, navigationDepthLight = 0.12f,
        variantTint = 0.085f, surfaceRange = 0.055f,
        accentSaturationFloor = 0.48f, accentSaturationScale = 0.78f,
        semanticSpread = 50f, semanticHarmonisation = 0.50f,
        statusHarmonisation = 0.17f,
        containerBlendDark = 0.26f, containerBlendLight = 0.19f
    )
)

private val treatmentRecipes = mapOf(
    ThemeTreatment.LEGACY to TreatmentRecipe(),
    ThemeTreatment.ORIGINAL to TreatmentRecipe(hueOffset = -2f),
    ThemeTreatment.DEPTH to TreatmentRecipe(
        hueOffset = -5f,
        backgroundTintScale = 0.82f,
        navigationDepthDelta = 0.065f,
        variantTintDelta = -0.006f,
        surfaceRangeDelta = -0.008f,
        saturationScale = 0.96f,
        semanticSpreadScale = 1.06f,
        harmonisationDelta = -0.035f,
        containerBlendDelta = -0.025f,
        accentToneDelta = 0.015f
    ),
    ThemeTreatment.VELVET to TreatmentRecipe(
        hueOffset = 8f,
        backgroundTintScale = 1.12f,
        surfacePrimaryDelta = 0.025f,
        surfaceSecondaryDelta = 0.010f,
        navigationDepthDelta = -0.010f,
        variantTintDelta = 0.025f,
        surfaceRangeDelta = 0.010f,
        saturationScale = 0.88f,
        semanticSpreadScale = 0.82f,
        harmonisationDelta = 0.110f,
        statusHarmonisationDelta = 0.015f,
        containerBlendDelta = 0.025f,
        accentToneDelta = -0.010f
    ),
    ThemeTreatment.GLOW to TreatmentRecipe(
        hueOffset = -10f,
        backgroundTintScale = 0.72f,
        navigationDepthDelta = 0.075f,
        variantTintDelta = 0.016f,
        surfaceRangeDelta = 0.020f,
        saturationScale = 1.10f,
        semanticSpreadScale = 1.16f,
        harmonisationDelta = -0.055f,
        statusHarmonisationDelta = -0.010f,
        containerBlendDelta = 0.040f,
        accentToneDelta = 0.025f
    ),
    ThemeTreatment.DUSK to TreatmentRecipe(
        hueOffset = 18f,
        backgroundTintScale = 1.08f,
        surfaceSecondaryDelta = 0.030f,
        navigationDepthDelta = 0.025f,
        variantTintDelta = 0.022f,
        surfaceRangeDelta = -0.005f,
        saturationScale = 0.76f,
        semanticSpreadScale = 0.72f,
        harmonisationDelta = 0.150f,
        statusHarmonisationDelta = 0.020f,
        containerBlendDelta = 0.020f,
        accentToneDelta = -0.020f
    )
)

internal fun themeArtDirection(preset: ThemePreset): ThemeArtDirection {
    val category = categoryRecipes.getValue(preset.category)
    val treatment = preset.themeTreatment()
    val treatmentRecipe = treatmentRecipes.getValue(treatment)
    val identityKey = themeIdentityKey(preset)
    val fingerprint = stableThemeUnit(identityKey, "identity")
    val microVariation = (fingerprint - 0.5f) * 0.012f

    return ThemeArtDirection(
        treatment = treatment,
        fingerprint = fingerprint,
        hueOffset = treatmentRecipe.hueOffset + (fingerprint - 0.5f) * 18f,
        tertiaryAnchors = category.tertiaryAnchors,
        backgroundTint = (
            (if (preset.isDark) category.backgroundTintDark else category.backgroundTintLight) *
                treatmentRecipe.backgroundTintScale + microVariation.coerceAtLeast(-0.006f)
            ).coerceIn(0f, 0.15f),
        surfacePrimaryTint = (
            category.surfacePrimaryTint + treatmentRecipe.surfacePrimaryDelta + microVariation
            ).coerceIn(0.008f, 0.22f),
        surfaceSecondaryTint = (
            category.surfaceSecondaryTint + treatmentRecipe.surfaceSecondaryDelta - microVariation * 0.5f
            ).coerceIn(0.006f, 0.16f),
        navigationDepth = (
            (if (preset.isDark) category.navigationDepthDark else category.navigationDepthLight) +
                treatmentRecipe.navigationDepthDelta
            ).coerceIn(0.08f, 0.62f),
        variantTint = (category.variantTint + treatmentRecipe.variantTintDelta).coerceIn(0.035f, 0.24f),
        surfaceRange = (category.surfaceRange + treatmentRecipe.surfaceRangeDelta).coerceIn(0.035f, 0.15f),
        accentSaturationFloor = category.accentSaturationFloor,
        accentSaturationScale = category.accentSaturationScale * treatmentRecipe.saturationScale,
        semanticSpread = category.semanticSpread * treatmentRecipe.semanticSpreadScale,
        semanticHarmonisation = (
            category.semanticHarmonisation + treatmentRecipe.harmonisationDelta
            ).coerceIn(0.16f, 0.68f),
        statusHarmonisation = (
            category.statusHarmonisation + treatmentRecipe.statusHarmonisationDelta
            ).coerceIn(0.08f, 0.22f),
        containerBlend = (
            (if (preset.isDark) category.containerBlendDark else category.containerBlendLight) +
                treatmentRecipe.containerBlendDelta
            ).coerceIn(0.14f, 0.38f),
        accentToneDelta = treatmentRecipe.accentToneDelta
    )
}

internal fun themeIdentitySeed(
    preset: ThemePreset,
    direction: ThemeArtDirection = themeArtDirection(preset)
): Color {
    val primary = Color(preset.primary).toThemeHsl()
    val secondary = Color(preset.secondary).toThemeHsl()
    val strongestSaturation = max(primary.saturation, secondary.saturation)
    val neutralFallback = direction.tertiaryAnchors[
        (stableThemeUnit(themeIdentityKey(preset), "neutral-hue") * direction.tertiaryAnchors.size)
            .toInt()
            .coerceIn(0, direction.tertiaryAnchors.lastIndex)
    ]
    val baseHue = weightedThemeHue(primary, secondary, neutralFallback)
    val vividness = smoothThemeStep(0.06f, 0.30f, strongestSaturation)
    val mutedSaturation = (strongestSaturation * 0.58f + 0.012f).coerceIn(0.012f, 0.16f)
    val vividSaturation = (strongestSaturation * direction.accentSaturationScale)
        .coerceIn(direction.accentSaturationFloor, 0.92f)
    val saturation = mutedSaturation + (vividSaturation - mutedSaturation) * vividness
    return themeHslColor(
        hue = baseHue + direction.hueOffset,
        saturation = saturation,
        lightness = if (preset.isDark) 0.58f else 0.48f
    )
}

internal fun stableThemeUnit(themeId: String, salt: String): Float {
    var hash = 0x811C9DC5u
    "$salt:$themeId".forEach { char ->
        hash = (hash xor char.code.toUInt()) * 0x01000193u
    }
    return (hash and 0x00FFFFFFu).toFloat() / 0x00FFFFFFu.toFloat()
}

/** Expanded treatments share one family identity; only their recipe may alter it. */
internal fun themeIdentityKey(preset: ThemePreset): String =
    if (preset.variantNameRes == null) preset.id else preset.id.substringBeforeLast('_')

/** Smooth 0..1 transition without a visible threshold jump. */
internal fun smoothThemeStep(edge0: Float, edge1: Float, value: Float): Float {
    if (edge0 == edge1) return if (value < edge0) 0f else 1f
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Harmonises a semantic status while keeping it inside its recognisable hue
 * neighbourhood. A red danger state can never rotate into yellow or green.
 */
internal fun harmonisedThemeStatusHue(
    canonicalHue: Float,
    identityHue: Float,
    amount: Float,
    maximumShift: Float
): Float {
    val delta = ((identityHue - canonicalHue + 540f) % 360f) - 180f
    return normaliseThemeHue(
        canonicalHue + (delta * amount.coerceIn(0f, 1f)).coerceIn(-maximumShift, maximumShift)
    )
}

internal data class ThemeHsl(
    val hue: Float,
    val saturation: Float,
    val lightness: Float
)

internal fun Color.toThemeHsl(): ThemeHsl {
    val maximum = max(red, max(green, blue))
    val minimum = min(red, min(green, blue))
    val delta = maximum - minimum
    val lightness = (maximum + minimum) / 2f
    val saturation = if (delta == 0f) {
        0f
    } else {
        delta / (1f - abs(2f * lightness - 1f))
    }
    val hue = when {
        delta == 0f -> 0f
        maximum == red -> 60f * (((green - blue) / delta) % 6f)
        maximum == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }.let(::normaliseThemeHue)
    return ThemeHsl(hue, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))
}

internal fun themeHslColor(hue: Float, saturation: Float, lightness: Float): Color {
    val h = normaliseThemeHue(hue)
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    val chroma = (1f - abs(2f * l - 1f)) * s
    val section = h / 60f
    val intermediate = chroma * (1f - abs(section % 2f - 1f))
    val (red, green, blue) = when (section.toInt().coerceIn(0, 5)) {
        0 -> Triple(chroma, intermediate, 0f)
        1 -> Triple(intermediate, chroma, 0f)
        2 -> Triple(0f, chroma, intermediate)
        3 -> Triple(0f, intermediate, chroma)
        4 -> Triple(intermediate, 0f, chroma)
        else -> Triple(chroma, 0f, intermediate)
    }
    val match = l - chroma / 2f
    return Color(red + match, green + match, blue + match, 1f)
}

internal fun mixThemeHue(from: Float, to: Float, amount: Float): Float {
    val delta = ((to - from + 540f) % 360f) - 180f
    return normaliseThemeHue(from + delta * amount.coerceIn(0f, 1f))
}

private fun weightedThemeHue(first: ThemeHsl, second: ThemeHsl, fallback: Float): Float {
    val firstWeight = first.saturation * (0.35f + first.lightness * (1f - first.lightness))
    val secondWeight = second.saturation * (0.35f + second.lightness * (1f - second.lightness))
    if (firstWeight + secondWeight < 0.015f) return fallback

    fun radians(hue: Float): Double = hue.toDouble() * PI / 180.0
    val x = cos(radians(first.hue)) * firstWeight + cos(radians(second.hue)) * secondWeight
    val y = sin(radians(first.hue)) * firstWeight + sin(radians(second.hue)) * secondWeight
    return normaliseThemeHue((atan2(y, x) * 180.0 / PI).toFloat())
}

internal fun themeHueDistance(first: Float, second: Float): Float {
    val distance = abs(normaliseThemeHue(first) - normaliseThemeHue(second))
    return min(distance, 360f - distance)
}

internal fun normaliseThemeHue(value: Float): Float = ((value % 360f) + 360f) % 360f

private fun ThemePreset.themeTreatment(): ThemeTreatment {
    if (variantNameRes == null) return ThemeTreatment.LEGACY
    return when (id.substringAfterLast('_')) {
        "original" -> ThemeTreatment.ORIGINAL
        "depth" -> ThemeTreatment.DEPTH
        "velvet" -> ThemeTreatment.VELVET
        "glow" -> ThemeTreatment.GLOW
        "dusk" -> ThemeTreatment.DUSK
        else -> ThemeTreatment.ORIGINAL
    }
}
