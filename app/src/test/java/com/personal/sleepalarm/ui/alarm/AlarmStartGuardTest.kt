package com.personal.sleepalarm.ui.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmStartGuardTest {

    @Test
    fun `legacy missing session id does not restart an initialized alarm`() {
        assertTrue(
            shouldIgnoreAlarmStart(
                alreadyStarted = true,
                currentSessionId = 42,
                requestedSessionId = null
            )
        )
    }

    @Test
    fun `explicit different session can replace previous alarm`() {
        assertFalse(
            shouldIgnoreAlarmStart(
                alreadyStarted = true,
                currentSessionId = 42,
                requestedSessionId = 43
            )
        )
    }

    @Test
    fun `first start is never ignored`() {
        assertFalse(
            shouldIgnoreAlarmStart(
                alreadyStarted = false,
                currentSessionId = null,
                requestedSessionId = null
            )
        )
    }
}
