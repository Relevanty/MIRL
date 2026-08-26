package com.personal.sleepalarm.data.repository

import androidx.room.withTransaction
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.PomodoroSessionEntity
import com.personal.sleepalarm.domain.model.FocusActivityType
import kotlinx.coroutines.flow.Flow

data class ManualActivityInput(
    val id: Int = 0,
    val taskId: Int? = null,
    val projectId: Int? = null,
    val activityType: FocusActivityType = FocusActivityType.WORK,
    val subjectId: Int? = null,
    val otherActivityId: Int? = null,
    val title: String,
    val startedAt: Long,
    val endedAt: Long,
    val result: String = "",
    val material: String = "",
    val note: String = ""
)

enum class ActivityConflictStrategy { ASK, ADJUST, REPLACE, MERGE, KEEP_PARALLEL }

sealed interface SaveActivityResult {
    data class Saved(val id: Int) : SaveActivityResult
    data class Conflicts(val records: List<ActivityRecordEntity>) : SaveActivityResult
    data class Invalid(val reason: String) : SaveActivityResult
}

/**
 * Single write boundary for actual work. It keeps TaskEntity/ProjectEntity totals
 * as disposable caches that are always rebuilt from activity_records.
 */
class ActivityRecordRepository(private val database: AppDatabase) {
    private val activities = database.activityRecordDao()
    private val pomodoros = database.pomodoroDao()
    private val tasks = database.taskDao()
    private val projects = database.projectDao()

    fun observeAll(): Flow<List<ActivityRecordEntity>> = activities.observeAll()
    fun observeForTask(taskId: Int): Flow<List<ActivityRecordEntity>> = activities.observeForTask(taskId)
    fun observeOverlapping(from: Long, to: Long): Flow<List<ActivityRecordEntity>> =
        activities.observeOverlapping(from, to)

    suspend fun getById(id: Int): ActivityRecordEntity? = activities.getById(id)
    suspend fun latestManual(): ActivityRecordEntity? = activities.getLatestManual()
    suspend fun findOverlaps(start: Long, end: Long, excludeId: Int = 0) =
        activities.findOverlaps(start, end, excludeId)

    suspend fun saveManual(
        input: ManualActivityInput,
        strategy: ActivityConflictStrategy = ActivityConflictStrategy.ASK
    ): SaveActivityResult {
        if (input.title.isBlank()) return SaveActivityResult.Invalid("title")
        if (input.endedAt <= input.startedAt) return SaveActivityResult.Invalid("duration")
        if (input.endedAt > System.currentTimeMillis() + 60_000L) {
            return SaveActivityResult.Invalid("future")
        }
        val duration = input.endedAt - input.startedAt
        if (duration > 24L * 60L * 60L * 1000L) return SaveActivityResult.Invalid("too_long")

        val conflicts = activities.findOverlaps(input.startedAt, input.endedAt, input.id)
        if (conflicts.isNotEmpty() && strategy == ActivityConflictStrategy.ASK) {
            return SaveActivityResult.Conflicts(conflicts)
        }
        if (conflicts.isNotEmpty() && strategy == ActivityConflictStrategy.ADJUST) {
            return SaveActivityResult.Conflicts(conflicts)
        }

        return database.withTransaction {
            val old: ActivityRecordEntity? = if (input.id != 0) activities.getById(input.id) else null
            val affectedTasks = linkedSetOf<Int>()
            val affectedProjects = linkedSetOf<Int>()
            old?.taskId?.let { affectedTasks.add(it) }
            old?.projectId?.let { affectedProjects.add(it) }

            var actualStart = input.startedAt
            var actualEnd = input.endedAt
            if (conflicts.isNotEmpty() && strategy == ActivityConflictStrategy.MERGE) {
                actualStart = minOf(actualStart, conflicts.minOf(ActivityRecordEntity::startedAt))
                actualEnd = maxOf(actualEnd, conflicts.maxOf(ActivityRecordEntity::endedAt))
            }
            if (conflicts.isNotEmpty() && strategy in setOf(ActivityConflictStrategy.REPLACE, ActivityConflictStrategy.MERGE)) {
                conflicts.forEach { conflict ->
                    conflict.taskId?.let { affectedTasks.add(it) }
                    conflict.projectId?.let { affectedProjects.add(it) }
                    activities.deleteById(conflict.id)
                    conflict.pomodoroSessionId?.let { pomodoros.deleteById(it) }
                }
            }

            val inputTaskId = input.taskId
            val task = if (inputTaskId != null) tasks.getById(inputTaskId) else null
            val resolvedProjectId = input.projectId ?: task?.projectId
            input.taskId?.let { affectedTasks.add(it) }
            resolvedProjectId?.let { affectedProjects.add(it) }
            val actualDuration = actualEnd - actualStart

            val oldPomodoroId = old?.pomodoroSessionId
            val pomodoro = PomodoroSessionEntity(
                id = oldPomodoroId ?: 0,
                startedAt = actualStart,
                durationMinutes = ((actualDuration + 59_999L) / 60_000L).toInt(),
                completedAt = actualEnd,
                isCompleted = true,
                isBreak = false,
                activityType = input.activityType,
                subjectId = input.subjectId,
                taskId = input.taskId,
                otherActivityId = input.otherActivityId,
                itemName = input.title.trim(),
                actualDurationMillis = actualDuration,
                recordSource = "MANUAL"
            )
            val pomodoroId = if (oldPomodoroId == null) {
                pomodoros.insert(pomodoro).toInt()
            } else {
                pomodoros.update(pomodoro)
                oldPomodoroId
            }

            val now = System.currentTimeMillis()
            val record = ActivityRecordEntity(
                id = input.id,
                taskId = input.taskId,
                projectId = resolvedProjectId,
                activityType = input.activityType,
                subjectId = input.subjectId,
                otherActivityId = input.otherActivityId,
                title = input.title.trim(),
                category = task?.category?.ifBlank { input.activityType.name } ?: input.activityType.name,
                startedAt = actualStart,
                endedAt = actualEnd,
                durationMillis = actualDuration,
                source = "MANUAL",
                result = input.result.trim(),
                material = input.material.trim(),
                note = input.note.trim(),
                pomodoroSessionId = pomodoroId,
                countsTowardProgress = true,
                createdAt = old?.createdAt ?: now,
                updatedAt = now
            )
            val id = if (input.id == 0) activities.insert(record).toInt() else {
                activities.update(record)
                input.id
            }
            rebuildTotals(affectedTasks, affectedProjects)
            SaveActivityResult.Saved(id)
        }
    }

