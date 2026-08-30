package com.personal.sleepalarm.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestSoundOperationPolicyTest {
    @Test
    fun onlyNewestGenerationWinsWithinSameSlot() {
        val policy = LatestSoundOperationPolicy()
        val first = policy.begin("cue")
        val second = policy.begin("cue")

        assertFalse(policy.isLatest("cue", first))
        assertTrue(policy.isLatest("cue", second))
    }

    @Test
    fun generationsAreIndependentBetweenSlots() {
        val policy = LatestSoundOperationPolicy()
        val cue = policy.begin("cue")
        policy.begin("alarm")

        assertTrue(policy.isLatest("cue", cue))
    }
}
