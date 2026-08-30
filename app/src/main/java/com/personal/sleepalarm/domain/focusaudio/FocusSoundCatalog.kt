package com.personal.sleepalarm.domain.focusaudio

/** What kind of playback source an entry represents. */
enum class FocusSoundKind {
    SILENCE,
    GENERATED_NOISE,
    AMBIENCE,
    MELODY,
    CUSTOM_FILE
}

/** Generated noise does not require an audio asset and always works offline. */
enum class FocusNoiseColor {
    WHITE,
    PINK,
    BROWN
}

/**
 * Stable category ids are persisted by the UI filters. Do not rename enum values or [id]
 * after release; entries can move between categories without changing their own ids.
 */
enum class FocusSoundCategory(
    val id: String,
    val titleRu: String,
    val titleEn: String
) {
    SILENCE("silence", "Без звука", "Silence"),
    NOISE("noise", "Шумы", "Noise"),
    STUDY("study", "Учёба", "Study"),
    SPACES("spaces", "Пространства", "Spaces"),
    WEATHER("weather", "Погода", "Weather"),
    NATURE("nature", "Природа", "Nature"),
    COZY("cozy", "Уют", "Cozy"),
    TRAVEL("travel", "В пути", "In transit"),
    MELODY("melody", "Мелодии", "Melodies"),
    CUSTOM("custom", "Мои", "My audio");

    fun title(languageTag: String?): String =
        if (languageTag.orEmpty().startsWith("ru", ignoreCase = true)) titleRu else titleEn
}

/** Metadata only: playback owns decoding and lifecycle. */
data class FocusSoundEntry(
    val id: String,
    val titleRu: String,
    val titleEn: String,
    val category: FocusSoundCategory,
    val kind: FocusSoundKind,
    /** Relative path under app/src/main/assets. Generated sources do not have one. */
    val bundledAssetName: String? = null,
    val noiseColor: FocusNoiseColor? = null,
    val loops: Boolean = true
) {
    fun title(languageTag: String?): String =
        if (languageTag.orEmpty().startsWith("ru", ignoreCase = true)) titleRu else titleEn
}

/**
 * Offline catalogue used by both the focus setup sheet and an active focus session.
 *
 * All ids are intentionally descriptive and stable. Ambient/melody asset paths are
 * declared even when a lightweight build provides a deterministic generated fallback.
 */
object FocusSoundCatalog {
    const val SILENCE_ID = "silence"
    const val CUSTOM_FILE_ID = "custom_file"

