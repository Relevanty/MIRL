package com.personal.sleepalarm.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Тема приложения. Строит ColorScheme из пресета по themeId.
 *
 * ДОБАВЛЕНО (фикс контраста): CompositionLocalProvider устанавливает
 * LocalContentColor = onBackground, чтобы иконки/текст БЕЗ явного цвета
 * (стрелки назад, «+»/«−», IconButton) были читаемы на любом фоне.
 */
@Composable
fun SleepAlarmTheme(
    themeId: String = ThemeCatalog.DEFAULT_ID,
    content: @Composable () -> Unit
) {
    val preset = ThemeCatalog.byId(themeId)
    val colorScheme = remember(preset) { buildColorScheme(preset) }
    val appAccents = remember(preset, colorScheme) {
        buildAppAccentPalette(preset, colorScheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SleepAlarmTypography
    ) {
        ThemeSystemBars(
            colorScheme = colorScheme,
            navigationColor = appAccents.chrome.navigation
        )
        CompositionLocalProvider(
            LocalAppAccentPalette provides appAccents,
            LocalContentColor provides colorScheme.onBackground
        ) {
            content()
        }
    }
}

@Composable
private fun ThemeSystemBars(
    colorScheme: ColorScheme,
    navigationColor: Color
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = colorScheme.background.toArgb()
        window.navigationBarColor = navigationColor.toArgb()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = prefersDarkSystemIcons(colorScheme.background)
            isAppearanceLightNavigationBars = prefersDarkSystemIcons(navigationColor)
        }
    }
}

/**
 * Строит полную ColorScheme из пресета, деривируя контейнеры и вторичные цвета.
 */
