package com.personal.sleepalarm.data.db

import androidx.room.TypeConverter
import com.personal.sleepalarm.data.db.entity.CalculationMode
import com.personal.sleepalarm.domain.model.CueEventState
import com.personal.sleepalarm.domain.model.CueScheduleMode
import com.personal.sleepalarm.domain.model.CueType
import com.personal.sleepalarm.domain.model.DismissType
import com.personal.sleepalarm.domain.model.MathDifficulty
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.domain.model.FocusProtocolPhase
import com.personal.sleepalarm.data.db.entity.LibraryItemType
import com.personal.sleepalarm.data.db.entity.LibraryResourceKind
import com.personal.sleepalarm.data.db.entity.RepeatMode

/**
 * Room type converters.
 *
 * ДОБАВЛЕНО: конвертер для CueScheduleMode (F7).
 * ThemeMode НЕ конвертируется — он хранится в DataStore, не в Room.
 */
class Converters {

    @TypeConverter
    fun fromFocusProtocolPhase(value: FocusProtocolPhase): String = value.name

    @TypeConverter
    fun toFocusProtocolPhase(value: String): FocusProtocolPhase =
        runCatching { FocusProtocolPhase.valueOf(value) }
            .getOrDefault(FocusProtocolPhase.CANCELLED)

    @TypeConverter
    fun fromFocusActivityType(value: FocusActivityType): String = value.name

    @TypeConverter
    fun toFocusActivityType(value: String): FocusActivityType =
        runCatching { FocusActivityType.valueOf(value) }.getOrDefault(FocusActivityType.STUDY)

    // === Существующие конвертеры (НЕ менять) ===

    @TypeConverter
    fun fromCalculationMode(value: CalculationMode): String {
        return value.name
    }

    @TypeConverter
    fun toCalculationMode(value: String): CalculationMode {
        return CalculationMode.valueOf(value)
    }

    @TypeConverter
    fun fromCueType(value: CueType): String {
        return value.name
    }

    @TypeConverter
    fun toCueType(value: String): CueType {
        return CueType.valueOf(value)
    }

    @TypeConverter
    fun fromCueEventState(value: CueEventState): String {
        return value.name
    }

    @TypeConverter
    fun toCueEventState(value: String): CueEventState {
        return CueEventState.valueOf(value)
    }

    // === ДОБАВЛЕНО (v5): режим повторения напоминания ===

    @TypeConverter
    fun fromRepeatMode(value: RepeatMode): String {
        return value.name
    }

    @TypeConverter
    fun toRepeatMode(value: String): RepeatMode {
        return runCatching { RepeatMode.valueOf(value) }.getOrDefault(RepeatMode.ONCE)
    }

    @TypeConverter
    fun fromMathDifficulty(value: MathDifficulty): String {
        return value.name
    }

    @TypeConverter
    fun toMathDifficulty(value: String): MathDifficulty {
        return MathDifficulty.valueOf(value)
    }

    @TypeConverter
    fun fromDismissType(value: DismissType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toDismissType(value: String?): DismissType? {
        return value?.let { DismissType.valueOf(it) }
    }

    // === ДОБАВЛЕНО: F7 — режим расписания подсказок ===

    @TypeConverter
    fun fromCueScheduleMode(value: CueScheduleMode): String {
        return value.name
    }

    @TypeConverter
    fun toCueScheduleMode(value: String): CueScheduleMode {
        return CueScheduleMode.valueOf(value)
    }
    // === ДОБАВЛЕНО (v4): тип элемента библиотеки ===

    @TypeConverter
    fun fromLibraryItemType(value: LibraryItemType): String {
        return value.name
    }

    @TypeConverter
    fun toLibraryItemType(value: String): LibraryItemType {
        return LibraryItemType.valueOf(value)
    }

    @TypeConverter
    fun fromLibraryResourceKind(value: LibraryResourceKind): String = value.name

    @TypeConverter
    fun toLibraryResourceKind(value: String): LibraryResourceKind =
        runCatching { LibraryResourceKind.valueOf(value) }.getOrDefault(LibraryResourceKind.NOTE)

}