    val all: List<FocusSoundEntry> = listOf(
        entry(SILENCE_ID, "Без звука", "Silence", FocusSoundCategory.SILENCE, FocusSoundKind.SILENCE),

        noise("white_noise", "Белый шум", "White noise", FocusNoiseColor.WHITE),
        noise("pink_noise", "Розовый шум", "Pink noise", FocusNoiseColor.PINK),
        noise("brown_noise", "Коричневый шум", "Brown noise", FocusNoiseColor.BROWN),

        ambience("large_library", "Большая библиотека", "Grand library", FocusSoundCategory.STUDY),
        ambience("quiet_reading_room", "Тихий читальный зал", "Quiet reading room", FocusSoundCategory.STUDY),
        ambience("pencil_on_paper", "Карандаш по бумаге", "Pencil on paper", FocusSoundCategory.STUDY),
        ambience("fountain_pen", "Перьевая ручка", "Fountain pen", FocusSoundCategory.STUDY),
        ambience("turning_pages", "Листание страниц", "Turning pages", FocusSoundCategory.STUDY),
        ambience("soft_keyboard", "Мягкая клавиатура", "Soft keyboard", FocusSoundCategory.STUDY),
        ambience("mechanical_keyboard", "Механическая клавиатура", "Mechanical keyboard", FocusSoundCategory.STUDY),
        ambience("laptop_keyboard", "Клавиатура ноутбука", "Laptop keyboard", FocusSoundCategory.STUDY),
        ambience("ticking_clock", "Тиканье часов", "Ticking clock", FocusSoundCategory.STUDY),
        ambience("wall_clock", "Настенные часы", "Wall clock", FocusSoundCategory.STUDY),
        ambience("distant_lecture_hall", "Далёкая аудитория", "Distant lecture hall", FocusSoundCategory.STUDY),

        ambience("morning_cafe", "Утреннее кафе", "Morning cafe", FocusSoundCategory.SPACES),
        ambience("rainy_cafe", "Кафе под дождём", "Rainy cafe", FocusSoundCategory.SPACES),
        ambience("evening_office", "Вечерний офис", "Evening office", FocusSoundCategory.SPACES),
        ambience("bookshop", "Книжный магазин", "Bookshop", FocusSoundCategory.SPACES),
        ambience("university_archive", "Университетский архив", "University archive", FocusSoundCategory.SPACES),
        ambience("museum_hall", "Музейный зал", "Museum hall", FocusSoundCategory.SPACES),
        ambience("night_water_drops", "Ночные капли воды", "Water drops at night", FocusSoundCategory.SPACES),
        ambience("ear_ringing", "Высокий звон", "High ringing tone", FocusSoundCategory.SPACES),

        ambience("rain_on_window", "Дождь по окну", "Rain on a window", FocusSoundCategory.WEATHER),
        ambience("rain_on_tent", "Дождь по палатке", "Rain on a tent", FocusSoundCategory.WEATHER),
        ambience("distant_thunder", "Далёкая гроза", "Distant thunder", FocusSoundCategory.WEATHER),
        ambience("snowstorm", "Метель", "Snowstorm", FocusSoundCategory.WEATHER),
        ambience("summer_rain", "Летний дождь", "Summer rain", FocusSoundCategory.WEATHER),
        ambience("rainy_night_city", "Дождливый ночной город", "Rainy night city", FocusSoundCategory.WEATHER),
        ambience("close_thunder", "Мощная гроза", "Powerful thunder", FocusSoundCategory.WEATHER),

        ambience("forest_stream", "Лесной ручей", "Forest stream", FocusSoundCategory.NATURE),
        ambience("ocean_waves", "Морские волны", "Ocean waves", FocusSoundCategory.NATURE),
        ambience("quiet_lake", "Тихое озеро", "Quiet lake", FocusSoundCategory.NATURE),
        ambience("wind_in_pines", "Ветер в соснах", "Wind in pines", FocusSoundCategory.NATURE),
        ambience("night_crickets", "Ночные сверчки", "Night crickets", FocusSoundCategory.NATURE),
        ambience("campfire", "Костёр", "Campfire", FocusSoundCategory.NATURE),

        ambience("fireplace", "Камин", "Fireplace", FocusSoundCategory.COZY),
        ambience("cat_purring", "Мурлыканье кота", "Cat purring", FocusSoundCategory.COZY),
        ambience("aquarium", "Аквариум", "Aquarium", FocusSoundCategory.COZY),
        ambience("ceiling_fan", "Потолочный вентилятор", "Ceiling fan", FocusSoundCategory.COZY),
        ambience("vinyl_crackle", "Виниловый треск", "Vinyl crackle", FocusSoundCategory.COZY),
        ambience("next_room", "Соседняя комната", "Next room", FocusSoundCategory.COZY),
        ambience("quiet_fire", "Тихий огонь", "Quiet fire", FocusSoundCategory.COZY),
        ambience("steady_heartbeat", "Ровное сердцебиение", "Steady heartbeat", FocusSoundCategory.COZY),
        ambience("deep_heartbeat", "Глубокий пульс", "Deep heartbeat", FocusSoundCategory.COZY),
        ambience("tuning_radio", "Настройка радио", "Tuning a radio", FocusSoundCategory.COZY),
        ambience("creaking_wood", "Скрип дерева", "Creaking wood", FocusSoundCategory.COZY),
        ambience("gentle_bubbling", "Тихое журчание воды", "Gentle water bubbling", FocusSoundCategory.COZY),
        ambience("rapid_bubbling", "Быстрое журчание воды", "Rapid water bubbling", FocusSoundCategory.COZY),

        ambience("night_train", "Ночной поезд", "Night train", FocusSoundCategory.TRAVEL),
        ambience("airplane_cabin", "Салон самолёта", "Airplane cabin", FocusSoundCategory.TRAVEL),
        ambience("car_in_rain", "Машина под дождём", "Car in the rain", FocusSoundCategory.TRAVEL),
        ambience("ferry_cabin", "Каюта парома", "Ferry cabin", FocusSoundCategory.TRAVEL),
        ambience("city_tram", "Городской трамвай", "City tram", FocusSoundCategory.TRAVEL),
        ambience("orbital_station", "Орбитальная станция", "Orbital station", FocusSoundCategory.TRAVEL),
        ambience("running_on_gravel", "Бег по гравию", "Running on gravel", FocusSoundCategory.TRAVEL),
        ambience("concrete_footsteps", "Шаги по бетону", "Concrete footsteps", FocusSoundCategory.TRAVEL),
        ambience("dirt_gravel_footsteps", "Шаги по земле и гравию", "Dirt and gravel footsteps", FocusSoundCategory.TRAVEL),
        ambience("railway_train", "Железная дорога", "Railway train", FocusSoundCategory.TRAVEL),

        melody("pumping_drone", "Пульсирующий эмбиент", "Pulsing ambient drone"),
        melody("anxiety_ticks", "Ритмичные импульсы", "Rhythmic pulses"),

        entry(
            id = CUSTOM_FILE_ID,
            titleRu = "Мой аудиофайл",
            titleEn = "My audio file",
            category = FocusSoundCategory.CUSTOM,
            kind = FocusSoundKind.CUSTOM_FILE
        )
    )

