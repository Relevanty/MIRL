package com.personal.sleepalarm.ui.tasks

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskMatrixDropTest {

    @Test
    fun smallMove_keepsTaskInOriginalQuadrant() {
        val target = calculateDropTarget(
            source = TaskQuadrant.SCHEDULE,
            offset = Offset(10f, -12f),
            quadrantWidthPx = 200f,
            quadrantHeightPx = 240f
        )

        assertEquals(TaskQuadrant.SCHEDULE, target.quadrant)
        assertFalse(target.completed)
    }

    @Test
    fun diagonalMove_changesBothPriorityDimensions() {
        val target = calculateDropTarget(
            source = TaskQuadrant.NOW,
            offset = Offset(140f, 160f),
            quadrantWidthPx = 200f,
            quadrantHeightPx = 240f
        )

        assertEquals(TaskQuadrant.LET_GO, target.quadrant)
        assertFalse(target.completed)
    }

    @Test
    fun draggingBelowMatrix_marksTaskCompleted() {
        val target = calculateDropTarget(
            source = TaskQuadrant.DELEGATE,
            offset = Offset(0f, 150f),
            quadrantWidthPx = 200f,
            quadrantHeightPx = 240f
        )

        assertTrue(target.completed)
        assertEquals(null, target.quadrant)
    }

    @Test
    fun invalidStoredQuadrant_fallsBackToSchedule() {
        assertEquals(TaskQuadrant.SCHEDULE, TaskQuadrant.fromStorage(999))
    }

    @Test
    fun absoluteBallCenter_movesToTargetQuadrantRegardlessOfSourceSlot() {
        val target = calculateDropTargetAtPosition(
            position = Offset(305f, 90f),
            quadrantWidthPx = 200f,
            quadrantHeightPx = 240f
        )

        assertEquals(TaskQuadrant.SCHEDULE, target.quadrant)
        assertFalse(target.completed)
    }

    @Test
    fun absoluteBallCenter_overCompletedDock_marksTaskCompleted() {
        val target = calculateDropTargetAtPosition(
            position = Offset(180f, 445f),
            quadrantWidthPx = 200f,
            quadrantHeightPx = 240f
        )

        assertTrue(target.completed)
        assertEquals(null, target.quadrant)
    }
}
