package com.personal.sleepalarm.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.max

/** A readable colour plus its softly tinted card treatment. */
@Immutable
data class AppAccentTone(
    val color: Color,
    val onColor: Color,
    val container: Color,
    val onContainer: Color,
    /** Unclamped expressive fill for artwork, swatches and larger shapes. */
    val fill: Color = color,
    val onFill: Color = onColor,
    /** Restrained theme-derived fill for large actions that should not glow. */
    val action: Color = container,
    val onAction: Color = onContainer
)

/** App chrome is separate from Material surface containers used by cards. */
@Immutable
data class AppChromePalette(
    val navigation: Color,
    val onNavigation: Color,
    val onNavigationMuted: Color
)

/**
 * Expressive semantic colours shared by detailed screens.
 *
 * Material's three accents remain the foundation of controls. These extra
 * roles stop charts, calendars and task quadrants from reusing the same colour
 * for unrelated meanings while still harmonising with every theme preset.
 */
@Immutable
data class AppAccentPalette(
    val focus: AppAccentTone,
    val sleep: AppAccentTone,
    val study: AppAccentTone,
    val work: AppAccentTone,
    val other: AppAccentTone,
    val success: AppAccentTone,
    val warning: AppAccentTone,
    val urgent: AppAccentTone,
    val calm: AppAccentTone,
    val chrome: AppChromePalette
) {
    val all: List<AppAccentTone>
        get() = listOf(focus, sleep, study, work, other, success, warning, urgent, calm)
}

private val FallbackAccentPalette = AppAccentPalette(
    focus = AppAccentTone(Color(0xFFFFB86B), Color.Black, Color(0xFF49331F), Color.White),
    sleep = AppAccentTone(Color(0xFFC5A3FF), Color.Black, Color(0xFF34284E), Color.White),
    study = AppAccentTone(Color(0xFF71C7FF), Color.Black, Color(0xFF173A50), Color.White),
    work = AppAccentTone(Color(0xFFFFA56B), Color.Black, Color(0xFF4A2C1C), Color.White),
    other = AppAccentTone(Color(0xFFFF8FC7), Color.Black, Color(0xFF49233A), Color.White),
    success = AppAccentTone(Color(0xFF69D99A), Color.Black, Color(0xFF173B29), Color.White),
    warning = AppAccentTone(Color(0xFFFFCC66), Color.Black, Color(0xFF463719), Color.White),
    urgent = AppAccentTone(Color(0xFFFF7F87), Color.Black, Color(0xFF492126), Color.White),
    calm = AppAccentTone(Color(0xFF63D8C2), Color.Black, Color(0xFF173D38), Color.White),
    chrome = AppChromePalette(Color(0xFF080C1B), Color.White, Color(0xFFC7CAD3))
)

internal val LocalAppAccentPalette = staticCompositionLocalOf { FallbackAccentPalette }

val MaterialTheme.appAccents: AppAccentPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalAppAccentPalette.current

