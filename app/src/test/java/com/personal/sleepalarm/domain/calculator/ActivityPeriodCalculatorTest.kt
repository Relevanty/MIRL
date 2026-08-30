package com.personal.sleepalarm.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityPeriodCalculatorTest {

    @Test
    fun uniqueActiveMillis_countsCrossCategoryOverlapOnce() {
        val total = ActivityPeriodCalculator.uniqueActiveMillis(
            periodStartMillis = 0L,
            periodEndMillis = 10_000L,
            intervals = listOf(
                TrackedInterval(TrackedActivityType.STUDY, 1_000L, 6_000L),
                TrackedInterval(TrackedActivityType.WORK, 4_000L, 9_000L)
            )
        )

        assertEquals(8_000L, total)
    }
    @Test
    fun clipsIntervalsToFourAmDay() {
        val hour = 60L * 60L * 1000L
        val start = 4L * hour
        val end = 28L * hour
        val totals = ActivityPeriodCalculator.calculate(
            periodStartMillis = start,
            periodEndMillis = end,
            intervals = listOf(
                TrackedInterval(TrackedActivityType.STUDY, 3L * hour, 5L * hour),
                TrackedInterval(TrackedActivityType.WORK, 27L * hour, 29L * hour),
                TrackedInterval(TrackedActivityType.OTHER, hour, 2L * hour)
            )
        )

        assertEquals(hour, totals.studyMillis)
        assertEquals(hour, totals.workMillis)
        assertEquals(0L, totals.otherMillis)
    }

    @Test
    fun overlappingIntervalsOfSameTypeAreNotDoubleCounted() {
        val hour = 60L * 60L * 1000L
        val totals = ActivityPeriodCalculator.calculate(
            periodStartMillis = 0L,
            periodEndMillis = 24L * hour,
            intervals = listOf(
                TrackedInterval(TrackedActivityType.SLEEP, 2L * hour, 8L * hour),
                TrackedInterval(TrackedActivityType.SLEEP, 7L * hour, 9L * hour)
            )
        )

        assertEquals(7L * hour, totals.sleepMillis)
    }
}
