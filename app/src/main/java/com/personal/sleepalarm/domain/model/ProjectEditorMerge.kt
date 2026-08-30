package com.personal.sleepalarm.domain.model

import com.personal.sleepalarm.data.db.entity.ProjectEntity

/** Applies only fields owned by the editor, preserving live progress and lifecycle state. */
fun mergeProjectEditorChanges(
    current: ProjectEntity,
    edited: ProjectEntity,
    updatedAt: Long
): ProjectEntity = current.copy(
    title = edited.title.trim(),
    description = edited.description.trim(),
    goal = edited.goal.trim(),
    workBudgetMinutes = edited.workBudgetMinutes.coerceIn(0, 100_000),
    updatedAt = updatedAt
)
