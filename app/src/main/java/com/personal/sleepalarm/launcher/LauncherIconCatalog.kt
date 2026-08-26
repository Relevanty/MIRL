package com.personal.sleepalarm.launcher

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.personal.sleepalarm.R

enum class LauncherIconFilter(@StringRes val titleRes: Int) {
    ALL(R.string.launcher_icon_filter_all),
    DARK(R.string.launcher_icon_filter_dark),
    LIGHT(R.string.launcher_icon_filter_light),
    NEON(R.string.launcher_icon_filter_neon),
    RETRO(R.string.launcher_icon_filter_retro),
    NATURE(R.string.launcher_icon_filter_nature),
    GEOMETRIC(R.string.launcher_icon_filter_geometric),
    COLORFUL(R.string.launcher_icon_filter_colorful)
}

data class LauncherIconSpec(
    val id: String,
    val aliasClassName: String,
    @StringRes val nameRes: Int,
    @DrawableRes val previewRes: Int,
    val filters: Set<LauncherIconFilter>,
    val enabledByDefault: Boolean = false
)

object LauncherIconCatalog {

    const val DEFAULT_ID = "standard"
    private const val ALIAS_PACKAGE = "com.personal.sleepalarm.launcher"
    private val DARK = LauncherIconFilter.DARK
    private val LIGHT = LauncherIconFilter.LIGHT
    private val NEON = LauncherIconFilter.NEON
    private val RETRO = LauncherIconFilter.RETRO
    private val NATURE = LauncherIconFilter.NATURE
    private val GEOMETRIC = LauncherIconFilter.GEOMETRIC
    private val COLORFUL = LauncherIconFilter.COLORFUL

