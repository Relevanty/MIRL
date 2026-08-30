package com.personal.sleepalarm.ui.theme

import com.personal.sleepalarm.R

/**
 * Каталог готовых тем: 200 тёмных и 55 светлых пресетов.
 *
 * Категории:
 * - Базовые (14): night, ocean, sunset, forest, lavender, rose, graphite, amoled,
 *                  coffee, mint, gold, day, paper, ice
 * - Киберпанк (7), Дизельпанк (5), Пост-панк (5)
 * - Природа тёмная (10) / светлая (12)
 * - Linux тёмный (12) / светлый (5)
 * - Космос тёмный (9) / светлый (5)
 * - Ретро-терминалы (4)
 * - Бумага/ретро светлая (7), Пастель (6), Стильные (12+7)
 */
object ThemeCatalog {

    const val DEFAULT_ID = "night"

    private val legacy: List<ThemePreset> = listOf(

        // ============================================================
        // БАЗОВЫЕ (оригинальные 14)
        // ============================================================
        ThemePreset("night",     R.string.theme_night,     true,  0xFF0B1026, 0xFF141A35, 0xFFFFB86B, 0xFF63D8C2, 0xFFE8EAF6),
        ThemePreset("ocean",     R.string.theme_ocean,     true,  0xFF041A22, 0xFF0A2A35, 0xFF4DD0E1, 0xFF80DEEA, 0xFFE0F7FA),
        ThemePreset("sunset",    R.string.theme_sunset,    true,  0xFF1A0D0A, 0xFF2E1712, 0xFFFF7657, 0xFF64D8CB, 0xFFFFF0EA),
        ThemePreset("forest",    R.string.theme_forest,    true,  0xFF0A1A10, 0xFF12291A, 0xFF66BB6A, 0xFFA5D6A7, 0xFFE8F5E9),
        ThemePreset("lavender",  R.string.theme_lavender,  true,  0xFF150F26, 0xFF201736, 0xFFB39DDB, 0xFF9575CD, 0xFFEDE7F6),
        ThemePreset("rose",      R.string.theme_rose,      true,  0xFF1F0A12, 0xFF2E1220, 0xFFF48FB1, 0xFFF06292, 0xFFFCE4EC),
        ThemePreset("graphite",  R.string.theme_graphite,  true,  0xFF111315, 0xFF202428, 0xFFB7C3CC, 0xFF73B5A6, 0xFFF0F4F7),
        ThemePreset("amoled",    R.string.theme_amoled,    true,  0xFF000000, 0xFF0A0A0A, 0xFF00E5FF, 0xFF18FFFF, 0xFFFFFFFF),
        ThemePreset("coffee",    R.string.theme_coffee,    true,  0xFF1A120B, 0xFF2A1E14, 0xFFBCAAA4, 0xFFA1887F, 0xFFEFEBE9),
        ThemePreset("mint",      R.string.theme_mint,      true,  0xFF06211C, 0xFF0B312B, 0xFF64FFDA, 0xFF1DE9B6, 0xFFE0F2F1),
        ThemePreset("gold",      R.string.theme_gold,      true,  0xFF1A1405, 0xFF2A220C, 0xFFFFD54F, 0xFFFFC107, 0xFFFFF8E1),
        ThemePreset("day",       R.string.theme_day,       false, 0xFFFAFAFA, 0xFFFFFFFF, 0xFF1976D2, 0xFF42A5F5, 0xFF212121),
        ThemePreset("paper",     R.string.theme_paper,     false, 0xFFFFF8F0, 0xFFFFFFFF, 0xFF8D6E63, 0xFFA1887F, 0xFF3E2723),
        ThemePreset("ice",       R.string.theme_ice,       false, 0xFFF0F8FF, 0xFFFFFFFF, 0xFF0288D1, 0xFF4FC3F7, 0xFF01579B),

        // ============================================================
        // КИБERPАНК (тёмные)
        // ============================================================
        ThemePreset("night_city",    R.string.theme_night_city,    true,  0xFF08031A, 0xFF170C30, 0xFFFF4FE1, 0xFF37E7FF, 0xFFFFF0FF),
        ThemePreset("netrunner",     R.string.theme_netrunner,     true,  0xFF0A1A0F, 0xFF1A2A1F, 0xFF00FF41, 0xFF00B8FF, 0xFFE0FFE0),
        ThemePreset("synthwave",     R.string.theme_synthwave,     true,  0xFF1A0A2E, 0xFF32154A, 0xFFFF4FD8, 0xFF8B7CFF, 0xFFFFF1FB),
        ThemePreset("vaporwave",     R.string.theme_vaporwave,     true,  0xFF1A0F2E, 0xFF2A1F40, 0xFFFF71CE, 0xFF01CDFE, 0xFFFFE8FF),
        ThemePreset("chrome",        R.string.theme_chrome,        true,  0xFF0A0A0F, 0xFF1A1A22, 0xFFC0C0C0, 0xFFFF0080, 0xFFEAEAEA),
        ThemePreset("neon_rain",     R.string.theme_neon_rain,     true,  0xFF050A1A, 0xFF0A1020, 0xFF00FF9F, 0xFFFF0080, 0xFFE0FFFF),
        ThemePreset("acid_grid",     R.string.theme_acid_grid,     true,  0xFF0A0F0A, 0xFF1A201A, 0xFF39FF14, 0xFFB026FF, 0xFFE0FFE0),

        // ============================================================
        // ДИЗЕЛЬПАНК (тёмные)
        // ============================================================
        ThemePreset("diesel",        R.string.theme_diesel,        true,  0xFF0F0A05, 0xFF1F1A0F, 0xFFCD7F32, 0xFFB87333, 0xFFF0E0C0),
        ThemePreset("rust",          R.string.theme_rust,          true,  0xFF1A0E05, 0xFF2A1E0F, 0xFFA0522D, 0xFFCD853F, 0xFFF5E0C0),
        ThemePreset("brass",         R.string.theme_brass,         true,  0xFF140F0A, 0xFF241F14, 0xFFB5A642, 0xFFE6BE8A, 0xFFF0E5C0),
        ThemePreset("smoke",         R.string.theme_smoke,         true,  0xFF11100F, 0xFF24211F, 0xFFB7AFA7, 0xFFC18A62, 0xFFF2ECE7),
        ThemePreset("war_room",      R.string.theme_war_room,      true,  0xFF0A1A0F, 0xFF1A2A1F, 0xFF556B2F, 0xFF8B7355, 0xFFD0E0C0),

        // ============================================================
        // ПОСТ-ПАНК (тёмные)
        // ============================================================
        ThemePreset("cold_wave",     R.string.theme_cold_wave,     true,  0xFF080D17, 0xFF17202D, 0xFFB7C4D4, 0xFF79A7C6, 0xFFEEF5FF),
        ThemePreset("gothic",        R.string.theme_gothic,        true,  0xFF0A0505, 0xFF1A1010, 0xFF8B0000, 0xFFC0C0C0, 0xFFF0E0E0),
        ThemePreset("bauhaus",       R.string.theme_bauhaus,       true,  0xFF0A0A0A, 0xFF1A1A1A, 0xFFFF0000, 0xFFFFD700, 0xFFE0E0E0),
        ThemePreset("mono",          R.string.theme_mono,          true,  0xFF050505, 0xFF151515, 0xFFF5F5F5, 0xFFA7B0BA, 0xFFFAFAFA),
        ThemePreset("factory",       R.string.theme_factory,       true,  0xFF121212, 0xFF222222, 0xFFC0C0C0, 0xFF808080, 0xFFE0E0E0),

        // ============================================================
        // ПРИРОДА (тёмные)
        // ============================================================
        ThemePreset("deep_forest",   R.string.theme_deep_forest,   true,  0xFF0A1A10, 0xFF122818, 0xFF2E8B57, 0xFF90EE90, 0xFFD0F0D0),
        ThemePreset("swamp",         R.string.theme_swamp,         true,  0xFF101408, 0xFF202410, 0xFF6B8E23, 0xFF808000, 0xFFE0E5C0),
        ThemePreset("autumn_night",  R.string.theme_autumn_night,  true,  0xFF1A0E05, 0xFF2A1E0F, 0xFFD2691E, 0xFFFF8C00, 0xFFF0E0C0),
        ThemePreset("winter_night",  R.string.theme_winter_night,  true,  0xFF0A1020, 0xFF1A2030, 0xFFB0C4DE, 0xFFE0E6F0, 0xFFE8F0FF),
        ThemePreset("aurora",        R.string.theme_aurora,        true,  0xFF050A20, 0xFF101830, 0xFF00FF88, 0xFF00BFFF, 0xFFE0FFF0),
        ThemePreset("desert_night",  R.string.theme_desert_night,  true,  0xFF1A1005, 0xFF2A1E10, 0xFFF4A460, 0xFFCD853F, 0xFFF5E8D0),
        ThemePreset("volcano",       R.string.theme_volcano,       true,  0xFF1A0A05, 0xFF2A1A0F, 0xFFFF4500, 0xFFFF8C00, 0xFFFFE0C0),
        ThemePreset("midnight_garden", R.string.theme_midnight_garden, true, 0xFF0A0F1A, 0xFF1A1F2A, 0xFF4682B4, 0xFF9370DB, 0xFFE0E8FF),
        ThemePreset("moss",          R.string.theme_moss,          true,  0xFF0F1A0A, 0xFF1F2A1A, 0xFF556B2F, 0xFF9ACD32, 0xFFE0F0D0),
        ThemePreset("coral_reef",    R.string.theme_coral_reef,    true,  0xFF0A1A20, 0xFF1A2A30, 0xFFFF6347, 0xFF20B2AA, 0xFFE0FFF0),

        // ============================================================
        // LINUX (тёмные)
        // ============================================================
        ThemePreset("ubuntu",        R.string.theme_ubuntu,        true,  0xFF1A1005, 0xFF2A1E0F, 0xFFE95420, 0xFF77216F, 0xFFF0E0D0),
        ThemePreset("debian",        R.string.theme_debian,        true,  0xFF1A0505, 0xFF2A1010, 0xFFA80030, 0xFFD70A53, 0xFFF5E0E0),
        ThemePreset("arch",          R.string.theme_arch,          true,  0xFF0A1020, 0xFF1A2030, 0xFF1793D1, 0xFF08385A, 0xFFE0F0FF),
        ThemePreset("fedora",        R.string.theme_fedora,        true,  0xFF0A1020, 0xFF1A2030, 0xFF3C6EB4, 0xFF294172, 0xFFE0F0FF),
        ThemePreset("mint_os",       R.string.theme_mint_os,       true,  0xFF091A12, 0xFF172A20, 0xFF9AD94A, 0xFF49B08A, 0xFFECFFE8),
        ThemePreset("manjaro",       R.string.theme_manjaro,       true,  0xFF07140D, 0xFF12271B, 0xFF35BF5C, 0xFF60D394, 0xFFE7FFED),
        ThemePreset("kali",          R.string.theme_kali,          true,  0xFF0A0A1A, 0xFF1A1A2A, 0xFF367BF0, 0xFFF22F46, 0xFFE0E8FF),
        ThemePreset("gentoo",        R.string.theme_gentoo,        true,  0xFF1A0A20, 0xFF2A1A30, 0xFF54487A, 0xFF61538D, 0xFFE8E0FF),
        ThemePreset("nixos",         R.string.theme_nixos,         true,  0xFF0A1020, 0xFF1A2030, 0xFF5277C3, 0xFF7EBAE4, 0xFFE0F0FF),
        ThemePreset("opensuse",      R.string.theme_opensuse,      true,  0xFF0A1A10, 0xFF1A2A20, 0xFF73BA25, 0xFF173C4F, 0xFFE0FFE0),
        ThemePreset("pop_os",        R.string.theme_pop_os,        true,  0xFF1A0A05, 0xFF2A1A0F, 0xFFFA9E43, 0xFF48B9C7, 0xFFF5E8D0),
        ThemePreset("elementary",    R.string.theme_elementary,    true,  0xFF0A1020, 0xFF1A2030, 0xFF3689E6, 0xFF64B4F4, 0xFFE0F0FF),

        // ============================================================
        // КОСМОС (тёмные)
        // ============================================================
        ThemePreset("deep_space",    R.string.theme_deep_space,    true,  0xFF000010, 0xFF0A0A20, 0xFF6A5ACD, 0xFF9370DB, 0xFFE0E8FF),
        ThemePreset("nebula",        R.string.theme_nebula,        true,  0xFF0B0524, 0xFF21113B, 0xFFEE5BB7, 0xFF4FD8D5, 0xFFFFF0FB),
        ThemePreset("mars",          R.string.theme_mars,          true,  0xFF1A0505, 0xFF2A1010, 0xFFCD5C5C, 0xFFF4A460, 0xFFF5E0D0),
        ThemePreset("moon",          R.string.theme_moon,          true,  0xFF090A13, 0xFF1B1C2A, 0xFFD2D6DE, 0xFFB7A0D8, 0xFFF3F6FC),
        ThemePreset("andromeda",     R.string.theme_andromeda,     true,  0xFF100A1A, 0xFF201A2A, 0xFF9370DB, 0xFF8A2BE2, 0xFFF0E8FF),
        ThemePreset("black_hole",    R.string.theme_black_hole,    true,  0xFF000000, 0xFF090B10, 0xFFFF4D8D, 0xFFA3FF12, 0xFFFFFFFF),
        ThemePreset("starfield",     R.string.theme_starfield,     true,  0xFF05050F, 0xFF101020, 0xFFFFD700, 0xFFFFFFFF, 0xFFF0F0FF),
        ThemePreset("comet",         R.string.theme_comet,         true,  0xFF0A0A1A, 0xFF1A1A2A, 0xFF87CEEB, 0xFFFFFFFF, 0xFFE8F0FF),
        ThemePreset("solar_flare",   R.string.theme_solar_flare,   true,  0xFF1A0A00, 0xFF2A1A0A, 0xFFFF4500, 0xFFFFD700, 0xFFFFE8C0),

        // ============================================================
        // РЕТРО-ТЕРМИНАЛЫ (тёмные)
        // ============================================================
        ThemePreset("matrix",        R.string.theme_matrix,        true,  0xFF000A00, 0xFF0A1A0A, 0xFF00FF00, 0xFF008F00, 0xFF00FF00),
        ThemePreset("phosphor",      R.string.theme_phosphor,      true,  0xFF1A0F00, 0xFF2A1F0A, 0xFFFFA500, 0xFFFF8C00, 0xFFFFA500),
        ThemePreset("amber_terminal", R.string.theme_amber_terminal, true, 0xFF0A0500, 0xFF1A1005, 0xFFFF8C00, 0xFFFFD700, 0xFFFF8C00),
        ThemePreset("hacker",        R.string.theme_hacker,        true,  0xFF0A0F0A, 0xFF1A1F1A, 0xFF00FF41, 0xFF008F00, 0xFF00FF41),

        // ============================================================
        // СТИЛЬНЫЕ ТЁМНЫЕ
        // ============================================================
        ThemePreset("noir",          R.string.theme_noir,          true,  0xFF0A0908, 0xFF1B1916, 0xFFF1E7D6, 0xFFD5AE69, 0xFFF6EFE4),
        ThemePreset("blood",         R.string.theme_blood,         true,  0xFF0A0505, 0xFF1A1010, 0xFFDC143C, 0xFF8B0000, 0xFFFFE0E0),
        ThemePreset("wine",          R.string.theme_wine,          true,  0xFF1A050A, 0xFF2A1015, 0xFF722F37, 0xFFC0A0A0, 0xFFF5E0E0),
        ThemePreset("royal",         R.string.theme_royal,         true,  0xFF0A0A1A, 0xFF1A1A2A, 0xFF4B0082, 0xFFFFD700, 0xFFF0E0FF),
        ThemePreset("emerald",       R.string.theme_emerald,       true,  0xFF0A1A10, 0xFF1A2A20, 0xFF50C878, 0xFF00A86B, 0xFFE0FFE0),
        ThemePreset("sapphire",      R.string.theme_sapphire,      true,  0xFF0A1020, 0xFF1A2030, 0xFF0F52BA, 0xFF007FFF, 0xFFE0F0FF),
        ThemePreset("ruby",          R.string.theme_ruby,          true,  0xFF1A0505, 0xFF2A1010, 0xFFE0115F, 0xFF9B111E, 0xFFFFE0E0),
        ThemePreset("obsidian",      R.string.theme_obsidian,      true,  0xFF0A0A10, 0xFF1A1A20, 0xFF808080, 0xFFC0C0C0, 0xFFF0F0F0),
        ThemePreset("cyber_violet",  R.string.theme_cyber_violet,  true,  0xFF0A051A, 0xFF1A152A, 0xFFBF40BF, 0xFF00FFFF, 0xFFFFE8FF),
        ThemePreset("neon_pink",     R.string.theme_neon_pink,     true,  0xFF1A0A15, 0xFF2A1A25, 0xFFFF1493, 0xFFFF69B4, 0xFFFFE8F0),
        ThemePreset("retro_sun",     R.string.theme_retro_sun,     true,  0xFF1A0A05, 0xFF2A1A0F, 0xFFFF6347, 0xFFFFD700, 0xFFFFE8D0),
        ThemePreset("deep_blue",     R.string.theme_deep_blue,     true,  0xFF050515, 0xFF101025, 0xFF4169E1, 0xFF87CEEB, 0xFFE0E8FF),

        // ============================================================
        // НОВЫЕ ТЁМНЫЕ
        // ============================================================
        ThemePreset("blue_hour",       R.string.theme_blue_hour,       true, 0xFF071426, 0xFF10243D, 0xFF6EA8FE, 0xFF64D8CB, 0xFFEAF2FF),
        ThemePreset("northern_lights", R.string.theme_northern_lights, true, 0xFF051915, 0xFF0D2A24, 0xFF54E0A4, 0xFFA88CFF, 0xFFE9FFF7),
        ThemePreset("plum_velvet",     R.string.theme_plum_velvet,     true, 0xFF1B0B1C, 0xFF2B142D, 0xFFE082D0, 0xFFD6A55D, 0xFFFDEBFA),
        ThemePreset("midnight_ink",    R.string.theme_midnight_ink,    true, 0xFF070B16, 0xFF11182A, 0xFF7AA2F7, 0xFF89DDFF, 0xFFE7ECFA),
        ThemePreset("storm",           R.string.theme_storm,           true, 0xFF111720, 0xFF1C2530, 0xFF82A6C8, 0xFFF2B880, 0xFFE8EFF5),
        ThemePreset("pine_night",      R.string.theme_pine_night,      true, 0xFF07150E, 0xFF10241A, 0xFF78C091, 0xFFDDA15E, 0xFFE8F5EC),
        ThemePreset("ember",           R.string.theme_ember,           true, 0xFF190904, 0xFF32140C, 0xFFFF7A3D, 0xFFFFC857, 0xFFFFF2E6),
        ThemePreset("eclipse",         R.string.theme_eclipse,         true, 0xFF08070C, 0xFF14111E, 0xFFA78BFA, 0xFFFF9E64, 0xFFF3EFFF),
        ThemePreset("deep_teal",       R.string.theme_deep_teal,       true, 0xFF041A1B, 0xFF0B2B2D, 0xFF3DD6C6, 0xFF86A8E7, 0xFFE6FFFD),
        ThemePreset("cocoa_night",     R.string.theme_cocoa_night,     true, 0xFF1A100F, 0xFF291B19, 0xFFD99A7C, 0xFFC8A7D9, 0xFFF7ECE8),

        // ============================================================
        // ПРИРОДА (светлые)
        // ============================================================
        ThemePreset("dawn",          R.string.theme_dawn,          false, 0xFFFFF5E6, 0xFFFFFFFF, 0xFFF4A460, 0xFF87CEEB, 0xFF3E2817),
        ThemePreset("morning_mist",  R.string.theme_morning_mist,  false, 0xFFF0F5FA, 0xFFFFFFFF, 0xFF708090, 0xFF87CEEB, 0xFF2F3A45),
        ThemePreset("meadow",        R.string.theme_meadow,        false, 0xFFF0F9E8, 0xFFFFFFFF, 0xFF228B22, 0xFF8FBC8F, 0xFF2A3A1A),
        ThemePreset("spring",        R.string.theme_spring,        false, 0xFFFFF0F5, 0xFFFFFFFF, 0xFFFF69B4, 0xFF98FB98, 0xFF3E2817),
        ThemePreset("summer",        R.string.theme_summer,        false, 0xFFFFFFF0, 0xFFFFFFFF, 0xFFFF8C00, 0xFF20B2AA, 0xFF3E2817),
        ThemePreset("birch",         R.string.theme_birch,         false, 0xFFF5F3E7, 0xFFFAF8EF, 0xFF6E7452, 0xFF795A3E, 0xFF24291D),
        ThemePreset("sakura",        R.string.theme_sakura,        false, 0xFFFFF0F5, 0xFFFFF5F8, 0xFFB44A7F, 0xFF5A709B, 0xFF321D28),
        ThemePreset("lavender_field", R.string.theme_lavender_field, false, 0xFFF5F0FF, 0xFFFFFFFF, 0xFF9370DB, 0xFFE6E6FA, 0xFF2A1A3A),
        ThemePreset("sky",           R.string.theme_sky,           false, 0xFFF0F8FF, 0xFFFFFFFF, 0xFF1E90FF, 0xFF87CEEB, 0xFF0A1A3A),
        ThemePreset("sand",          R.string.theme_sand,          false, 0xFFFFF5E1, 0xFFFFFFFF, 0xFFDEB887, 0xFFF4A460, 0xFF3E2817),
        ThemePreset("ocean_light",   R.string.theme_ocean_light,   false, 0xFFF0FFFF, 0xFFFFFFFF, 0xFF008B8B, 0xFF20B2AA, 0xFF0A2A2A),
        ThemePreset("autumn",        R.string.theme_autumn,        false, 0xFFFFF8DC, 0xFFFFFFFF, 0xFFCD853F, 0xFF8B4513, 0xFF3E2817),

        // ============================================================
        // БУМАГА / РЕТРО (светлые)
        // ============================================================
        ThemePreset("parchment",     R.string.theme_parchment,     false, 0xFFF4E1BD, 0xFFFBEED2, 0xFF70452A, 0xFF9E4033, 0xFF352417),
        ThemePreset("sepia",         R.string.theme_sepia,         false, 0xFFE6C99C, 0xFFF0DAB6, 0xFF583923, 0xFF8B6238, 0xFF2E2118),
        ThemePreset("old_book",      R.string.theme_old_book,      false, 0xFFF4E8D0, 0xFFFFFFFF, 0xFF8B4513, 0xFF6B4423, 0xFF3E2817),
        ThemePreset("cream",         R.string.theme_cream,         false, 0xFFFFF9E8, 0xFFFFFDF5, 0xFF8A6620, 0xFFAD6B4E, 0xFF362812),
        ThemePreset("ivory",         R.string.theme_ivory,         false, 0xFFFFFDF0, 0xFFFFFFF8, 0xFF385848, 0xFFB28A3E, 0xFF1F2C25),
        ThemePreset("linen",         R.string.theme_linen,         false, 0xFFF3E8D7, 0xFFFAF4E8, 0xFF667054, 0xFF7B5C4C, 0xFF2A2820),
        ThemePreset("ecru",          R.string.theme_ecru,          false, 0xFFEFE5D0, 0xFFF8F1E4, 0xFF6B6944, 0xFF53677A, 0xFF302D22),

        // ============================================================
        // ПАСТЕЛЬ (светлые)
        // ============================================================
        ThemePreset("pastel_pink",   R.string.theme_pastel_pink,   false, 0xFFFFE4E1, 0xFFFFEDEB, 0xFFAA4678, 0xFF2B7168, 0xFF351D24),
        ThemePreset("pastel_blue",   R.string.theme_pastel_blue,   false, 0xFFE6F2FF, 0xFFFFFFFF, 0xFF4169E1, 0xFF87CEEB, 0xFF0A1A3A),
        ThemePreset("mint_cream",    R.string.theme_mint_cream,    false, 0xFFF5FFFA, 0xFFFFFFFF, 0xFF3CB371, 0xFF98FB98, 0xFF0A2A1A),
        ThemePreset("peach",         R.string.theme_peach,         false, 0xFFFFEFD5, 0xFFFFFFFF, 0xFFFF8C00, 0xFFFFA07A, 0xFF3E1A0A),
        ThemePreset("powder",        R.string.theme_powder,        false, 0xFFF8E8F0, 0xFFFFFFFF, 0xFFD2691E, 0xFFFFB7C5, 0xFF3E1A2A),
        ThemePreset("lilac",         R.string.theme_lilac,         false, 0xFFF5F0FA, 0xFFFFFFFF, 0xFF9370DB, 0xFFD8BFD8, 0xFF2A1A3A),

        // ============================================================
        // LINUX (светлые)
        // ============================================================
        ThemePreset("ubuntu_light",  R.string.theme_ubuntu_light,  false, 0xFFFFF5E6, 0xFFFFFFFF, 0xFFE95420, 0xFF77216F, 0xFF3E1A0A),
        ThemePreset("debian_light",  R.string.theme_debian_light,  false, 0xFFFFF0F0, 0xFFFFFFFF, 0xFFA80030, 0xFFD70A53, 0xFF3E1A1A),
        ThemePreset("fedora_light",  R.string.theme_fedora_light,  false, 0xFFF0F5FF, 0xFFFFFFFF, 0xFF3C6EB4, 0xFF294172, 0xFF0A1A2A),
        ThemePreset("mint_light",    R.string.theme_mint_light,    false, 0xFFF0FFF0, 0xFFFFFFFF, 0xFF87CF3E, 0xFF5FAA4C, 0xFF1A2A1A),
        ThemePreset("pop_os_light",  R.string.theme_pop_os_light,  false, 0xFFFFF5E6, 0xFFFFFFFF, 0xFFFA9E43, 0xFF48B9C7, 0xFF3E2817),

        // ============================================================
        // КОСМОС (светлые)
        // ============================================================
        ThemePreset("moonlight",     R.string.theme_moonlight,     false, 0xFFF0F0FA, 0xFFFFFFFF, 0xFF6A5ACD, 0xFF9370DB, 0xFF1A1A2A),
        ThemePreset("cloud",         R.string.theme_cloud,         false, 0xFFEEF5FA, 0xFFF6FAFD, 0xFF466F91, 0xFF75657F, 0xFF202B35),
        ThemePreset("snow",          R.string.theme_snow,          false, 0xFFFFFAFA, 0xFFFFFFFF, 0xFF4682B4, 0xFFB0C4DE, 0xFF1A1A2A),
        ThemePreset("pearl",         R.string.theme_pearl,         false, 0xFFFFF8F0, 0xFFFFFFFF, 0xFFB8860B, 0xFFDEB887, 0xFF2A2010),
        ThemePreset("starlight",     R.string.theme_starlight,     false, 0xFFF8F5FA, 0xFFFFFFFF, 0xFF9370DB, 0xFFB19CD9, 0xFF1A1020),

        // ============================================================
        // СТИЛЬНЫЕ СВЕТЛЫЕ
        // ============================================================
        ThemePreset("honey",         R.string.theme_honey,         false, 0xFFFFF5D6, 0xFFFFFFFF, 0xFFDAA520, 0xFFF0E68C, 0xFF3E2817),
        ThemePreset("sage",          R.string.theme_sage,          false, 0xFFF0F5E8, 0xFFFFFFFF, 0xFF87A96B, 0xFFB2C2A4, 0xFF1A2A1A),
        ThemePreset("coral",         R.string.theme_coral,         false, 0xFFFFF0F0, 0xFFFFFFFF, 0xFFFF6347, 0xFFFF7F50, 0xFF3E1A1A),
        ThemePreset("terracotta",    R.string.theme_terracotta,    false, 0xFFFFF0E0, 0xFFFFFFFF, 0xFFCD5C5C, 0xFFE2725B, 0xFF3E1A0A),
        ThemePreset("olive",         R.string.theme_olive,         false, 0xFFF5F5DC, 0xFFFFFFFF, 0xFF6B8E23, 0xFF808000, 0xFF2A2A1A),
        ThemePreset("rose_garden",   R.string.theme_rose_garden,   false, 0xFFFFF5F5, 0xFFFFFFFF, 0xFFC71585, 0xFFFF69B4, 0xFF3E1A2A),
        ThemePreset("cotton",        R.string.theme_cotton,        false, 0xFFFAF8F6, 0xFFFFFDFC, 0xFF5E7092, 0xFF8C5B68, 0xFF282429),

        // ============================================================
        // НОВЫЕ СВЕТЛЫЕ
        // ============================================================
        ThemePreset("sunrise",      R.string.theme_sunrise,      false, 0xFFFFF7ED, 0xFFFFFFFF, 0xFFE76F51, 0xFFF4B860, 0xFF3A241B),
        ThemePreset("sea_breeze",   R.string.theme_sea_breeze,   false, 0xFFEFFBFA, 0xFFFFFFFF, 0xFF168AAD, 0xFF5BC0BE, 0xFF0E3440),
        ThemePreset("alpine",       R.string.theme_alpine,       false, 0xFFF2F7F4, 0xFFFFFFFF, 0xFF2D6A4F, 0xFF4D96B9, 0xFF18342A),
        ThemePreset("lemon",        R.string.theme_lemon,        false, 0xFFFFFBE6, 0xFFFFFFFF, 0xFFB7791F, 0xFF7A9E2A, 0xFF3A3212),
        ThemePreset("blush",        R.string.theme_blush,        false, 0xFFFFF2F5, 0xFFFFFFFF, 0xFFB23A67, 0xFFD97B93, 0xFF401C2A),
        ThemePreset("pistachio",    R.string.theme_pistachio,    false, 0xFFF4F8E9, 0xFFFFFFFF, 0xFF5E7D2B, 0xFF99B83C, 0xFF263414),
        ThemePreset("arctic",       R.string.theme_arctic,       false, 0xFFF2F8FC, 0xFFFFFFFF, 0xFF246B9C, 0xFF5BB8D1, 0xFF163246),
        ThemePreset("latte",        R.string.theme_latte,        false, 0xFFFAF3EA, 0xFFFFFFFF, 0xFF8C5A3C, 0xFFC58B63, 0xFF3D281E),
        ThemePreset("orchid_mist",  R.string.theme_orchid_mist,  false, 0xFFF8F2FB, 0xFFFFFFFF, 0xFF7D4E9E, 0xFFB47EB3, 0xFF321D3D),
        ThemePreset("clear_sky",    R.string.theme_clear_sky,    false, 0xFFEDF7FF, 0xFFFFFFFF, 0xFF146C94, 0xFF2BA8C6, 0xFF12354A)
    )