    private val byId: Map<String, FocusSoundEntry> = all.associateBy(FocusSoundEntry::id)

    val silence: FocusSoundEntry = requireNotNull(byId[SILENCE_ID])
    val ambientEntries: List<FocusSoundEntry> = all.filter { it.kind == FocusSoundKind.AMBIENCE }
    val melodyEntries: List<FocusSoundEntry> = all.filter { it.kind == FocusSoundKind.MELODY }
    val noiseEntries: List<FocusSoundEntry> = all.filter { it.kind == FocusSoundKind.GENERATED_NOISE }
    val browseCategories: List<FocusSoundCategory> = FocusSoundCategory.entries.filter { category ->
        category != FocusSoundCategory.SILENCE &&
            category != FocusSoundCategory.CUSTOM &&
            all.any { it.category == category }
    }

    init {
        require(byId.size == all.size) { "Focus sound ids must be unique" }
        require(all.all { it.id.matches(Regex("[a-z0-9_]+")) }) { "Focus sound ids must be stable snake_case" }
        require(noiseEntries.all { it.noiseColor != null && it.bundledAssetName == null })
        require(ambientEntries.all { it.bundledAssetName != null })
        require(melodyEntries.all { it.bundledAssetName != null })
    }

    fun find(id: String?): FocusSoundEntry? = id?.let(byId::get)

    /** Unknown or removed ids degrade safely to silence after an app update. */
    fun resolve(id: String?): FocusSoundEntry = find(id) ?: silence

    fun inCategory(category: FocusSoundCategory): List<FocusSoundEntry> =
        all.filter { it.category == category }

    fun search(query: String, languageTag: String? = null): List<FocusSoundEntry> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return all
        return all.filter { entry ->
            entry.title(languageTag).contains(normalized, ignoreCase = true) ||
                entry.titleRu.contains(normalized, ignoreCase = true) ||
                entry.titleEn.contains(normalized, ignoreCase = true)
        }
    }

    private fun noise(
        id: String,
        titleRu: String,
        titleEn: String,
        color: FocusNoiseColor
    ) = entry(
        id = id,
        titleRu = titleRu,
        titleEn = titleEn,
        category = FocusSoundCategory.NOISE,
        kind = FocusSoundKind.GENERATED_NOISE,
        noiseColor = color
    )

    private fun ambience(
        id: String,
        titleRu: String,
        titleEn: String,
        category: FocusSoundCategory
    ) = entry(
        id = id,
        titleRu = titleRu,
        titleEn = titleEn,
        category = category,
        kind = FocusSoundKind.AMBIENCE,
        bundledAssetName = "focus/ambience/${category.id}/$id.ogg"
    )

    private fun melody(id: String, titleRu: String, titleEn: String) = entry(
        id = id,
        titleRu = titleRu,
        titleEn = titleEn,
        category = FocusSoundCategory.MELODY,
        kind = FocusSoundKind.MELODY,
        bundledAssetName = "focus/melodies/$id.ogg"
    )

    private fun entry(
        id: String,
        titleRu: String,
        titleEn: String,
        category: FocusSoundCategory,
        kind: FocusSoundKind,
        bundledAssetName: String? = null,
        noiseColor: FocusNoiseColor? = null
    ) = FocusSoundEntry(
        id = id,
        titleRu = titleRu,
        titleEn = titleEn,
        category = category,
        kind = kind,
        bundledAssetName = bundledAssetName,
        noiseColor = noiseColor
    )
}
