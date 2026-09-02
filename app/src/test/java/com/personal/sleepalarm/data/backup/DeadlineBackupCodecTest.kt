package com.personal.sleepalarm.data.backup

import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.util.DeadlineLinks
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeadlineBackupCodecTest {
    @Test
    fun roundTripPreservesDeadlineIdentityLinksAndTaskAssociation() {
        val deadline = DDayEntity(
            id = 12,
            title = "Сдать проект",
            targetDate = "2026-09-10",
            projectId = 3,
            taskId = 8,
            notes = "Материалы и требования",
            linksJson = DeadlineLinks.encode(listOf("https://example.com/brief", "https://example.org/upload")),
            createdAt = 123456L
        )
        val exported = deadlineToBackupJson(deadline)
        assertEquals(2, exported.getJSONArray("links").length())
        assertEquals(deadline, deadlineFromBackupJson(exported))
    }

    @Test
    fun olderDeadlinesRestoreWithoutLinksOrInventedAssociations() {
        val restored = deadlineFromBackupJson(JSONObject().apply {
            put("id", 7)
            put("title", "Экзамен")
            put("targetDate", "2026-09-20")
            put("createdAt", 321L)
        })
        assertEquals(7, restored.id)
        assertEquals(321L, restored.createdAt)
        assertEquals("[]", restored.linksJson)
        assertNull(restored.projectId)
        assertNull(restored.taskId)
    }

    @Test
    fun malformedOrUnsafeBackupLinksDoNotBecomeActions() {
        val value = JSONObject().apply {
            put("title", "Deadline")
            put("targetDate", "2026-09-20")
            put("links", JSONArray().put("javascript:alert(1)").put(4).put("example.com"))
        }
        assertEquals(listOf("https://example.com"), DeadlineLinks.decode(deadlineFromBackupJson(value).linksJson))
        value.put("links", "not an array")
        assertEquals("[]", deadlineFromBackupJson(value).linksJson)
    }
}
