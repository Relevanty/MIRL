package com.personal.sleepalarm.domain.externalcontext

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DaylightCalculatorTest {
    @Test
    fun `equinox near equator is approximately twelve hours`() {
        val result = DaylightCalculator.calculate(
            date = LocalDate.of(2026, 3, 20),
            location = CoarseLocation(
                cityLabel = "Quito",
                latitude = -0.18,
                longitude = -78.47,
                zoneId = "America/Guayaquil"
            )
        )

        assertEquals(PolarDaylightState.NORMAL, result.state)
        assertTrue(result.daylightMinutes in 710..735)
        assertNotNull(result.sunrise)
        assertNotNull(result.sunset)
        assertTrue(result.sunset!! > result.sunrise!!)
    }

    @Test
    fun `moscow summer day is materially longer than winter day`() {
        val location = CoarseLocation(
            cityLabel = "Moscow",
            latitude = 55.76,
            longitude = 37.62,
            zoneId = "Europe/Moscow"
        )
        val summer = DaylightCalculator.calculate(LocalDate.of(2026, 6, 21), location)
        val winter = DaylightCalculator.calculate(LocalDate.of(2026, 12, 21), location)

        assertTrue(summer.daylightMinutes > winter.daylightMinutes + 500)
        assertTrue(summer.daylightMinutes > 1_000)
        assertTrue(winter.daylightMinutes < 500)
    }

    @Test
    fun `high latitude reports polar day and polar night without fake sunrise`() {
        val location = CoarseLocation(
            cityLabel = "Tromso",
            latitude = 69.65,
            longitude = 18.96,
            zoneId = "Europe/Oslo"
        )
        val summer = DaylightCalculator.calculate(
            LocalDate.of(2026, 6, 21),
            location,
            ZoneId.of(location.zoneId)
        )
        val winter = DaylightCalculator.calculate(
            LocalDate.of(2026, 12, 21),
            location,
            ZoneId.of(location.zoneId)
        )

        assertEquals(PolarDaylightState.POLAR_DAY, summer.state)
        assertEquals(1_440, summer.daylightMinutes)
        assertEquals(null, summer.sunrise)
        assertEquals(null, summer.sunset)
        assertEquals(PolarDaylightState.POLAR_NIGHT, winter.state)
        assertEquals(0, winter.daylightMinutes)
        assertEquals(null, winter.sunrise)
        assertEquals(null, winter.sunset)
    }
}
