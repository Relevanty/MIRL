package com.personal.sleepalarm.domain.model

import com.personal.sleepalarm.data.db.entity.TaskEntity

/** Shared ordering for every "what should I do now" surface. */
object NextActionRanker {
    fun rank(tasks: List<TaskEntity>, nowMillis: Long = System.currentTimeMillis()): List<TaskEntity> =
        tasks.asSequence()
            .filterNot { it.isDone || it.isMorningRoutine }
            .sortedWith(
                compareBy<TaskEntity> { deadlineBucket(it.dueAtMillis, nowMillis) }
                    .thenBy { quadrantBucket(it.matrixQuadrant) }
                    .thenBy { it.startAtMillis ?: Long.MAX_VALUE }
                    .thenBy { it.dueAtMillis ?: Long.MAX_VALUE }
                    .thenBy { it.sortOrder }
            )
            .toList()

    private fun deadlineBucket(dueAtMillis: Long?, nowMillis: Long): Int = when {
        dueAtMillis == null -> 4
        dueAtMillis < nowMillis -> 0
        dueAtMillis - nowMillis <= DAY_MS -> 1
        dueAtMillis - nowMillis <= 3L * DAY_MS -> 2
        else -> 3
    }

    private fun quadrantBucket(quadrant: Int): Int = when (quadrant) {
        1 -> 0
        2 -> 1
        3 -> 2
        else -> 3
    }

    private const val DAY_MS = 24L * 60L * 60L * 1000L
}
