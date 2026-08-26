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
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.EnergySampleEntity
import com.personal.sleepalarm.data.db.entity.FocusProtocolSessionEntity
import com.personal.sleepalarm.data.db.entity.PomodoroSessionEntity
import com.personal.sleepalarm.data.db.entity.StudySessionEntity
import com.personal.sleepalarm.data.repository.TaskRepository
import com.personal.sleepalarm.data.repository.ActivityRecordRepository
import com.personal.sleepalarm.data.preferences.PomodoroSoundPreference
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.domain.model.FocusProtocolPhase
import com.personal.sleepalarm.service.audio.AppNotificationSoundPlayer
import com.personal.sleepalarm.ui.MainActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class FocusProtocolConfig(
    val activityType: FocusActivityType,
    val itemId: Int,
    val itemName: String,
    val outcome: String,
    val resetMinutes: Int,
    val focusMinutes: Int,
    val recoveryMinutes: Int,
    val energyBefore: Int
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
    private val pomodoroDao = database.pomodoroDao()
    private val studyDao = database.studySessionDao()
    private val taskRepository = TaskRepository(database.taskDao())
    private val activityRepository = ActivityRecordRepository(database)
    private val scheduler = FocusProtocolScheduler(appContext)
    private val soundPreference = PomodoroSoundPreference(appContext)
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
            }
        )
    }

    suspend fun start(config: FocusProtocolConfig): Int {
        protocolDao.getActive().firstOrNull()?.let { return it.id }
        val now = System.currentTimeMillis()
        val resetMinutes = config.resetMinutes.coerceIn(0, 20)
        val phase = if (resetMinutes == 0) {
            FocusProtocolPhase.ACTIVATE
        } else {
            FocusProtocolPhase.RESET
        }
        val session = FocusProtocolSessionEntity(
            activityType = config.activityType,
            itemId = config.itemId,
            itemName = config.itemName.trim(),
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
            focusDurationMinutes = config.focusMinutes.coerceIn(5, 180),
            recoveryDurationMinutes = config.recoveryMinutes.coerceIn(1, 30),
            energyBefore = config.energyBefore.coerceIn(1, 10)
        )
        val id = protocolDao.insert(session).toInt()
        energyDao.insert(
            EnergySampleEntity(
                timestamp = now,
                energy = session.energyBefore,
                context = ENERGY_BEFORE,
                protocolSessionId = id
            )
        )
        val saved = session.copy(id = id)
        scheduler.schedule(saved)
        showNotification(saved, alert = false)
        return id
    }

    suspend fun reconcileActiveSessions() {
        protocolDao.getActive().forEach { session ->
            val end = session.phaseEndsAt
            if (session.phase.hasCountdown && end != null && end <= System.currentTimeMillis()) {
                advanceIfDue(session.id)
            } else {
                scheduler.schedule(session)
                showNotification(session, alert = false)
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
                showNotification(updated, alert = true)
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
                showNotification(updated, alert = true)
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
                showNotification(updated, alert = true)
            }
            else -> scheduler.cancel(sessionId)
        }
    }

    suspend fun skipReset(sessionId: Int) {
        val session = protocolDao.getById(sessionId) ?: return
        if (session.phase != FocusProtocolPhase.RESET) return
        val now = System.currentTimeMillis()
        val updated = session.copy(
            phase = FocusProtocolPhase.ACTIVATE,
            phaseStartedAt = now,
            phaseEndsAt = null
        )
        protocolDao.update(updated)
        scheduler.cancel(sessionId)
        showNotification(updated, alert = false)
    }

    suspend fun startFocus(sessionId: Int) {
        val session = protocolDao.getById(sessionId) ?: return
        if (session.phase != FocusProtocolPhase.ACTIVATE) return
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
        scheduler.schedule(updated)
        showNotification(updated, alert = false)
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
        val now = System.currentTimeMillis()
        val updated = session.copy(
            activityType = activityType ?: session.activityType,
            itemId = itemId ?: session.itemId,
            itemName = itemName?.trim()?.takeIf { it.isNotEmpty() } ?: session.itemName,
            outcome = outcome?.trim()?.takeIf { it.isNotEmpty() } ?: session.outcome,
            phase = FocusProtocolPhase.FOCUS,
            phaseStartedAt = now,
            phaseEndsAt = now + session.focusDurationMinutes * MINUTE_MS,
            focusStartedAt = now,
            focusElapsedMillis = 0L,
            pausedRemainingMillis = 0L,
            pomodoroRecorded = false
        )
        protocolDao.update(updated)
        scheduler.schedule(updated)
        showNotification(updated, alert = false)
    }

    suspend fun pauseFocus(sessionId: Int) {
        val session = protocolDao.getById(sessionId) ?: return
        if (session.phase != FocusProtocolPhase.FOCUS) return
        val now = System.currentTimeMillis()
        val updated = session.copy(
            phase = FocusProtocolPhase.FOCUS_PAUSED,
            phaseStartedAt = now,
            phaseEndsAt = null,
            focusElapsedMillis = elapsedFocusAt(session, now),
            pausedRemainingMillis = ((session.phaseEndsAt ?: now) - now).coerceAtLeast(0L)
        )
        protocolDao.update(updated)
        scheduler.cancel(sessionId)
        showNotification(updated, alert = false)
    }

    suspend fun resumeFocus(sessionId: Int) {
        val session = protocolDao.getById(sessionId) ?: return
        if (session.phase != FocusProtocolPhase.FOCUS_PAUSED) return
        val now = System.currentTimeMillis()
        val remaining = session.pausedRemainingMillis.coerceAtLeast(1_000L)
        val updated = session.copy(
            phase = FocusProtocolPhase.FOCUS,
            phaseStartedAt = now,
            phaseEndsAt = now + remaining,
            pausedRemainingMillis = 0L
        )
        protocolDao.update(updated)
        scheduler.schedule(updated)
        showNotification(updated, alert = false)
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
                showNotification(updated, alert = true)
            }
    }

    suspend fun finishRecovery(sessionId: Int) {
        val session = protocolDao.getById(sessionId) ?: return
        if (session.phase != FocusProtocolPhase.RECOVERY) return
        val now = System.currentTimeMillis()
        val updated = session.copy(
            phase = FocusProtocolPhase.CYCLE_READY,
            phaseStartedAt = now,
            phaseEndsAt = null
        )
        protocolDao.update(updated)
        scheduler.cancel(sessionId)
        showNotification(updated, alert = false)
    }

    suspend fun finishBlock(sessionId: Int) {
        val session = protocolDao.getById(sessionId) ?: return
        if (session.phase != FocusProtocolPhase.CYCLE_READY) return
        val now = System.currentTimeMillis()
        val updated = session.copy(
            phase = FocusProtocolPhase.REVIEW,
            phaseStartedAt = now,
            phaseEndsAt = null
        )
        protocolDao.update(updated)
        scheduler.cancel(sessionId)
        showNotification(updated, alert = false)
    }

    suspend fun incrementDistraction(sessionId: Int) {
        protocolDao.incrementDistractions(sessionId)
    }

    suspend fun cancel(sessionId: Int, reason: String) {
        database.withTransaction {
            val session = protocolDao.getById(sessionId) ?: return@withTransaction
            if (session.phase.isTerminal) return@withTransaction
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
        }
        scheduler.cancel(sessionId)
        cancelNotification(sessionId)
    }

    suspend fun completeReview(sessionId: Int, energyAfter: Int) {
        val session = protocolDao.getById(sessionId) ?: return
        if (session.phase != FocusProtocolPhase.REVIEW) return
        val now = System.currentTimeMillis()
        val safeEnergy = energyAfter.coerceIn(1, 10)
        database.withTransaction {
            energyDao.insert(
                EnergySampleEntity(
                    timestamp = now,
                    energy = safeEnergy,
                    context = ENERGY_AFTER,
                    protocolSessionId = session.id
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
        }
        scheduler.cancel(sessionId)
        cancelNotification(sessionId)
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
        val pomodoro = PomodoroSessionEntity(
                startedAt = startedAt,
                durationMinutes = ((elapsed + MINUTE_MS - 1L) / MINUTE_MS).toInt(),
                completedAt = completedAt,
                isCompleted = completed,
                isBreak = false,
                activityType = session.activityType,
                subjectId = session.itemId.takeIf { session.activityType == FocusActivityType.STUDY },
                taskId = session.itemId.takeIf { session.activityType == FocusActivityType.WORK },
                otherActivityId = session.itemId.takeIf { session.activityType == FocusActivityType.OTHER },
                itemName = session.itemName,
                actualDurationMillis = elapsed,
                recordSource = "TIMER"
            )
        val pomodoroId = pomodoroDao.insert(pomodoro).toInt()
        activityRepository.recordTimer(pomodoro, pomodoroId)
        if (session.activityType == FocusActivityType.STUDY) {
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
            .setOnlyAlertOnce(!alert)
            .setSilent(!alert)
            .setAutoCancel(false)
            .setOngoing(!session.phase.isTerminal)
            .setPriority(if (alert) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)

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
                soundUri = soundPreference.getUri()
            )
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
        private const val CHANNEL_ID = "focus_protocol_channel_app_volume_v3"
        private const val NOTIFICATION_BASE = 680_000
        private const val ENERGY_BEFORE = "BEFORE_FOCUS"
        private const val ENERGY_AFTER = "AFTER_FOCUS"
        private const val MINUTE_MS = 60_000L
        private const val MIN_RECORDED_FOCUS_MS = 1_000L
    }
}
