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
    val chrome: AppChromePalette,
    /** General information, weather and system explanations. */
    val info: AppAccentTone = study,
    /** Energy, movement and high-activation states. */
    val energy: AppAccentTone = warning,
    /** Statistics, milestones and measurable progress. */
    val progress: AppAccentTone = success,
    /** Notes, diary entries and original ideas. */
    val creative: AppAccentTone = other,
    /** Library, recovery and unstructured leisure. */
    val leisure: AppAccentTone = calm,
    /** Calendar, reminders and deadline planning. */
    val schedule: AppAccentTone = focus
) {
    val all: List<AppAccentTone>
        get() = listOf(
            focus,
            sleep,
            study,
            work,
            other,
            success,
            warning,
            urgent,
            calm,
            info,
            energy,
            progress,
            creative,
            leisure,
            schedule
        )
}
private val DEFAULT_APP_ACCENTS = AppAccentPalette(
    focus = AppAccentTone(Color(0xFFFFB86B), Color.Black, Color(0xFF49331F), Color.White),
    sleep = AppAccentTone(Color(0xFFC5A3FF), Color.Black, Color(0xFF34284E), Color.White),
    study = AppAccentTone(Color(0xFF71C7FF), Color.Black, Color(0xFF173A50), Color.White),
    work = AppAccentTone(Color(0xFFFFA56B), Color.Black, Color(0xFF4A2C1C), Color.White),
    other = AppAccentTone(Color(0xFFFF8FC7), Color.Black, Color(0xFF49233A), Color.White),
    success = AppAccentTone(Color(0xFF69D99A), Color.Black, Color(0xFF173B29), Color.White),
    warning = AppAccentTone(Color(0xFFFFCC66), Color.Black, Color(0xFF463719), Color.White),
    urgent = AppAccentTone(Color(0xFFFF7F87), Color.Black, Color(0xFF492126), Color.White),
    calm = AppAccentTone(Color(0xFF63D8C2), Color.Black, Color(0xFF173D38), Color.White),
    chrome = AppChromePalette(Color(0xFF080C1B), Color.White, Color(0xFFC7CAD3)),
    info = AppAccentTone(Color(0xFF58D4ED), Color.Black, Color(0xFF153D47), Color.White),
    energy = AppAccentTone(Color(0xFFFF9857), Color.Black, Color(0xFF4B2B19), Color.White),
    progress = AppAccentTone(Color(0xFF58D6AA), Color.Black, Color(0xFF163D32), Color.White),
    creative = AppAccentTone(Color(0xFFD58AFF), Color.Black, Color(0xFF40244D), Color.White),
    leisure = AppAccentTone(Color(0xFFFF8FAD), Color.Black, Color(0xFF492632), Color.White),
    schedule = AppAccentTone(Color(0xFF7EA7FF), Color.Black, Color(0xFF203554), Color.White)
)

