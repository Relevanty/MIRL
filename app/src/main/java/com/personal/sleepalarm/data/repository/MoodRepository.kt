package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.dao.MoodEntryDao
import com.personal.sleepalarm.data.db.entity.MoodEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Репозиторий настроения. Одна запись в день (REPLACE по date).
 */
class MoodRepository(
    private val dao: MoodEntryDao
) {
    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun observeAll(): Flow<List<MoodEntryEntity>> = dao.observeAll()

    /** Сохраняет настроение за сегодня (перезаписывает, если уже было). */
    suspend fun saveToday(mood: Int) {
        val today = LocalDate.now().format(dateFormat)
        dao.upsert(
            MoodEntryEntity(
                date = today,
                mood = mood.coerceIn(1, 5)
            )
        )
    }

    suspend fun getByDate(date: String): MoodEntryEntity? = dao.getByDate(date)
}