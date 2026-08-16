package com.personal.sleepalarm.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {

    @Test
    fun lightThemes_areTintedAndNavigationIsDarker() {
        ThemeCatalog.all.filterNot { it.isDark }.forEach { preset ->
            val colors = buildColorScheme(preset)
            assertTrue("${preset.id}: background must not be pure white", colors.background != Color.White)
            assertTrue("${preset.id}: surface must not be pure white", colors.surface != Color.White)
            assertTrue(
                "${preset.id}: navigation must be darker than the main background",
                colors.surfaceContainer.luminance() < colors.background.luminance()
            )
        }
    }

    @Test
    fun everyTheme_keepsCoreTextAndAccentContentReadable() {
        ThemeCatalog.all.forEach { preset ->
            val colors = buildColorScheme(preset)
            assertReadable(preset.id, "background", colors.onBackground, colors.background)
            assertReadable(preset.id, "surface", colors.onSurface, colors.surface)
            assertReadable(preset.id, "primary", colors.onPrimary, colors.primary)
            assertReadable(preset.id, "secondary", colors.onSecondary, colors.secondary)
            assertReadable(preset.id, "tertiary", colors.onTertiary, colors.tertiary)
        }
    }

    private fun assertReadable(themeId: String, role: String, foreground: Color, background: Color) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$themeId/$role contrast was $ratio", ratio >= 4.5f)
    }

    private fun contrastRatio(foreground: Color, background: Color): Float {
        val renderedForeground = foreground.compositeOver(background)
        val lighter = maxOf(renderedForeground.luminance(), background.luminance())
        val darker = minOf(renderedForeground.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
