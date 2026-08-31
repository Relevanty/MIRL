package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.dao.DailyCheckInDao
import com.personal.sleepalarm.data.db.entity.DailyCheckInEntity
import kotlinx.coroutines.flow.Flow

class DailyCheckInRepository(private val dao: DailyCheckInDao) {
    fun observeForDate(localDate: String): Flow<List<DailyCheckInEntity>> =
        dao.observeForDate(localDate)

    fun observeLatest(): Flow<DailyCheckInEntity?> = dao.observeLatest()

    suspend fun getById(id: Int): DailyCheckInEntity? = dao.getById(id)

    suspend fun getLatest(): DailyCheckInEntity? = dao.getLatest()

    suspend fun save(checkIn: DailyCheckInEntity): Int {
        require(checkIn.localDate.isNotBlank()) { "localDate is required" }
        require(checkIn.zoneId.isNotBlank()) { "zoneId is required" }
        val now = System.currentTimeMillis()
        val normalized = checkIn.normalizedForStorage(now)
        return if (normalized.id == 0) {
            dao.insert(normalized).toInt()
        } else {
            dao.update(normalized)
            normalized.id
        }
    }
}

internal fun DailyCheckInEntity.normalizedForStorage(
    now: Long = System.currentTimeMillis()
): DailyCheckInEntity = copy(
    localDate = localDate.trim(),
    zoneId = zoneId.trim(),
    energy = energy?.coerceIn(1, 10),
    mood = mood?.coerceIn(1, 5),
    clarity = clarity?.coerceIn(0, 4),
    focus = focus?.coerceIn(0, 4),
    social = social?.coerceIn(0, 4),
    physical = physical?.coerceIn(0, 4),
    stress = stress?.coerceIn(0, 4),
    source = source.trim().ifBlank { "AD_HOC" },
    unusualDayFlags = unusualDayFlags.trim(),
    unusualDayNote = unusualDayNote.trim(),
    createdAt = if (id == 0) now else createdAt,
    updatedAt = now
)
