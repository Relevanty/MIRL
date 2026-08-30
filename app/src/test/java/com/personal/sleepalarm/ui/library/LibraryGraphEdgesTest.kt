package com.personal.sleepalarm.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryGraphEdgesTest {
    @Test
    fun edgesAreNormalizedDeduplicatedAndLimitedToCurrentItems() {
        val edges = buildLibraryGraphEdges(
            groups = listOf(
                listOf(3, 1, 404),
                listOf(1, 3),
                listOf(2, 1)
            ),
            validItemIds = setOf(1, 2, 3)
        )

        assertEquals(
            listOf(GraphEdge(1, 3), GraphEdge(1, 2)),
            edges
        )
    }
}
