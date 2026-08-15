package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.dao.DDayDao
import com.personal.sleepalarm.data.db.entity.DDayEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Репозиторий D-Day.
 *
 * Ближайшее событие = минимальное targetDate >= сегодня.
 * daysUntil = число полных дней от сегодня до targetDate (0 = «сегодня»).
 */
class DDayRepository(
    private val dao: DDayDao
) {
    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun observeAll(): Flow<List<DDayEntity>> = dao.observeAll()

    /** Ближайшее будущее событие (Flow, пересчитывается от текущей даты). */
    fun observeNearest(): Flow<DDayEntity?> =
        dao.observeNearest(todayString())

    suspend fun getById(id: Int): DDayEntity? = dao.getById(id)

    suspend fun addEvent(title: String, targetDate: String): Long =
        dao.insert(DDayEntity(title = title.trim(), targetDate = targetDate))

    suspend fun update(event: DDayEntity) = dao.update(event)

    suspend fun delete(id: Int) = dao.deleteById(id)

    /** Дней до события (0 = сегодня). Может быть отрицательным для прошедших. */
    fun daysUntil(event: DDayEntity): Int {
        return runCatching {
            val target = LocalDate.parse(event.targetDate, dateFormat)
            ChronoUnit.DAYS.between(LocalDate.now(), target).toInt()
        }.getOrDefault(Int.MAX_VALUE)
    }

    fun todayString(): String = LocalDate.now().format(dateFormat)

    /** Валидация строки даты yyyy-MM-dd. */
    fun isValidDate(value: String): Boolean = runCatching {
        LocalDate.parse(value, dateFormat)
        true
    }.getOrDefault(false)
}