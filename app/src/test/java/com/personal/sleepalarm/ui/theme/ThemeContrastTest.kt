package com.personal.sleepalarm.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {

    @Test
    fun catalogue_hasExactlyTwoHundredDistinctDarkThemes() {
        assertEquals(200, ThemeCatalog.night.size)
        assertEquals(55, ThemeCatalog.day.size)
        assertEquals(255, ThemeCatalog.all.size)
        assertEquals(ThemeCatalog.all.size, ThemeCatalog.all.map { it.id }.toSet().size)

        val paletteSignatures = ThemeCatalog.night.map {
            listOf(it.background, it.surface, it.primary, it.secondary)
        }
        assertEquals(
            "Dark presets must not be exact palette duplicates",
            paletteSignatures.size,
            paletteSignatures.toSet().size
        )
    }

    @Test
    fun everyTheme_hasAUniqueDerivedVisualIdentity() {
        val materialSignatures = ThemeCatalog.all.associate { preset ->
            val scheme = buildColorScheme(preset)
            preset.id to listOf(
                scheme.background,
                scheme.surface,
                scheme.surfaceVariant,
                scheme.surfaceContainer,
                scheme.primary,
                scheme.secondary,
                scheme.tertiary,
                scheme.error
            ).map(Color::toArgb)
        }
        val duplicateMaterial = materialSignatures.entries
            .groupBy { it.value }
            .values
            .filter { it.size > 1 }
            .map { group -> group.map { it.key } }
        assertTrue("Duplicate derived Material themes: $duplicateMaterial", duplicateMaterial.isEmpty())

        val semanticSignatures = ThemeCatalog.all.associate { preset ->
            val scheme = buildColorScheme(preset)
            val palette = buildAppAccentPalette(preset, scheme)
            preset.id to palette.all.flatMap { tone ->
                listOf(tone.color.toArgb(), tone.fill.toArgb(), tone.container.toArgb())
            } + listOf(
                palette.chrome.navigation.toArgb(),
                palette.chrome.onNavigation.toArgb(),
                palette.chrome.onNavigationMuted.toArgb()
            )
        }
        val duplicateSemantic = semanticSignatures.entries
            .groupBy { it.value }
            .values
            .filter { it.size > 1 }
            .map { group -> group.map { it.key } }
        assertTrue("Duplicate semantic theme palettes: $duplicateSemantic", duplicateSemantic.isEmpty())
    }

    @Test
    fun everyTheme_hasAPerceptuallyDistinctCoreIdentity() {
        val signatures = ThemeCatalog.all.associate { preset ->
            preset.id to perceptualSignature(preset)
        }
        val ids = signatures.keys.toList()
        val distances = buildList {
            ids.indices.forEach { firstIndex ->
                for (secondIndex in firstIndex + 1 until ids.size) {
                    val firstId = ids[firstIndex]
                    val secondId = ids[secondIndex]
                    add(
                        ThemePairDistance(
                            firstId = firstId,
                            secondId = secondId,
                            distance = aggregateOklabDistance(
                                signatures.getValue(firstId),
                                signatures.getValue(secondId)
                            ),
                            sameTreatmentFamily = treatmentFamily(firstId) != null &&
                                treatmentFamily(firstId) == treatmentFamily(secondId)
                        )
                    )
                }
            }
        }
        val violations = distances.filter { pair ->
            pair.distance < if (pair.sameTreatmentFamily) {
                MIN_RELATED_TREATMENT_OKLAB_RMS
            } else {
                MIN_UNRELATED_THEME_OKLAB_RMS
            }
        }.sortedBy(ThemePairDistance::distance)

        assertTrue(
            buildString {
                append("Perceptually duplicate theme identities. ")
                append("Thresholds: unrelated=")
                append(MIN_UNRELATED_THEME_OKLAB_RMS.formatDistance())
                append(", related treatments=")
                append(MIN_RELATED_TREATMENT_OKLAB_RMS.formatDistance())
                append(". Nearest unrelated: ")
                appendClosest(distances.filterNot(ThemePairDistance::sameTreatmentFamily))
                append(". Nearest related treatments: ")
                appendClosest(distances.filter(ThemePairDistance::sameTreatmentFamily))
                append(". Violations: ")
                append(violations.take(12).joinToString { it.description() })
            },
            violations.isEmpty()
        )
    }

    @Test
    fun darkThemes_areActuallyDarkAndSortedByCategory() {
        ThemeCatalog.night.forEach { preset ->
            assertTrue(
                "${preset.id}: dark background is too bright",
                Color(preset.background).luminance() < 0.12f
            )
            assertTrue(
                "${preset.id}: dark surface is too bright",
                Color(preset.surface).luminance() < 0.18f
            )
        }

        val categoryOrder = ThemeCatalog.night.map { it.category.sortOrder }
        assertEquals(categoryOrder.sorted(), categoryOrder)
        assertEquals(
            ThemeCategory.entries.filterNot { it == ThemeCategory.PAPER || it == ThemeCategory.PASTEL }.toSet(),
            ThemeCatalog.night.map { it.category }.toSet()
        )
    }

    @Test
    fun lightThemes_areTintedAndNavigationIsDarker() {
        ThemeCatalog.all.filterNot { it.isDark }.forEach { preset ->
            val colors = buildColorScheme(preset)
            val accents = buildAppAccentPalette(preset, colors)
            assertTrue("${preset.id}: background must not be pure white", colors.background != Color.White)
            assertTrue("${preset.id}: surface must not be pure white", colors.surface != Color.White)
            assertTrue(
                "${preset.id}: navigation must be darker than the main background",
                accents.chrome.navigation.luminance() < colors.background.luminance()
            )
            assertReadable(
                preset.id,
                "onNavigation",
                accents.chrome.onNavigation,
                accents.chrome.navigation
            )
            assertReadable(
                preset.id,
                "onNavigationMuted",
                accents.chrome.onNavigationMuted,
                accents.chrome.navigation
            )
        }
    }

    @Test
    fun everyTheme_keepsCoreTextAndAccentContentReadable() {
        ThemeCatalog.all.forEach { preset ->
            val colors = buildColorScheme(preset)
            assertReadable(preset.id, "background", colors.onBackground, colors.background)
            assertReadable(preset.id, "surface", colors.onSurface, colors.surface)
            assertReadable(preset.id, "surfaceVariant", colors.onSurfaceVariant, colors.surfaceVariant)
            assertReadable(preset.id, "surfaceContainer", colors.onSurface, colors.surfaceContainer)
            assertReadable(preset.id, "surfaceContainerLow", colors.onSurfaceVariant, colors.surfaceContainerLow)
            assertReadable(preset.id, "surfaceContainerHigh", colors.onSurfaceVariant, colors.surfaceContainerHigh)
            assertReadable(preset.id, "surfaceContainerHighest", colors.onSurfaceVariant, colors.surfaceContainerHighest)
            assertReadable(preset.id, "primary", colors.onPrimary, colors.primary)
            assertReadable(preset.id, "secondary", colors.onSecondary, colors.secondary)
            assertReadable(preset.id, "tertiary", colors.onTertiary, colors.tertiary)
            assertReadable(preset.id, "primaryContainer", colors.onPrimaryContainer, colors.primaryContainer)
            assertReadable(preset.id, "secondaryContainer", colors.onSecondaryContainer, colors.secondaryContainer)
            assertReadable(preset.id, "tertiaryContainer", colors.onTertiaryContainer, colors.tertiaryContainer)
            assertReadable(preset.id, "error", colors.onError, colors.error)
            assertReadable(preset.id, "errorContainer", colors.onErrorContainer, colors.errorContainer)
            assertReadable(preset.id, "inverseSurface", colors.inverseOnSurface, colors.inverseSurface)

            listOf(
                "background" to colors.background,
                "surface" to colors.surface,
                "surfaceVariant" to colors.surfaceVariant,
                "surfaceContainer" to colors.surfaceContainer
            ).forEach { (surfaceName, surfaceColor) ->
                assertReadable(preset.id, "primaryAsContent/$surfaceName", colors.primary, surfaceColor)
                assertReadable(preset.id, "secondaryAsContent/$surfaceName", colors.secondary, surfaceColor)
                assertReadable(preset.id, "tertiaryAsContent/$surfaceName", colors.tertiary, surfaceColor)
            }

            val commonSurfaces = listOf(
                colors.background,
                colors.surface,
                colors.surfaceVariant,
                colors.surfaceContainer,
                colors.surfaceContainerLowest,
                colors.surfaceContainerLow,
                colors.surfaceContainerHigh,
                colors.surfaceContainerHighest,
                colors.surfaceBright,
                colors.surfaceDim
            )
            commonSurfaces.forEachIndexed { index, surface ->
                assertReadable(preset.id, "onSurfaceVariant/$index", colors.onSurfaceVariant, surface)
                assertContrastAtLeast(preset.id, "outline/$index", colors.outline, surface, MIN_UI_CONTRAST)
                assertContrastAtLeast(
                    preset.id,
                    "outlineVariant/$index",
                    colors.outlineVariant,
                    surface,
                    MIN_UI_CONTRAST
                )
            }
            assertContrastAtLeast(
                preset.id,
                "inversePrimary",
                colors.inversePrimary,
                colors.inverseSurface,
                MIN_UI_CONTRAST
            )
            assertContrastAtLeast(
                preset.id,
                "disabled",
                disabledContentColor(colors.onSurface, colors.surface),
                colors.surface,
                MIN_DISABLED_CONTRAST
            )
            assertContrastAtLeast(
                preset.id,
                "materialDisabled",
                colors.onSurface.copy(alpha = MATERIAL_DISABLED_ALPHA),
                colors.surface,
                MIN_DISABLED_CONTRAST
            )

            listOf(
                colors.onBackground,
                colors.onSurface,
                colors.onSurfaceVariant,
                colors.onPrimary,
                colors.onSecondary,
                colors.onTertiary,
                colors.outline,
                colors.outlineVariant
            ).forEachIndexed { index, color ->
                assertEquals("${preset.id}/opaqueRole/$index", 1f, color.alpha, 0.0001f)
            }
        }
    }

    @Test
    fun everyTheme_hasAReadableExpressiveSemanticSpectrum() {
        ThemeCatalog.all.forEach { preset ->
            val scheme = buildColorScheme(preset)
            val accents = buildAppAccentPalette(preset, scheme)
            assertEquals(
                "${preset.id}: focus fill must preserve the preset primary seed",
                Color(preset.primary).toArgb(),
                accents.focus.fill.toArgb()
            )
            assertTrue(
                "${preset.id}: semantic accents collapsed into too few colours",
                accents.all.map { it.color }.toSet().size >= 7
            )
            assertTrue(
                "${preset.id}: expressive tertiary duplicates a seed",
                scheme.tertiary != scheme.primary && scheme.tertiary != scheme.secondary
            )
            accents.all.forEachIndexed { index, tone ->
                listOf(
                    scheme.background,
                    scheme.surface,
                    scheme.surfaceVariant,
                    scheme.surfaceContainer,
                    scheme.surfaceContainerLow,
                    scheme.surfaceContainerHigh,
                    scheme.surfaceContainerHighest,
                    accents.chrome.navigation
                ).forEachIndexed { surfaceIndex, surface ->
                    assertReadable(preset.id, "accent/$index/surface/$surfaceIndex", tone.color, surface)
                }
                assertReadable(preset.id, "accent/$index/onColor", tone.onColor, tone.color)
                assertReadable(preset.id, "accent/$index/onFill", tone.onFill, tone.fill)
                assertReadable(preset.id, "accent/$index/onContainer", tone.onContainer, tone.container)
                assertTrue(
                    "${preset.id}: accent/$index container must be visibly tinted",
                    tone.container != scheme.background
                )
            }

            val urgentHue = accents.urgent.color.toThemeHsl().hue
            val warningHue = accents.warning.color.toThemeHsl().hue
            val successHue = accents.success.color.toThemeHsl().hue
            assertTrue(
                "${preset.id}: urgent left the danger hue family ($urgentHue)",
                themeHueDistance(urgentHue, 354f) <= 21f
            )
            assertTrue(
                "${preset.id}: warning left the amber hue family ($warningHue)",
                themeHueDistance(warningHue, 47f) <= 13.5f
            )
            assertTrue(
                "${preset.id}: success left the green hue family ($successHue)",
                themeHueDistance(successHue, 142f) <= 18.5f
            )
            assertTrue(
                "${preset.id}: Material error (${scheme.error.toThemeHsl().hue}) and urgent ($urgentHue) diverged",
                themeHueDistance(scheme.error.toThemeHsl().hue, urgentHue) <= 6.5f
            )
        }
    }

    @Test
    fun lightAndDarkVariants_keepTheirSurfaceCharacter() {
        ThemeCatalog.night.forEach { preset ->
            val colors = buildColorScheme(preset)
            listOf(
                colors.background,
                colors.surface,
                colors.surfaceContainerLow,
                colors.surfaceContainerHigh,
                colors.surfaceContainerHighest
            ).forEachIndexed { index, color ->
                assertTrue("${preset.id}/darkSurface/$index", color.luminance() < 0.25f)
            }
        }
        ThemeCatalog.day.forEach { preset ->
            val colors = buildColorScheme(preset)
            listOf(
                colors.background,
                colors.surface,
                colors.surfaceContainerLow,
                colors.surfaceContainerHigh,
                colors.surfaceContainerHighest
            ).forEachIndexed { index, color ->
                assertTrue("${preset.id}/lightSurface/$index", color.luminance() > 0.45f)
            }
        }
    }

    private fun assertReadable(themeId: String, role: String, foreground: Color, background: Color) {
        assertContrastAtLeast(themeId, role, foreground, background, MIN_TEXT_CONTRAST)
    }

    private fun assertContrastAtLeast(
        themeId: String,
        role: String,
        foreground: Color,
        background: Color,
        minimum: Float
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$themeId/$role contrast was $ratio, expected $minimum", ratio + 0.001f >= minimum)
    }

    private fun perceptualSignature(preset: ThemePreset): List<Oklab> {
        val scheme = buildColorScheme(preset)
        val accents = buildAppAccentPalette(preset, scheme)
        return listOf(
            scheme.background,
            scheme.surface,
            scheme.primary,
            scheme.secondary,
            scheme.tertiary,
            accents.focus.fill,
            accents.sleep.fill,
            accents.study.fill,
            accents.work.fill,
            accents.other.fill,
            accents.calm.fill
        ).map { color -> color.toOklab() }
    }

    private fun aggregateOklabDistance(first: List<Oklab>, second: List<Oklab>): Double {
        require(first.size == second.size)
        return sqrt(
            first.indices.sumOf { index ->
                first[index].squaredDistanceTo(second[index])
            } / first.size
        )
    }

    private fun Color.toOklab(): Oklab {
        val argb = toArgb()
        val red = ((argb ushr 16) and 0xFF).toDouble().srgbToLinear()
        val green = ((argb ushr 8) and 0xFF).toDouble().srgbToLinear()
        val blue = (argb and 0xFF).toDouble().srgbToLinear()

        val l = 0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue
        val m = 0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue
        val s = 0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue
        val lRoot = Math.cbrt(l)
        val mRoot = Math.cbrt(m)
        val sRoot = Math.cbrt(s)

        return Oklab(
            lightness = 0.2104542553 * lRoot + 0.7936177850 * mRoot - 0.0040720468 * sRoot,
            greenRed = 1.9779984951 * lRoot - 2.4285922050 * mRoot + 0.4505937099 * sRoot,
            blueYellow = 0.0259040371 * lRoot + 0.7827717662 * mRoot - 0.8086757660 * sRoot
        )
    }

    private fun Double.srgbToLinear(): Double {
        val encoded = this / 255.0
        return if (encoded <= 0.04045) {
            encoded / 12.92
        } else {
            ((encoded + 0.055) / 1.055).pow(2.4)
        }
    }

    private fun treatmentFamily(themeId: String): String? = TREATMENT_SUFFIXES
        .firstOrNull { suffix -> themeId.endsWith("_$suffix") }
        ?.let { suffix -> themeId.removeSuffix("_$suffix") }

    private fun StringBuilder.appendClosest(distances: List<ThemePairDistance>) {
        append(
            distances.sortedBy(ThemePairDistance::distance)
                .take(5)
                .joinToString { it.description() }
        )
    }

    private fun ThemePairDistance.description(): String =
        "$firstId/$secondId=${distance.formatDistance()}"

    private fun Double.formatDistance(): String = String.format(Locale.US, "%.5f", this)

    private data class Oklab(
        val lightness: Double,
        val greenRed: Double,
        val blueYellow: Double
    ) {
        fun squaredDistanceTo(other: Oklab): Double {
            val deltaLightness = lightness - other.lightness
            val deltaGreenRed = greenRed - other.greenRed
            val deltaBlueYellow = blueYellow - other.blueYellow
            return deltaLightness * deltaLightness +
                deltaGreenRed * deltaGreenRed +
                deltaBlueYellow * deltaBlueYellow
        }
    }

    private data class ThemePairDistance(
        val firstId: String,
        val secondId: String,
        val distance: Double,
        val sameTreatmentFamily: Boolean
    )

    private companion object {
        // Oklab is expressed on a 0..1 scale. Around 0.02 is a useful
        // just-noticeable reference for one flat colour; this signature is the
        // RMS of eleven UI roles. The current catalogue's nearest unrelated
        // pair is 0.03081, so 0.028 keeps a regression margin without making
        // harmless colour rounding fail the build. Treatments intentionally
        // share a family identity: their observed floor is 0.01561, hence the
        // separate, slightly more permissive 0.014 guard.
        const val MIN_UNRELATED_THEME_OKLAB_RMS = 0.028
        const val MIN_RELATED_TREATMENT_OKLAB_RMS = 0.014

        val TREATMENT_SUFFIXES = setOf("original", "depth", "velvet", "glow", "dusk")
    }
}
