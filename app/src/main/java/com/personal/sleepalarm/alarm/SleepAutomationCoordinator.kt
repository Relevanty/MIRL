package com.personal.sleepalarm.alarm

import android.content.Context
import com.personal.sleepalarm.app.App
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.data.preferences.SleepAutomationPreference
import com.personal.sleepalarm.domain.automation.AUTOMATION_ARMED_SOURCE
import com.personal.sleepalarm.domain.automation.FocusSleepTransitionGate
import com.personal.sleepalarm.domain.automation.SleepAutomationWindow
import com.personal.sleepalarm.domain.automation.isAutomationPausedForFocus
import com.personal.sleepalarm.domain.automation.isAutomaticSleepSession
import com.personal.sleepalarm.domain.automation.resumeAutomaticDetectionAfterFocus
import com.personal.sleepalarm.service.SleepForegroundService
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class SleepAutomationCoordinator(
    private val context: Context
) {
    private val app = context.applicationContext as App
    private val locator = app.serviceLocator
    private val preference = SleepAutomationPreference(context)
    private val repository = locator.sessionRepository
    private val alarmScheduler = locator.createAlarmScheduler()

    suspend fun armCurrentWindow(nowMillis: Long = System.currentTimeMillis()): ArmResult =
        FocusSleepTransitionGate.serialized { armCurrentWindowSerialized(nowMillis) }

    private suspend fun armCurrentWindowSerialized(nowMillis: Long): ArmResult {
        val settings = preference.get()
        if (!settings.enabled) return ArmResult.DISABLED

        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val window = SleepAutomationWindow.containing(
            now,
            settings.windowStartMinutes,
            settings.windowEndMinutes
        ) ?: return ArmResult.OUTSIDE_WINDOW

        if (settings.skippedWindowStartEpochDay == window.id) return ArmResult.SKIPPED
        if (locator.database.focusProtocolDao().getActive().isNotEmpty()) return ArmResult.ACTIVE_FOCUS
        val activeSession = repository.getActiveSession()
        if (activeSession != null) {
            val originalWindow = SleepAutomationWindow.containing(
                Instant.ofEpochMilli(activeSession.bedTimePlanned).atZone(
                    runCatching { ZoneId.of(activeSession.zoneId) }.getOrDefault(zone)
                ),
                settings.windowStartMinutes,
                settings.windowEndMinutes
            )
            val canResume = activeSession.isAutomaticSleepSession() &&
                activeSession.detectedSleepOnsetTime == null &&
                activeSession.estimatedWakeTime > nowMillis &&
                originalWindow?.id == window.id
            if (canResume) {
                val resumed = if (activeSession.isAutomationPausedForFocus()) {
                    activeSession.resumeAutomaticDetectionAfterFocus()
                } else {
                    activeSession
                }
                // Register the future safety alarm before changing Room. If
                // the process dies between these operations AlarmReceiver can
                // still validate either snapshot when the alarm fires.
                if (!alarmScheduler.scheduleMainAlarm(resumed)) {
                    return ArmResult.ALARM_PERMISSION_MISSING
                }
                if (resumed != activeSession) {
                    repository.replaceCues(resumed, emptyList())
                }
                SleepForegroundService.rearmOnset(context, resumed.id)
            }
            preference.markWindowHandled(window.id)
            return ArmResult.ACTIVE_SESSION_EXISTS
        }

        val profile = locator.profileRepository.getProfile()
        if (!profile.autoDetectOnsetEnabled) {
            preference.markWindowHandled(window.id)
            return ArmResult.DETECTION_DISABLED
        }

        var hardWake = window.start.toLocalDate()
            .atTime(LocalTime.of(profile.preferredWakeHour, profile.preferredWakeMinute))
            .atZone(zone)
        while (!hardWake.isAfter(now.plusMinutes(MIN_WAKE_LEAD_MINUTES))) {
            hardWake = hardWake.plusDays(1)
        }

        val session = SleepSessionEntity(
            bedTimePlanned = nowMillis,
            sleepOnsetLatencyMinutes = profile.onsetLatencyMinutes,
            estimatedSleepStartTime = now.plusMinutes(profile.onsetLatencyMinutes.toLong())
                .toInstant().toEpochMilli(),
            cycleLengthMinutes = profile.cycleLengthMinutes,
            cyclesPlanned = profile.cycles,
            estimatedWakeTime = hardWake.toInstant().toEpochMilli(),
            automationSafetyWakeTime = hardWake.toInstant().toEpochMilli(),
            cuesEnabled = false,
            cueVolumePercent = profile.cueVolumePercent,
            cuesScheduledCount = 0,
            isActive = true,
            zoneId = zone.id,
            cueRingtoneUri = profile.cueRingtoneUri,
            detectedOnsetSource = AUTOMATION_ARMED_SOURCE
        )

        val sessionId = repository.startSessionIfNoActive(session, emptyList())
            ?: return ArmResult.ACTIVE_SESSION_EXISTS
        val saved = repository.getSession(sessionId) ?: session.copy(id = sessionId)

        if (!alarmScheduler.scheduleMainAlarm(saved)) {
            repository.cancelSession(sessionId)
            return ArmResult.ALARM_PERMISSION_MISSING
        }

        SleepForegroundService.start(
            context = context,
            sessionId = sessionId,
            wakeTime = saved.estimatedWakeTime,
            firstCueTime = null,
            showStartConfirmation = false
        )
        preference.markWindowHandled(window.id)
        return ArmResult.ARMED
    }

    enum class ArmResult {
        ARMED,
        DISABLED,
        OUTSIDE_WINDOW,
        SKIPPED,
        ACTIVE_SESSION_EXISTS,
        ACTIVE_FOCUS,
        DETECTION_DISABLED,
        ALARM_PERMISSION_MISSING
    }

    private companion object {
        const val MIN_WAKE_LEAD_MINUTES = 30L
    }
}
