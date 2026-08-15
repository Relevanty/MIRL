package com.personal.sleepalarm.ui.theme

import androidx.annotation.StringRes

/**
 * Пресет темы. Цвета хранятся как ARGB-Long (0xAARRGGBB).
 */
data class ThemePreset(
    val id: String,
    @StringRes val nameRes: Int,
    val isDark: Boolean,
    val background: Long,
    val surface: Long,
    val primary: Long,
    val secondary: Long,
    val onBackground: Long
)