internal fun buildColorScheme(p: ThemePreset): ColorScheme {
    val direction = themeArtDirection(p)
    val presetBackground = Color(p.background)
    val presetSurface = Color(p.surface)
    val rawPrimary = Color(p.primary)
    val rawSecondary = Color(p.secondary)
    val paletteTint = lerp(rawPrimary, rawSecondary, 0.38f)
    val identityTint = themeIdentitySeed(p, direction)
    val expressiveTint = lerp(paletteTint, identityTint, 0.34f)
    val background = if (p.isDark) {
        // AMOLED recipes retain true black; the other families receive a
        // restrained colour cast that makes their atmosphere visible even on
        // large empty screens.
        if (p.category == ThemeCategory.AMOLED && presetBackground.luminance() < 0.003f) {
            presetBackground
        } else {
            lerp(presetBackground, expressiveTint, direction.backgroundTint)
        }
    } else {
        // Pastel stays in the canvas while controls remain dark, readable ink.
        lerp(presetBackground, expressiveTint, direction.backgroundTint)
    }
    val surface = (if (p.isDark) {
        lerp(
            lerp(presetSurface, rawPrimary, direction.surfacePrimaryTint),
            rawSecondary,
            direction.surfaceSecondaryTint
        )
    } else {
        // Honour the surface stored by the preset, then tint it. Previously
        // every light theme discarded this value and converged on off-white.
        val raised = lerp(background, presetSurface, 0.62f)
        lerp(
            lerp(raised, rawPrimary, direction.surfacePrimaryTint),
            rawSecondary,
            direction.surfaceSecondaryTint
        )
    }).limitDarkSurface(p.isDark, 0.155f)
    val variantSeed = lerp(identityTint, rawSecondary, 0.42f)
    val surfaceVariant = lerp(surface, variantSeed, direction.variantTint)
        .limitDarkSurface(p.isDark, 0.165f)
    val surfaceContainerLowest = (if (p.isDark) {
        lerp(background, Color.Black, 0.10f + direction.surfaceRange)
    } else {
        lerp(surface, Color.White, 0.08f + direction.surfaceRange * 0.35f)
    }).limitDarkSurface(p.isDark, 0.105f)
    val surfaceContainerLow = lerp(
        background,
        surface,
        if (p.isDark) 0.38f + direction.surfaceRange else 0.54f + direction.surfaceRange * 0.5f
    ).limitDarkSurface(p.isDark, 0.135f)
    val surfaceContainerHigh = lerp(
        surface,
        surfaceVariant,
        0.48f + direction.surfaceRange
    ).limitDarkSurface(p.isDark, 0.160f)
    val surfaceContainer = lerp(surfaceContainerLow, surfaceContainerHigh, 0.52f)
        .limitDarkSurface(p.isDark, 0.150f)
    val surfaceContainerHighest = lerp(
        surfaceVariant,
        expressiveTint,
        if (p.isDark) 0.035f + direction.surfaceRange * 0.35f else 0.020f + direction.surfaceRange * 0.18f
    ).limitDarkSurface(p.isDark, 0.172f)
    val surfaceBright = (if (p.isDark) {
        lerp(surface, Color.White, direction.surfaceRange)
    } else {
        lerp(surface, Color.White, direction.surfaceRange * 0.28f)
    }).limitDarkSurface(p.isDark, 0.178f)
    val surfaceDim = (if (p.isDark) {
        lerp(background, Color.Black, direction.surfaceRange * 0.70f)
    } else {
        lerp(background, expressiveTint, 0.035f + direction.surfaceRange * 0.18f)
            .let { lerp(it, Color.Black, 0.045f + direction.surfaceRange * 0.25f) }
    }).limitDarkSurface(p.isDark, 0.120f)
    val baseSurfaces = listOf(
        background,
        surface,
        surfaceVariant,
        surfaceContainer,
        surfaceContainerLowest,
        surfaceContainerLow,
        surfaceContainerHigh,
        surfaceContainerHighest,
        surfaceBright,
        surfaceDim
    )

    // Material components also use these roles as foreground labels (TextButton,
    // links, switches). Keep them readable on the normal canvas; AppAccentTone
    // retains each original seed as `fill` for artwork and larger colour areas.
    val materialContentSurfaces = listOf(background, surface, surfaceVariant, surfaceContainer)
    val rawTertiary = expressiveTertiarySeed(p, direction)
    val primary = ensureContrast(rawPrimary, materialContentSurfaces, MIN_TEXT_CONTRAST)
    val secondary = ensureContrast(rawSecondary, materialContentSurfaces, MIN_TEXT_CONTRAST)
    val tertiary = ensureContrast(rawTertiary, materialContentSurfaces, MIN_TEXT_CONTRAST)

    val containerBlend = direction.containerBlend
    val primaryContainer = lerp(background, rawPrimary, containerBlend)
    val secondaryContainer = lerp(background, rawSecondary, containerBlend)
    val tertiaryContainer = lerp(background, rawTertiary, containerBlend)

    val contentSurfaces = baseSurfaces + listOf(
        primaryContainer,
        secondaryContainer,
        tertiaryContainer
    )
    val readableOnSurface = ensureContrast(Color(p.onBackground), contentSurfaces, MIN_TEXT_CONTRAST)
    val onSurface = ensureContrastAtAlpha(
        foreground = readableOnSurface,
        backgrounds = contentSurfaces,
        renderedAlpha = MATERIAL_DISABLED_ALPHA,
        minimumRatio = MIN_DISABLED_CONTRAST
    )
    val onBackground = ensureContrast(onSurface, background, MIN_TEXT_CONTRAST)
    val mutedSeed = lerp(
        onSurface,
        expressiveTint,
        if (p.isDark) 0.13f + direction.variantTint * 0.30f else 0.10f + direction.variantTint * 0.24f
    )
    val onSurfaceVariant = ensureContrast(
        mutedSeed,
        baseSurfaces,
        MIN_TEXT_CONTRAST
    )

    val onPrimary = ensureContrast(onSurface, primary, MIN_TEXT_CONTRAST)
    val onSecondary = ensureContrast(onSurface, secondary, MIN_TEXT_CONTRAST)
    val onTertiary = ensureContrast(onSurface, tertiary, MIN_TEXT_CONTRAST)
    val onPrimaryContainer = ensureContrast(onSurface, primaryContainer, MIN_TEXT_CONTRAST)
    val onSecondaryContainer = ensureContrast(onSurface, secondaryContainer, MIN_TEXT_CONTRAST)
    val onTertiaryContainer = ensureContrast(onSurface, tertiaryContainer, MIN_TEXT_CONTRAST)

    val identityHsl = identityTint.toThemeHsl()
    val rawError = themeHslColor(
        hue = harmonisedThemeStatusHue(
            canonicalHue = 354f,
            identityHue = identityHsl.hue,
            amount = direction.statusHarmonisation,
            maximumShift = 9f
        ),
        saturation = if (p.isDark) 0.84f else 0.72f,
        lightness = if (p.isDark) 0.72f + direction.accentToneDelta * 0.25f else 0.35f
    )
    val error = ensureContrast(rawError, baseSurfaces, MIN_TEXT_CONTRAST)
    val onError = ensureContrast(onSurface, error, MIN_TEXT_CONTRAST)
    val errorContainer = lerp(background, error, if (p.isDark) 0.24f else 0.16f)
    val onErrorContainer = ensureContrast(onSurface, errorContainer, MIN_TEXT_CONTRAST)

    val outlineSeed = lerp(onSurface, expressiveTint, if (p.isDark) 0.33f else 0.42f)
    val outline = ensureContrast(outlineSeed, baseSurfaces, MIN_UI_CONTRAST)
    val outlineVariant = ensureContrast(
        lerp(onSurfaceVariant, variantSeed, if (p.isDark) 0.36f else 0.44f),
        baseSurfaces,
        MIN_UI_CONTRAST
    )

    val inverseSurface = if (p.isDark) {
        lerp(onSurface, expressiveTint, 0.08f + direction.surfaceSecondaryTint * 0.30f)
    } else {
        lerp(onSurface, expressiveTint, 0.06f + direction.surfaceSecondaryTint * 0.24f)
    }
    val inverseOnSurface = ensureContrast(background, inverseSurface, MIN_TEXT_CONTRAST)
    val inversePrimary = ensureContrast(primary, inverseSurface, MIN_UI_CONTRAST)

    return if (p.isDark) {
        darkColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary, onSecondary = onSecondary,
            secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary, onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
            background = background, onBackground = onBackground,
            surface = surface, onSurface = onSurface,
            surfaceContainer = surfaceContainer,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceBright = surfaceBright,
            surfaceDim = surfaceDim,
            surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
            surfaceTint = primary,
            inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface,
            error = error, onError = onError,
            errorContainer = errorContainer, onErrorContainer = onErrorContainer,
            outline = outline, outlineVariant = outlineVariant,
            scrim = lerp(Color.Black, identityTint, 0.075f)
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary, onSecondary = onSecondary,
            secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary, onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
            background = background, onBackground = onBackground,
            surface = surface, onSurface = onSurface,
            surfaceContainer = surfaceContainer,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceBright = surfaceBright,
            surfaceDim = surfaceDim,
            surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
            surfaceTint = primary,
            inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface,
            error = error, onError = onError,
            errorContainer = errorContainer, onErrorContainer = onErrorContainer,
            outline = outline, outlineVariant = outlineVariant,
            scrim = lerp(Color.Black, identityTint, 0.045f)
        )
    }
}

/**
 * A bright accent may tint a dark Velvet/Glow surface, but the surface must
 * remain dark enough for one readable content colour across the entire stack.
 * The binary search changes luminance only as far as needed and keeps the hue.
 */
private fun Color.limitDarkSurface(isDark: Boolean, maximumLuminance: Float): Color {
    if (!isDark || luminance() <= maximumLuminance) return this

    var low = 0f
    var high = 1f
    repeat(14) {
        val middle = (low + high) / 2f
        if (lerp(this, Color.Black, middle).luminance() > maximumLuminance) {
            low = middle
        } else {
            high = middle
        }
    }
    return lerp(this, Color.Black, high)
}

private fun prefersDarkSystemIcons(background: Color): Boolean =
    contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background)
