package com.personal.sleepalarm.ui.theme

import androidx.annotation.StringRes
import com.personal.sleepalarm.R

/** Stable display order for the large theme catalogue. */
enum class ThemeCategory(
    @StringRes val titleRes: Int,
    val sortOrder: Int
) {
    BASIC(R.string.theme_category_basic, 0),
    AMOLED(R.string.theme_category_amoled, 1),
    NATURE(R.string.theme_category_nature, 2),
    OCEAN(R.string.theme_category_ocean, 3),
    SPACE(R.string.theme_category_space, 4),
    NEON(R.string.theme_category_neon, 5),
    INDUSTRIAL(R.string.theme_category_industrial, 6),
    RETRO(R.string.theme_category_retro, 7),
    ELEGANT(R.string.theme_category_elegant, 8),
    SYSTEM(R.string.theme_category_system, 9),
    PAPER(R.string.theme_category_paper, 10),
    PASTEL(R.string.theme_category_pastel, 11)
}
