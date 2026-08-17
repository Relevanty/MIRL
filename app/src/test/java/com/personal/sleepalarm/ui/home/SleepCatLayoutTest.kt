package com.personal.sleepalarm.ui.home

import org.junit.Assert.assertEquals
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
}