    val all: List<LauncherIconSpec> = listOf(
        icon("standard", "StandardIconAlias", R.string.launcher_icon_standard, R.drawable.ic_launcher_preview_standard, DARK, default = true),
        icon("classic", "ClassicIconAlias", R.string.launcher_icon_classic, R.drawable.ic_launcher_preview_classic, DARK),
        icon("amoled", "AmoledIconAlias", R.string.launcher_icon_amoled, R.drawable.ic_launcher_preview_amoled, DARK),
        icon("monochrome", "MonochromeIconAlias", R.string.launcher_icon_monochrome, R.drawable.ic_launcher_preview_monochrome, DARK, LIGHT),
        icon("graphite", "GraphiteIconAlias", R.string.launcher_icon_graphite, R.drawable.ic_launcher_preview_graphite, LIGHT),
        icon("cyberpunk", "CyberpunkIconAlias", R.string.launcher_icon_cyberpunk, R.drawable.ic_launcher_preview_cyberpunk, DARK, NEON),
        icon("neon_city", "NeonCityIconAlias", R.string.launcher_icon_neon_city, R.drawable.ic_launcher_preview_neon_city, DARK, NEON),
        icon("synthwave", "SynthwaveIconAlias", R.string.launcher_icon_synthwave, R.drawable.ic_launcher_preview_synthwave, DARK, NEON, RETRO),
        icon("vaporwave", "VaporwaveIconAlias", R.string.launcher_icon_vaporwave, R.drawable.ic_launcher_preview_vaporwave, LIGHT, NEON, RETRO),
        icon("dieselpunk", "DieselpunkIconAlias", R.string.launcher_icon_dieselpunk, R.drawable.ic_launcher_preview_dieselpunk, DARK, RETRO),
        icon("brass", "BrassIconAlias", R.string.launcher_icon_brass, R.drawable.ic_launcher_preview_brass, DARK, RETRO),
        icon("post_punk", "PostPunkIconAlias", R.string.launcher_icon_post_punk, R.drawable.ic_launcher_preview_post_punk, LIGHT, RETRO),
        icon("noir", "NoirIconAlias", R.string.launcher_icon_noir, R.drawable.ic_launcher_preview_noir, DARK, RETRO),
        icon("forest", "ForestIconAlias", R.string.launcher_icon_forest, R.drawable.ic_launcher_preview_forest, DARK, NATURE),
        icon("aurora", "AuroraIconAlias", R.string.launcher_icon_aurora, R.drawable.ic_launcher_preview_aurora, DARK, NEON, NATURE),
        icon("ocean", "OceanIconAlias", R.string.launcher_icon_ocean, R.drawable.ic_launcher_preview_ocean, DARK, NATURE),
        icon("sunset", "SunsetIconAlias", R.string.launcher_icon_sunset, R.drawable.ic_launcher_preview_sunset, LIGHT, NATURE),
        icon("lavender", "LavenderIconAlias", R.string.launcher_icon_lavender, R.drawable.ic_launcher_preview_lavender, LIGHT, NATURE),
        icon("paper_retro", "PaperRetroIconAlias", R.string.launcher_icon_paper_retro, R.drawable.ic_launcher_preview_paper_retro, LIGHT, RETRO, NATURE),
        icon("nebula", "NebulaIconAlias", R.string.launcher_icon_nebula, R.drawable.ic_launcher_preview_nebula, DARK, NEON),
        icon("matrix_terminal", "MatrixTerminalIconAlias", R.string.launcher_icon_matrix_terminal, R.drawable.ic_launcher_preview_matrix_terminal, DARK, NEON, RETRO),
        icon("amethyst_facet", "AmethystFacetIconAlias", R.string.launcher_icon_amethyst_facet, R.drawable.ic_launcher_preview_amethyst_facet, DARK, NEON, GEOMETRIC, COLORFUL),
        icon("obsidian_facet", "ObsidianFacetIconAlias", R.string.launcher_icon_obsidian_facet, R.drawable.ic_launcher_preview_obsidian_facet, DARK, GEOMETRIC),
        icon("sapphire_prism", "SapphirePrismIconAlias", R.string.launcher_icon_sapphire_prism, R.drawable.ic_launcher_preview_sapphire_prism, DARK, NEON, GEOMETRIC, COLORFUL),
        icon("emerald_facet", "EmeraldFacetIconAlias", R.string.launcher_icon_emerald_facet, R.drawable.ic_launcher_preview_emerald_facet, DARK, NEON, GEOMETRIC, COLORFUL),
        icon("coral_polygon", "CoralPolygonIconAlias", R.string.launcher_icon_coral_polygon, R.drawable.ic_launcher_preview_coral_polygon, LIGHT, GEOMETRIC, COLORFUL),
        icon("holographic_glass", "HolographicGlassIconAlias", R.string.launcher_icon_holographic_glass, R.drawable.ic_launcher_preview_holographic_glass, LIGHT, GEOMETRIC, COLORFUL),
        icon("kawaii_pink", "KawaiiPinkIconAlias", R.string.launcher_icon_kawaii_pink, R.drawable.ic_launcher_preview_kawaii_pink, LIGHT, COLORFUL),
        icon("pixel_cozy", "PixelCozyIconAlias", R.string.launcher_icon_pixel_cozy, R.drawable.ic_launcher_preview_pixel_cozy, DARK, RETRO, COLORFUL),
        icon("constructivist", "ConstructivistIconAlias", R.string.launcher_icon_constructivist, R.drawable.ic_launcher_preview_constructivist, LIGHT, RETRO, GEOMETRIC, COLORFUL),
        icon("comic_pop", "ComicPopIconAlias", R.string.launcher_icon_comic_pop, R.drawable.ic_launcher_preview_comic_pop, LIGHT, RETRO, COLORFUL),
        icon("cyan_grunge", "CyanGrungeIconAlias", R.string.launcher_icon_cyan_grunge, R.drawable.ic_launcher_preview_cyan_grunge, LIGHT, RETRO, COLORFUL),
        icon("yellow_halftone", "YellowHalftoneIconAlias", R.string.launcher_icon_yellow_halftone, R.drawable.ic_launcher_preview_yellow_halftone, LIGHT, RETRO, GEOMETRIC, COLORFUL),
        icon("ice_engraved", "IceEngravedIconAlias", R.string.launcher_icon_ice_engraved, R.drawable.ic_launcher_preview_ice_engraved, LIGHT, NATURE, GEOMETRIC),
        icon("celestial_gold", "CelestialGoldIconAlias", R.string.launcher_icon_celestial_gold, R.drawable.ic_launcher_preview_celestial_gold, DARK, RETRO),
        icon("electric_blue", "ElectricBlueIconAlias", R.string.launcher_icon_electric_blue, R.drawable.ic_launcher_preview_electric_blue, DARK, NEON, COLORFUL),
        icon("ultraviolet_plasma", "UltravioletPlasmaIconAlias", R.string.launcher_icon_ultraviolet_plasma, R.drawable.ic_launcher_preview_ultraviolet_plasma, DARK, NEON, COLORFUL),
        icon("rainbow_prism", "RainbowPrismIconAlias", R.string.launcher_icon_rainbow_prism, R.drawable.ic_launcher_preview_rainbow_prism, DARK, NEON, GEOMETRIC, COLORFUL),
        icon("gold_wireframe", "GoldWireframeIconAlias", R.string.launcher_icon_gold_wireframe, R.drawable.ic_launcher_preview_gold_wireframe, DARK, RETRO, GEOMETRIC),
        icon("porcelain_minimal", "PorcelainMinimalIconAlias", R.string.launcher_icon_porcelain_minimal, R.drawable.ic_launcher_preview_porcelain_minimal, LIGHT, GEOMETRIC),
        icon("stained_glass", "StainedGlassIconAlias", R.string.launcher_icon_stained_glass, R.drawable.ic_launcher_preview_stained_glass, DARK, GEOMETRIC, COLORFUL),
        icon("ceramic_mosaic", "CeramicMosaicIconAlias", R.string.launcher_icon_ceramic_mosaic, R.drawable.ic_launcher_preview_ceramic_mosaic, LIGHT, GEOMETRIC, COLORFUL),
        icon("cloisonne_enamel", "CloisonneEnamelIconAlias", R.string.launcher_icon_cloisonne_enamel, R.drawable.ic_launcher_preview_cloisonne_enamel, DARK, RETRO, COLORFUL),
        icon("terrazzo", "TerrazzoIconAlias", R.string.launcher_icon_terrazzo, R.drawable.ic_launcher_preview_terrazzo, LIGHT, GEOMETRIC, COLORFUL),
        icon("carved_wood", "CarvedWoodIconAlias", R.string.launcher_icon_carved_wood, R.drawable.ic_launcher_preview_carved_wood, DARK, RETRO, NATURE),
        icon("wood_marquetry", "WoodMarquetryIconAlias", R.string.launcher_icon_wood_marquetry, R.drawable.ic_launcher_preview_wood_marquetry, DARK, RETRO, NATURE, GEOMETRIC),
        icon("satin_embroidery", "SatinEmbroideryIconAlias", R.string.launcher_icon_satin_embroidery, R.drawable.ic_launcher_preview_satin_embroidery, DARK, RETRO, COLORFUL),
        icon("knitted_wool", "KnittedWoolIconAlias", R.string.launcher_icon_knitted_wool, R.drawable.ic_launcher_preview_knitted_wool, DARK, RETRO),
        icon("felt_applique", "FeltAppliqueIconAlias", R.string.launcher_icon_felt_applique, R.drawable.ic_launcher_preview_felt_applique, DARK, NATURE, COLORFUL),
        icon("claymation", "ClaymationIconAlias", R.string.launcher_icon_claymation, R.drawable.ic_launcher_preview_claymation, LIGHT, COLORFUL),
        icon("linocut", "LinocutIconAlias", R.string.launcher_icon_linocut, R.drawable.ic_launcher_preview_linocut, LIGHT, RETRO),
        icon("risograph", "RisographIconAlias", R.string.launcher_icon_risograph, R.drawable.ic_launcher_preview_risograph, LIGHT, RETRO, COLORFUL),
        icon("cyanotype", "CyanotypeIconAlias", R.string.launcher_icon_cyanotype, R.drawable.ic_launcher_preview_cyanotype, DARK, RETRO),
        icon("copper_engraving", "CopperEngravingIconAlias", R.string.launcher_icon_copper_engraving, R.drawable.ic_launcher_preview_copper_engraving, DARK, RETRO),
        icon("scratchboard", "ScratchboardIconAlias", R.string.launcher_icon_scratchboard, R.drawable.ic_launcher_preview_scratchboard, DARK, RETRO),
        icon("watercolor_wash", "WatercolorWashIconAlias", R.string.launcher_icon_watercolor_wash, R.drawable.ic_launcher_preview_watercolor_wash, LIGHT, NATURE, COLORFUL),
        icon("gouache", "GouacheIconAlias", R.string.launcher_icon_gouache, R.drawable.ic_launcher_preview_gouache, LIGHT, NATURE, COLORFUL),
        icon("dry_pastel", "DryPastelIconAlias", R.string.launcher_icon_dry_pastel, R.drawable.ic_launcher_preview_dry_pastel, DARK, COLORFUL),
        icon("charcoal_drawing", "CharcoalDrawingIconAlias", R.string.launcher_icon_charcoal_drawing, R.drawable.ic_launcher_preview_charcoal_drawing, LIGHT, RETRO),
        icon("sumi_e", "SumiEIconAlias", R.string.launcher_icon_sumi_e, R.drawable.ic_launcher_preview_sumi_e, LIGHT, RETRO),
        icon("art_nouveau", "ArtNouveauIconAlias", R.string.launcher_icon_art_nouveau, R.drawable.ic_launcher_preview_art_nouveau, DARK, RETRO, NATURE, COLORFUL),
        icon("art_deco", "ArtDecoIconAlias", R.string.launcher_icon_art_deco, R.drawable.ic_launcher_preview_art_deco, DARK, RETRO, GEOMETRIC),
        icon("ukiyo_e", "UkiyoEIconAlias", R.string.launcher_icon_ukiyo_e, R.drawable.ic_launcher_preview_ukiyo_e, LIGHT, RETRO, NATURE),
        icon("lubok", "LubokIconAlias", R.string.launcher_icon_lubok, R.drawable.ic_launcher_preview_lubok, LIGHT, RETRO, NATURE, COLORFUL),
        icon("medieval_miniature", "MedievalMiniatureIconAlias", R.string.launcher_icon_medieval_miniature, R.drawable.ic_launcher_preview_medieval_miniature, DARK, RETRO, COLORFUL),
        icon("rococo", "RococoIconAlias", R.string.launcher_icon_rococo, R.drawable.ic_launcher_preview_rococo, LIGHT, RETRO),
        icon("impressionist", "ImpressionistIconAlias", R.string.launcher_icon_impressionist, R.drawable.ic_launcher_preview_impressionist, DARK, NATURE, COLORFUL),
        icon("pointillist", "PointillistIconAlias", R.string.launcher_icon_pointillist, R.drawable.ic_launcher_preview_pointillist, DARK, COLORFUL),
        icon("cubist", "CubistIconAlias", R.string.launcher_icon_cubist, R.drawable.ic_launcher_preview_cubist, LIGHT, GEOMETRIC),
        icon("surrealist", "SurrealistIconAlias", R.string.launcher_icon_surrealist, R.drawable.ic_launcher_preview_surrealist, LIGHT, NATURE, COLORFUL),
        icon("solarpunk_greenhouse", "SolarpunkGreenhouseIconAlias", R.string.launcher_icon_solarpunk_greenhouse, R.drawable.ic_launcher_preview_solarpunk_greenhouse, LIGHT, NATURE),
        icon("bioluminescent_mushrooms", "BioluminescentMushroomsIconAlias", R.string.launcher_icon_bioluminescent_mushrooms, R.drawable.ic_launcher_preview_bioluminescent_mushrooms, DARK, NEON, NATURE),
        icon("snow_globe", "SnowGlobeIconAlias", R.string.launcher_icon_snow_globe, R.drawable.ic_launcher_preview_snow_globe, DARK, NATURE),
        icon("adobe_desert", "AdobeDesertIconAlias", R.string.launcher_icon_adobe_desert, R.drawable.ic_launcher_preview_adobe_desert, LIGHT, NATURE),
        icon("volcanic_basalt", "VolcanicBasaltIconAlias", R.string.launcher_icon_volcanic_basalt, R.drawable.ic_launcher_preview_volcanic_basalt, DARK, NATURE),
        icon("autumn_collage", "AutumnCollageIconAlias", R.string.launcher_icon_autumn_collage, R.drawable.ic_launcher_preview_autumn_collage, DARK, NATURE, COLORFUL),
        icon("underwater_reef", "UnderwaterReefIconAlias", R.string.launcher_icon_underwater_reef, R.drawable.ic_launcher_preview_underwater_reef, DARK, NATURE, COLORFUL),
        icon("floating_islands", "FloatingIslandsIconAlias", R.string.launcher_icon_floating_islands, R.drawable.ic_launcher_preview_floating_islands, LIGHT, NATURE, COLORFUL),
        icon("cloud_kingdom", "CloudKingdomIconAlias", R.string.launcher_icon_cloud_kingdom, R.drawable.ic_launcher_preview_cloud_kingdom, LIGHT, NATURE),
        icon("bonsai_garden", "BonsaiGardenIconAlias", R.string.launcher_icon_bonsai_garden, R.drawable.ic_launcher_preview_bonsai_garden, DARK, NATURE),
        icon("architectural_blueprint", "ArchitecturalBlueprintIconAlias", R.string.launcher_icon_architectural_blueprint, R.drawable.ic_launcher_preview_architectural_blueprint, DARK, RETRO, GEOMETRIC),
        icon("xray_house", "XrayHouseIconAlias", R.string.launcher_icon_xray_house, R.drawable.ic_launcher_preview_xray_house, DARK, NEON, GEOMETRIC),
        icon("thermal_vision", "ThermalVisionIconAlias", R.string.launcher_icon_thermal_vision, R.drawable.ic_launcher_preview_thermal_vision, DARK, NEON, COLORFUL),
        icon("crt_screen", "CrtScreenIconAlias", R.string.launcher_icon_crt_screen, R.drawable.ic_launcher_preview_crt_screen, DARK, NEON, RETRO),
        icon("digital_glitch", "DigitalGlitchIconAlias", R.string.launcher_icon_digital_glitch, R.drawable.ic_launcher_preview_digital_glitch, DARK, NEON, COLORFUL),
        icon("voxel_isometric", "VoxelIsometricIconAlias", R.string.launcher_icon_voxel_isometric, R.drawable.ic_launcher_preview_voxel_isometric, LIGHT, RETRO, GEOMETRIC, COLORFUL),
        icon("liquid_chrome", "LiquidChromeIconAlias", R.string.launcher_icon_liquid_chrome, R.drawable.ic_launcher_preview_liquid_chrome, DARK, NEON, GEOMETRIC),
        icon("engineering_exploded", "EngineeringExplodedIconAlias", R.string.launcher_icon_engineering_exploded, R.drawable.ic_launcher_preview_engineering_exploded, DARK, GEOMETRIC),
        icon("retro_space_poster", "RetroSpacePosterIconAlias", R.string.launcher_icon_retro_space_poster, R.drawable.ic_launcher_preview_retro_space_poster, DARK, RETRO, NATURE),
        icon("quantum_field", "QuantumFieldIconAlias", R.string.launcher_icon_quantum_field, R.drawable.ic_launcher_preview_quantum_field, DARK, NEON, COLORFUL),
        icon("crayon_drawing", "CrayonDrawingIconAlias", R.string.launcher_icon_crayon_drawing, R.drawable.ic_launcher_preview_crayon_drawing, LIGHT, COLORFUL),
        icon("chalkboard", "ChalkboardIconAlias", R.string.launcher_icon_chalkboard, R.drawable.ic_launcher_preview_chalkboard, DARK, RETRO),
        icon("sticker_pack", "StickerPackIconAlias", R.string.launcher_icon_sticker_pack, R.drawable.ic_launcher_preview_sticker_pack, DARK, COLORFUL),
        icon("toy_blocks", "ToyBlocksIconAlias", R.string.launcher_icon_toy_blocks, R.drawable.ic_launcher_preview_toy_blocks, LIGHT, GEOMETRIC, COLORFUL),
        icon("gingerbread", "GingerbreadIconAlias", R.string.launcher_icon_gingerbread, R.drawable.ic_launcher_preview_gingerbread, DARK, RETRO),
        icon("gummy_glass", "GummyGlassIconAlias", R.string.launcher_icon_gummy_glass, R.drawable.ic_launcher_preview_gummy_glass, DARK, NEON, COLORFUL),
        icon("inflatable_sculpture", "InflatableSculptureIconAlias", R.string.launcher_icon_inflatable_sculpture, R.drawable.ic_launcher_preview_inflatable_sculpture, LIGHT, GEOMETRIC, COLORFUL),
        icon("plush_toy", "PlushToyIconAlias", R.string.launcher_icon_plush_toy, R.drawable.ic_launcher_preview_plush_toy, LIGHT, COLORFUL),
        icon("shadow_theatre", "ShadowTheatreIconAlias", R.string.launcher_icon_shadow_theatre, R.drawable.ic_launcher_preview_shadow_theatre, DARK, RETRO),
        icon("paper_diorama", "PaperDioramaIconAlias", R.string.launcher_icon_paper_diorama, R.drawable.ic_launcher_preview_paper_diorama, LIGHT, RETRO, NATURE)
    )

