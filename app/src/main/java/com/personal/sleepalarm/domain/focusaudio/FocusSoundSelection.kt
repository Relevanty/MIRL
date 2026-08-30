package com.personal.sleepalarm.domain.focusaudio

/** Persistable metadata for an audio document selected through Android's document picker. */
data class CustomFocusSoundFile(
    val uriString: String,
    val displayName: String = "",
    val mimeType: String? = null,
    val durationMillis: Long? = null,
    val sizeBytes: Long? = null,
    val persistablePermissionTaken: Boolean = false,
    val artist: String? = null,
    val album: String? = null,
    val addedAtMillis: Long = 0L
) {
    fun normalized(): CustomFocusSoundFile? {
        val uri = uriString.trim().takeIf(String::isNotEmpty) ?: return null
        return copy(
            uriString = uri,
            displayName = displayName.trim().ifEmpty { "Custom audio" },
            mimeType = mimeType?.trim()?.takeIf(String::isNotEmpty),
            durationMillis = durationMillis?.coerceAtLeast(0L),
            sizeBytes = sizeBytes?.coerceAtLeast(0L),
            artist = artist?.trim()?.takeIf(String::isNotEmpty),
            album = album?.trim()?.takeIf(String::isNotEmpty),
            addedAtMillis = addedAtMillis.coerceAtLeast(0L)
        )
    }
}

/** A single catalogue item, optionally carrying a concrete custom document. */
data class FocusSoundSelection(
    val catalogId: String = FocusSoundCatalog.SILENCE_ID,
    val customFile: CustomFocusSoundFile? = null
) {
    fun normalized(): FocusSoundSelection {
        val entry = FocusSoundCatalog.find(catalogId.trim()) ?: return silence()
        return if (entry.kind == FocusSoundKind.CUSTOM_FILE) {
            val file = customFile?.normalized() ?: return silence()
            copy(catalogId = FocusSoundCatalog.CUSTOM_FILE_ID, customFile = file)
        } else {
            copy(catalogId = entry.id, customFile = null)
        }
    }

    fun entry(): FocusSoundEntry = FocusSoundCatalog.resolve(normalized().catalogId)

    /** Used to de-duplicate recent custom files without exposing Android Uri. */
    fun historyKey(): String {
        val safe = normalized()
        return if (safe.catalogId == FocusSoundCatalog.CUSTOM_FILE_ID) {
            "${safe.catalogId}:${safe.customFile?.uriString.orEmpty()}"
        } else {
            safe.catalogId
        }
    }

    companion object {
        fun silence() = FocusSoundSelection(FocusSoundCatalog.SILENCE_ID)

        fun custom(file: CustomFocusSoundFile) =
            FocusSoundSelection(FocusSoundCatalog.CUSTOM_FILE_ID, file).normalized()
    }
}

/**
 * Complete focus soundscape. It deliberately allows at most two layers: a primary
 * ambience/melody/custom file and an optional generated colour-noise layer.
 */
data class FocusSoundscapeSelection(
    val primary: FocusSoundSelection = FocusSoundSelection.silence(),
    val secondaryLayerId: String? = null,
    val secondaryVolumePercent: Int = FocusSoundRules.DEFAULT_SECONDARY_VOLUME_PERCENT,
    val playDuringRecovery: Boolean = false
) {
    fun normalized(): FocusSoundscapeSelection {
        val safePrimary = primary.normalized()
        val primaryKind = safePrimary.entry().kind
        val secondary = FocusSoundCatalog.find(secondaryLayerId?.trim())
            ?.takeIf { it.kind == FocusSoundKind.GENERATED_NOISE }
            ?.takeIf {
                primaryKind == FocusSoundKind.AMBIENCE ||
                    primaryKind == FocusSoundKind.MELODY ||
                    primaryKind == FocusSoundKind.CUSTOM_FILE
            }
            ?.id
        return copy(
            primary = safePrimary,
            secondaryLayerId = secondary,
            secondaryVolumePercent = FocusSoundRules.normalizeVolume(secondaryVolumePercent)
        )
    }

    val isSilent: Boolean
        get() = normalized().primary.catalogId == FocusSoundCatalog.SILENCE_ID
}

