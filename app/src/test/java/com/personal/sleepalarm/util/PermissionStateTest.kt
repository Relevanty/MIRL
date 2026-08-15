package com.personal.sleepalarm.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionStateTest {

    @Test
    fun `all required permissions must be granted`() {
        val granted = PermissionState(
            notificationsGranted = true,
            exactAlarmsAllowed = true,
            batteryOptimizationDisabled = true,
            fullScreenIntentAllowed = true,
            notificationPolicyAccessGranted = true
        )

        assertTrue(granted.allRequiredGranted)
        assertFalse(granted.copy(notificationsGranted = false).allRequiredGranted)
        assertFalse(granted.copy(exactAlarmsAllowed = false).allRequiredGranted)
        assertFalse(granted.copy(batteryOptimizationDisabled = false).allRequiredGranted)
        assertFalse(granted.copy(fullScreenIntentAllowed = false).allRequiredGranted)
        assertFalse(granted.copy(notificationPolicyAccessGranted = false).allRequiredGranted)
    }
}
