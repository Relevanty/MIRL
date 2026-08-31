package com.personal.sleepalarm.domain.adaptive

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvailabilityCalculatorTest {
    @Test
    fun overlappingAdjacentAndOutOfRangeCommitmentsAreNormalized() {
        val result = AvailabilityCalculator.calculate(
            horizonStartMillis = minutes(0),
            horizonEndMillis = minutes(300),
            fixedWindows = listOf(
                TimeWindow(minutes(-30), minutes(20)),
                TimeWindow(minutes(60), minutes(100)),
                TimeWindow(minutes(90), minutes(140)),
                TimeWindow(minutes(140), minutes(160)),
                TimeWindow(minutes(400), minutes(500))
            ),
            minimumFreeMinutes = 0
        )

        assertEquals(
            listOf(
                TimeWindow(minutes(0), minutes(20)),
                TimeWindow(minutes(60), minutes(160))
            ),
            result.mergedFixedWindows
        )
        assertEquals(
            listOf(
                TimeWindow(minutes(20), minutes(60)),
                TimeWindow(minutes(160), minutes(300))
            ),
            result.freeWindows
        )
    }

    @Test
    fun placementRespectsReleaseFixedAndStrictFinishConstraints() {
        val free = listOf(
            TimeWindow(minutes(0), minutes(50)),
            TimeWindow(minutes(90), minutes(180))
        )

        assertEquals(
            TimeWindow(minutes(100), minutes(130)),
            AvailabilityCalculator.findEarliestPlacement(
                freeWindows = free,
                durationMinutes = 30,
                notBeforeMillis = minutes(20),
                earliestStartMillis = minutes(100)
            )
        )
        assertEquals(
            TimeWindow(minutes(110), minutes(140)),
            AvailabilityCalculator.findEarliestPlacement(
                freeWindows = free,
                durationMinutes = 30,
                notBeforeMillis = minutes(20),
                fixedStartMillis = minutes(110),
                finishByMillis = minutes(140)
            )
        )
        assertNull(
            AvailabilityCalculator.findEarliestPlacement(
                freeWindows = free,
                durationMinutes = 30,
                notBeforeMillis = minutes(20),
                fixedStartMillis = minutes(110),
                finishByMillis = minutes(139)
            )
        )
    }

    @Test
    fun randomIntervalSubtractionPreservesPartitionInvariants() {
        val random = Random(7)
        repeat(250) {
            val horizonStart = minutes(0)
            val horizonEnd = minutes(1_000)
            val fixed = List(random.nextInt(0, 25)) {
                val startMinute = random.nextInt(-200, 1_200)
                val duration = random.nextInt(1, 250)
                TimeWindow(minutes(startMinute), minutes(startMinute + duration))
            }
            val result = AvailabilityCalculator.calculate(
                horizonStartMillis = horizonStart,
                horizonEndMillis = horizonEnd,
                fixedWindows = fixed,
                minimumFreeMinutes = 0
            )

            assertSortedAndDisjoint(result.freeWindows)
            assertSortedAndDisjoint(result.mergedFixedWindows)
            assertTrue(result.freeWindows.all {
                it.startMillis >= horizonStart && it.endMillis <= horizonEnd
            })
            assertTrue(result.mergedFixedWindows.all {
                it.startMillis >= horizonStart && it.endMillis <= horizonEnd
            })
            assertFalse(result.freeWindows.any { free ->
                result.mergedFixedWindows.any(free::overlaps)
            })
            val partitionDuration = (result.freeWindows + result.mergedFixedWindows)
                .sumOf(TimeWindow::durationMillis)
            assertEquals(horizonEnd - horizonStart, partitionDuration)
        }
    }

    @Test
    fun invalidHorizonFailsClosed() {
        val result = AvailabilityCalculator.calculate(100, 100, emptyList())
        assertTrue(result.freeWindows.isEmpty())
        assertTrue(result.mergedFixedWindows.isEmpty())
    }

    private fun assertSortedAndDisjoint(windows: List<TimeWindow>) {
        windows.zipWithNext().forEach { (left, right) ->
            assertTrue(left.startMillis <= right.startMillis)
            assertTrue(left.endMillis <= right.startMillis)
        }
    }

    private fun minutes(value: Int): Long = value * 60_000L
}
