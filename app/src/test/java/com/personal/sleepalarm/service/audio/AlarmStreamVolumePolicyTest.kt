package com.personal.sleepalarm.service.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmStreamVolumePolicyTest {
    @Test
    fun `first holder opens stream and last holder restores it`() {
        val acquired = AlarmStreamVolumePolicy.acquire(
            state = AlarmStreamVolumeState(),
            currentVolume = 3,
            maxVolume = 15
        )

        assertEquals(1, acquired.state.holderCount)
        assertEquals(3, acquired.state.originalVolume)
        assertEquals(15, acquired.state.forcedVolume)
        assertEquals(15, acquired.volumeToSet)

        val released = AlarmStreamVolumePolicy.release(acquired.state, currentVolume = 15)
        assertEquals(0, released.state.holderCount)
        assertEquals(3, released.volumeToSet)
    }

    @Test
    fun `nested holders neither reopen nor restore stream early`() {
        val first = AlarmStreamVolumePolicy.acquire(
            AlarmStreamVolumeState(),
            currentVolume = 4,
            maxVolume = 12
        )
        val second = AlarmStreamVolumePolicy.acquire(
            first.state,
            currentVolume = 12,
            maxVolume = 12
        )

        assertEquals(2, second.state.holderCount)
        assertNull(second.volumeToSet)

        val oneHolderLeft = AlarmStreamVolumePolicy.release(second.state, currentVolume = 12)
        assertEquals(1, oneHolderLeft.state.holderCount)
        assertNull(oneHolderLeft.volumeToSet)

        val finalRelease = AlarmStreamVolumePolicy.release(
            oneHolderLeft.state,
            currentVolume = 12
        )
        assertEquals(4, finalRelease.volumeToSet)
    }

    @Test
    fun `user volume change is never overwritten on release`() {
        val acquired = AlarmStreamVolumePolicy.acquire(
            AlarmStreamVolumeState(),
            currentVolume = 5,
            maxVolume = 15
        )

        val released = AlarmStreamVolumePolicy.release(acquired.state, currentVolume = 9)

        assertEquals(0, released.state.holderCount)
        assertNull(released.volumeToSet)
    }

    @Test
    fun `already maximal stream needs no write but remains restorable`() {
        val acquired = AlarmStreamVolumePolicy.acquire(
            AlarmStreamVolumeState(),
            currentVolume = 15,
            maxVolume = 15
        )
        assertNull(acquired.volumeToSet)

        val released = AlarmStreamVolumePolicy.release(acquired.state, currentVolume = 15)
        assertNull(released.volumeToSet)
    }
}
