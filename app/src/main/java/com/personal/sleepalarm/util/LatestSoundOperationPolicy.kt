package com.personal.sleepalarm.util

/**
 * Assigns a monotonically increasing generation to each managed-sound slot.
 *
 * Picker results are processed on an IO dispatcher and can finish out of order.
 * A generation check lets the ViewModel discard stale work so the last user
 * action is always the one that is persisted.
 */
internal class LatestSoundOperationPolicy {
    private val generations = mutableMapOf<String, Long>()

    @Synchronized
    fun begin(slot: String): Long {
        val next = (generations[slot] ?: 0L) + 1L
        generations[slot] = next
        return next
    }

    @Synchronized
    fun isLatest(slot: String, generation: Long): Boolean =
        generations[slot] == generation
}
