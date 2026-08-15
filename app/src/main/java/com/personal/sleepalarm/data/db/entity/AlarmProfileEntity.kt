package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.personal.sleepalarm.domain.model.CueScheduleMode
import com.personal.sleepalarm.domain.model.CueType
import com.personal.sleepalarm.domain.model.MathDifficulty

/**
 * Режим расчёта.
 *
 * @deprecated Логика переведена на расчёт от текущего момента.
 * Режим больше не используется. Поле хранится только для совместимости БД.
 */
@Deprecated("Расчёт теперь всегда от текущего момента. Режим не используется.")
enum class CalculationMode {
    BED_TIME,
    WAKE_TIME
}

/**
 * Профиль настроек (одна запись, id = 1).
 *
 * Поля calculationMode, preferredBedTimeHour/Minute и cueType устарели
 * и не используются, но остаются в таблице ради сохранности данных.
 */
@Entity(tableName = "alarm_profiles")
data class AlarmProfileEntity(
    @PrimaryKey
    val id: Int = 1,

    // === Актуальные поля сна ===
    val cycleLengthMinutes: Int = 90,
    val cycles: Int = 5,
    val onsetLatencyMinutes: Int = 15,

    // === УСТАРЕЛО: режим и время отхода ко сну ===
    @Deprecated("Расчёт теперь от текущего момента. Не используется.")
    val calculationMode: CalculationMode = CalculationMode.WAKE_TIME,

    @Deprecated("Время отхода ко сну больше не задаётся. Не используется.")
    val preferredBedTimeHour: Int = 23,

    @Deprecated("Время отхода ко сну больше не задаётся. Не используется.")
    val preferredBedTimeMinute: Int = 0,

    // Актуально: к какому времени нужно проснуться (ориентир).
    val preferredWakeHour: Int = 7,
    val preferredWakeMinute: Int = 0,

    // === Подсказки (тип убран, звук выбирает пользователь) ===
    val cuesEnabled: Boolean = true,

    @Deprecated("Тип подсказки больше не выбирается. Играет cueRingtoneUri.")
    val cueType: CueType = CueType.BEEP,

    val firstCueDelayMinutes: Int = 60,
    val cueIntervalMinutes: Int = 30,
    val cueVolumePercent: Int = 10,

    // === Будильник ===
    val mathDifficulty: MathDifficulty = MathDifficulty.MEDIUM,
    val quietAlarmEnabled: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val alarmRingtoneUri: String? = null,
    val cueScheduleMode: CueScheduleMode = CueScheduleMode.REM_TARGETED,
    val remCueOffsetPercent: Int = 40,

    // === Автоопределение засыпания ===
    val autoDetectOnsetEnabled: Boolean = false,
    val autoCorrectWakeEnabled: Boolean = false,

    // === Умные повторы ===
    val smartRepeatEnabled: Boolean = true,
    val smartRepeatFirstDelayMinutes: Int = 3,
    val smartRepeatIntervalMinutes: Int = 2,
    val smartRepeatMaxCount: Int = 5,

    // === Системный дублёр ===
    val mirrorToSystemClock: Boolean = false,

    // === Свой звук подсказки ===
    val cueRingtoneUri: String? = null,

    val updatedAt: Long = System.currentTimeMillis()
)