package com.personal.sleepalarm.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepCatLayoutTest {

    @Test
    fun `uses minimum edge on narrow screen`() {
        assertEquals(96f, calculateSleepCatCardEdgeDp(280f), 0.0001f)
    }

    @Test
    fun `scales edge with ordinary phone width`() {
        assertEquals(108f, calculateSleepCatCardEdgeDp(360f), 0.0001f)
    }

    @Test
    fun `caps edge on wide screen`() {
        assertEquals(124f, calculateSleepCatCardEdgeDp(500f), 0.0001f)
    }

    @Test
    fun `learning shortcuts start below the header and keep accessible targets`() {
        val geometry = calculateHomeLearningShortcutsGeometry()

        assertEquals(60f, geometry.topOffsetDp, 0.0001f)
        assertTrue(geometry.touchTargetDp >= 48f)
        assertTrue(geometry.visualDiameterDp < geometry.touchTargetDp)
    }

    @Test
    fun `two learning shortcuts form a compact overlay`() {
        val geometry = calculateHomeLearningShortcutsGeometry()

        assertEquals(96f, geometry.rowWidthDp, 0.0001f)
    }
}
