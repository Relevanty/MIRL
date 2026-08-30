package com.personal.sleepalarm.ui.tasks

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

    @Test
    fun sameQuadrantDrop_landsWhereReleasedInsteadOfSnappingBackFirst() {
        val releasedOffset = Offset(38f, 21f)

        val landing = calculateDropLandingOffset(
            source = TaskQuadrant.NOW,
            target = TaskDropTarget(TaskQuadrant.NOW, completed = false),
            dragOffset = releasedOffset,
            baseCenterInMatrix = Offset(60f, 70f),
            quadrantWidthPx = 200f,
            quadrantHeightPx = 240f
        )

        assertEquals(releasedOffset, landing)
    }

    @Test
    fun expandedListDrag_commitsDirectionOnlyAfterThreshold() {
        assertEquals(1, calculateReorderDirection(35f))
        assertEquals(-1, calculateReorderDirection(-35f))
        assertEquals(null, calculateReorderDirection(12f))
    }

    @Test
    fun measuredLanding_usesRealSingleRowSlots() {
        val area = TaskAreaGeometry(Offset(100f, 200f), Size(320f, 240f))

        val left = calculateTaskSlotCenter(area, taskCount = 1, targetIndex = 0, ballSizePx = 60f)
        val right = calculateTaskSlotCenter(area, taskCount = 2, targetIndex = 1, ballSizePx = 60f)

        assertEquals(196.66667f, left.x, 0.001f)
        assertEquals(320f, left.y, 0.001f)
        assertEquals(323.33334f, right.x, 0.001f)
        assertEquals(320f, right.y, 0.001f)
    }

    @Test
    fun measuredLanding_usesRealSecondRowSlot() {
        val area = TaskAreaGeometry(Offset(100f, 200f), Size(320f, 240f))

        val bottomLeft = calculateTaskSlotCenter(area, taskCount = 3, targetIndex = 2, ballSizePx = 60f)

        assertEquals(196.66667f, bottomLeft.x, 0.001f)
        assertEquals(370f, bottomLeft.y, 0.001f)
    }
}
