package com.personal.sleepalarm.util

import com.personal.sleepalarm.data.db.entity.AlarmProfileEntity
import com.personal.sleepalarm.data.db.entity.CalculationMode
import com.personal.sleepalarm.domain.calculator.CueScheduleCalculator
import com.personal.sleepalarm.domain.calculator.SleepCalculator
import com.personal.sleepalarm.domain.model.CueScheduleMode
import com.personal.sleepalarm.domain.model.CueType
import com.personal.sleepalarm.domain.model.MathDifficulty
import org.json.JSONException
import org.json.JSONObject

/**
 * Кодирование/декодирование профиля настроек в JSON (F6).
 *
 * Использует org.json (встроен в Android), НЕ kotlinx.serialization —
 * чтобы не подключать Gradle-плагин.
 *
 * Правила:
 * - encode пишет ВСЕ поля профиля + "schema_version";
 *   updatedAt тоже пишется (для полноты/отладки);
 * - decode устойчив: неизвестные ключи игнорируются,
 *   отсутствующие берутся из AlarmProfileEntity() (дефолты),
 *   невалидные enum — fallback на дефолт;
 * - decode НЕ восстанавливает updatedAt из JSON — импорт считается
 *   новым изменением, поэтому updatedAt = System.currentTimeMillis();
 * - themeMode НЕ входит в JSON (он живёт в DataStore, не в профиле);
 * - значения нормализуются (coerce/snapToStep) по аналогии с репозиторием.
 *
 * Всё локальное, без интернета.
 */
object ProfileJsonCodec {

    /** Версия формата JSON. Задел на будущие миграции формата. */
    const val SCHEMA_VERSION = 2

    private const val KEY_SCHEMA = "schema_version"

    // === Ключи полей (соответствуют именам полей entity) ===
    private const val K_CYCLE_LENGTH = "cycleLengthMinutes"
    private const val K_CYCLES = "cycles"
    private const val K_ONSET = "onsetLatencyMinutes"
    private const val K_CALC_MODE = "calculationMode"
    private const val K_BED_HOUR = "preferredBedTimeHour"
    private const val K_BED_MIN = "preferredBedTimeMinute"
    private const val K_WAKE_HOUR = "preferredWakeHour"
    private const val K_WAKE_MIN = "preferredWakeMinute"
    private const val K_CUES_ENABLED = "cuesEnabled"
    private const val K_CUE_TYPE = "cueType"
    private const val K_FIRST_CUE = "firstCueDelayMinutes"
    private const val K_CUE_INTERVAL = "cueIntervalMinutes"
    private const val K_CUE_VOLUME = "cueVolumePercent"
    private const val K_NOTIFICATION_VOLUME = "notificationVolumePercent"
    private const val K_MATH = "mathDifficulty"
    private const val K_QUIET = "quietAlarmEnabled"
    private const val K_UPDATED_AT = "updatedAt"
    private const val K_VIBRATION = "vibrationEnabled"
    private const val K_RINGTONE = "alarmRingtoneUri"
    private const val K_CUE_MODE = "cueScheduleMode"
    private const val K_REM_OFFSET = "remCueOffsetPercent"
    private const val K_AUTO_DETECT = "autoDetectOnsetEnabled"
    private const val K_AUTO_CORRECT = "autoCorrectWakeEnabled"
    private const val K_SMART_ENABLED = "smartRepeatEnabled"
    private const val K_SMART_FIRST = "smartRepeatFirstDelayMinutes"
    private const val K_SMART_INTERVAL = "smartRepeatIntervalMinutes"
    private const val K_SMART_MAX = "smartRepeatMaxCount"
    private const val K_MIRROR = "mirrorToSystemClock"
    private const val K_CUE_RINGTONE = "cueRingtoneUri"

