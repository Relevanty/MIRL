package com.personal.sleepalarm.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationLabelSizingTest {

    @Test
    fun `keeps configured size when every label fits`() {
        val result = calculateNavigationLabelFontSizePx(
            maximumFontSizePx = 24f,
            widestLabelWidthPx = 80f,
            availableLabelWidthPx = 90f
        )

        assertEquals(24f, result, 0.0001f)
    }

    @Test
    fun `shrinks shared size using widest label`() {
        val result = calculateNavigationLabelFontSizePx(
            maximumFontSizePx = 24f,
            widestLabelWidthPx = 120f,
            availableLabelWidthPx = 90f
        )

        assertEquals(18f, result, 0.0001f)
    }

    @Test
    fun `never increases font beyond configured maximum`() {
        val result = calculateNavigationLabelFontSizePx(
            maximumFontSizePx = 24f,
            widestLabelWidthPx = 40f,
            availableLabelWidthPx = 200f
        )

        assertEquals(24f, result, 0.0001f)
    }
}
