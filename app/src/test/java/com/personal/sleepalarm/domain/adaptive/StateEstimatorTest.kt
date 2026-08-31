package com.personal.sleepalarm.domain.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateEstimatorTest {
    @Test
    fun wakeProximateEnergyRatingIsProvisional() {
        val wake = 1_000_000L
        val immediate = StateEstimator.estimate(
            PersonalStateObservation(
                nowMillis = wake + minutes(10),
                wakeTimeMillis = wake,
                sleepMinutes = 8 * 60,
                currentEnergy = 9,
                currentEnergyMeasuredAtMillis = wake + minutes(10),
                recentLoad = 0.0
            )
        )
        val settled = StateEstimator.estimate(
            PersonalStateObservation(
                nowMillis = wake + minutes(90),
                wakeTimeMillis = wake,
                sleepMinutes = 8 * 60,
                currentEnergy = 9,
                currentEnergyMeasuredAtMillis = wake + minutes(90),
                recentLoad = 0.0
            )
        )

        assertTrue(settled.capacity > immediate.capacity)
        assertTrue(settled.confidence.value > immediate.confidence.value)
        assertTrue(immediate.explanations.any { it.factor == ScoreFactor.PROVISIONAL_WAKE_RATING })
    }

    @Test
    fun sleepRestrictionAndRecentLoadLowerCapacity() {
        val rested = estimate(sleepMinutes = 8 * 60, recentLoad = 0.0)
        val restricted = estimate(sleepMinutes = 4 * 60, recentLoad = 0.0)
        val loaded = estimate(sleepMinutes = 8 * 60, recentLoad = 1.0)

        assertTrue(restricted.capacity < rested.capacity)
        assertTrue(loaded.capacity < rested.capacity)
        assertTrue(restricted.fatigue > rested.fatigue)
        assertTrue(loaded.fatigue > rested.fatigue)
    }

    @Test
    fun seasonalPriorIsOptInAndStrictlyCapped() {
        val noSensitivity = estimate(
            photoperiodMinutes = 4 * 60,
            personalSeasonSensitivity = 0.0
        )
        val shortDay = estimate(
            photoperiodMinutes = 4 * 60,
            personalSeasonSensitivity = 1.0
        )
        val longDay = estimate(
            photoperiodMinutes = 20 * 60,
            personalSeasonSensitivity = 1.0
        )

        assertFalse(noSensitivity.explanations.any { it.factor == ScoreFactor.SEASONAL_PRIOR })
        val seasonalContributions = (shortDay.explanations + longDay.explanations)
            .filter { it.factor == ScoreFactor.SEASONAL_PRIOR }
        assertTrue(seasonalContributions.all { kotlin.math.abs(it.contribution) <= 0.03 })
        assertTrue(longDay.capacity - shortDay.capacity <= 0.06 + 1e-9)
    }

    @Test
    fun moreDirectSignalsIncreaseConfidence() {
        val energyOnly = StateEstimator.estimate(
            PersonalStateObservation(nowMillis = 10_000L, currentEnergy = 7)
        )
        val full = estimate()

        assertEquals(ConfidenceLevel.LOW, energyOnly.confidence.level)
        assertTrue(full.confidence.value > energyOnly.confidence.value)
        assertTrue(full.confidence.level >= ConfidenceLevel.MEDIUM)
    }

    @Test
    fun outputAndExplanationSumRemainBoundedAcrossExtremeInputs() {
        val energies = listOf(1, 5, 10)
        val sleeps = listOf(0, 240, 480, 1_440)
        val loads = listOf(0.0, 0.5, 1.0)
        energies.forEach { energy ->
            sleeps.forEach { sleep ->
                loads.forEach { load ->
                    val state = estimate(
                        sleepMinutes = sleep,
                        currentEnergy = energy,
                        recentLoad = load,
                        wakeMinutesAgo = 24 * 60
                    )
                    assertTrue(state.capacity in 0.0..1.0)
                    assertTrue(state.estimatedEnergy in 1.0..10.0)
                    assertTrue(state.fatigue in 0.0..1.0)
                    assertTrue(state.explanations.all { it.contribution.isFinite() })
                    assertEquals(
                        state.capacity,
                        state.explanations.sumOf(ScoreExplanation::contribution),
                        1e-9
                    )
                }
            }
        }
    }

    private fun estimate(
        sleepMinutes: Int = 8 * 60,
        currentEnergy: Int = 7,
        recentLoad: Double = 0.2,
        wakeMinutesAgo: Int = 120,
        photoperiodMinutes: Int? = null,
        personalSeasonSensitivity: Double = 0.0
    ): PersonalState {
        val now = 10_000_000L
        return StateEstimator.estimate(
            PersonalStateObservation(
                nowMillis = now,
                wakeTimeMillis = now - minutes(wakeMinutesAgo),
                sleepMinutes = sleepMinutes,
                currentEnergy = currentEnergy,
                currentEnergyMeasuredAtMillis = now,
                recentLoad = recentLoad,
                photoperiodMinutes = photoperiodMinutes,
                personalSeasonSensitivity = personalSeasonSensitivity
            )
        )
    }

    private fun minutes(value: Int): Long = value * 60_000L
}