    /**
     * Кодирует профиль в JSON-строку.
     */
    fun encode(profile: AlarmProfileEntity): String {
        val json = JSONObject()

        json.put(KEY_SCHEMA, SCHEMA_VERSION)

        json.put(K_CYCLE_LENGTH, profile.cycleLengthMinutes)
        json.put(K_CYCLES, profile.cycles)
        json.put(K_ONSET, profile.onsetLatencyMinutes)
        json.put(K_CALC_MODE, profile.calculationMode.name)
        json.put(K_BED_HOUR, profile.preferredBedTimeHour)
        json.put(K_BED_MIN, profile.preferredBedTimeMinute)
        json.put(K_WAKE_HOUR, profile.preferredWakeHour)
        json.put(K_WAKE_MIN, profile.preferredWakeMinute)
        json.put(K_CUES_ENABLED, profile.cuesEnabled)
        json.put(K_CUE_TYPE, profile.cueType.name)
        json.put(K_FIRST_CUE, profile.firstCueDelayMinutes)
        json.put(K_CUE_INTERVAL, profile.cueIntervalMinutes)
        json.put(K_CUE_VOLUME, profile.cueVolumePercent)
        json.put(K_NOTIFICATION_VOLUME, profile.notificationVolumePercent)
        json.put(K_MATH, profile.mathDifficulty.name)
        json.put(K_QUIET, profile.quietAlarmEnabled)
        json.put(K_UPDATED_AT, profile.updatedAt)
        json.put(K_VIBRATION, profile.vibrationEnabled)
        // nullable: JSONObject.put(key, null) сохраняет JSON null
        json.put(K_RINGTONE, profile.alarmRingtoneUri)
        json.put(K_CUE_MODE, profile.cueScheduleMode.name)
        json.put(K_REM_OFFSET, profile.remCueOffsetPercent)
        json.put(K_AUTO_DETECT, profile.autoDetectOnsetEnabled)
        json.put(K_AUTO_CORRECT, profile.autoCorrectWakeEnabled)
        json.put(K_SMART_ENABLED, profile.smartRepeatEnabled)
        json.put(K_SMART_FIRST, profile.smartRepeatFirstDelayMinutes)
        json.put(K_SMART_INTERVAL, profile.smartRepeatIntervalMinutes)
        json.put(K_SMART_MAX, profile.smartRepeatMaxCount)
        json.put(K_MIRROR, profile.mirrorToSystemClock)
        json.put(K_CUE_RINGTONE, profile.cueRingtoneUri)

        return json.toString(2)
    }

