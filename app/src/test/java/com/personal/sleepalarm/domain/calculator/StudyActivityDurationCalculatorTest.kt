package com.personal.sleepalarm.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyActivityDurationCalculatorTest {
    @Test
    fun canonicalActivityRecordsTakePrecedenceOverLegacySessions() {
        val result = StudyActivityDurationCalculator.calculate(
            periodStartMillis = 0L,
            periodEndMillis = 10_000L,
            canonicalIntervals = listOf(StudyTimeInterval(1_000L, 3_000L)),
            legacyIntervals = listOf(StudyTimeInterval(0L, 9_000L))
        )

        assertEquals(2_000L, result)
    }

    @Test
    fun legacySessionsAreUsedOnlyAsFallbackAndOverlapsAreNotDoubleCounted() {
        val result = StudyActivityDurationCalculator.calculate(
            periodStartMillis = 1_000L,
            periodEndMillis = 8_000L,
            canonicalIntervals = emptyList(),
            legacyIntervals = listOf(
                StudyTimeInterval(0L, 5_000L),
                StudyTimeInterval(4_000L, 10_000L)
            )
        )

        assertEquals(7_000L, result)
    }
}
