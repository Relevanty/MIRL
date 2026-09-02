package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.util.DeadlineLinks
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

internal const val CANONICAL_DEADLINE_MODEL_VERSION = 1

internal data class CanonicalDeadlineState(
    val tasks: List<TaskEntity>,
    val deadlines: List<DDayEntity>
)

/**
 * One-time upgrade/restore normalization. Only legacy data may supply a missing
 * task date; current backups must preserve a deliberately cleared deadline.
 * Conflicting old dates become independent events, never discarded obligations.
 */
internal fun canonicalizeTaskDeadlines(
    tasks: List<TaskEntity>,
    deadlines: List<DDayEntity>,
    adoptLegacyDates: Boolean,
    zone: ZoneId = ZoneId.systemDefault()
): CanonicalDeadlineState {
    val normalizedTasks = if (adoptLegacyDates) tasks.map { task ->
        task.copy(dueAtMillis = task.dueAtMillis?.let { normalizeLegacyDateOnlyDeadline(it, zone) })
    } else tasks
    val taskById = normalizedTasks.associateBy { it.id }
    val results = mutableListOf<DDayEntity>()
    val updatedTasks = normalizedTasks.associateByTo(linkedMapOf()) { it.id }
    var nextId = (deadlines.maxOfOrNull { it.id } ?: 0).coerceAtLeast(0) + 1
    deadlines.filter { it.taskId == null }.forEach { results += it }
    deadlines.filter { it.taskId != null }.groupBy { it.taskId!! }.forEach { (taskId, group) ->
        val task = taskById[taskId]
        if (task == null) {
            results += group.map { detachLegacyDeadline(it, null) }
            return@forEach
        }
        val validDates = group.mapNotNull { row ->
            runCatching { LocalDate.parse(row.targetDate) }.getOrNull()
        }.distinct()
        val due = task.dueAtMillis ?: if (adoptLegacyDates && validDates.size == 1 &&
            group.all { it.targetDate == validDates.single().toString() }
        ) {
            validDates.single().atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
        } else null
        val canonicalTask = task.copy(dueAtMillis = due)
        updatedTasks[taskId] = canonicalTask
        val date = due?.let { taskDeadlineLocalDate(it, zone) }
        val linked = if (adoptLegacyDates) group.filter { date != null && it.targetDate == date } else group
        val detached = group.filterNot { it in linked }
        results += detached.map { detachLegacyDeadline(it, task) }
        if (linked.isNotEmpty()) {
            val ordered = linked.sortedWith(compareBy<DDayEntity> { it.createdAt }.thenBy { it.id })
            results += canonicalTaskDeadlineDetails(
                mergeDeadlineMetadata(ordered.first(), ordered.drop(1)), canonicalTask
            ).copy(
                taskId = taskId,
                targetDate = date ?: ordered.first().targetDate,
                projectId = canonicalTask.projectId
            )
        }
    }
    updatedTasks.values.filter { it.dueAtMillis != null }.forEach { task ->
        if (results.none { it.taskId == task.id }) {
            results += taskDeadlineMetadata(task, nextId++, zone)
        }
    }
    // Old/imported rows with zero or duplicate ids remain distinct instead of
    // being silently replaced by Room's INSERT OR REPLACE.
    val usedIds = mutableSetOf<Int>()
    val uniqueRows = results.map { row ->
        if (row.id > 0 && usedIds.add(row.id)) row
        else row.copy(id = nextId++).also { usedIds += it.id }
    }
    return CanonicalDeadlineState(updatedTasks.values.toList(), uniqueRows)
}

/** Old Material DatePicker persisted its chosen date as UTC midnight, not an intended alarm time. */
internal fun normalizeLegacyDateOnlyDeadline(dueAtMillis: Long, zone: ZoneId): Long {
    if (Math.floorMod(dueAtMillis, 86_400_000L) != 0L) return dueAtMillis
    val selectedDate = Instant.ofEpochMilli(dueAtMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return selectedDate.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
}

/** Canonical label/project, with legacy custom metadata retained as text instead of lost. */
internal fun canonicalTaskDeadlineDetails(metadata: DDayEntity, task: TaskEntity): DDayEntity {
    val context = buildList {
        if (metadata.title.isNotBlank() && metadata.title != task.primaryLabel()) {
            add("Название события: ${metadata.title.trim()}")
        }
        if (metadata.projectId != null && metadata.projectId != task.projectId) {
            add("Проект события: №${metadata.projectId}")
        }
    }
    return metadata.copy(
        title = task.primaryLabel(),
        projectId = task.projectId,
        notes = (listOf(metadata.notes.trim()) + context).filter(String::isNotBlank).distinct().joinToString("\n\n")
    )
}

internal fun taskDeadlineLocalDate(dueAtMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(dueAtMillis).atZone(zone).toLocalDate().toString()

internal fun taskDeadlineMetadata(
    task: TaskEntity,
    id: Int = 0,
    zone: ZoneId = ZoneId.systemDefault()
): DDayEntity = DDayEntity(
    id = id,
    title = task.primaryLabel(),
    targetDate = taskDeadlineLocalDate(requireNotNull(task.dueAtMillis), zone),
    taskId = task.id,
    projectId = task.projectId,
    createdAt = task.createdAt
)

/** Merge only metadata; retain every distinct title, note, project and link. */
internal fun mergeDeadlineMetadata(primary: DDayEntity, others: List<DDayEntity>): DDayEntity {
    val rows = listOf(primary) + others
    val notes = mutableListOf<String>()
    if (primary.notes.isNotBlank()) notes += primary.notes.trim()
    others.forEach { row ->
        val extra = buildList {
            if (row.title.isNotBlank() && row.title != primary.title) add("Название: ${row.title.trim()}")
            if (row.projectId != null && row.projectId != primary.projectId) add("Проект: №${row.projectId}")
            if (row.notes.isNotBlank()) add(row.notes.trim())
        }.joinToString("\n")
        if (extra.isNotBlank() && extra !in notes) notes += extra
    }
    return primary.copy(
        notes = notes.distinct().joinToString("\n\n"),
        linksJson = DeadlineLinks.encode(rows.flatMap { DeadlineLinks.decode(it.linksJson) }),
        createdAt = rows.minOf { it.createdAt }
    )
}

private fun detachLegacyDeadline(row: DDayEntity, task: TaskEntity?): DDayEntity {
    val context = if (task == null) "Событие бывшей задачи №${row.taskId}." else
        "Отдельная дата из задачи «${task.primaryLabel()}» (№${task.id}): ${row.targetDate}."
    return row.copy(
        taskId = null,
        notes = listOf(row.notes.trim(), context).filter(String::isNotBlank).distinct().joinToString("\n\n")
    )
}