    /**
     * Декодирует JSON-строку в профиль.
     *
     * Возвращает null при ошибке парсинга (битый JSON).
     * Отдельные невалидные поля тихо подменяются дефолтами.
     */
    fun decode(json: String): AlarmProfileEntity? {
        return try {
            val obj = JSONObject(json)
            val defaults = AlarmProfileEntity()

            val decoded = AlarmProfileEntity(
                id = AlarmProfileEntity().id, // всегда 1
                cycleLengthMinutes = obj.optInt(K_CYCLE_LENGTH, defaults.cycleLengthMinutes),
                cycles = obj.optInt(K_CYCLES, defaults.cycles),
                onsetLatencyMinutes = obj.optInt(K_ONSET, defaults.onsetLatencyMinutes),
                calculationMode = safeEnum(
                    obj.optString(K_CALC_MODE, defaults.calculationMode.name),
                    CalculationMode.values(),
                    defaults.calculationMode
                ),
                preferredBedTimeHour = obj.optInt(K_BED_HOUR, defaults.preferredBedTimeHour),
                preferredBedTimeMinute = obj.optInt(K_BED_MIN, defaults.preferredBedTimeMinute),
                preferredWakeHour = obj.optInt(K_WAKE_HOUR, defaults.preferredWakeHour),
                preferredWakeMinute = obj.optInt(K_WAKE_MIN, defaults.preferredWakeMinute),
                cuesEnabled = obj.optBoolean(K_CUES_ENABLED, defaults.cuesEnabled),
                cueType = safeEnum(
                    obj.optString(K_CUE_TYPE, defaults.cueType.name),
                    CueType.values(),
                    defaults.cueType
                ),
                firstCueDelayMinutes = obj.optInt(K_FIRST_CUE, defaults.firstCueDelayMinutes),
                cueIntervalMinutes = obj.optInt(K_CUE_INTERVAL, defaults.cueIntervalMinutes),
                cueVolumePercent = obj.optInt(K_CUE_VOLUME, defaults.cueVolumePercent),
                notificationVolumePercent = obj.optInt(
                    K_NOTIFICATION_VOLUME,
                    defaults.notificationVolumePercent
                ),
                mathDifficulty = safeEnum(
                    obj.optString(K_MATH, defaults.mathDifficulty.name),
                    MathDifficulty.values(),
                    defaults.mathDifficulty
                ),
                quietAlarmEnabled = obj.optBoolean(K_QUIET, defaults.quietAlarmEnabled),
                // updatedAt НЕ читаем из JSON — импорт = новое изменение
                updatedAt = System.currentTimeMillis(),
                vibrationEnabled = obj.optBoolean(K_VIBRATION, defaults.vibrationEnabled),
                alarmRingtoneUri = optNullableString(obj, K_RINGTONE),
                cueScheduleMode = safeEnum(
                    obj.optString(K_CUE_MODE, defaults.cueScheduleMode.name),
                    CueScheduleMode.values(),
                    defaults.cueScheduleMode
                ),
                remCueOffsetPercent = obj.optInt(K_REM_OFFSET, defaults.remCueOffsetPercent),
                autoDetectOnsetEnabled = obj.optBoolean(K_AUTO_DETECT, defaults.autoDetectOnsetEnabled),
                autoCorrectWakeEnabled = obj.optBoolean(K_AUTO_CORRECT, defaults.autoCorrectWakeEnabled),
                smartRepeatEnabled = obj.optBoolean(K_SMART_ENABLED, defaults.smartRepeatEnabled),
                smartRepeatFirstDelayMinutes = obj.optInt(K_SMART_FIRST, defaults.smartRepeatFirstDelayMinutes),
                smartRepeatIntervalMinutes = obj.optInt(K_SMART_INTERVAL, defaults.smartRepeatIntervalMinutes),
                smartRepeatMaxCount = obj.optInt(K_SMART_MAX, defaults.smartRepeatMaxCount),
                mirrorToSystemClock = obj.optBoolean(K_MIRROR, defaults.mirrorToSystemClock),
                cueRingtoneUri = optNullableString(obj, K_CUE_RINGTONE)
            )

            normalize(decoded)
        } catch (_: JSONException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Нормализация значений после декодирования.
     * Дублирует логику SleepProfileRepository.normalize,
     * чтобы util не зависел от репозитория.
     */
    private fun normalize(profile: AlarmProfileEntity): AlarmProfileEntity {
        return profile.copy(
            cycleLengthMinutes = snapToStep(
                profile.cycleLengthMinutes,
                SleepCalculator.MIN_CYCLE_LENGTH_MINUTES,
                SleepCalculator.MAX_CYCLE_LENGTH_MINUTES,
                SleepCalculator.CYCLE_STEP_MINUTES
            ),
            cycles = profile.cycles.coerceIn(
                SleepCalculator.MIN_CYCLES,
                SleepCalculator.MAX_CYCLES
            ),
            onsetLatencyMinutes = snapToStep(
                profile.onsetLatencyMinutes,
                SleepCalculator.MIN_ONSET_LATENCY_MINUTES,
                SleepCalculator.MAX_ONSET_LATENCY_MINUTES,
                SleepCalculator.ONSET_STEP_MINUTES
            ),
            preferredBedTimeHour = profile.preferredBedTimeHour.coerceIn(0, 23),
            preferredBedTimeMinute = profile.preferredBedTimeMinute.coerceIn(0, 59),
            preferredWakeHour = profile.preferredWakeHour.coerceIn(0, 23),
            preferredWakeMinute = profile.preferredWakeMinute.coerceIn(0, 59),
            firstCueDelayMinutes = CueScheduleCalculator.normalizeFirstCueDelay(
                profile.firstCueDelayMinutes
            ),
            cueIntervalMinutes = CueScheduleCalculator.normalizeCueInterval(
                profile.cueIntervalMinutes
            ),
            cueVolumePercent = CueScheduleCalculator.normalizeCueVolume(
                profile.cueVolumePercent
            ),
            notificationVolumePercent = profile.notificationVolumePercent.coerceIn(0, 100),
            remCueOffsetPercent = profile.remCueOffsetPercent.coerceIn(10, 90),
            smartRepeatFirstDelayMinutes = profile.smartRepeatFirstDelayMinutes.coerceIn(1, 10),
            smartRepeatIntervalMinutes = profile.smartRepeatIntervalMinutes.coerceIn(1, 10),
            smartRepeatMaxCount = profile.smartRepeatMaxCount.coerceIn(1, 20),
            alarmRingtoneUri = profile.alarmRingtoneUri
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            cueRingtoneUri = profile.cueRingtoneUri
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        )
    }

    /**
     * Безопасный парсинг enum с fallback на дефолт.
     */
    private inline fun <reified E : Enum<E>> safeEnum(
        raw: String,
        values: Array<E>,
        default: E
    ): E {
        return values.firstOrNull { it.name == raw } ?: default
    }

    /**
     * Читает nullable-строку: если ключа нет или значение JSON null —
     * возвращает null (а не пустую строку, как optString).
     */
    private fun optNullableString(
        obj: JSONObject,
        key: String
    ): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return obj.optString(key, "")
    }

    /**
     * Приводит значение к ближайшему допустимому шагу.
     */
    private fun snapToStep(
        value: Int,
        min: Int,
        max: Int,
        step: Int
    ): Int {
        val coerced = value.coerceIn(min, max)
        val stepsFromMin = (coerced - min + step / 2) / step
        val snapped = min + stepsFromMin * step
        return snapped.coerceIn(min, max)
    }
}
