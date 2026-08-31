package com.personal.sleepalarm.service.focus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.room.withTransaction
import com.personal.sleepalarm.R
import com.personal.sleepalarm.alarm.FocusProtocolReceiver
import com.personal.sleepalarm.alarm.FocusProtocolScheduler
import com.personal.sleepalarm.alarm.AlarmScheduler
import com.personal.sleepalarm.alarm.SleepAutomationScheduler
import com.personal.sleepalarm.alarm.TaskLinkedReminderCoordinator
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.EnergyObservationEntity
import com.personal.sleepalarm.data.db.entity.EnergySampleEntity
import com.personal.sleepalarm.data.db.entity.FocusProtocolSessionEntity
import com.personal.sleepalarm.data.db.entity.PomodoroSessionEntity
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.data.db.entity.StudySessionEntity
import com.personal.sleepalarm.data.repository.TaskRepository
import com.personal.sleepalarm.data.repository.ActivityRecordRepository
import com.personal.sleepalarm.data.preferences.AppSignalPreferences
import com.personal.sleepalarm.data.preferences.AppSignalType
import com.personal.sleepalarm.data.preferences.SleepAutomationPreference
import com.personal.sleepalarm.data.repository.SleepSessionRepository
import com.personal.sleepalarm.domain.automation.SleepAutomationWindow
import com.personal.sleepalarm.domain.automation.ExplicitAwakeSleepConflict
import com.personal.sleepalarm.domain.automation.FocusSleepTransitionGate
import com.personal.sleepalarm.domain.automation.conflictForExplicitAwakeAction
import com.personal.sleepalarm.domain.automation.pauseAutomaticDetectionForFocus
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.domain.model.focusItemTaskId
import com.personal.sleepalarm.domain.model.focusActivityType
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.domain.model.remainingWorkMinutesOrNull
import com.personal.sleepalarm.domain.model.taskFocusItemId
import com.personal.sleepalarm.domain.model.FocusProtocolPhase
import com.personal.sleepalarm.service.audio.AppNotificationSoundPlayer
import com.personal.sleepalarm.domain.focusaudio.FocusSoundscapeSelection
import com.personal.sleepalarm.service.audio.FocusSoundscapeController
import com.personal.sleepalarm.service.audio.FocusSoundscapeService
import com.personal.sleepalarm.service.audio.soundscapeMix
import com.personal.sleepalarm.service.SleepForegroundService
import com.personal.sleepalarm.service.AppNotificationChannelIds
import com.personal.sleepalarm.ui.MainActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class FocusProtocolConfig(
    val activityType: FocusActivityType,
    val itemId: Int,
    val itemName: String,
    val outcome: String,
    val resetMinutes: Int,
    val focusMinutes: Int,
    val recoveryMinutes: Int,
    val energyBefore: Int,
    val soundscapeId: String = "silence",
    val soundscapeCustomUri: String? = null,
    val soundscapeCustomName: String? = null,
    val soundscapeVolume: Int = 35,
    val soundscapeSecondaryId: String? = null,
    val soundscapeSecondaryVolume: Int = 20,
    val soundscapePlayDuringRecovery: Boolean = false
)

private data class CanonicalFocusTarget(
    val activityType: FocusActivityType,
    val itemId: Int,
    val itemName: String,
    val remainingTaskMinutes: Int? = null,
    val preferredBoutMinutes: Int? = null
)

private sealed interface AutomaticSleepTransition {
    data class Paused(val session: SleepSessionEntity) : AutomaticSleepTransition
    data class Cancelled(val session: SleepSessionEntity) : AutomaticSleepTransition
}

private data class FocusStartCommit(
    val session: FocusProtocolSessionEntity,
    val created: Boolean,
    val automaticSleepTransition: AutomaticSleepTransition? = null
)

/**
 * Единая точка переходов между фазами. Её используют и UI, и BroadcastReceiver,
 * поэтому один и тот же сценарий работает после уничтожения процесса.
 */
