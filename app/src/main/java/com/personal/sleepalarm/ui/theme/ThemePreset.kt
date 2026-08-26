package com.personal.sleepalarm.ui.theme

import android.content.res.Resources
import androidx.annotation.StringRes
import com.personal.sleepalarm.R

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
    val onBackground: Long,
    val category: ThemeCategory = ThemeCategory.BASIC,
    @StringRes val variantNameRes: Int? = null
) {
    fun localizedName(resources: Resources): String {
        val variant = variantNameRes ?: return resources.getString(nameRes)
        return resources.getString(
            R.string.theme_compound_name,
            resources.getString(nameRes),
            resources.getString(variant)
        )
    }
}