internal val LocalAppAccentPalette = staticCompositionLocalOf { DEFAULT_APP_ACCENTS }

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
    val identityKey = themeIdentityKey(preset)
    // Treatments deliberately share a family identity, but their semantic
    // spectrum still needs its own fine-grained character. Using the complete
    // preset id for the small variations keeps siblings recognisable without
    // rendering their cards as the same palette.
    val variantKey = preset.id
    val usedSemanticFills = mutableListOf<Color>()

    fun separatedSemanticFill(raw: Color): Color {
        val source = raw.toThemeHsl()
        var candidate = raw
        repeat(15) { attempt ->
            val isSeparated = usedSemanticFills.none { used ->
                val red = candidate.red - used.red
                val green = candidate.green - used.green
                val blue = candidate.blue - used.blue
                red * red + green * green + blue * blue < 0.00045f
            }
            if (isSeparated) {
                usedSemanticFills += candidate
                return candidate
            }

            // Preserve the semantic hue (especially for warning/danger) and
            // move through neighbouring tonal stops until the fill is clearly
            // distinct. Alternating directions avoids forcing every later role
            // brighter and keeps both light and dark themes balanced.
            val stepIndex = attempt / 2 + 1
            val directionSign = if (attempt % 2 == 0) 1f else -1f
            candidate = themeHslColor(
                hue = source.hue,
                saturation = source.saturation,
                lightness = (source.lightness + directionSign * stepIndex * 0.012f)
                    .coerceIn(0.25f, 0.82f)
            )
        }
        usedSemanticFills += candidate
        return candidate
    }

    fun tone(
        raw: Color,
        containerOverride: Color? = null
    ): AppAccentTone {
        val fill = separatedSemanticFill(raw)
        val content = ensureContrast(fill, commonSurfaces, MIN_TEXT_CONTRAST)
        val container = containerOverride ?: lerp(scheme.background, fill, direction.containerBlend)
        val rawHsl = fill.toThemeHsl()
        val actionSaturationFloor = minOf(
            rawHsl.saturation,
            0.045f + vividness * 0.045f
        )
        val actionLightnessDelta = (rawHsl.lightness - lightness) * 0.16f
        val action = themeHslColor(
            hue = rawHsl.hue,
            saturation = (rawHsl.saturation * (0.38f + direction.fingerprint * 0.06f))
                .coerceIn(actionSaturationFloor, 0.38f),
            lightness = if (preset.isDark) {
                (0.225f + direction.fingerprint * 0.025f +
                    direction.accentToneDelta * 0.05f + actionLightnessDelta)
                    .coerceIn(0.21f, 0.26f)
            } else {
                (0.84f + direction.fingerprint * 0.025f -
                    direction.accentToneDelta * 0.08f + actionLightnessDelta)
                    .coerceIn(0.81f, 0.88f)
            }
        )
        return AppAccentTone(
            color = content,
            onColor = ensureContrast(scheme.onSurface, content, MIN_TEXT_CONTRAST),
            container = container,
            onContainer = ensureContrast(scheme.onSurface, container, MIN_TEXT_CONTRAST),
            fill = fill,
            onFill = ensureContrast(scheme.onSurface, fill, MIN_TEXT_CONTRAST),
            action = action,
            onAction = scheme.onSurfaceVariant
        )
    }

    /**
     * Non-status roles stay inside the selected theme's own colour family.
     * `position` only moves them around a restrained arc near the preset
     * primary hue, so a green theme produces distinct greens/teals/limes
     * instead of importing fixed purple, pink or blue cards.
     */
    fun familyRole(
        name: String,
        position: Float,
        saturationScale: Float = 1f,
        toneDelta: Float = 0f
    ): AppAccentTone {
        val roleJitter = (stableThemeUnit(variantKey, "family-$name") - 0.5f) * 9f
        val familyRotation = (stableThemeUnit(variantKey, "family-rotation") - 0.5f) * 16f
        val familyAnchor = normaliseThemeHue(
            mixThemeHue(
                primaryHsl.hue,
                identityHsl.hue,
                0.12f + direction.semanticHarmonisation * 0.10f
            ) + familyRotation
        )
        val radiusPersonality = 0.90f + stableThemeUnit(variantKey, "family-radius") * 0.18f
        val familyRadius = ((direction.semanticSpread * 0.34f).coerceIn(18f, 36f) * radiusPersonality)
            .coerceAtMost(38f)
        val hue = normaliseThemeHue(familyAnchor + familyRadius * position + roleJitter)
        val saturationPersonality = 0.85f + stableThemeUnit(
            variantKey,
            "family-saturation-$name"
        ) * 0.30f
        val tonePersonality =
            (stableThemeUnit(variantKey, "family-tone-$name") - 0.5f) * 0.08f
        val raw = themeHslColor(
            hue = hue,
            saturation = (saturation * saturationScale * saturationPersonality).coerceIn(
                0.06f + 0.44f * vividness,
                0.28f + 0.64f * vividness
            ),
            lightness = (lightness + toneDelta + tonePersonality).coerceIn(0.25f, 0.82f)
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

    return AppAccentPalette(
        focus = tone(
            raw = Color(preset.primary),
            containerOverride = scheme.primaryContainer
        ),
        sleep = familyRole(
            name = "sleep",
            position = -0.35f,
            saturationScale = 0.90f,
            toneDelta = 0.015f
        ),
        study = familyRole(
            name = "study",
            position = 0.50f,
            saturationScale = 1.00f,
            toneDelta = -0.085f
        ),
        work = familyRole(
            name = "work",
            position = -0.82f,
            saturationScale = 0.94f,
            toneDelta = -0.075f
        ),
        other = familyRole(
            name = "other",
            position = 0.78f,
            saturationScale = 0.92f,
            toneDelta = 0.040f
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
        calm = familyRole(
            name = "calm",
            position = -0.08f,
            saturationScale = 0.82f,
            toneDelta = 0.060f
        ),
        info = familyRole(
            name = "info",
            position = 0.20f,
            saturationScale = 0.96f,
            toneDelta = if (preset.isDark) -0.065f else -0.055f
        ),
        energy = familyRole(
            name = "energy",
            position = -0.92f,
            saturationScale = 1.06f,
            toneDelta = 0.055f
        ),
        progress = familyRole(
            name = "progress",
            position = 0.92f,
            saturationScale = 0.90f,
            toneDelta = -0.028f
        ),
        creative = familyRole(
            name = "creative",
            position = 0.60f,
            saturationScale = 1.02f,
            toneDelta = 0.085f
        ),
        leisure = familyRole(
            name = "leisure",
            position = -0.55f,
            saturationScale = 0.88f,
            toneDelta = -0.045f
        ),
        schedule = familyRole(
            name = "schedule",
            position = -0.24f,
            saturationScale = 0.94f,
            toneDelta = if (preset.isDark) 0.105f else 0.095f
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
