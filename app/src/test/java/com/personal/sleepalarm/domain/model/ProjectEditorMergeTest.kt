package com.personal.sleepalarm.domain.model

import com.personal.sleepalarm.data.db.entity.ProjectEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectEditorMergeTest {
    @Test
    fun editorCannotOverwriteProgressOrLifecycleFieldsFromAStaleSnapshot() {
        val current = ProjectEntity(
            id = 4,
            title = "Current",
            color = 0xFF112233,
            spentMillis = 90_000L,
            dueAtMillis = 123_000L,
            isArchived = true,
            createdAt = 10L,
            updatedAt = 20L
        )
        val staleEdit = current.copy(
            title = "  Renamed  ",
            goal = "  Goal  ",
            color = 0xFF998877,
            spentMillis = 0L,
            dueAtMillis = null,
            isArchived = false
        )

        val merged = mergeProjectEditorChanges(current, staleEdit, updatedAt = 30L)

        assertEquals("Renamed", merged.title)
        assertEquals("Goal", merged.goal)
        assertEquals(90_000L, merged.spentMillis)
        assertEquals(0xFF112233, merged.color)
        assertEquals(123_000L, merged.dueAtMillis)
        assertEquals(true, merged.isArchived)
        assertEquals(10L, merged.createdAt)
        assertEquals(30L, merged.updatedAt)
    }
}
