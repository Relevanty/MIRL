package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.dao.AlarmProfileDao
import com.personal.sleepalarm.data.db.entity.AlarmProfileEntity
import com.personal.sleepalarm.domain.calculator.CueScheduleCalculator
import com.personal.sleepalarm.domain.calculator.SleepCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Репозиторий профиля настроек.
 *
 * ДОБАВЛЕНО: нормализация новых полей (F1, F2, F7, F9, F10, F11).
 */
class SleepProfileRepository(
    private val profileDao: AlarmProfileDao
) {

    fun observeProfile(): Flow<AlarmProfileEntity> {
        return profileDao.observeProfile()
            .map { entity ->
                entity ?: AlarmProfileEntity()
            }
    }

    suspend fun getProfile(): AlarmProfileEntity {
        val existing = profileDao.getProfile()
        if (existing != null) {
            return existing
        }

        val defaultProfile = AlarmProfileEntity()
        profileDao.upsert(defaultProfile)
        return defaultProfile
    }

    suspend fun updateProfile(
        transform: (AlarmProfileEntity) -> AlarmProfileEntity
    ) {
        val current = getProfile()
        val updated = transform(current)
            .copy(
                id = SINGLE_PROFILE_ID,
                updatedAt = System.currentTimeMillis()
            )

        profileDao.upsert(normalize(updated))
    }

    suspend fun setCuesEnabled(enabled: Boolean) {
        getProfile()
        profileDao.setCuesEnabled(
            enabled = enabled,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun ensureProfileExists() {
        if (profileDao.getProfile() == null) {
            profileDao.upsert(AlarmProfileEntity())
        }
    }

    /**
     * Нормализует профиль, чтобы в базу не попали случайные невалидные значения.
     *
     * ДОБАВЛЕНО: нормализация remCueOffsetPercent, smartRepeat*,
     * alarmRingtoneUri (trim/empty→null).
     */
    private fun normalize(profile: AlarmProfileEntity): AlarmProfileEntity {
        return profile.copy(
            // === Существующая нормализация (НЕ менять) ===
            cycleLengthMinutes = snapToStep(
                value = profile.cycleLengthMinutes,
                min = SleepCalculator.MIN_CYCLE_LENGTH_MINUTES,
                max = SleepCalculator.MAX_CYCLE_LENGTH_MINUTES,
                step = SleepCalculator.CYCLE_STEP_MINUTES
            ),
            cycles = profile.cycles.coerceIn(
                SleepCalculator.MIN_CYCLES,
                SleepCalculator.MAX_CYCLES
            ),
            onsetLatencyMinutes = snapToStep(
                value = profile.onsetLatencyMinutes,
                min = SleepCalculator.MIN_ONSET_LATENCY_MINUTES,
                max = SleepCalculator.MAX_ONSET_LATENCY_MINUTES,
                step = SleepCalculator.ONSET_STEP_MINUTES
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
            autoCorrectMinConfidencePercent = profile.autoCorrectMinConfidencePercent.coerceIn(50, 95),
            autoCorrectMaxShiftMinutes = profile.autoCorrectMaxShiftMinutes.coerceIn(0, 120),

            // === ДОБАВЛЕНО: F7 — нормализация remCueOffsetPercent ===
            remCueOffsetPercent = profile.remCueOffsetPercent.coerceIn(10, 90),

            // === ДОБАВЛЕНО: F10 — нормализация smartRepeat ===
            smartRepeatFirstDelayMinutes = profile.smartRepeatFirstDelayMinutes.coerceIn(1, 10),
            smartRepeatIntervalMinutes = profile.smartRepeatIntervalMinutes.coerceIn(1, 10),
            smartRepeatMaxCount = profile.smartRepeatMaxCount.coerceIn(1, 20),
            cueRingtoneUri = profile.cueRingtoneUri
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            // === ДОБАВЛЕНО: F2 — нормализация alarmRingtoneUri ===
            alarmRingtoneUri = profile.alarmRingtoneUri
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        )
    }

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

    companion object {
        const val SINGLE_PROFILE_ID = 1
    }
}