class FocusProtocolManager(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val protocolDao = database.focusProtocolDao()
    private val energyDao = database.energySampleDao()
    private val energyObservationDao = database.energyObservationDao()
    private val pomodoroDao = database.pomodoroDao()
    private val studyDao = database.studySessionDao()
    private val taskRepository = TaskRepository(database.taskDao())
    private val sleepSessionRepository = SleepSessionRepository(
        database = database,
        sessionDao = database.sleepSessionDao(),
        cueEventDao = database.cueEventDao()
    )
    private val sleepAlarmScheduler = AlarmScheduler.create(appContext, sleepSessionRepository)
    private val sleepAutomationPreference = SleepAutomationPreference(appContext)
    private val sleepAutomationScheduler = SleepAutomationScheduler(
        appContext,
        sleepAutomationPreference
    )
    private val activityRepository = ActivityRecordRepository(
        database,
        TaskLinkedReminderCoordinator(appContext, database)
    )
    private val scheduler = FocusProtocolScheduler(appContext)
    private val signalPreferences = AppSignalPreferences(appContext)
    private val soundscapeController = FocusSoundscapeController.get(appContext)
    private val runtimeSyncMutex = Mutex()
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.focus_protocol_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = appContext.getString(R.string.focus_protocol_channel_description)
                setSound(null, null)
                enableVibration(true)
                setBypassDnd(true)
            }
        )
    }

    suspend fun start(config: FocusProtocolConfig): Int =
        FocusSleepTransitionGate.serialized { startSerialized(config) }

    private suspend fun startSerialized(config: FocusProtocolConfig): Int {
        val now = System.currentTimeMillis()
        val automation = sleepAutomationPreference.get()

        // Room is the durable ownership boundary. If insertion fails, a
        // paused/cancelled automatic sleep row rolls back with it, so process
        // death cannot leave the user with neither sleep nor focus.
        val commit = database.withTransaction {
            protocolDao.getActive().firstOrNull()?.let {
                return@withTransaction FocusStartCommit(it, created = false)
            }

            val target = canonicalTarget(config.activityType, config.itemId, config.itemName)
                ?: return@withTransaction null
            val requestedFocusMinutes = config.focusMinutes.coerceIn(5, 180)
            val focusMinutes = target.remainingTaskMinutes
                ?.let { remaining -> minOf(requestedFocusMinutes, remaining) }
                ?: requestedFocusMinutes
            if (focusMinutes <= 0) return@withTransaction null

            var sleepTransition: AutomaticSleepTransition? = null
            val activeSleep = sleepSessionRepository.getActiveSession()
            when (activeSleep.conflictForExplicitAwakeAction()) {
                ExplicitAwakeSleepConflict.BLOCKED_BY_MANUAL_SLEEP -> {
                    return@withTransaction null
                }
                ExplicitAwakeSleepConflict.PAUSE_AUTOMATIC_SLEEP -> {
                    checkNotNull(activeSleep)
                    val safetyWake = activeSleep.automationSafetyWakeTime
                        ?: activeSleep.estimatedWakeTime
                    if (safetyWake <= now) {
                        sleepSessionRepository.cancelSession(activeSleep.id)
                        sleepTransition = AutomaticSleepTransition.Cancelled(activeSleep)
                    } else {
                        val paused = activeSleep.pauseAutomaticDetectionForFocus()
                        sleepSessionRepository.replaceCues(paused, emptyList())
                        sleepTransition = AutomaticSleepTransition.Paused(paused)
                    }
                }
                ExplicitAwakeSleepConflict.PROCEED -> Unit
            }

            val resetMinutes = config.resetMinutes.coerceIn(0, 20)
            val phase = if (resetMinutes == 0) {
                FocusProtocolPhase.ACTIVATE
            } else {
                FocusProtocolPhase.RESET
            }
            val session = FocusProtocolSessionEntity(
                activityType = target.activityType,
                itemId = target.itemId,
                itemName = target.itemName,
                outcome = config.outcome.trim(),
                phase = phase,
                createdAt = now,
                phaseStartedAt = now,
                phaseEndsAt = if (phase == FocusProtocolPhase.RESET) {
                    now + resetMinutes * MINUTE_MS
                } else {
                    null
                },
                resetDurationMinutes = resetMinutes,
                focusDurationMinutes = focusMinutes,
                recoveryDurationMinutes = config.recoveryMinutes.coerceIn(1, 30),
                energyBefore = config.energyBefore.coerceIn(1, 10),
                soundscapeId = config.soundscapeId,
                soundscapeCustomUri = config.soundscapeCustomUri,
                soundscapeCustomName = config.soundscapeCustomName,
                soundscapeVolume = config.soundscapeVolume.coerceIn(0, 100),
                soundscapeSecondaryId = config.soundscapeSecondaryId,
                soundscapeSecondaryVolume = config.soundscapeSecondaryVolume.coerceIn(0, 100),
                soundscapePlayDuringRecovery = config.soundscapePlayDuringRecovery
            )
            val id = protocolDao.insert(session).toInt()
            val legacyEnergySampleId = energyDao.insert(
                EnergySampleEntity(
                    timestamp = now,
                    energy = session.energyBefore,
                    context = ENERGY_BEFORE,
                    protocolSessionId = id
                )
            ).toInt()
            energyObservationDao.insert(
                EnergyObservationEntity(
                    timestamp = now,
                    absoluteEnergy = session.energyBefore,
                    context = OBSERVATION_BEFORE,
                    taskId = linkedTaskId(session.activityType, session.itemId),
                    focusProtocolSessionId = id,
                    source = OBSERVATION_SOURCE_FOCUS_PROTOCOL,
                    quality = "EXACT",
                    confidence = 1f,
                    legacyEnergySampleId = legacyEnergySampleId,
                    createdAt = now
                )
            )
            FocusStartCommit(
                session = session.copy(id = id),
                created = true,
                automaticSleepTransition = sleepTransition
            )
        } ?: return 0

        if (!commit.created) return commit.session.id

        // Focus is already durable at this point. Register its own runtime
        // first; sleep cleanup and automation retry are compensating side
        // effects and must never turn a successful start into a stuck sheet.
        runCatching { scheduler.schedule(commit.session) }
        runCatching { showNotificationAndSyncSoundscape(commit.session, alert = false) }

        when (val transition = commit.automaticSleepTransition) {
            is AutomaticSleepTransition.Paused -> {
                // Replace a possibly false early wake with the immutable
                // safety wake. AlarmReceiver repairs a stale early PI if the
                // process dies in the small Room -> AlarmManager gap.
                runCatching {
                    sleepAlarmScheduler.scheduleMainAlarm(transition.session)
                }
                runCatching {
                    SleepForegroundService.stop(appContext, cancelSession = false)
                }
                val sessionZone = runCatching { ZoneId.of(transition.session.zoneId) }
                    .getOrDefault(ZoneId.systemDefault())
                SleepAutomationWindow.containing(
                    Instant.ofEpochMilli(transition.session.bedTimePlanned).atZone(sessionZone),
                    automation.windowStartMinutes,
                    automation.windowEndMinutes
                )?.let { window ->
                    runCatching {
                        sleepAutomationPreference.releaseHandledWindow(window.id)
                    }
                }
                // Retry keeps yielding while focus is active. If the window
                // has closed, the retained alarm still rings at its safe time.
                runCatching { sleepAutomationScheduler.scheduleRetry() }
            }
            is AutomaticSleepTransition.Cancelled -> {
                runCatching {
                    sleepAlarmScheduler.cancelAllAlarmsForSession(transition.session.id)
                }
                runCatching {
                    SleepForegroundService.stop(appContext, cancelSession = false)
                }
            }
            null -> Unit
        }
        return commit.session.id
    }

    suspend fun reconcileActiveSessions(resumeSoundscape: Boolean = false) {
        // A process can die after Room committed COMPLETE/CANCELLED but before
        // the alarm/notification side effects ran. Terminal rows are the
        // durable truth, so remove any retained ongoing UI before restoring
        // live sessions.
        protocolDao.getAll()
            .filter { it.phase.isTerminal }
            .forEach { session ->
                scheduler.cancel(session.id)
                runCatching { FocusSoundscapeService.stopForTerminal(appContext, session.id) }
                cancelNotification(session.id)
            }
        val activeSessions = protocolDao.getActive()
        if (activeSessions.isEmpty()) runCatching { soundscapeController.stop() }
        activeSessions.forEach { session ->
            val target = canonicalTarget(session.activityType, session.itemId, session.itemName)
            if (target == null) {
                cancel(session.id, "MISSING_TARGET")
                return@forEach
            }
            val canonicalSession = if (
                session.activityType != target.activityType ||
                session.itemId != target.itemId ||
                session.itemName != target.itemName
            ) {
                session.copy(
                    activityType = target.activityType,
                    itemId = target.itemId,
                    itemName = target.itemName
                ).also { protocolDao.update(it) }
            } else {
                session
            }
            val end = canonicalSession.phaseEndsAt
            if (session.phase.hasCountdown && end != null && end <= System.currentTimeMillis()) {
                advanceIfDue(canonicalSession.id)
            } else {
                scheduler.schedule(canonicalSession)
                if (resumeSoundscape) {
                    showNotificationAndSyncSoundscape(canonicalSession, alert = false)
                } else {
                    // Android 15 forbids starting a mediaPlayback FGS from BOOT_COMPLETED.
                    // Durable timer/notification state is restored here; audio resumes when
                    // the focus UI is foregrounded or the service itself is redelivered.
                    showNotification(canonicalSession, alert = false)
                }
            }
        }
    }

    suspend fun advanceIfDue(
        sessionId: Int,
        expectedPhase: FocusProtocolPhase? = null,
        expectedEnd: Long? = null
    ) {
        val session = protocolDao.getById(sessionId) ?: return
        if (expectedPhase != null && session.phase != expectedPhase) return
        if (expectedEnd != null && session.phaseEndsAt != expectedEnd) return
        val end = session.phaseEndsAt ?: return
        val now = System.currentTimeMillis()
        if (end > now) {
            scheduler.schedule(session)
            return
        }
        when (session.phase) {
            FocusProtocolPhase.RESET -> {
                val updated = database.withTransaction {
                    val latest = protocolDao.getById(sessionId) ?: return@withTransaction null
                    if (latest.phase != FocusProtocolPhase.RESET ||
                        (expectedEnd != null && latest.phaseEndsAt != expectedEnd) ||
                        (latest.phaseEndsAt ?: Long.MAX_VALUE) > System.currentTimeMillis()
                    ) return@withTransaction null
                    val updated = latest.copy(
                        phase = FocusProtocolPhase.ACTIVATE,
                        phaseStartedAt = System.currentTimeMillis(),
                        phaseEndsAt = null
                    )
                    protocolDao.update(updated)
                    updated
                } ?: return
                scheduler.cancel(sessionId)
                showNotificationAndSyncSoundscape(updated, alert = true)
            }
            FocusProtocolPhase.FOCUS -> finishFocusInternal(
                session = session,
                focusEndAt = end,
                transitionAt = now,
                completed = true,
                expectedPhase = expectedPhase,
                expectedEnd = expectedEnd
            )?.let { updated ->
                scheduler.schedule(updated)
                showNotificationAndSyncSoundscape(updated, alert = true)
            }
            FocusProtocolPhase.RECOVERY -> {
                val updated = database.withTransaction {
                    val latest = protocolDao.getById(sessionId) ?: return@withTransaction null
                    if (latest.phase != FocusProtocolPhase.RECOVERY ||
                        (expectedEnd != null && latest.phaseEndsAt != expectedEnd) ||
                        (latest.phaseEndsAt ?: Long.MAX_VALUE) > System.currentTimeMillis()
                    ) return@withTransaction null
                    val updated = latest.copy(
                        phase = FocusProtocolPhase.CYCLE_READY,
                        phaseStartedAt = System.currentTimeMillis(),
                        phaseEndsAt = null
                    )
                    protocolDao.update(updated)
                    updated
                } ?: return
                scheduler.cancel(sessionId)
                showNotificationAndSyncSoundscape(updated, alert = true)
            }
            else -> scheduler.cancel(sessionId)
        }
    }

    suspend fun skipReset(sessionId: Int) {
        val updated = database.withTransaction {
            val session = protocolDao.getById(sessionId) ?: return@withTransaction null
            if (session.phase != FocusProtocolPhase.RESET) return@withTransaction null
            val updated = session.copy(
                phase = FocusProtocolPhase.ACTIVATE,
                phaseStartedAt = System.currentTimeMillis(),
                phaseEndsAt = null
            )
            protocolDao.update(updated)
            updated
        } ?: return
        scheduler.cancel(sessionId)
        showNotificationAndSyncSoundscape(updated, alert = false)
    }

    suspend fun startFocus(sessionId: Int) {
        val updated = database.withTransaction {
            val session = protocolDao.getById(sessionId) ?: return@withTransaction null
            if (session.phase != FocusProtocolPhase.ACTIVATE) return@withTransaction null
            val now = System.currentTimeMillis()
            val updated = session.copy(
                phase = FocusProtocolPhase.FOCUS,
                phaseStartedAt = now,
                phaseEndsAt = now + session.focusDurationMinutes * MINUTE_MS,
                focusStartedAt = now,
                focusElapsedMillis = 0L,
                pausedRemainingMillis = 0L
            )
            protocolDao.update(updated)
            updated
        } ?: return
        scheduler.schedule(updated)
        showNotificationAndSyncSoundscape(updated, alert = false)
    }

    suspend fun startNextCycle(
        sessionId: Int,
        activityType: FocusActivityType? = null,
        itemId: Int? = null,
        itemName: String? = null,
        outcome: String? = null
    ) {
        val session = protocolDao.getById(sessionId) ?: return
        if (session.phase != FocusProtocolPhase.CYCLE_READY) return
        val target = canonicalTarget(
            activityType ?: session.activityType,
            itemId ?: session.itemId,
            itemName ?: session.itemName
        ) ?: return
        val preferred = if (activityType != null || itemId != null) {
            target.preferredBoutMinutes ?: session.focusDurationMinutes
        } else {
            session.focusDurationMinutes
        }
        val focusMinutes = target.remainingTaskMinutes
            ?.let { remaining -> minOf(preferred, remaining) }
            ?: preferred
        if (focusMinutes <= 0) return
        val updated = database.withTransaction {
            val latest = protocolDao.getById(sessionId) ?: return@withTransaction null
            if (latest.phase != FocusProtocolPhase.CYCLE_READY) return@withTransaction null
            val now = System.currentTimeMillis()
            val updated = latest.copy(
                activityType = target.activityType,
                itemId = target.itemId,
                itemName = target.itemName,
                outcome = outcome?.trim()?.takeIf { it.isNotEmpty() } ?: latest.outcome,
                focusDurationMinutes = focusMinutes,
                phase = FocusProtocolPhase.FOCUS,
                phaseStartedAt = now,
                phaseEndsAt = now + focusMinutes * MINUTE_MS,
                focusStartedAt = now,
                focusElapsedMillis = 0L,
                pausedRemainingMillis = 0L,
                pomodoroRecorded = false
            )
            protocolDao.update(updated)
            updated
        } ?: return
        scheduler.schedule(updated)
        showNotificationAndSyncSoundscape(updated, alert = false)
    }

    suspend fun pauseFocus(sessionId: Int) {
        val updated = database.withTransaction {
            val session = protocolDao.getById(sessionId) ?: return@withTransaction null
            if (session.phase != FocusProtocolPhase.FOCUS) return@withTransaction null
            val now = System.currentTimeMillis()
            val updated = session.copy(
                phase = FocusProtocolPhase.FOCUS_PAUSED,
                phaseStartedAt = now,
                phaseEndsAt = null,
                focusElapsedMillis = elapsedFocusAt(session, now),
                pausedRemainingMillis = ((session.phaseEndsAt ?: now) - now).coerceAtLeast(0L)
            )
            protocolDao.update(updated)
            updated
        } ?: return
        scheduler.cancel(sessionId)
        showNotificationAndSyncSoundscape(updated, alert = false)
    }

    suspend fun resumeFocus(sessionId: Int) {
        val updated = database.withTransaction {
            val session = protocolDao.getById(sessionId) ?: return@withTransaction null
            if (session.phase != FocusProtocolPhase.FOCUS_PAUSED) return@withTransaction null
            val now = System.currentTimeMillis()
            val remaining = session.pausedRemainingMillis.coerceAtLeast(1_000L)
            val updated = session.copy(
                phase = FocusProtocolPhase.FOCUS,
                phaseStartedAt = now,
                phaseEndsAt = now + remaining,
                pausedRemainingMillis = 0L
            )
            protocolDao.update(updated)
            updated
        } ?: return
        scheduler.schedule(updated)
        showNotificationAndSyncSoundscape(updated, alert = false)
    }

    suspend fun finishFocus(sessionId: Int) {
        val session = protocolDao.getById(sessionId) ?: return
        if (session.phase != FocusProtocolPhase.FOCUS &&
            session.phase != FocusProtocolPhase.FOCUS_PAUSED
        ) return
        val now = System.currentTimeMillis()
        finishFocusInternal(session, focusEndAt = now, transitionAt = now, completed = true)
            ?.let { updated ->
                scheduler.schedule(updated)
                showNotificationAndSyncSoundscape(updated, alert = true)
            }
    }

    suspend fun finishRecovery(sessionId: Int) {
        val updated = database.withTransaction {
            val session = protocolDao.getById(sessionId) ?: return@withTransaction null
            if (session.phase != FocusProtocolPhase.RECOVERY) return@withTransaction null
            val updated = session.copy(
                phase = FocusProtocolPhase.CYCLE_READY,
                phaseStartedAt = System.currentTimeMillis(),
                phaseEndsAt = null
            )
            protocolDao.update(updated)
            updated
        } ?: return
        scheduler.cancel(sessionId)
        showNotificationAndSyncSoundscape(updated, alert = false)
    }

    suspend fun finishBlock(sessionId: Int) {
        val updated = database.withTransaction {
            val session = protocolDao.getById(sessionId) ?: return@withTransaction null
            if (session.phase != FocusProtocolPhase.CYCLE_READY) return@withTransaction null
            val updated = session.copy(
                phase = FocusProtocolPhase.REVIEW,
                phaseStartedAt = System.currentTimeMillis(),
                phaseEndsAt = null
            )
            protocolDao.update(updated)
            updated
        } ?: return
        scheduler.cancel(sessionId)
        showNotificationAndSyncSoundscape(updated, alert = false)
    }

    suspend fun incrementDistraction(sessionId: Int) {
        protocolDao.incrementDistractions(sessionId)
    }

    suspend fun cancel(sessionId: Int, reason: String) {
        val cancelled = database.withTransaction {
            val session = protocolDao.getById(sessionId) ?: return@withTransaction false
            if (session.phase.isTerminal) return@withTransaction false
            val now = System.currentTimeMillis()
            val elapsed = elapsedFocusAt(session, now)
            if ((session.phase == FocusProtocolPhase.FOCUS ||
                    session.phase == FocusProtocolPhase.FOCUS_PAUSED) &&
                !session.pomodoroRecorded
            ) {
                recordFocus(session, elapsed, now, completed = false)
            }
            val cancelled = session.copy(
                phase = FocusProtocolPhase.CANCELLED,
                phaseStartedAt = now,
                phaseEndsAt = null,
                focusElapsedMillis = elapsed,
                completedAt = now,
                cancelReason = reason,
                pomodoroRecorded = session.pomodoroRecorded || elapsed >= MIN_RECORDED_FOCUS_MS,
                totalFocusMillis = session.totalFocusMillis +
                    if (session.pomodoroRecorded) 0L else elapsed
            )
            protocolDao.update(cancelled)
            true
        }
        if (!cancelled) return
        scheduler.cancel(sessionId)
        runCatching { FocusSoundscapeService.stopForTerminal(appContext, sessionId) }
        cancelNotification(sessionId)
    }

    suspend fun completeReview(sessionId: Int, energyAfter: Int) {
        val safeEnergy = energyAfter.coerceIn(1, 10)
        val completed = database.withTransaction {
            val session = protocolDao.getById(sessionId) ?: return@withTransaction false
            if (session.phase != FocusProtocolPhase.REVIEW) return@withTransaction false
            val now = System.currentTimeMillis()
            val legacyEnergySampleId = energyDao.insert(
                EnergySampleEntity(
                    timestamp = now,
                    energy = safeEnergy,
                    context = ENERGY_AFTER,
                    protocolSessionId = session.id
                )
            ).toInt()
            energyObservationDao.insert(
                EnergyObservationEntity(
                    timestamp = now,
                    absoluteEnergy = safeEnergy,
                    context = OBSERVATION_AFTER,
                    taskId = linkedTaskId(session.activityType, session.itemId),
                    focusProtocolSessionId = session.id,
                    source = OBSERVATION_SOURCE_FOCUS_PROTOCOL,
                    quality = "EXACT",
                    confidence = 1f,
                    legacyEnergySampleId = legacyEnergySampleId,
                    createdAt = now
                )
            )
            protocolDao.update(
                session.copy(
                    phase = FocusProtocolPhase.COMPLETE,
                    phaseStartedAt = now,
                    phaseEndsAt = null,
                    energyAfter = safeEnergy,
                    completedAt = now
                )
            )
            true
        }
        if (!completed) return
        scheduler.cancel(sessionId)
        runCatching { FocusSoundscapeService.stopForTerminal(appContext, sessionId) }
        cancelNotification(sessionId)
    }

    suspend fun updateSoundscape(
        sessionId: Int,
        selection: FocusSoundscapeSelection,
        primaryVolumePercent: Int
    ): FocusProtocolSessionEntity? {
        val safe = selection.normalized()
        val changed = protocolDao.updateSoundscape(
            id = sessionId,
            primaryId = safe.primary.catalogId,
            customUri = safe.primary.customFile?.uriString,
            customName = safe.primary.customFile?.displayName,
            primaryVolume = primaryVolumePercent.coerceIn(0, 100),
            secondaryId = safe.secondaryLayerId,
            secondaryVolume = safe.secondaryVolumePercent.coerceIn(0, 100),
            playDuringRecovery = safe.playDuringRecovery
        )
        if (changed == 0) return null
        val updated = protocolDao.getById(sessionId) ?: return null
        showNotificationAndSyncSoundscape(updated, alert = false)
        return updated
    }

    private suspend fun finishFocusInternal(
        session: FocusProtocolSessionEntity,
        focusEndAt: Long,
        transitionAt: Long,
        completed: Boolean,
        expectedPhase: FocusProtocolPhase? = null,
        expectedEnd: Long? = null
    ): FocusProtocolSessionEntity? {
        var updated: FocusProtocolSessionEntity? = null
        database.withTransaction {
            val latest = protocolDao.getById(session.id) ?: return@withTransaction
            if (expectedPhase != null && latest.phase != expectedPhase) return@withTransaction
            if (expectedEnd != null && latest.phaseEndsAt != expectedEnd) return@withTransaction
            if (latest.phase != FocusProtocolPhase.FOCUS &&
                latest.phase != FocusProtocolPhase.FOCUS_PAUSED
            ) return@withTransaction
            val elapsed = elapsedFocusAt(latest, focusEndAt)
            if (!latest.pomodoroRecorded) {
                recordFocus(latest, elapsed, focusEndAt, completed)
            }
            updated = latest.copy(
                phase = FocusProtocolPhase.RECOVERY,
                phaseStartedAt = transitionAt,
                phaseEndsAt = transitionAt + latest.recoveryDurationMinutes * MINUTE_MS,
                focusElapsedMillis = elapsed,
                pausedRemainingMillis = 0L,
                pomodoroRecorded = latest.pomodoroRecorded || elapsed >= MIN_RECORDED_FOCUS_MS,
                completedCycles = latest.completedCycles +
                    if (elapsed >= MIN_RECORDED_FOCUS_MS) 1 else 0,
                totalFocusMillis = latest.totalFocusMillis + elapsed
            )
            protocolDao.update(updated!!)
        }
        return updated
    }

    private suspend fun recordFocus(
        session: FocusProtocolSessionEntity,
        elapsed: Long,
        completedAt: Long,
        completed: Boolean
    ) {
        if (elapsed < MIN_RECORDED_FOCUS_MS) return
        val startedAt = session.focusStartedAt ?: (completedAt - elapsed)
        val linkedTaskId = focusItemTaskId(session.itemId)
            ?: session.itemId.takeIf { session.activityType == FocusActivityType.WORK }
                ?.takeIf { taskRepository.getById(it) != null }
        val pomodoro = PomodoroSessionEntity(
                startedAt = startedAt,
                durationMinutes = ((elapsed + MINUTE_MS - 1L) / MINUTE_MS).toInt(),
                completedAt = completedAt,
                isCompleted = completed,
                isBreak = false,
                activityType = session.activityType,
                subjectId = session.itemId.takeIf {
                    it > 0 && linkedTaskId == null && session.activityType == FocusActivityType.STUDY
                },
                taskId = linkedTaskId,
                otherActivityId = session.itemId.takeIf {
                    it > 0 && linkedTaskId == null && session.activityType == FocusActivityType.OTHER
                },
                itemName = session.itemName,
                actualDurationMillis = elapsed,
                recordSource = "TIMER"
            )
        val pomodoroId = pomodoroDao.insert(pomodoro).toInt()
        activityRepository.recordTimer(pomodoro, pomodoroId)
        if (session.activityType == FocusActivityType.STUDY && linkedTaskId == null && session.itemId > 0) {
            studyDao.insert(
                StudySessionEntity(
                    subjectId = session.itemId,
                    startMillis = startedAt,
                    endMillis = completedAt,
                    durationMillis = elapsed,
                    dateKey = dateKeyOf(startedAt)
                )
            )
        }
    }

    private fun elapsedFocusAt(session: FocusProtocolSessionEntity, atMillis: Long): Long {
        if (session.focusStartedAt == null) return session.focusElapsedMillis
        if (session.phase == FocusProtocolPhase.FOCUS_PAUSED) return session.focusElapsedMillis
        if (session.phase != FocusProtocolPhase.FOCUS) return session.focusElapsedMillis
        val legEnd = minOf(atMillis, session.phaseEndsAt ?: atMillis)
        return session.focusElapsedMillis +
            (legEnd - session.phaseStartedAt).coerceAtLeast(0L)
    }

    /** Resolves both current encoded task targets and legacy positive WORK ids. */
    private suspend fun linkedTaskId(activityType: FocusActivityType, itemId: Int): Int? {
        val candidate = focusItemTaskId(itemId)
            ?: itemId.takeIf { activityType == FocusActivityType.WORK && it > 0 }
        return candidate?.takeIf { database.taskDao().getById(it) != null }
    }

    private suspend fun canonicalTarget(
        requestedType: FocusActivityType,
        requestedItemId: Int,
        requestedName: String
    ): CanonicalFocusTarget? {
        val encodedTaskId = focusItemTaskId(requestedItemId)
        val legacyTaskId = requestedItemId.takeIf {
            requestedType == FocusActivityType.WORK && it > 0
        }
        val taskId = encodedTaskId ?: legacyTaskId
        if (taskId != null) {
            val task = taskRepository.getById(taskId) ?: return null
            if (task.isDone) return null
            return CanonicalFocusTarget(
                activityType = task.focusActivityType(),
                itemId = taskFocusItemId(task.id),
                itemName = task.primaryLabel(),
                remainingTaskMinutes = task.remainingWorkMinutesOrNull(),
                preferredBoutMinutes = task.estimatedMinutes.coerceIn(1, 180)
            )
        }
        val name = requestedName.trim()
        if (requestedItemId <= 0 || name.isEmpty()) return null
        return CanonicalFocusTarget(requestedType, requestedItemId, name)
    }

    private suspend fun showNotification(session: FocusProtocolSessionEntity, alert: Boolean) {
        val title = when (session.phase) {
            FocusProtocolPhase.RESET -> R.string.focus_protocol_phase_reset
            FocusProtocolPhase.ACTIVATE -> R.string.focus_protocol_phase_activate
            FocusProtocolPhase.FOCUS -> R.string.focus_protocol_phase_focus
            FocusProtocolPhase.FOCUS_PAUSED -> R.string.focus_protocol_phase_paused
            FocusProtocolPhase.RECOVERY -> R.string.focus_protocol_phase_recovery
            FocusProtocolPhase.CYCLE_READY -> R.string.focus_protocol_phase_cycle_ready
            FocusProtocolPhase.REVIEW -> R.string.focus_protocol_phase_review
            else -> return
        }
        val openIntent = PendingIntent.getActivity(
            appContext,
            session.id,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_FOCUS_PROTOCOL)
                putExtra(FocusProtocolReceiver.EXTRA_SESSION_ID, session.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(title))
            .setSubText(session.itemName)
            .setContentText(session.outcome.ifBlank { session.itemName })
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            // Even if Android retained a user-selected channel sound, the
            // visual notification must never become a second audio source.
            .setSilent(true)
            .setAutoCancel(false)
            .setOngoing(!session.phase.isTerminal)
            .setPriority(if (alert) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)

        if (session.phase.isTerminal) {
            builder.setTimeoutAfter(6L * 60L * 60L * 1000L)
        }

        val phaseEnd = session.phaseEndsAt
        if (session.phase.hasCountdown && phaseEnd != null) {
            builder
                .setWhen(phaseEnd)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else if (session.phase == FocusProtocolPhase.FOCUS_PAUSED) {
            builder
                .setShowWhen(false)
                .setContentText(
                    appContext.getString(
                        R.string.focus_protocol_paused_remaining,
                        formatRemaining(session.pausedRemainingMillis)
                    )
                )
        }

        when (session.phase) {
            FocusProtocolPhase.RESET -> builder.addAction(
                0,
                appContext.getString(R.string.focus_protocol_skip_reset),
                actionPendingIntent(session.id, FocusProtocolReceiver.ACTION_SKIP_RESET)
            )
            FocusProtocolPhase.ACTIVATE -> builder.addAction(
                0,
                appContext.getString(R.string.focus_protocol_begin_focus),
                actionPendingIntent(session.id, FocusProtocolReceiver.ACTION_START_FOCUS)
            )
            FocusProtocolPhase.FOCUS -> builder.addAction(
                0,
                appContext.getString(R.string.focus_protocol_mark_distraction),
                actionPendingIntent(session.id, FocusProtocolReceiver.ACTION_MARK_DISTRACTION)
            ).addAction(
                0,
                appContext.getString(R.string.focus_protocol_pause),
                actionPendingIntent(session.id, FocusProtocolReceiver.ACTION_PAUSE)
            ).addAction(
                0,
                appContext.getString(R.string.focus_protocol_finish_focus),
                actionPendingIntent(session.id, FocusProtocolReceiver.ACTION_FINISH_FOCUS)
            )
            FocusProtocolPhase.FOCUS_PAUSED -> builder.addAction(
                0,
                appContext.getString(R.string.focus_protocol_resume),
                actionPendingIntent(session.id, FocusProtocolReceiver.ACTION_RESUME)
            ).addAction(
                0,
                appContext.getString(R.string.focus_protocol_finish_focus),
                actionPendingIntent(session.id, FocusProtocolReceiver.ACTION_FINISH_FOCUS)
            )
            FocusProtocolPhase.RECOVERY -> builder.addAction(
                0,
                appContext.getString(R.string.focus_protocol_finish_recovery),
                actionPendingIntent(session.id, FocusProtocolReceiver.ACTION_FINISH_RECOVERY)
            )
            FocusProtocolPhase.CYCLE_READY -> builder.addAction(
                0,
                appContext.getString(R.string.focus_block_one_more_cycle),
                actionPendingIntent(session.id, FocusProtocolReceiver.ACTION_REPEAT)
            ).addAction(
                0,
                appContext.getString(R.string.focus_block_finish),
                actionPendingIntent(session.id, FocusProtocolReceiver.ACTION_FINISH_BLOCK)
            )
            else -> Unit
        }
        if (session.phase != FocusProtocolPhase.FOCUS &&
            session.phase != FocusProtocolPhase.REVIEW
        ) {
            builder.addAction(
                0,
                appContext.getString(R.string.action_cancel),
                actionPendingIntent(session.id, FocusProtocolReceiver.ACTION_CANCEL)
            )
        }
        val shown = runCatching {
            notificationManager.notify(notificationId(session.id), builder.build())
        }.isSuccess
        if (shown && alert) {
            AppNotificationSoundPlayer.play(
                context = appContext,
                settings = signalPreferences.get(AppSignalType.POMODORO),
                dedupeKey = "focus-protocol-${session.id}-${session.phase}-${session.phaseStartedAt}"
            )
        }
    }

    private suspend fun showNotificationAndSyncSoundscape(
        session: FocusProtocolSessionEntity,
        alert: Boolean
    ) = runtimeSyncMutex.withLock {
        // Side effects may arrive from UI and AlarmManager almost simultaneously.
        // Room is authoritative: never revive a stale phase or alert for a transition
        // which has already been superseded.
        val current = protocolDao.getById(session.id) ?: return@withLock
        if (current.phase.isTerminal) {
            runCatching { FocusSoundscapeService.stopForTerminal(appContext, current.id) }
            cancelNotification(current.id)
            return@withLock
        }
        val transitionStillCurrent = current.phase == session.phase &&
            current.phaseStartedAt == session.phaseStartedAt
        val shouldAlert = alert && transitionStillCurrent
        // A phase cue is the only foreground event while it plays. Pausing the
        // ambience first prevents it from becoming a second simultaneous sound.
        if (shouldAlert) runCatching { soundscapeController.pause() }
        showNotification(current, shouldAlert)
        syncSoundscapeForPhase(current)
    }

    private fun syncSoundscapeForPhase(session: FocusProtocolSessionEntity) {
        runCatching {
            when {
                session.phase == FocusProtocolPhase.FOCUS -> {
                    soundscapeController.play(session.soundscapeMix(), session.id)
                }
                session.phase == FocusProtocolPhase.RECOVERY &&
                    session.soundscapePlayDuringRecovery -> {
                    soundscapeController.play(session.soundscapeMix(), session.id)
                }
                session.phase == FocusProtocolPhase.FOCUS_PAUSED -> soundscapeController.pause()
                else -> soundscapeController.stop()
            }
        }
    }

    private fun actionPendingIntent(sessionId: Int, action: String): PendingIntent {
        val intent = Intent(appContext, FocusProtocolReceiver::class.java).apply {
            this.action = action
            putExtra(FocusProtocolReceiver.EXTRA_SESSION_ID, sessionId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            action.hashCode() * 31 + sessionId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelNotification(sessionId: Int) {
        notificationManager.cancel(notificationId(sessionId))
    }

    private fun notificationId(sessionId: Int): Int = NOTIFICATION_BASE + sessionId

    private fun formatRemaining(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L) + 999L) / 1_000L
        return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
    }

    private fun dateKeyOf(millis: Long): String = Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)

    companion object {
        private const val CHANNEL_ID = AppNotificationChannelIds.FOCUS_PROTOCOL
        private const val NOTIFICATION_BASE = 680_000
        private const val ENERGY_BEFORE = "BEFORE_FOCUS"
        private const val ENERGY_AFTER = "AFTER_FOCUS"
        private const val OBSERVATION_BEFORE = "BEFORE_TASK"
        private const val OBSERVATION_AFTER = "AFTER_TASK"
        private const val OBSERVATION_SOURCE_FOCUS_PROTOCOL = "FOCUS_PROTOCOL"
        private const val MINUTE_MS = 60_000L
        private const val MIN_RECORDED_FOCUS_MS = 1_000L
    }
}
