package com.personal.sleepalarm.service.audio

import kotlin.math.pow

/**
 * Converts a user-facing loudness percentage to a player amplitude gain.
 *
 * Android player gains are linear amplitudes, while the percentage shown to a
 * person is expected to feel approximately linear in loudness. A squared curve
 * preserves silence and full scale exactly, gives quiet sounds useful low-end
 * control, and leaves materially more audible range near 100% than p / 100.
 */
object AppVolumeScale {

    fun gainForPercent(volumePercent: Int): Float =
        gainForFraction(volumePercent.coerceIn(0, 100) / 100f)

    fun gainForFraction(volumeFraction: Float): Float {
        val normalized = volumeFraction.coerceIn(0f, 1f)
        return normalized.pow(LOUDNESS_EXPONENT)
    }

    private const val LOUDNESS_EXPONENT = 2f
}
