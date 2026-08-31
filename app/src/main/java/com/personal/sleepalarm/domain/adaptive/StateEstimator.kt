package com.personal.sleepalarm.domain.adaptive

/** Deterministic, bounded estimator. It performs no I/O and keeps weak priors weak. */
object StateEstimator {
    private const val BASE_CAPACITY = 0.58
    private const val MAX_SLEEP_MINUTES = 24 * 60
    private const val MINUTES_PER_DAY = 24 * 60

    fun estimate(observation: PersonalStateObservation): PersonalState {
        val explanations = mutableListOf(
            ScoreExplanation(
                factor = ScoreFactor.BASE_CAPACITY,
                contribution = BASE_CAPACITY,
                evidence = "neutral personal baseline"
            )
        )
        var rawCapacity = BASE_CAPACITY
        val observed = linkedSetOf<StateSignal>()

        val minutesSinceWake = observation.wakeTimeMillis
            ?.let { wake -> nonNegativeMinutesBetween(wake, observation.nowMillis) }
        if (minutesSinceWake != null) {
            observed += StateSignal.TIME_SINCE_WAKE
            val contribution = wakeContribution(minutesSinceWake)
            rawCapacity += contribution
            explanations += ScoreExplanation(
                factor = if (minutesSinceWake < 60) {
                    ScoreFactor.SLEEP_INERTIA
                } else {
                    ScoreFactor.TIME_AWAKE
                },
                contribution = contribution,
                evidence = "$minutesSinceWake minutes since wake"
            )
        }

        val safeSleep = observation.sleepMinutes
            ?.takeIf { it in 0..MAX_SLEEP_MINUTES && observation.targetSleepMinutes > 0 }
        var sleepDebtRatio = 0.0
        if (safeSleep != null) {
            observed += StateSignal.SLEEP_DURATION
            val target = observation.targetSleepMinutes.coerceIn(1, MAX_SLEEP_MINUTES)
            val differenceRatio = (safeSleep - target).toDouble() / target.toDouble()
            sleepDebtRatio = (-differenceRatio).coerceIn(0.0, 1.0)
            val contribution = if (differenceRatio < 0.0) {
                differenceRatio.coerceAtLeast(-1.0) * 0.30
            } else {
                differenceRatio.coerceAtMost(0.50) * 0.08
            }
            rawCapacity += contribution
            explanations += ScoreExplanation(
                factor = ScoreFactor.SLEEP_DURATION,
                contribution = contribution,
                evidence = "$safeSleep of $target target sleep minutes"
            )
        }

        val safeEnergy = observation.currentEnergy?.takeIf { it in 1..10 }
        var energyReliability = 0.0
        if (safeEnergy != null) {
            observed += StateSignal.CURRENT_ENERGY
            val measuredAt = observation.currentEnergyMeasuredAtMillis ?: observation.nowMillis
            val ratingMinutesAfterWake = observation.wakeTimeMillis
                ?.let { wake -> nonNegativeMinutesBetween(wake, measuredAt) }
            energyReliability = when {
                ratingMinutesAfterWake == null -> 1.0
                ratingMinutesAfterWake < 30 -> 0.45
                ratingMinutesAfterWake < 60 -> 0.75
                else -> 1.0
            }
            val centeredEnergy = (safeEnergy - 5.5) / 4.5
            val contribution = centeredEnergy.coerceIn(-1.0, 1.0) * 0.30 * energyReliability
            rawCapacity += contribution
            explanations += ScoreExplanation(
                factor = ScoreFactor.CURRENT_ENERGY,
                contribution = contribution,
                evidence = "self-rated energy $safeEnergy/10"
            )
            if (energyReliability < 1.0) {
                explanations += ScoreExplanation(
                    factor = ScoreFactor.PROVISIONAL_WAKE_RATING,
                    contribution = 0.0,
                    evidence = "wake-proximate rating discounted to $energyReliability reliability"
                )
            }
        }

        val safeLoad = observation.recentLoad
            ?.takeIf { it.isFinite() }
            ?.coerceIn(0.0, 1.0)
        if (safeLoad != null) {
            observed += StateSignal.RECENT_LOAD
            val contribution = -0.18 * safeLoad
            rawCapacity += contribution
            explanations += ScoreExplanation(
                factor = ScoreFactor.RECENT_LOAD,
                contribution = contribution,
                evidence = "normalized recent load $safeLoad"
            )
        }

        val safePhotoperiod = observation.photoperiodMinutes
            ?.takeIf { it in 0..MINUTES_PER_DAY }
        val safeSensitivity = observation.personalSeasonSensitivity
            .takeIf(Double::isFinite)
            ?.coerceIn(-1.0, 1.0)
            ?: 0.0
        if (safePhotoperiod != null && safeSensitivity != 0.0) {
            observed += StateSignal.SEASONAL_CONTEXT
            val daylightFromEquinox = ((safePhotoperiod - 12 * 60) / (6.0 * 60.0))
                .coerceIn(-1.0, 1.0)
            val contribution = (daylightFromEquinox * safeSensitivity * 0.03)
                .coerceIn(-0.03, 0.03)
            rawCapacity += contribution
            explanations += ScoreExplanation(
                factor = ScoreFactor.SEASONAL_PRIOR,
                contribution = contribution,
                evidence = "personal seasonal prior from $safePhotoperiod daylight minutes"
            )
        }

        val capacity = rawCapacity.coerceIn(0.0, 1.0)
        if (capacity != rawCapacity) {
            explanations += ScoreExplanation(
                factor = ScoreFactor.BOUNDS_CLAMP,
                contribution = capacity - rawCapacity,
                evidence = "capacity constrained to 0..1"
            )
        }

        val confidenceValue =
            (if (StateSignal.TIME_SINCE_WAKE in observed) 0.20 else 0.0) +
                (if (StateSignal.SLEEP_DURATION in observed) 0.25 else 0.0) +
                (if (StateSignal.CURRENT_ENERGY in observed) 0.40 * energyReliability else 0.0) +
                (if (StateSignal.RECENT_LOAD in observed) 0.10 else 0.0) +
                (if (StateSignal.SEASONAL_CONTEXT in observed) 0.05 else 0.0)
        val confidence = EstimateConfidence.of(confidenceValue, observed)
        val fatigue = (1.0 - capacity + (safeLoad ?: 0.0) * 0.08 + sleepDebtRatio * 0.08)
            .coerceIn(0.0, 1.0)

        return PersonalState(
            capacity = capacity,
            estimatedEnergy = (1.0 + capacity * 9.0).coerceIn(1.0, 10.0),
            fatigue = fatigue,
            minutesSinceWake = minutesSinceWake,
            sleepMinutes = safeSleep,
            currentEnergy = safeEnergy,
            confidence = confidence,
            explanations = explanations
        )
    }

    private fun wakeContribution(minutesSinceWake: Int): Double = when {
        minutesSinceWake < 30 -> -0.12 * (1.0 - minutesSinceWake / 30.0)
        minutesSinceWake < 60 -> -0.04 * (1.0 - (minutesSinceWake - 30) / 30.0)
        minutesSinceWake <= 14 * 60 -> 0.0
        minutesSinceWake < 18 * 60 -> -0.18 * ((minutesSinceWake - 14 * 60) / (4.0 * 60.0))
        else -> -0.18
    }

    private fun nonNegativeMinutesBetween(startMillis: Long, endMillis: Long): Int? {
        if (startMillis > endMillis) return null
        val difference = runCatching { Math.subtractExact(endMillis, startMillis) }
            .getOrElse { Long.MAX_VALUE }
        return (difference / 60_000L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }
}
