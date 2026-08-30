package com.personal.sleepalarm.domain.model

import com.personal.sleepalarm.data.db.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class NextActionRankerTest {
    @Test
    fun overdueTaskWinsOverQuadrantWithoutDeadline() {
        val now = 1_000_000L
        val ranked = NextActionRanker.rank(
            listOf(
                TaskEntity(id = 1, title = "important", matrixQuadrant = 1),
                TaskEntity(id = 2, title = "overdue", matrixQuadrant = 3, dueAtMillis = now - 1)
            ),
            now
        )

        assertEquals(listOf(2, 1), ranked.map(TaskEntity::id))
    }
}
