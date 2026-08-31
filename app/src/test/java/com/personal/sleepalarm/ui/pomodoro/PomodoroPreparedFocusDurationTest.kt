package com.personal.sleepalarm.ui.pomodoro

import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroPreparedFocusDurationTest {

    @Test
    fun preparedDurationWinsForThePreparedTask() {
        val prepared = PreparedTaskFocusDuration(taskId = 42, minutes = 12)

        assertEquals(
            12,
            resolvePomodoroSuggestedFocusMinutes(
                selectedTaskId = 42,
                prepared = prepared,
                taskFocusMinutes = 40,
                previousFocusMinutes = 25,
                fallbackFocusMinutes = 30
            )
        )
        assertEquals(
            12,
            resolvePomodoroBoutMinutes(
                taskId = 42,
                prepared = prepared,
                defaultBoutMinutes = 40
            )
        )
    }

    @Test
    fun preparedDurationDoesNotLeakIntoAnotherTask() {
        val prepared = PreparedTaskFocusDuration(taskId = 42, minutes = 12)

        assertEquals(
            35,
            resolvePomodoroSuggestedFocusMinutes(
                selectedTaskId = 7,
                prepared = prepared,
                taskFocusMinutes = 35,
                previousFocusMinutes = 25,
                fallbackFocusMinutes = 30
            )
        )
        assertEquals(
            35,
            resolvePomodoroBoutMinutes(
                taskId = 7,
                prepared = prepared,
                defaultBoutMinutes = 35
            )
        )
    }

    @Test
    fun ordinarySelectionKeepsPreviousAndFallbackOrder() {
        assertEquals(
            20,
            resolvePomodoroSuggestedFocusMinutes(
                selectedTaskId = null,
                prepared = null,
                taskFocusMinutes = null,
                previousFocusMinutes = 20,
                fallbackFocusMinutes = 30
            )
        )
        assertEquals(
            30,
            resolvePomodoroSuggestedFocusMinutes(
                selectedTaskId = null,
                prepared = null,
                taskFocusMinutes = null,
                previousFocusMinutes = null,
                fallbackFocusMinutes = 30
            )
        )
    }

    @Test
    fun clearingPreparedDurationRestoresThePreviousFallbackOnlyWhenUntouched() {
        val prepared = PreparedTaskFocusDuration(
            taskId = 42,
            minutes = 12,
            fallbackMinutesBeforePreparation = 25
        )

        assertEquals(25, resolveFallbackAfterPreparedFocus(12, prepared))
        assertEquals(40, resolveFallbackAfterPreparedFocus(40, prepared))
    }
}