    private val legacyCategories: Map<String, ThemeCategory> = mapOf(
        ThemeCategory.BASIC to setOf(
            "night", "sunset", "lavender", "rose", "graphite", "coffee", "mint", "gold",
            "blue_hour", "midnight_ink", "deep_teal",
            "day", "ice", "dawn", "morning_mist", "sky", "cloud", "snow",
            "sunrise", "clear_sky", "arctic"
        ),
        ThemeCategory.AMOLED to setOf(
            "amoled", "mono", "black_hole", "noir", "obsidian"
        ),
        ThemeCategory.NATURE to setOf(
            "forest", "deep_forest", "swamp", "autumn_night", "winter_night", "aurora",
            "desert_night", "volcano", "midnight_garden", "moss", "northern_lights",
            "pine_night", "ember", "meadow", "spring", "summer", "birch", "sakura",
            "lavender_field", "sand", "autumn", "alpine",
            "lemon", "pistachio", "honey", "sage", "coral", "terracotta", "olive",
            "rose_garden"
        ),
        ThemeCategory.OCEAN to setOf(
            "ocean", "coral_reef", "storm", "deep_blue", "ocean_light", "sea_breeze"
        ),
        ThemeCategory.SPACE to setOf(
            "deep_space", "nebula", "mars", "moon", "andromeda", "starfield", "comet",
            "solar_flare", "eclipse", "moonlight", "starlight"
        ),
        ThemeCategory.NEON to setOf(
            "night_city", "netrunner", "synthwave", "vaporwave", "chrome", "neon_rain",
            "acid_grid", "cyber_violet", "neon_pink"
        ),
        ThemeCategory.INDUSTRIAL to setOf(
            "diesel", "rust", "brass", "smoke", "war_room", "bauhaus", "factory"
        ),
        ThemeCategory.RETRO to setOf(
            "cold_wave", "gothic", "matrix", "phosphor", "amber_terminal", "hacker",
            "retro_sun"
        ),
        ThemeCategory.ELEGANT to setOf(
            "blood", "wine", "royal", "emerald", "sapphire", "ruby", "plum_velvet",
            "cocoa_night", "pearl"
        ),
        ThemeCategory.SYSTEM to setOf(
            "ubuntu", "debian", "arch", "fedora", "mint_os", "manjaro", "kali", "gentoo",
            "nixos", "opensuse", "pop_os", "elementary", "ubuntu_light", "debian_light",
            "fedora_light", "mint_light", "pop_os_light"
        ),
        ThemeCategory.PAPER to setOf(
            "paper", "parchment", "sepia", "old_book", "cream", "ivory", "linen", "ecru",
            "latte"
        ),
        ThemeCategory.PASTEL to setOf(
            "pastel_pink", "pastel_blue", "mint_cream", "peach", "powder", "lilac", "blush",
            "orchid_mist", "cotton"
        )
    ).flatMap { (category, ids) -> ids.map { it to category } }.toMap()

    val all: List<ThemePreset> = (
        legacy.map { preset ->
            preset.copy(category = legacyCategories[preset.id] ?: ThemeCategory.BASIC)
        } + ExpandedDarkThemes.all
    ).sortedWith(
        compareBy<ThemePreset>(
            { if (it.isDark) 0 else 1 },
            { it.category.sortOrder },
            ThemePreset::id
        )
    )

    val day: List<ThemePreset> = all.filterNot(ThemePreset::isDark)
    val night: List<ThemePreset> = all.filter(ThemePreset::isDark)

    fun byId(id: String?): ThemePreset =
        all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT_ID }
}