    suspend fun recordTimer(pomodoro: PomodoroSessionEntity, pomodoroId: Int) {
        if (pomodoro.isBreak || pomodoro.actualDurationMillis <= 0L) return
        database.withTransaction {
            val timerTaskId = pomodoro.taskId
            val task = if (timerTaskId != null) tasks.getById(timerTaskId) else null
            val end = pomodoro.completedAt ?: (pomodoro.startedAt + pomodoro.actualDurationMillis)
            activities.insert(
                ActivityRecordEntity(
                    taskId = pomodoro.taskId,
                    projectId = task?.projectId,
                    activityType = pomodoro.activityType,
                    subjectId = pomodoro.subjectId,
                    otherActivityId = pomodoro.otherActivityId,
                    title = pomodoro.itemName,
                    category = task?.category?.ifBlank { pomodoro.activityType.name }
                        ?: pomodoro.activityType.name,
                    startedAt = pomodoro.startedAt,
                    endedAt = end,
                    durationMillis = pomodoro.actualDurationMillis,
                    source = pomodoro.recordSource,
                    pomodoroSessionId = pomodoroId,
                    countsTowardProgress = true
                )
            )
            rebuildTotals(
                pomodoro.taskId?.let { setOf(it) } ?: emptySet(),
                task?.projectId?.let { setOf(it) } ?: emptySet()
            )
        }
    }

    suspend fun deleteManual(id: Int): Boolean = database.withTransaction {
        val record = activities.getById(id) ?: return@withTransaction false
        if (record.source != "MANUAL") return@withTransaction false
        activities.deleteById(id)
        record.pomodoroSessionId?.let { pomodoros.deleteById(it) }
        rebuildTotals(
            record.taskId?.let { setOf(it) } ?: emptySet(),
            record.projectId?.let { setOf(it) } ?: emptySet()
        )
        true
    }

    private suspend fun rebuildTotals(taskIds: Set<Int>, projectIds: Set<Int>) {
        taskIds.forEach { id -> tasks.setSpentMillis(id, activities.sumForTask(id)) }
        projectIds.forEach { id -> projects.setSpent(id, activities.sumForProject(id)) }
    }
}
