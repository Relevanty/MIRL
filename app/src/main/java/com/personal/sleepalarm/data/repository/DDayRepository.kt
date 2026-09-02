package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.dao.DDayDao
import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.util.DeadlineLinks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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

    fun observeMetadata(): Flow<List<DDayEntity>> = dao.observeMetadata()

    /** Ближайшее будущее событие (Flow, пересчитывается от текущей даты). */
    fun observeNearest(): Flow<DDayEntity?> =
        flow {
            while (true) {
                emit(todayString())
                delay(60_000L)
            }
        }.distinctUntilChanged().flatMapLatest(dao::observeNearest)

    suspend fun getById(id: Int): DDayEntity? = dao.getById(id)

    suspend fun addEvent(
        title: String,
        targetDate: String,
        projectId: Int? = null,
        taskId: Int? = null,
        notes: String = "",
        links: List<String> = emptyList()
    ): Long {
        require(taskId == null) { "Task deadlines must be saved through TaskEcosystemRepository" }
        return dao.insert(DDayEntity(
            title = title.trim(),
            targetDate = targetDate,
            projectId = projectId,
            taskId = taskId,
            notes = notes.trim(),
            linksJson = DeadlineLinks.encode(links)
        ))
    }

    suspend fun update(event: DDayEntity) {
        require(event.taskId == null) { "Task deadlines must be saved through TaskEcosystemRepository" }
        dao.update(event.copy(
            title = event.title.trim(),
            notes = event.notes.trim(),
            linksJson = DeadlineLinks.encode(DeadlineLinks.decode(event.linksJson))
        ))
    }

    suspend fun delete(id: Int) {
        require(dao.getById(id)?.taskId == null) { "Clear task deadlines through TaskEcosystemRepository" }
        dao.deleteById(id)
    }

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
