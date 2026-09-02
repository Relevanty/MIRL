package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.util.DeadlineLinks
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeadlineTaskSynchronizationTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private fun due(date: String, time: LocalTime = LocalTime.MAX): Long =
        LocalDate.parse(date).atTime(time).atZone(zone).toInstant().toEpochMilli()
    private fun task(dueAt: Long? = due("2026-09-12")) = TaskEntity(
        id = 8, title = "Prepare release", dueAtMillis = dueAt, createdAt = 1L
    )
    private fun event(id: Int = 3, date: String = "2026-09-12", notes: String = "") = DDayEntity(
        id = id, title = "Release", targetDate = date, taskId = 8, notes = notes, createdAt = 10L
    )

    @Test fun `legacy date picker timestamp becomes end of selected day in Moscow`() {
        val oldPickerValue = LocalDate.parse("2026-09-12").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = canonicalizeTaskDeadlines(listOf(task(oldPickerValue)), emptyList(), true, zone)
        assertEquals(due("2026-09-12", LocalTime.of(23, 59)), state.tasks.single().dueAtMillis)
        assertEquals("2026-09-12", state.deadlines.single().targetDate)
    }

    @Test fun `legacy date picker timestamp preserves the chosen date west of UTC`() {
        val west = ZoneId.of("America/Los_Angeles")
        val selectedDate = LocalDate.parse("2026-09-12")
        val oldPickerValue = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val expected = selectedDate.atTime(23, 59).atZone(west).toInstant().toEpochMilli()
        val state = canonicalizeTaskDeadlines(listOf(task(oldPickerValue)), emptyList(), true, west)
        assertEquals(expected, state.tasks.single().dueAtMillis)
        assertEquals("2026-09-12", state.deadlines.single().targetDate)
    }

    @Test fun `new format backup preserves intentional exact UTC midnight`() {
        val exact = LocalDate.parse("2026-09-12").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = canonicalizeTaskDeadlines(listOf(task(exact)), emptyList(), false, zone)
        assertEquals(exact, state.tasks.single().dueAtMillis)
    }

    @Test fun `legacy non-midnight exact timestamps are unchanged`() {
        val exact = due("2026-09-12", LocalTime.of(14, 37, 12))
        assertEquals(exact, normalizeLegacyDateOnlyDeadline(exact, zone))
    }

    @Test fun `existing exact task timestamp always wins over a conflicting legacy event`() {
        val task = task(due("2026-09-12", LocalTime.of(14, 37)))
        val old = event(date = "2026-09-15", notes = "Don't lose this").copy(
            linksJson = DeadlineLinks.encode(listOf("example.com"))
        )
        val state = canonicalizeTaskDeadlines(listOf(task), listOf(old), true, zone)
        assertEquals(task.dueAtMillis, state.tasks.single().dueAtMillis)
        assertEquals(2, state.deadlines.size)
        val detached = state.deadlines.single { it.id == old.id }
        assertNull(detached.taskId)
        assertEquals(old.targetDate, detached.targetDate)
        assertEquals(old.linksJson, detached.linksJson)
        assertTrue(detached.notes.contains(old.notes))
        assertTrue(detached.notes.contains(task.title))
        assertEquals("2026-09-12", state.deadlines.single { it.taskId == task.id }.targetDate)
    }

    @Test fun `one unambiguous legacy date may seed an absent task deadline once`() {
        val state = canonicalizeTaskDeadlines(listOf(task(null)), listOf(event()), true, zone)
        assertEquals(due("2026-09-12"), state.tasks.single().dueAtMillis)
        assertEquals(1, state.deadlines.size)
        assertEquals(8, state.deadlines.single().taskId)
    }

    @Test fun `conflicting legacy dates never invent a task deadline`() {
        val state = canonicalizeTaskDeadlines(
            listOf(task(null)), listOf(event(), event(4, "2026-09-20")), true, zone
        )
        assertNull(state.tasks.single().dueAtMillis)
        assertEquals(2, state.deadlines.size)
        assertTrue(state.deadlines.all { it.taskId == null })
    }

    @Test fun `matching duplicates merge titles notes links and retain earliest creation`() {
        val first = event(notes = "First note").copy(linksJson = DeadlineLinks.encode(listOf("example.com")))
        val second = event(4, notes = "Second note").copy(
            title = "Submission", createdAt = 20L,
            linksJson = DeadlineLinks.encode(listOf("example.org", "example.com"))
        )
        val merged = canonicalizeTaskDeadlines(listOf(task()), listOf(first, second), true, zone).deadlines.single()
        assertEquals(first.id, merged.id)
        assertEquals(10L, merged.createdAt)
        assertTrue(merged.notes.contains("First note"))
        assertTrue(merged.notes.contains("Second note"))
        assertTrue(merged.notes.contains("Submission"))
        assertEquals(listOf("https://example.com", "https://example.org"), DeadlineLinks.decode(merged.linksJson))
    }

    @Test fun `current backup preserves cleared deadline and hidden materials`() {
        val material = event(notes = "Keep for later").copy(title = task().title)
        val restored = canonicalizeTaskDeadlines(listOf(task(null)), listOf(material), false, zone)
        assertNull(restored.tasks.single().dueAtMillis)
        assertEquals(material, restored.deadlines.single())
        assertEquals(restored, canonicalizeTaskDeadlines(restored.tasks, restored.deadlines, false, zone))
    }

    @Test fun `current linked metadata date is merely refreshed from task timestamp`() {
        val task = task(due("2026-10-22", LocalTime.of(9, 45)))
        val restored = canonicalizeTaskDeadlines(listOf(task), listOf(event()), false, zone)
        assertEquals(task.dueAtMillis, restored.tasks.single().dueAtMillis)
        assertEquals("2026-10-22", restored.deadlines.single().targetDate)
    }

    @Test fun `every task deadline gets one metadata projection even without DDay creation`() {
        val restored = canonicalizeTaskDeadlines(listOf(task()), emptyList(), true, zone)
        assertEquals(8, restored.deadlines.single().taskId)
        assertEquals(task().title, restored.deadlines.single().title)
        assertEquals(task().createdAt, restored.deadlines.single().createdAt)
    }

    @Test fun `orphan and standalone events survive`() {
        val orphan = event().copy(taskId = 999)
        val standalone = event(4).copy(taskId = null)
        val state = canonicalizeTaskDeadlines(emptyList(), listOf(orphan, standalone), true, zone)
        assertEquals(2, state.deadlines.size)
        assertTrue(state.deadlines.all { it.taskId == null })
        assertEquals(standalone, state.deadlines.single { it.id == 4 })
        assertTrue(state.deadlines.single { it.id == 3 }.notes.contains("999"))
    }

    @Test fun `normalization keeps existing identity creation and validated links`() {
        val current = event()
        val edited = current.copy(title = "  Updated  ", notes = "  Notes  ", createdAt = 90L,
            linksJson = "[\"example.org\",\"https://example.org\",\"javascript:alert(1)\"]")
        val saved = normalizeDeadlineForSave(edited, current)
        assertEquals(current.id, saved.id)
        assertEquals(current.createdAt, saved.createdAt)
        assertEquals("Updated", saved.title)
        assertEquals("Notes", saved.notes)
        assertEquals(listOf("https://example.org"), DeadlineLinks.decode(saved.linksJson))
    }

    @Test fun `cached local date follows the supplied exact timestamp`() {
        val timestamp = due("2026-10-22", LocalTime.of(23, 30))
        assertEquals("2026-10-22", taskDeadlineLocalDate(timestamp, zone))
        assertEquals("2026-10-23", taskDeadlineLocalDate(timestamp, ZoneId.of("Asia/Tokyo")))
    }
}
