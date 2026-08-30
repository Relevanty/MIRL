package com.personal.sleepalarm.data.repository

import androidx.room.withTransaction
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.PomodoroSessionEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.alarm.TaskLinkedReminderCoordinator
import com.personal.sleepalarm.domain.model.focusActivityType
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

/** Canonical relational fields used by both manual history and its Pomodoro mirror. */
internal data class CanonicalManualActivityLinks(
    val taskId: Int?,
    val projectId: Int?,
    val activityType: FocusActivityType,
    val subjectId: Int?,
    val otherActivityId: Int?,
    val category: String
)

internal fun canonicalizeManualActivityLinks(
    input: ManualActivityInput,
    linkedTask: TaskEntity?
): CanonicalManualActivityLinks {
    if (linkedTask != null) {
        val taskType = linkedTask.focusActivityType()
        return CanonicalManualActivityLinks(
            taskId = linkedTask.id,
            projectId = linkedTask.projectId,
            activityType = taskType,
            subjectId = null,
            otherActivityId = null,
            category = taskType.name
        )
    }

    return CanonicalManualActivityLinks(
        taskId = null,
        projectId = input.projectId,
        activityType = input.activityType,
        subjectId = input.subjectId.takeIf { input.activityType == FocusActivityType.STUDY },
        otherActivityId = input.otherActivityId.takeIf { input.activityType == FocusActivityType.OTHER },
        category = input.activityType.name
    )
}

internal fun manualActivityCountsTowardProgress(
    taskId: Int?,
    conflicts: List<ActivityRecordEntity>,
    strategy: ActivityConflictStrategy
): Boolean {
    if (strategy != ActivityConflictStrategy.KEEP_PARALLEL || taskId == null) return true
    return conflicts.none { conflict ->
        conflict.taskId == taskId && conflict.countsTowardProgress
    }
}

/**
 * Single write boundary for actual work. It keeps TaskEntity/ProjectEntity totals
 * as disposable caches that are always rebuilt from activity_records.
 */
class ActivityRecordRepository(
    private val database: AppDatabase,
    private val reminderCoordinator: TaskLinkedReminderCoordinator? = null
) {
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

        val affectedTaskIds = linkedSetOf<Int>()
        val result = database.withTransaction {
            val old: ActivityRecordEntity? = if (input.id != 0) activities.getById(input.id) else null
            if (input.id != 0 && old == null) {
                return@withTransaction SaveActivityResult.Invalid("missing_record")
            }

            val linkedTask = input.taskId?.let { taskId ->
                tasks.getById(taskId)
                    ?: return@withTransaction SaveActivityResult.Invalid("missing_task")
            }
            val canonical = canonicalizeManualActivityLinks(input, linkedTask)

            val affectedTasks = affectedTaskIds
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

            canonical.taskId?.let { affectedTasks.add(it) }
            canonical.projectId?.let { affectedProjects.add(it) }
            val actualDuration = actualEnd - actualStart
            val countsTowardProgress = manualActivityCountsTowardProgress(
                taskId = canonical.taskId,
                conflicts = conflicts,
                strategy = strategy
            )

            val oldPomodoroId = old?.pomodoroSessionId
            val pomodoro = PomodoroSessionEntity(
                id = oldPomodoroId ?: 0,
                startedAt = actualStart,
                durationMinutes = ((actualDuration + 59_999L) / 60_000L).toInt(),
                completedAt = actualEnd,
                // Manual time is real work, but it is not a completed timer cycle.
                isCompleted = false,
                isBreak = false,
                activityType = canonical.activityType,
                subjectId = canonical.subjectId,
                taskId = canonical.taskId,
                otherActivityId = canonical.otherActivityId,
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
                taskId = canonical.taskId,
                projectId = canonical.projectId,
                activityType = canonical.activityType,
                subjectId = canonical.subjectId,
                otherActivityId = canonical.otherActivityId,
                title = input.title.trim(),
                category = canonical.category,
                startedAt = actualStart,
                endedAt = actualEnd,
                durationMillis = actualDuration,
                source = "MANUAL",
                result = input.result.trim(),
                material = input.material.trim(),
                note = input.note.trim(),
                pomodoroSessionId = pomodoroId,
                countsTowardProgress = countsTowardProgress,
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
        affectedTaskIds.forEach { reminderCoordinator?.taskChanged(it) }
        return result
    }

    suspend fun recordTimer(pomodoro: PomodoroSessionEntity, pomodoroId: Int) {
        if (pomodoro.isBreak || pomodoro.actualDurationMillis <= 0L) return
        database.withTransaction {
            val timerTaskId = pomodoro.taskId
            val task = if (timerTaskId != null) tasks.getById(timerTaskId) else null
            val canonicalPomodoro = if (task != null) {
                pomodoro.copy(
                    id = pomodoroId,
                    activityType = task.focusActivityType(),
                    subjectId = null,
                    taskId = task.id,
                    otherActivityId = null
                )
            } else {
                pomodoro.copy(id = pomodoroId)
            }
            // The timer row and canonical activity history must never disagree
            // about which task/category the same focus interval belongs to.
            pomodoros.update(canonicalPomodoro)
            val end = canonicalPomodoro.completedAt
                ?: (canonicalPomodoro.startedAt + canonicalPomodoro.actualDurationMillis)
            activities.insert(
                ActivityRecordEntity(
                    taskId = canonicalPomodoro.taskId,
                    projectId = task?.projectId,
                    activityType = canonicalPomodoro.activityType,
                    subjectId = canonicalPomodoro.subjectId,
                    otherActivityId = canonicalPomodoro.otherActivityId,
                    title = canonicalPomodoro.itemName,
                    category = canonicalPomodoro.activityType.name,
                    startedAt = canonicalPomodoro.startedAt,
                    endedAt = end,
                    durationMillis = canonicalPomodoro.actualDurationMillis,
                    source = canonicalPomodoro.recordSource,
                    pomodoroSessionId = pomodoroId,
                    countsTowardProgress = true
                )
            )
            rebuildTotals(
                canonicalPomodoro.taskId?.let { setOf(it) } ?: emptySet(),
                task?.projectId?.let { setOf(it) } ?: emptySet()
            )
        }
        pomodoro.taskId?.let { reminderCoordinator?.taskChanged(it) }
    }

    suspend fun deleteManual(id: Int): Boolean {
        var affectedTaskId: Int? = null
        val deleted = database.withTransaction {
            val record = activities.getById(id) ?: return@withTransaction false
            if (record.source != "MANUAL") return@withTransaction false
            affectedTaskId = record.taskId
            activities.deleteById(id)
            record.pomodoroSessionId?.let { pomodoros.deleteById(it) }
            rebuildTotals(
                record.taskId?.let { setOf(it) } ?: emptySet(),
                record.projectId?.let { setOf(it) } ?: emptySet()
            )
            true
        }
        if (deleted) affectedTaskId?.let { reminderCoordinator?.taskChanged(it) }
        return deleted
    }

    private suspend fun rebuildTotals(taskIds: Set<Int>, projectIds: Set<Int>) {
        taskIds.forEach { id -> tasks.setSpentMillis(id, activities.sumForTask(id)) }
        projectIds.forEach { id -> projects.setSpent(id, activities.sumForProject(id)) }
    }
}