internal fun buildAppAccentPalette(
    preset: ThemePreset,
    scheme: ColorScheme
): AppAccentPalette {
    val direction = themeArtDirection(preset)
    val primaryHsl = Color(preset.primary).toThemeHsl()
    val secondaryHsl = Color(preset.secondary).toThemeHsl()
    val tertiaryHsl = scheme.tertiary.toThemeHsl()
    val identitySeed = themeIdentitySeed(preset, direction)
    val identityHsl = identitySeed.toThemeHsl()
    val navigation = if (preset.isDark) {
        lerp(
            lerp(scheme.background, Color.Black, direction.navigationDepth),
            identitySeed,
            0.018f + direction.fingerprint * 0.012f
        )
    } else {
        lerp(
            scheme.background,
            lerp(identitySeed, Color.Black, 0.52f),
            direction.navigationDepth
        )
    }
    val commonSurfaces = listOf(
        scheme.background,
        scheme.surface,
        scheme.surfaceVariant,
        scheme.surfaceContainer,
        scheme.surfaceContainerLowest,
        scheme.surfaceContainerLow,
        scheme.surfaceContainerHigh,
        scheme.surfaceContainerHighest,
        scheme.surfaceBright,
        scheme.surfaceDim,
        navigation
    )
    val seedSaturation = max(primaryHsl.saturation, secondaryHsl.saturation)
    val vividness = smoothThemeStep(0.06f, 0.30f, seedSaturation)
    val mutedSaturation = (seedSaturation * 0.58f + 0.045f).coerceIn(0.045f, 0.22f)
    val vividSaturation = (seedSaturation * direction.accentSaturationScale)
        .coerceIn(direction.accentSaturationFloor, 0.94f)
    val saturation = mutedSaturation + (vividSaturation - mutedSaturation) * vividness
    val lightness = if (preset.isDark) {
        0.69f + direction.accentToneDelta
    } else {
        0.34f + direction.accentToneDelta * 0.35f
    }.coerceIn(if (preset.isDark) 0.62f else 0.27f, if (preset.isDark) 0.79f else 0.41f)
    val semanticRecipe = semanticHueRecipe(preset.category)
    val identityKey = themeIdentityKey(preset)

    fun tone(
        raw: Color,
        containerOverride: Color? = null
    ): AppAccentTone {
        val content = ensureContrast(raw, commonSurfaces, MIN_TEXT_CONTRAST)
        val container = containerOverride ?: lerp(scheme.background, raw, direction.containerBlend)
        val rawHsl = raw.toThemeHsl()
        val action = themeHslColor(
            hue = rawHsl.hue,
            saturation = (rawHsl.saturation * (0.38f + direction.fingerprint * 0.06f))
                .coerceIn(0f, 0.38f),
            lightness = if (preset.isDark) {
                (0.225f + direction.fingerprint * 0.025f + direction.accentToneDelta * 0.05f)
                    .coerceIn(0.21f, 0.26f)
            } else {
                (0.84f + direction.fingerprint * 0.025f - direction.accentToneDelta * 0.08f)
                    .coerceIn(0.81f, 0.88f)
            }
        )
        return AppAccentTone(
            color = content,
            onColor = ensureContrast(scheme.onSurface, content, MIN_TEXT_CONTRAST),
            container = container,
            onContainer = ensureContrast(scheme.onSurface, container, MIN_TEXT_CONTRAST),
            fill = raw,
            onFill = ensureContrast(scheme.onSurface, raw, MIN_TEXT_CONTRAST),
            action = action,
            onAction = scheme.onSurfaceVariant
        )
    }

    fun role(
        name: String,
        seedHue: Float,
        saturationScale: Float = 1f,
        harmonisationScale: Float = 0.28f,
        toneDelta: Float = 0f
    ): AppAccentTone {
        val roleJitter = (stableThemeUnit(identityKey, "accent-$name") - 0.5f) * 5f
        val hue = mixThemeHue(
            seedHue + roleJitter,
            identityHsl.hue,
            direction.semanticHarmonisation * harmonisationScale
        )
        val raw = themeHslColor(
            hue = hue,
            saturation = (saturation * saturationScale).coerceIn(
                0.06f + 0.44f * vividness,
                0.28f + 0.64f * vividness
            ),
            lightness = (lightness + toneDelta).coerceIn(0.25f, 0.82f)
        )
        return tone(raw)
    }

    fun statusRole(
        name: String,
        canonicalHue: Float,
        maximumHueShift: Float,
        saturationScale: Float,
        toneDelta: Float = 0f,
        hueOverride: Float? = null
    ): AppAccentTone {
        val restrainedJitter = (stableThemeUnit(identityKey, "status-$name") - 0.5f) * 1.5f
        val hue = hueOverride ?: harmonisedThemeStatusHue(
            canonicalHue = canonicalHue + restrainedJitter,
            identityHue = identityHsl.hue,
            amount = direction.statusHarmonisation,
            maximumShift = maximumHueShift
        )
        val statusSaturation = max(saturation, 0.58f)
        val raw = themeHslColor(
            hue = hue,
            saturation = (statusSaturation * saturationScale).coerceIn(0.46f, 0.92f),
            lightness = (lightness + toneDelta).coerceIn(0.26f, 0.81f)
        )
        return tone(raw)
    }

    val spread = direction.semanticSpread

    return AppAccentPalette(
        focus = tone(
            raw = Color(preset.primary),
            containerOverride = scheme.primaryContainer
        ),
        sleep = role(
            name = "sleep",
            seedHue = secondaryHsl.hue + spread * semanticRecipe.sleep,
            saturationScale = 0.90f,
            harmonisationScale = 0.20f,
            toneDelta = if (preset.isDark) 0.015f else 0f
        ),
        study = role(
            name = "study",
            seedHue = tertiaryHsl.hue + spread * semanticRecipe.study,
            saturationScale = 1.00f,
            harmonisationScale = 0.12f
        ),
        work = role(
            name = "work",
            seedHue = primaryHsl.hue + spread * semanticRecipe.work,
            saturationScale = 0.94f,
            harmonisationScale = 0.30f
        ),
        other = role(
            name = "other",
            seedHue = secondaryHsl.hue + spread * semanticRecipe.other,
            saturationScale = 0.92f,
            harmonisationScale = 0.34f
        ),
        success = statusRole(
            name = "success",
            canonicalHue = 142f,
            maximumHueShift = 16f,
            saturationScale = 0.88f
        ),
        warning = statusRole(
            name = "warning",
            canonicalHue = 47f,
            maximumHueShift = 11f,
            saturationScale = 0.92f,
            toneDelta = if (preset.isDark) 0.015f else -0.010f
        ),
        urgent = statusRole(
            name = "urgent",
            canonicalHue = 354f,
            maximumHueShift = 9f,
            saturationScale = 0.96f,
            hueOverride = scheme.error.toThemeHsl().hue
        ),
        calm = role(
            name = "calm",
            seedHue = identityHsl.hue + spread * semanticRecipe.calm,
            saturationScale = 0.82f,
            harmonisationScale = 0.22f,
            toneDelta = if (preset.isDark) 0.010f else 0f
        ),
        chrome = ensureContrast(scheme.onSurface, navigation, MIN_TEXT_CONTRAST).let { onNavigation ->
            AppChromePalette(
                navigation = navigation,
                onNavigation = onNavigation,
                onNavigationMuted = ensureContrast(
                    lerp(onNavigation, navigation, 0.24f),
                    navigation,
                    MIN_TEXT_CONTRAST
                )
            )
        }
    )
}

