package com.personal.sleepalarm.ui.theme

import androidx.annotation.StringRes
import com.personal.sleepalarm.R
import kotlin.math.roundToInt

/**
 * 23 carefully separated dark colour families with five treatments each.
 * Together with the original 85 presets this brings the dark catalogue to 200.
 */
internal object ExpandedDarkThemes {

    private data class Family(
        val id: String,
        @StringRes val nameRes: Int,
        val category: ThemeCategory,
        val background: Long,
        val surface: Long,
        val primary: Long,
        val secondary: Long,
        val onBackground: Long = 0xFFF2F5F8
    )

    private data class Treatment(
        val id: String,
        @StringRes val nameRes: Int
    )

    private val treatments = listOf(
        Treatment("original", R.string.theme_variant_original),
        Treatment("depth", R.string.theme_variant_depth),
        Treatment("velvet", R.string.theme_variant_velvet),
        Treatment("glow", R.string.theme_variant_glow),
        Treatment("dusk", R.string.theme_variant_dusk)
    )

    private val families = listOf(
        Family("pure_black", R.string.theme_family_pure_black, ThemeCategory.AMOLED, 0xFF000000, 0xFF090B10, 0xFF56CCF2, 0xFFBB6BD9),
        Family("carbon", R.string.theme_family_carbon, ThemeCategory.AMOLED, 0xFF040506, 0xFF121519, 0xFFAAB4BE, 0xFF5EEAD4),
        Family("black_cherry", R.string.theme_family_black_cherry, ThemeCategory.AMOLED, 0xFF070104, 0xFF17060D, 0xFFFF4D8D, 0xFFC084FC),

        Family("cedar", R.string.theme_family_cedar, ThemeCategory.NATURE, 0xFF07110B, 0xFF102018, 0xFF73C991, 0xFFD9A066),
        Family("fern", R.string.theme_family_fern, ThemeCategory.NATURE, 0xFF04150F, 0xFF0C281D, 0xFF4ADE80, 0xFF2DD4BF),
        Family("moss_rain", R.string.theme_family_moss_rain, ThemeCategory.NATURE, 0xFF0B1207, 0xFF192313, 0xFFA3C95B, 0xFF7DD3A7),

        Family("abyss", R.string.theme_family_abyss, ThemeCategory.OCEAN, 0xFF010B14, 0xFF061B2A, 0xFF38BDF8, 0xFF22D3EE),
        Family("glacier", R.string.theme_family_glacier, ThemeCategory.OCEAN, 0xFF06111B, 0xFF102333, 0xFF7DD3FC, 0xFFC4B5FD),
        Family("tidal", R.string.theme_family_tidal, ThemeCategory.OCEAN, 0xFF031817, 0xFF0B2D2C, 0xFF2DD4BF, 0xFF60A5FA),

        Family("quasar", R.string.theme_family_quasar, ThemeCategory.SPACE, 0xFF07031A, 0xFF150B32, 0xFFD946EF, 0xFF22D3EE),
        Family("pulsar", R.string.theme_family_pulsar, ThemeCategory.SPACE, 0xFF030716, 0xFF0A1630, 0xFF60A5FA, 0xFFF472B6),
        Family("lunar_dust", R.string.theme_family_lunar_dust, ThemeCategory.SPACE, 0xFF0B0C12, 0xFF1B1D27, 0xFFCBD5E1, 0xFFA78BFA),

        Family("ultraviolet", R.string.theme_family_ultraviolet, ThemeCategory.NEON, 0xFF090016, 0xFF1B082B, 0xFFC026D3, 0xFF22D3EE),
        Family("laser_cyan", R.string.theme_family_laser_cyan, ThemeCategory.NEON, 0xFF001014, 0xFF06252C, 0xFF00F5FF, 0xFF7C3AED),
        Family("toxic_lime", R.string.theme_family_toxic_lime, ThemeCategory.NEON, 0xFF071006, 0xFF14230E, 0xFFA3FF12, 0xFF22D3EE),

        Family("gunmetal", R.string.theme_family_gunmetal, ThemeCategory.INDUSTRIAL, 0xFF0B0D0F, 0xFF1A1F23, 0xFF94A3B8, 0xFFF59E0B),
        Family("copper_foundry", R.string.theme_family_copper_foundry, ThemeCategory.INDUSTRIAL, 0xFF120905, 0xFF28150C, 0xFFF97316, 0xFFD4A574),

        Family("crt_green", R.string.theme_family_crt_green, ThemeCategory.RETRO, 0xFF000B06, 0xFF071B12, 0xFF33FF8A, 0xFF9EFFB9),
        Family("amber_console", R.string.theme_family_amber_console, ThemeCategory.RETRO, 0xFF100800, 0xFF251603, 0xFFFFB000, 0xFFF97316),

        Family("velvet_wine", R.string.theme_family_velvet_wine, ThemeCategory.ELEGANT, 0xFF15050D, 0xFF2B0F1C, 0xFFFB7185, 0xFFD8B4FE),
        Family("royal_ink", R.string.theme_family_royal_ink, ThemeCategory.ELEGANT, 0xFF080817, 0xFF17172B, 0xFF818CF8, 0xFFF5C451),

        Family("cobalt_code", R.string.theme_family_cobalt_code, ThemeCategory.SYSTEM, 0xFF040B18, 0xFF0B1B36, 0xFF3B82F6, 0xFF22D3EE),
        Family("terminal_blue", R.string.theme_family_terminal_blue, ThemeCategory.SYSTEM, 0xFF020B11, 0xFF0A1D29, 0xFF38BDF8, 0xFF34D399)
    )

