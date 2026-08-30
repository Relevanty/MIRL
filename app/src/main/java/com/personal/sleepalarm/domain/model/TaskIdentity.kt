package com.personal.sleepalarm.domain.model

import com.personal.sleepalarm.data.db.entity.TaskEntity

/**
 * One deterministic label for every projection of a task. Tasks may be
 * intentionally image-first, so the title itself is allowed to be empty.
 */
fun TaskEntity.primaryLabel(): String = sequenceOf(
    title,
    nextAction,
    expectedResult,
    description.lineSequence().firstOrNull().orEmpty()
).map(String::trim).firstOrNull(String::isNotEmpty) ?: "№$id"

/** Morning-routine rows are habits, not ordinary one-off tasks. */
fun Iterable<TaskEntity>.ordinaryTasks(): List<TaskEntity> =
    filterNot { it.isMorningRoutine }
