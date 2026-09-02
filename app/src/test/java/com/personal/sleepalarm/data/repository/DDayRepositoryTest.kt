package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.dao.DDayDao
import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.util.DeadlineLinks
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DDayRepositoryTest {
    @Test(expected = IllegalArgumentException::class)
    fun linkedCreationCannotBypassCanonicalTaskBoundary() = runBlocking {
        DDayRepository(recordingDao { _, _ -> error("No DAO write is allowed") }).addEvent(
            title = "Deadline", targetDate = "2026-10-01", taskId = 9
        )
        Unit
    }

    @Test(expected = IllegalArgumentException::class)
    fun linkedEditCannotBypassCanonicalTaskBoundary() = runBlocking {
        DDayRepository(recordingDao { _, _ -> error("No DAO write is allowed") }).update(
            DDayEntity(id = 4, title = "Deadline", targetDate = "2026-10-01", taskId = 9)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun deletingOnlyTheCountdownCannotDiscardTaskDeadlineMaterials() = runBlocking {
        val dao = Proxy.newProxyInstance(DDayDao::class.java.classLoader, arrayOf(DDayDao::class.java)) {
                _, method, _ ->
            check(method.name == "getById") { "Linked metadata must not be deleted by the standalone repository" }
            DDayEntity(id = 4, title = "Deadline", targetDate = "2026-10-01", taskId = 9, notes = "Keep this")
        } as DDayDao
        DDayRepository(dao).delete(4)
    }

    @Test
    fun createNormalizesLinksAlongsideExistingDeadlineFields() = runBlocking {
        var inserted: DDayEntity? = null
        val repository = DDayRepository(recordingDao { method, event ->
            assertEquals("insert", method)
            inserted = event
        })

        repository.addEvent(
            title = "  Отчёт  ", targetDate = "2026-10-01", projectId = 2,
            notes = "  Требования  ", links = listOf("example.com", "https://example.com", "file:///tmp")
        )

        assertEquals("Отчёт", inserted?.title)
        assertEquals("Требования", inserted?.notes)
        assertEquals(2, inserted?.projectId)
        assertEquals(null, inserted?.taskId)
        assertEquals(listOf("https://example.com"), DeadlineLinks.decode(inserted!!.linksJson))
    }

    @Test
    fun editPreservesIdentityCreationTimeAndAssociations() = runBlocking {
        var updated: DDayEntity? = null
        val repository = DDayRepository(recordingDao { method, event ->
            assertEquals("update", method)
            updated = event
        })
        val original = DDayEntity(
            id = 4, title = "Отчёт", targetDate = "2026-10-01", projectId = 2,
            createdAt = 9876L, linksJson = DeadlineLinks.encode(listOf("https://example.com"))
        )
        val edited = original.copy(title = "  Новый отчёт  ", targetDate = "2026-10-03", notes = "  Проверить  ")

        repository.update(edited)

        assertEquals(edited.copy(title = "Новый отчёт", notes = "Проверить"), updated)
    }

    private fun recordingDao(record: (String, DDayEntity) -> Unit): DDayDao = Proxy.newProxyInstance(
        DDayDao::class.java.classLoader,
        arrayOf(DDayDao::class.java)
    ) { _, method, arguments ->
        record(method.name, arguments!!.first() as DDayEntity)
        if (method.name == "insert") 1L else Unit
    } as DDayDao
}