    val all: List<ThemePreset> = families.flatMap { family ->
        treatments.mapIndexed { index, treatment ->
            createPreset(family, treatment, index)
        }
    }

    private fun createPreset(family: Family, treatment: Treatment, index: Int): ThemePreset {
        val black = 0xFF000000
        val white = 0xFFFFFFFF
        val colors = when (index) {
            0 -> listOf(family.background, family.surface, family.primary, family.secondary)
            1 -> listOf(
                blend(family.background, black, 0.30f),
                blend(family.surface, black, 0.18f),
                blend(family.primary, white, 0.08f),
                blend(family.secondary, white, 0.06f)
            )
            2 -> listOf(
                blend(family.background, family.primary, 0.07f),
                blend(family.surface, family.primary, 0.13f),
                blend(family.primary, family.secondary, 0.12f),
                blend(family.secondary, white, 0.13f)
            )
            3 -> listOf(
                blend(family.background, black, 0.40f),
                blend(family.surface, black, 0.20f),
                blend(family.primary, white, 0.24f),
                blend(family.secondary, white, 0.21f)
            )
            else -> listOf(
                blend(family.background, family.secondary, 0.06f),
                blend(family.surface, family.secondary, 0.11f),
                blend(family.primary, white, 0.15f),
                blend(family.primary, family.secondary, 0.58f)
            )
        }

        return ThemePreset(
            id = "${family.id}_${treatment.id}",
            nameRes = family.nameRes,
            isDark = true,
            background = colors[0],
            surface = colors[1],
            primary = colors[2],
            secondary = colors[3],
            onBackground = family.onBackground,
            category = family.category,
            variantNameRes = treatment.nameRes
        )
    }

    private fun blend(from: Long, to: Long, amount: Float): Long {
        val clamped = amount.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val start = ((from shr shift) and 0xFF).toInt()
            val end = ((to shr shift) and 0xFF).toInt()
            return (start + (end - start) * clamped).roundToInt().coerceIn(0, 255)
        }
        return (channel(24).toLong() shl 24) or
            (channel(16).toLong() shl 16) or
            (channel(8).toLong() shl 8) or
            channel(0).toLong()
    }
}
