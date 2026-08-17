package com.personal.sleepalarm.ui.pomodoro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroBigCatSizingTest {

    @Test
    fun `measured size uses the tighter width boundary`() {
        assertEquals(
            0.46f,
            calculateMeasuredBigCatScale(720f, 400f, 360f, 300f, 1f),
            0.001f
        )
    }

    @Test
    fun `measured size uses the tighter height boundary`() {
        assertEquals(
            0.46f,
            calculateMeasuredBigCatScale(400f, 400f, 500f, 200f, 1f),
            0.001f
        )
    }

    @Test
    fun `sleep breathing reserve keeps the scaled frame inside`() {
        assertEquals(
            0.434f,
            calculateMeasuredBigCatScale(400f, 400f, 500f, 200f, 1.06f),
            0.002f
        )
    }

    @Test
    fun `invalid measurement has a safe fallback`() {
        assertEquals(
            0.12f,
            calculateMeasuredBigCatScale(0f, 10f, 10f, 10f, 1f),
            0.001f
        )
    }

    @Test
    fun `roaming distance follows available screen width`() {
        assertEquals(
            72f,
            calculateCatHorizontalTravel(360f, 100f),
            0.001f
        )
    }

    @Test
    fun `roaming distance never pushes a cat outside a narrow scene`() {
        assertEquals(
            0f,
            calculateCatHorizontalTravel(240f, 180f),
            0.001f
        )
    }

    @Test
    fun `rest cat hop follows a parabola`() {
        assertEquals(0f, calculateParabolicHopOffset(0f, 32f), 0.001f)
        assertEquals(-32f, calculateParabolicHopOffset(0.5f, 32f), 0.001f)
        assertEquals(0f, calculateParabolicHopOffset(1f, 32f), 0.001f)
    }

    @Test
    fun `animated frames always use one stable canvas`() {
        val shortFrame = " /\\_/\\\n( o.o )"
        val wideFrame = " /\\_/\\    o\n( o.o )"
        val normalizedShort = normalizeAnimatedCatFrame(shortFrame)
        val normalizedWide = normalizeAnimatedCatFrame(wideFrame)

        assertEquals(6, normalizedShort.lines().size)
        assertEquals(6, normalizedWide.lines().size)
        assertTrue(normalizedShort.lines().all { it.length == 15 })
        assertTrue(normalizedWide.lines().all { it.length == 15 })
        assertEquals(7, normalizedShort.lines().first { '.' in it }.indexOf('.'))
        assertEquals(7, normalizedWide.lines().first { '.' in it }.indexOf('.'))
    }
}
