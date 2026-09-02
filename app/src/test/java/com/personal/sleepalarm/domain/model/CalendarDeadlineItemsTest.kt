package com.personal.sleepalarm.domain.model

import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CalendarDeadlineItemsTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val task = TaskEntity(id = 7, title = "Report", dueAtMillis = Instant.parse("2026-09-12T15:30:00Z").toEpochMilli())

    @Test fun taskWithoutMetadataStillAppearsInCalendar() {
        val item = calendarDeadlineItems(emptyList(), listOf(task), zone).single()
        assertEquals(-7, item.id)
        assertEquals(7, item.taskId)
        assertEquals("2026-09-12", item.targetDate)
    }

    @Test fun taskDateAndTitleOverrideLegacySnapshotWithoutLosingMaterials() {
        val metadata = DDayEntity(id = 4, title = "Old", targetDate = "2026-01-01", taskId = 7, notes = "Keep", linksJson = "[\"https://example.com\"]")
        val item = calendarDeadlineItems(listOf(metadata), listOf(task), zone).single()
        assertEquals(task.title, item.title)
        assertEquals("2026-09-12", item.targetDate)
        assertEquals(metadata.notes, item.notes)
        assertEquals(metadata.linksJson, item.linksJson)
        assertEquals(4, item.id)
    }

    @Test fun clearingTaskDateHidesCountdownButDoesNotTurnMetadataIntoStandaloneDeadline() {
        val metadata = DDayEntity(id = 4, title = "Report", targetDate = "2026-09-12", taskId = 7)
        assertTrue(calendarDeadlineItems(listOf(metadata), listOf(task.copy(dueAtMillis = null)), zone).isEmpty())
    }

    @Test fun standaloneDatesRemainIndependent() {
        val standalone = DDayEntity(id = 8, title = "Exam", targetDate = "2026-09-18")
        assertEquals(listOf(standalone), calendarDeadlineItems(listOf(standalone), emptyList(), zone))
    }
}