data class FocusSoundSettings(
    val defaultSelection: FocusSoundscapeSelection = FocusSoundscapeSelection(),
    val lastSelection: FocusSoundscapeSelection = FocusSoundscapeSelection(),
    val volumePercent: Int = FocusSoundRules.DEFAULT_VOLUME_PERCENT,
    val favoriteIds: Set<String> = emptySet(),
    val recentSelections: List<FocusSoundSelection> = emptyList(),
    /** Persistent local library. Removing an item never deletes the original document. */
    val customLibrary: List<CustomFocusSoundFile> = emptyList()
) {
    fun normalized(): FocusSoundSettings = copy(
        defaultSelection = defaultSelection.normalized(),
        lastSelection = lastSelection.normalized(),
        volumePercent = FocusSoundRules.normalizeVolume(volumePercent),
        favoriteIds = FocusSoundRules.sanitizeFavoriteIds(favoriteIds),
        recentSelections = FocusSoundRules.normalizeRecents(recentSelections),
        customLibrary = FocusSoundRules.normalizeCustomLibrary(customLibrary)
    )
}

object FocusSoundRules {
    const val DEFAULT_VOLUME_PERCENT = 35
    const val DEFAULT_SECONDARY_VOLUME_PERCENT = 22
    const val MAX_RECENT_ITEMS = 10
    const val MAX_CUSTOM_FILES = 100

    fun normalizeVolume(value: Int): Int = value.coerceIn(0, 100)

    fun sanitizeFavoriteIds(ids: Iterable<String>): Set<String> = ids
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { id ->
            if (id.startsWith("${FocusSoundCatalog.CUSTOM_FILE_ID}:")) {
                id.takeIf { it.length > FocusSoundCatalog.CUSTOM_FILE_ID.length + 1 }
            } else {
                FocusSoundCatalog.find(id)
                    ?.takeUnless { it.kind == FocusSoundKind.SILENCE || it.kind == FocusSoundKind.CUSTOM_FILE }
                    ?.id
            }
        }
        .toCollection(linkedSetOf())

    fun toggleFavorite(ids: Set<String>, id: String): Set<String> {
        val safe = sanitizeFavoriteIds(ids).toMutableSet()
        val favoriteKey = if (id.startsWith("${FocusSoundCatalog.CUSTOM_FILE_ID}:")) {
            id.takeIf { it.length > FocusSoundCatalog.CUSTOM_FILE_ID.length + 1 }
        } else {
            FocusSoundCatalog.find(id)
                ?.takeUnless { it.kind == FocusSoundKind.SILENCE || it.kind == FocusSoundKind.CUSTOM_FILE }
                ?.id
        } ?: return safe
        if (!safe.add(favoriteKey)) safe.remove(favoriteKey)
        return safe
    }

    fun normalizeCustomLibrary(
        files: List<CustomFocusSoundFile>,
        limit: Int = MAX_CUSTOM_FILES
    ): List<CustomFocusSoundFile> {
        if (limit <= 0) return emptyList()
        return files.asSequence()
            .mapNotNull(CustomFocusSoundFile::normalized)
            .distinctBy(CustomFocusSoundFile::uriString)
            .take(limit)
            .toList()
    }

    fun pushRecent(
        current: List<FocusSoundSelection>,
        selected: FocusSoundSelection,
        limit: Int = MAX_RECENT_ITEMS
    ): List<FocusSoundSelection> {
        if (limit <= 0) return emptyList()
        val safeSelected = selected.normalized()
        if (safeSelected.catalogId == FocusSoundCatalog.SILENCE_ID) {
            return normalizeRecents(current, limit)
        }
        return buildList {
            add(safeSelected)
            current.asSequence()
                .map(FocusSoundSelection::normalized)
                .filterNot { it.catalogId == FocusSoundCatalog.SILENCE_ID }
                .filterNot { it.historyKey() == safeSelected.historyKey() }
                .distinctBy(FocusSoundSelection::historyKey)
                .take(limit - 1)
                .forEach(::add)
        }
    }

    fun normalizeRecents(
        items: List<FocusSoundSelection>,
        limit: Int = MAX_RECENT_ITEMS
    ): List<FocusSoundSelection> {
        if (limit <= 0) return emptyList()
        return items.asSequence()
            .map(FocusSoundSelection::normalized)
            .filterNot { it.catalogId == FocusSoundCatalog.SILENCE_ID }
            .distinctBy(FocusSoundSelection::historyKey)
            .take(limit)
            .toList()
    }

    fun effectiveSelection(
        settings: FocusSoundSettings,
        taskOverride: FocusSoundscapeSelection?
    ): FocusSoundscapeSelection = (taskOverride ?: settings.defaultSelection).normalized()
}
