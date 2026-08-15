package com.personal.sleepalarm.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

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
        CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
            content()
        }
    }
}

/**
 * Строит полную ColorScheme из пресета, деривируя контейнеры и вторичные цвета.
 */
internal fun buildColorScheme(p: ThemePreset): ColorScheme {
    val background = Color(p.background)
    val surface = Color(p.surface)
    val primary = Color(p.primary)
    val secondary = Color(p.secondary)
    val onBackground = Color(p.onBackground)

    val onSurface = onBackground
    val onSurfaceVariant = onBackground.copy(alpha = 0.7f)
    val surfaceVariant = lerp(surface, onBackground, 0.08f)

    val primaryContainer = lerp(background, primary, 0.25f)
    val onPrimaryContainer = primary
    val secondaryContainer = lerp(background, secondary, 0.25f)
    val onSecondaryContainer = secondary

    val tertiary = lerp(primary, secondary, 0.5f)
    val onTertiary = Color(0xFF111111)
    val onPrimary = Color(0xFF111111)
    val onSecondary = Color(0xFF111111)

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
            background = background, onBackground = onBackground,
            surface = surface, onSurface = onSurface,
            surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
            error = error, onError = onError,
            outline = outline
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = Color.White,
            primaryContainer = primaryContainer, onPrimaryContainer = primary,
            secondary = secondary, onSecondary = Color.White,
            secondaryContainer = secondaryContainer, onSecondaryContainer = secondary,
            tertiary = tertiary, onTertiary = Color.White,
            background = background, onBackground = onBackground,
            surface = surface, onSurface = onSurface,
            surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
            error = error, onError = Color.White,
            outline = outline
        )
    }
}