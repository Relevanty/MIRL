package com.personal.sleepalarm.ui.library

import com.personal.sleepalarm.data.db.entity.LibraryItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryEditStateTest {

    @Test
    fun `new editor discards previously edited book`() {
        val previous = LibraryEditState(
            itemId = 42,
            type = LibraryItemType.MOVIE,
            title = "Первая книга",
            author = "Автор",
            coverPath = "cover.webp",
            rating = 5,
            tags = listOf("прочитано")
        )

        val fresh = previous.clearedForCreate()

        assertNull(fresh.itemId)
        assertEquals(LibraryItemType.BOOK, fresh.type)
        assertEquals("", fresh.title)
        assertEquals("", fresh.author)
        assertNull(fresh.coverPath)
        assertEquals(0, fresh.rating)
        assertEquals(emptyList<String>(), fresh.tags)
    }
}
