package com.personal.sleepalarm.ui.pomodoro

import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroAsciiSizingTest {

    @Test
    fun `keeps original scale when art fits`() {
        val scale = calculateAsciiFitScale(
            measuredWidthPx = 200f,
            measuredHeightPx = 100f,
            availableWidthPx = 400f,
            availableHeightPx = 300f
        )

        assertEquals(1f, scale, 0.0001f)
    }

    @Test
    fun `shrinks art to narrow screen without wrapping`() {
        val scale = calculateAsciiFitScale(
            measuredWidthPx = 400f,
            measuredHeightPx = 100f,
            availableWidthPx = 300f,
            availableHeightPx = 300f
        )

        assertEquals(0.705f, scale, 0.0001f)
    }

    @Test
    fun `uses height when it is the tighter constraint`() {
        val scale = calculateAsciiFitScale(
            measuredWidthPx = 200f,
            measuredHeightPx = 400f,
            availableWidthPx = 400f,
            availableHeightPx = 200f
        )

        assertEquals(0.47f, scale, 0.0001f)
    }

    @Test
    fun `reserves room for breathing animation`() {
        val scale = calculateAsciiFitScale(
            measuredWidthPx = 300f,
            measuredHeightPx = 160f,
            availableWidthPx = 300f,
            availableHeightPx = 200f,
            scaleReserve = 1.06f
        )

        assertEquals(0.94f / 1.06f, scale, 0.0001f)
    }
}
