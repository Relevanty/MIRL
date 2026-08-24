package com.personal.sleepalarm.util

import com.personal.sleepalarm.data.db.entity.AlarmProfileEntity
import com.personal.sleepalarm.data.db.entity.CalculationMode
import com.personal.sleepalarm.domain.model.CueScheduleMode
import com.personal.sleepalarm.domain.model.CueType
import com.personal.sleepalarm.domain.model.MathDifficulty
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты round-trip и устойчивости кодека профиля (F6).
 *
 * Требует testImplementation("org.json:json:...") — см. Часть 9.1,
 * иначе org.json в JVM-тестах бросает "not mocked".
 */
class ProfileJsonCodecTest {

    /**
     * Профиль со всеми нестандартными (но валидными) значениями,
     * включая поля F1/F2/F7/F9/F10/F11. Значения подобраны так,
     * чтобы нормализация была no-op (уже в диапазонах).
     */
    private fun fullProfile() = AlarmProfileEntity(
        id = 1,
        cycleLengthMinutes = 100,
        cycles = 6,
        onsetLatencyMinutes = 20,
        calculationMode = CalculationMode.WAKE_TIME,
        preferredBedTimeHour = 22,
        preferredBedTimeMinute = 30,
        preferredWakeHour = 6,
        preferredWakeMinute = 15,
        cuesEnabled = false,
        cueType = CueType.BINAURAL,
        firstCueDelayMinutes = 80,
        cueIntervalMinutes = 45,
        cueVolumePercent = 15,
        notificationVolumePercent = 65,
        mathDifficulty = MathDifficulty.HARD,
        quietAlarmEnabled = true,
        updatedAt = 1_700_000_000_000L,
        vibrationEnabled = false,
        alarmRingtoneUri = "content://media/external/audio/media/42",
        cueScheduleMode = CueScheduleMode.PERIODIC,
        remCueOffsetPercent = 70,
        autoDetectOnsetEnabled = true,
        autoCorrectWakeEnabled = true,
        smartRepeatEnabled = false,
        smartRepeatFirstDelayMinutes = 5,
        smartRepeatIntervalMinutes = 4,
        smartRepeatMaxCount = 8,
        mirrorToSystemClock = true,
        cueRingtoneUri = "content://media/external/audio/media/84"
    )

    @Test
    fun `round trip preserves all fields except updatedAt`() {
        val original = fullProfile()

        val json = ProfileJsonCodec.encode(original)
        val decoded = ProfileJsonCodec.decode(json)

        assertNotNull(decoded)

        // updatedAt не восстанавливается из JSON (импорт = новое изменение),
        // поэтому выравниваем его перед сравнением.
        assertEquals(
            original.copy(updatedAt = decoded!!.updatedAt),
            decoded
        )
    }

    @Test
    fun `schema version is written`() {
        val json = ProfileJsonCodec.encode(fullProfile())
        val obj = JSONObject(json)
        assertEquals(ProfileJsonCodec.SCHEMA_VERSION, obj.getInt("schema_version"))
    }

    @Test
    fun `null ringtone round trips as null`() {
        val original = fullProfile().copy(alarmRingtoneUri = null)
        val decoded = ProfileJsonCodec.decode(ProfileJsonCodec.encode(original))
        assertNotNull(decoded)
        assertNull(decoded!!.alarmRingtoneUri)
    }

    @Test
    fun `blank ringtone normalizes to null`() {
        val original = fullProfile().copy(alarmRingtoneUri = "   ")
        val decoded = ProfileJsonCodec.decode(ProfileJsonCodec.encode(original))
        assertNotNull(decoded)
        assertNull(decoded!!.alarmRingtoneUri)
    }

    @Test
    fun `malformed json returns null`() {
        assertNull(ProfileJsonCodec.decode("{ this is not json"))
        assertNull(ProfileJsonCodec.decode(""))
    }

    @Test
    fun `unknown keys are ignored`() {
        val original = fullProfile()
        val obj = JSONObject(ProfileJsonCodec.encode(original))
        obj.put("someFutureField", 999)
        obj.put("anotherUnknown", "hello")

        val decoded = ProfileJsonCodec.decode(obj.toString())
        assertNotNull(decoded)
        assertEquals(original.copy(updatedAt = decoded!!.updatedAt), decoded)
    }

    @Test
    fun `invalid enum falls back to default`() {
        val obj = JSONObject(ProfileJsonCodec.encode(fullProfile()))
        obj.put("cueType", "GARBAGE_VALUE")
        obj.put("cueScheduleMode", "NOPE")
        obj.put("calculationMode", "WHATEVER")

        val decoded = ProfileJsonCodec.decode(obj.toString())
        assertNotNull(decoded)

        // Дефолты берутся из актуальной AlarmProfileEntity().
        val defaults = AlarmProfileEntity()
        assertEquals(CueType.BEEP, decoded!!.cueType)
        assertEquals(CueScheduleMode.REM_TARGETED, decoded.cueScheduleMode)
        assertEquals(defaults.calculationMode, decoded.calculationMode)
    }

    @Test
    fun `missing fields fall back to defaults`() {
        // Минимальный валидный JSON — только schema.
        val decoded = ProfileJsonCodec.decode("""{"schema_version":1}""")
        assertNotNull(decoded)

        val defaults = AlarmProfileEntity()
        assertEquals(defaults.cycleLengthMinutes, decoded!!.cycleLengthMinutes)
        assertEquals(defaults.cycles, decoded.cycles)
        assertEquals(defaults.cueScheduleMode, decoded.cueScheduleMode)
        assertEquals(defaults.smartRepeatMaxCount, decoded.smartRepeatMaxCount)
        assertEquals(defaults.vibrationEnabled, decoded.vibrationEnabled)
        assertEquals(defaults.notificationVolumePercent, decoded.notificationVolumePercent)
    }

    @Test
    fun `out of range values are normalized on decode`() {
        val obj = JSONObject(ProfileJsonCodec.encode(fullProfile()))
        obj.put("remCueOffsetPercent", 5)      // вне 10..90 → 10
        obj.put("cycles", 99)                  // вне 3..7 → 7
        obj.put("cueVolumePercent", 150)       // вне 5..100 → 100
        obj.put("notificationVolumePercent", -20) // вне 0..100 → 0
        obj.put("smartRepeatMaxCount", 100)    // вне 1..20 → 20

        val decoded = ProfileJsonCodec.decode(obj.toString())
        assertNotNull(decoded)
        assertEquals(10, decoded!!.remCueOffsetPercent)
        assertEquals(7, decoded.cycles)
        assertEquals(100, decoded.cueVolumePercent)
        assertEquals(0, decoded.notificationVolumePercent)
        assertEquals(20, decoded.smartRepeatMaxCount)
    }

    @Test
    fun `encode contains every new field key`() {
        val json = ProfileJsonCodec.encode(fullProfile())
        val obj = JSONObject(json)

        // Проверяем присутствие всех ключей новых функций, чтобы encode
        // не «забыл» поле при рефакторинге.
        listOf(
            "vibrationEnabled",
            "alarmRingtoneUri",
            "cueRingtoneUri",
            "cueScheduleMode",
            "remCueOffsetPercent",
            "autoDetectOnsetEnabled",
            "autoCorrectWakeEnabled",
            "smartRepeatEnabled",
            "smartRepeatFirstDelayMinutes",
            "smartRepeatIntervalMinutes",
            "smartRepeatMaxCount",
            "mirrorToSystemClock",
            "notificationVolumePercent"
        ).forEach { key ->
            assertTrue("encode должен содержать ключ $key", obj.has(key))
        }
    }
}
