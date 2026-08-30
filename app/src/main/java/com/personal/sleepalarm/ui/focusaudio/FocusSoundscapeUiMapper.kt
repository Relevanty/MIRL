package com.personal.sleepalarm.ui.focusaudio

import com.personal.sleepalarm.domain.focusaudio.FocusSoundCatalog
import com.personal.sleepalarm.domain.focusaudio.FocusSoundCategory
import com.personal.sleepalarm.domain.focusaudio.FocusSoundKind
import com.personal.sleepalarm.domain.focusaudio.FocusSoundSelection
import com.personal.sleepalarm.domain.focusaudio.FocusSoundSettings
import com.personal.sleepalarm.domain.focusaudio.FocusSoundscapeSelection

fun FocusSoundSettings.toUiItems(
    selection: FocusSoundscapeSelection,
    languageTag: String?
): List<FocusSoundUiItem> {
    val safe = selection.normalized()
    val catalogItems = FocusSoundCatalog.all
        .filterNot { it.kind == FocusSoundKind.CUSTOM_FILE }
        .map { entry ->
        FocusSoundUiItem(
            id = entry.id,
            catalogId = entry.id,
            title = entry.title(languageTag),
            subtitle = entrySubtitle(entry.kind, entry.category, languageTag),
            categoryId = entry.category.id,
            symbol = symbolFor(entry.category, entry.kind),
            isFavorite = entry.id in favoriteIds,
            isAvailable = true,
            isSilence = entry.kind == FocusSoundKind.SILENCE,
            isCustomFile = entry.kind == FocusSoundKind.CUSTOM_FILE
        )
    }
    val customItems = buildList {
        customLibrary.forEach { add(FocusSoundSelection.custom(it)) }
        add(safe.primary)
        add(defaultSelection.primary)
        add(lastSelection.primary)
        addAll(recentSelections)
    }
        .map { it.normalized() }
        .filter { it.catalogId == FocusSoundCatalog.CUSTOM_FILE_ID && it.customFile != null }
        .distinctBy { it.historyKey() }
        .map { custom ->
            FocusSoundUiItem(
                id = custom.historyKey(),
                catalogId = FocusSoundCatalog.CUSTOM_FILE_ID,
                customFile = custom.customFile,
                title = custom.customFile?.displayName
                    ?.takeIf { it.isNotBlank() }
                    ?: if (languageTag.isRussian()) "Мой аудиофайл" else "Custom audio",
                subtitle = customFileSubtitle(custom.customFile, languageTag),
                categoryId = FocusSoundCategory.CUSTOM.id,
                symbol = symbolFor(FocusSoundCategory.CUSTOM, FocusSoundKind.CUSTOM_FILE),
                isFavorite = custom.historyKey() in favoriteIds,
                isAvailable = true,
                isSilence = false,
                isCustomFile = true
            )
        }
    return catalogItems + customItems
}

private fun customFileSubtitle(file: com.personal.sleepalarm.domain.focusaudio.CustomFocusSoundFile?, languageTag: String?): String {
    val duration = file?.durationMillis
        ?.takeIf { it > 0L }
        ?.let { millis ->
            val totalSeconds = millis / 1_000L
            "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
        }
    val source = file?.artist?.takeIf { it.isNotBlank() }
        ?: if (file?.persistablePermissionTaken == false) {
            if (languageTag.isRussian()) "Временный доступ" else "Temporary access"
        } else if (languageTag.isRussian()) {
            "С устройства"
        } else {
            "On device"
        }
    return listOfNotNull(source, duration).joinToString(" · ")
}

private fun entrySubtitle(
    kind: FocusSoundKind,
    category: FocusSoundCategory,
    languageTag: String?
): String = when (kind) {
    FocusSoundKind.SILENCE -> if (languageTag.isRussian()) "Спокойный режим" else "Quiet mode"
    FocusSoundKind.GENERATED_NOISE -> if (languageTag.isRussian()) {
        "Бесконечный · без стыков"
    } else {
        "Endless · seamless"
    }
    FocusSoundKind.CUSTOM_FILE -> if (languageTag.isRussian()) "Файл с устройства" else "File on device"
    else -> category.title(languageTag) + if (languageTag.isRussian()) {
        " · без интернета"
    } else {
        " · offline"
    }
}

private fun symbolFor(category: FocusSoundCategory, kind: FocusSoundKind): String = when {
    kind == FocusSoundKind.SILENCE -> "—"
    kind == FocusSoundKind.GENERATED_NOISE -> "≋"
    kind == FocusSoundKind.MELODY -> "♪"
    kind == FocusSoundKind.CUSTOM_FILE -> "+"
    category == FocusSoundCategory.STUDY -> "✎"
    category == FocusSoundCategory.SPACES -> "⌂"
    category == FocusSoundCategory.WEATHER -> "☂"
    category == FocusSoundCategory.NATURE -> "♧"
    category == FocusSoundCategory.COZY -> "♨"
    category == FocusSoundCategory.TRAVEL -> "↝"
    else -> "≋"
}

private fun String?.isRussian(): Boolean =
    orEmpty().startsWith("ru", ignoreCase = true)