    val ids: Set<String> = all.mapTo(linkedSetOf(), LauncherIconSpec::id)

    fun byId(id: String?): LauncherIconSpec =
        all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT_ID }

    fun isValid(id: String?): Boolean = id in ids

    /** Maps broad theme families to one of the curated icons, not one icon per preset. */
    fun forTheme(themeId: String, isDark: Boolean): LauncherIconSpec {
        val id = themeId.lowercase()
        val matched = when {
            id == "amoled" || id == "black_hole" -> "amoled"
            id in setOf("matrix", "phosphor", "amber_terminal", "hacker", "netrunner") -> "matrix_terminal"
            id.contains("pixel") || id.contains("8bit") || id.contains("16bit") -> "pixel_cozy"
            id.contains("kawaii") || id.contains("candy") || id.contains("bubblegum") -> "kawaii_pink"
            id.contains("hologram") || id.contains("iridescent") || id.contains("opal") -> "holographic_glass"
            id == "chrome" -> "liquid_chrome"
            id.contains("rainbow") || id.contains("prism") -> "rainbow_prism"
            id.contains("comic") || id.contains("pop_art") -> "comic_pop"
            id.contains("construct") || id == "bauhaus" -> "constructivist"
            id.contains("wireframe") || id.contains("gold_line") -> "gold_wireframe"
            id.contains("ice") || id.contains("frost") || id.contains("glacier") -> "ice_engraved"
            id.contains("amethyst") || id.contains("crystal") -> "amethyst_facet"
            id.contains("obsidian") -> "obsidian_facet"
            id in setOf("synthwave", "retro_sun") -> "synthwave"
            id == "vaporwave" -> "vaporwave"
            id.contains("neon") || id.contains("cyber") || id.contains("grid") || id == "night_city" -> "neon_city"
            id.contains("diesel") || id in setOf("rust", "war_room", "factory") -> "dieselpunk"
            id.contains("brass") || id == "gold" -> "brass"
            id in setOf("cold_wave", "gothic", "mono") -> "post_punk"
            id.contains("noir") || id == "moon" || id == "moonlight" -> "noir"
            id.contains("graphite") || id in setOf("smoke", "storm") -> "graphite"
            id.contains("aurora") || id.contains("northern_lights") -> "aurora"
            id in setOf("autumn", "autumn_night") -> "autumn_collage"
            id == "volcano" -> "volcanic_basalt"
            id.contains("desert") || id == "sand" -> "adobe_desert"
            id == "coral_reef" -> "underwater_reef"
            id in setOf("cloud", "cotton") -> "cloud_kingdom"
            id == "snow" -> "snow_globe"
            id == "rose_garden" -> "art_nouveau"
            id.contains("forest") || id.contains("pine") || id in setOf("moss", "meadow", "birch", "sage", "alpine") -> "forest"
            id.contains("ocean") || id.contains("sea") || id.contains("coral") || id.contains("teal") -> "ocean"
            id.contains("sunset") || id.contains("sunrise") || id in setOf("ember", "solar_flare", "terracotta", "peach") -> "sunset"
            id.contains("lavender") || id.contains("lilac") || id.contains("orchid") || id.contains("plum") -> "lavender"
            id.contains("space") || id.contains("nebula") || id in setOf("andromeda", "starfield", "comet", "starlight") -> "nebula"
            id.contains("paper") || id in setOf("parchment", "sepia", "old_book", "cream", "ivory", "linen", "ecru", "latte") -> "paper_retro"
            else -> if (isDark) "classic" else "paper_retro"
        }
        return byId(matched)
    }

    private fun icon(
        id: String,
        alias: String,
        @StringRes nameRes: Int,
        @DrawableRes previewRes: Int,
        vararg filters: LauncherIconFilter,
        default: Boolean = false
    ) = LauncherIconSpec(
        id = id,
        aliasClassName = "$ALIAS_PACKAGE.$alias",
        nameRes = nameRes,
        previewRes = previewRes,
        filters = filters.toSet(),
        enabledByDefault = default
    )

}
