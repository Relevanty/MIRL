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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SleepAlarmTypography
    ) {
        ThemeSystemBars(
            colorScheme = colorScheme,
            useDarkIcons = !preset.isDark
        )
        CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
            content()
        }
    }
}

@Composable
private fun ThemeSystemBars(
    colorScheme: ColorScheme,
    useDarkIcons: Boolean
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = colorScheme.background.toArgb()
        window.navigationBarColor = colorScheme.surfaceContainer.toArgb()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = useDarkIcons
            isAppearanceLightNavigationBars = useDarkIcons
        }
    }
}

/**
 * Строит полную ColorScheme из пресета, деривируя контейнеры и вторичные цвета.
 */
internal fun buildColorScheme(p: ThemePreset): ColorScheme {
    val presetBackground = Color(p.background)
    val primary = Color(p.primary)
    val secondary = Color(p.secondary)
    val paletteTint = lerp(primary, secondary, 0.38f)
    val background = if (p.isDark) {
        presetBackground
    } else {
        // Светлая тема остаётся светлой, но получает заметный оттенок пресета
        // вместо стерильного белого полотна.
        lerp(presetBackground, paletteTint, 0.055f)
    }
    val surface = if (p.isDark) {
        Color(p.surface)
    } else {
        // Карточки чуть светлее фона, но никогда не становятся чисто белыми.
        lerp(background, Color.White, 0.30f)
    }
    val navigationSurface = if (p.isDark) {
        lerp(background, Color.Black, 0.26f)
    } else {
        // Нижняя панель всегда темнее основного фона и сохраняет оттенок темы.
        lerp(background, lerp(paletteTint, Color.Black, 0.48f), 0.15f)
    }
    val presetOnBackground = Color(p.onBackground)
    val onBackground = if (presetOnBackground.luminance() > 0.78f) {
        // Почти белый текст слегка пропускает оттенок выбранной темы.
        presetOnBackground.copy(alpha = 0.92f)
    } else {
        presetOnBackground
    }

    val onSurface = onBackground
    val onSurfaceVariant = onBackground.copy(alpha = 0.7f)
    val surfaceVariant = if (p.isDark) {
        lerp(surface, onBackground, 0.08f)
    } else {
        lerp(surface, paletteTint, 0.09f)
    }

    val containerBlend = if (p.isDark) 0.25f else 0.20f
    val primaryContainer = lerp(background, primary, containerBlend)
    val secondaryContainer = lerp(background, secondary, containerBlend)

    val tertiary = lerp(primary, secondary, 0.5f)
    val tertiaryContainer = lerp(background, tertiary, containerBlend)
    val onPrimary = readableContentColor(primary)
    val onSecondary = readableContentColor(secondary)
    val onTertiary = readableContentColor(tertiary)
    val onPrimaryContainer = readableContentColor(primaryContainer)
    val onSecondaryContainer = readableContentColor(secondaryContainer)
    val onTertiaryContainer = readableContentColor(tertiaryContainer)

    val error = Color(0xFFE57373)
    val onError = Color(0xFF1A0000)
    val outline = onBackground.copy(alpha = 0.3f)

    return if (p.isDark) {
        darkColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            secondary = secondary, onSecondary = onSecondary,
            secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary, onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
            background = background, onBackground = onBackground,
            surface = surface, onSurface = onSurface,
            surfaceContainer = navigationSurface,
            surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
            error = error, onError = onError,
            outline = outline
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            secondary = secondary, onSecondary = onSecondary,
            secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary, onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
            background = background, onBackground = onBackground,
            surface = surface, onSurface = onSurface,
            surfaceContainer = navigationSurface,
            surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
            error = error, onError = Color.White,
            outline = outline
        )
    }
}

private fun readableContentColor(background: Color): Color =
    if (background.luminance() > 0.18f) Color.Black else Color.White