/** Picks a third Material accent that is visibly separated from both seeds. */
internal fun expressiveTertiarySeed(
    preset: ThemePreset,
    direction: ThemeArtDirection = themeArtDirection(preset)
): Color {
    val first = Color(preset.primary).toThemeHsl()
    val second = Color(preset.secondary).toThemeHsl()
    val hue = direction.tertiaryAnchors
        .mapIndexed { index, anchor ->
            val candidate = normaliseThemeHue(anchor + direction.hueOffset * 0.72f)
            val separation = minOf(
                themeHueDistance(candidate, first.hue),
                themeHueDistance(candidate, second.hue)
            )
            val personality = stableThemeUnit(themeIdentityKey(preset), "tertiary-$index") * 9f
            candidate to (separation + personality)
        }
        .maxBy { it.second }
        .first
    val seedSaturation = max(first.saturation, second.saturation)
    val vividness = smoothThemeStep(0.06f, 0.30f, seedSaturation)
    val mutedSaturation = (seedSaturation * 0.60f + 0.055f).coerceIn(0.055f, 0.24f)
    val vividSaturation = (seedSaturation * direction.accentSaturationScale)
        .coerceIn(direction.accentSaturationFloor, 0.92f)
    val saturation = mutedSaturation + (vividSaturation - mutedSaturation) * vividness
    return themeHslColor(
        hue = hue,
        saturation = saturation,
        lightness = if (preset.isDark) {
            0.69f + direction.accentToneDelta
        } else {
            0.34f + direction.accentToneDelta * 0.35f
        }
    )
}

private data class SemanticHueRecipe(
    val sleep: Float,
    val study: Float,
    val work: Float,
    val other: Float,
    val calm: Float
)

private fun semanticHueRecipe(category: ThemeCategory): SemanticHueRecipe = when (category) {
    ThemeCategory.BASIC -> SemanticHueRecipe(0.08f, 0.00f, -0.82f, 1.02f, 1.74f)
    ThemeCategory.AMOLED -> SemanticHueRecipe(0.04f, 0.00f, 0.88f, -1.02f, 1.92f)
    ThemeCategory.NATURE -> SemanticHueRecipe(-0.14f, 0.05f, -0.62f, 0.88f, 1.42f)
    ThemeCategory.OCEAN -> SemanticHueRecipe(-0.10f, 0.04f, 1.78f, 0.78f, -0.46f)
    ThemeCategory.SPACE -> SemanticHueRecipe(0.20f, -0.04f, 1.34f, -0.92f, 1.88f)
    ThemeCategory.NEON -> SemanticHueRecipe(0.02f, 0.06f, 1.18f, -1.10f, 1.78f)
    ThemeCategory.INDUSTRIAL -> SemanticHueRecipe(-0.12f, 0.02f, 0.56f, 1.38f, 1.92f)
    ThemeCategory.RETRO -> SemanticHueRecipe(0.12f, -0.04f, 0.82f, 1.62f, 2.16f)
    ThemeCategory.ELEGANT -> SemanticHueRecipe(0.16f, 0.03f, 0.66f, -0.94f, 1.72f)
    ThemeCategory.SYSTEM -> SemanticHueRecipe(-0.08f, 0.00f, 0.78f, 1.48f, 2.12f)
    ThemeCategory.PAPER -> SemanticHueRecipe(0.10f, 0.02f, 0.72f, 1.38f, 2.10f)
    ThemeCategory.PASTEL -> SemanticHueRecipe(0.18f, -0.03f, 0.94f, 1.72f, 2.48f)
}
