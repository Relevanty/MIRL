package com.personal.sleepalarm.data.repository

import androidx.room.withTransaction
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.dao.CueEventDao
import com.personal.sleepalarm.data.db.dao.SleepSessionDao
import com.personal.sleepalarm.data.db.entity.CueEventEntity
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.domain.model.CueEventState
import com.personal.sleepalarm.domain.model.DismissType
import com.personal.sleepalarm.domain.model.SleepWindow
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Репозиторий сессий сна и cue-событий.
 *
 * ДОБАВЛЕНО: updateDetectedOnset (F9).
 */
class SleepSessionRepository(
    private val database: AppDatabase,
    private val sessionDao: SleepSessionDao,
    private val cueEventDao: CueEventDao
) {

    // === Существующие методы (НЕ менять) ===

    // В SleepSessionRepository:
    fun observeAllSessions(): Flow<List<SleepSessionEntity>> =
        sessionDao.observeAll()

    fun observeActiveSession(): Flow<SleepSessionEntity?> {
        return sessionDao.observeActiveSession()
    }

    fun observeLatestCompleted(): Flow<SleepSessionEntity?> =
        sessionDao.observeLatestCompleted()

    fun observeRecentSessions(limit: Int = 14): Flow<List<SleepSessionEntity>> {
        return sessionDao.observeRecentSessions(limit)
    }

    fun observeSessionsSince(sinceTimestamp: Long): Flow<List<SleepSessionEntity>> {
        return sessionDao.observeSessionsSince(sinceTimestamp)
    }

    suspend fun getActiveSession(): SleepSessionEntity? {
        return sessionDao.getActiveSession()
    }

    suspend fun getSession(sessionId: Int): SleepSessionEntity? {
        return sessionDao.getById(sessionId)
    }

    suspend fun getCuesForSession(sessionId: Int): List<CueEventEntity> {
        return cueEventDao.getCuesForSession(sessionId)
    }

    suspend fun getScheduledCues(sessionId: Int): List<CueEventEntity> {
        return cueEventDao.getScheduledCues(sessionId)
    }

    suspend fun startSession(
        session: SleepSessionEntity,
        cues: List<CueEventEntity>
    ): Int {
        val now = System.currentTimeMillis()

        return database.withTransaction {
            val activeSession = sessionDao.getActiveSession()

            if (activeSession != null) {
                sessionDao.cancelSession(
                    sessionId = activeSession.id,
                    dismissType = DismissType.CANCELLED,
                    cancelledAt = now,          // ← ДОБАВЛЕНО
                    updatedAt = now
                )
                cueEventDao.cancelScheduled(
                    sessionId = activeSession.id,
                    updatedAt = now
                )
                // ДОБАВЛЕНО: старую короткую сессию не сохраняем.
                deleteSessionIfShort(activeSession.id, DismissType.CANCELLED)

            }

            val sessionId = sessionDao.insert(session).toInt()

            val cuesWithSessionId = cues.map { cue ->
                cue.copy(sessionId = sessionId)
            }

            cueEventDao.insertAll(cuesWithSessionId)

            sessionId
        }
    }

    suspend fun updateSession(session: SleepSessionEntity) {
        sessionDao.update(
            session.copy(updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun cancelSession(sessionId: Int) {
        val now = System.currentTimeMillis()

        database.withTransaction {
            sessionDao.cancelSession(
                sessionId = sessionId,
                dismissType = DismissType.CANCELLED,
                cancelledAt = now,
                updatedAt = now
            )
            cueEventDao.cancelScheduled(
                sessionId = sessionId,
                updatedAt = now
            )
            // ДОБАВЛЕНО: короткую отменённую сессию не сохраняем.
            deleteSessionIfShort(sessionId, DismissType.CANCELLED)
        }
    }

    suspend fun finishSession(
        sessionId: Int,
        actualWakeTime: Long?,
        dismissType: DismissType
    ) {
        val now = System.currentTimeMillis()

        database.withTransaction {
            val played = cueEventDao.playedCount(sessionId)
            val skipped = cueEventDao.skippedCount(sessionId)

            sessionDao.finishSession(
                sessionId = sessionId,
                actualWakeTime = actualWakeTime,
                dismissType = dismissType,
                cuesPlayedCount = played,
                cuesSkippedCount = skipped,
                updatedAt = now
            )

            cueEventDao.cancelScheduled(
                sessionId = sessionId,
                updatedAt = now
            )
            // ДОБАВЛЕНО: короткую сессию не сохраняем (snooze пропускается внутри).
            deleteSessionIfShort(sessionId, dismissType)
        }
    }

    suspend fun snoozeSession(
        currentSession: SleepSessionEntity,
        newWakeTime: Long
    ): Int {
        val now = System.currentTimeMillis()

        return database.withTransaction {
            val played = cueEventDao.playedCount(currentSession.id)
            val skipped = cueEventDao.skippedCount(currentSession.id)

            sessionDao.finishSession(
                sessionId = currentSession.id,
                actualWakeTime = now,
                dismissType = DismissType.SNOOZE,
                cuesPlayedCount = played,
                cuesSkippedCount = skipped,
                updatedAt = now
            )

            cueEventDao.cancelScheduled(
                sessionId = currentSession.id,
                updatedAt = now
            )

            val snoozeSession = SleepSessionEntity(
                bedTimePlanned = now,
                sleepOnsetLatencyMinutes = 0,
                estimatedSleepStartTime = now,
                cycleLengthMinutes = currentSession.cycleLengthMinutes,
                cyclesPlanned = 1,
                estimatedWakeTime = newWakeTime,
                actualWakeTime = null,
                dismissType = null,
                cuesEnabled = false,
                cueVolumePercent = currentSession.cueVolumePercent,
                cuesScheduledCount = 0,
                isActive = true,
                isSnoozeSession = true,
                parentSessionId = currentSession.id,
                zoneId = currentSession.zoneId,
                cueRingtoneUri = currentSession.cueRingtoneUri
            )

            sessionDao.insert(snoozeSession).toInt()
        }
    }

    suspend fun claimCuePlayback(
        sessionId: Int,
        cueIndex: Int,
        playedBy: String
    ): Boolean {
        val now = System.currentTimeMillis()
        return cueEventDao.claimForPlayback(
            sessionId = sessionId,
            cueIndex = cueIndex,
            claimedAt = now,
            playedBy = playedBy
        ) > 0
    }

    suspend fun completeCuePlayback(
        sessionId: Int,
        cueIndex: Int,
        played: Boolean
    ): Boolean {
        val now = System.currentTimeMillis()
        return database.withTransaction {
            val rows = if (played) {
                cueEventDao.completePlaybackAsPlayed(sessionId, cueIndex, now)
            } else {
                cueEventDao.completePlaybackAsSkipped(sessionId, cueIndex, now)
            }
            if (rows > 0) {
                if (played) sessionDao.incrementPlayed(sessionId, now)
                else sessionDao.incrementSkipped(sessionId, now)
            }
            rows > 0
        }
    }

    /**
     * Атомарный вариант для фоновой автоматизации. В отличие от ручного
     * startSession никогда не отменяет уже начатую пользователем сессию.
     */
    suspend fun startSessionIfNoActive(
        session: SleepSessionEntity,
        cues: List<CueEventEntity>
    ): Int? = database.withTransaction {
        if (sessionDao.getActiveSession() != null) return@withTransaction null
        val sessionId = sessionDao.insert(session).toInt()
        cueEventDao.insertAll(cues.map { it.copy(sessionId = sessionId) })
        sessionId
    }

    suspend fun replaceCues(
        session: SleepSessionEntity,
        cues: List<CueEventEntity>
    ) {
        database.withTransaction {
            sessionDao.update(session.copy(updatedAt = System.currentTimeMillis()))
            cueEventDao.deleteForSession(session.id)
            cueEventDao.insertAll(cues.map { it.copy(sessionId = session.id) })
        }
    }

    /**
     * Applies an alarm correction only while the exact detected onset that
     * produced it is still accepted. A user can reject a false detection at
     * any moment without a late service coroutine restoring the old result.
     */
    suspend fun replaceCuesIfDetectedOnsetMatches(
        session: SleepSessionEntity,
        cues: List<CueEventEntity>,
        expectedOnsetMillis: Long
    ): Boolean = database.withTransaction {
        val current = sessionDao.getById(session.id)
        if (current?.isActive != true || current.detectedSleepOnsetTime != expectedOnsetMillis) {
            return@withTransaction false
        }
        sessionDao.update(session.copy(updatedAt = System.currentTimeMillis()))
        cueEventDao.deleteForSession(session.id)
        cueEventDao.insertAll(cues.map { it.copy(sessionId = session.id) })
        true
    }

    suspend fun recoverInterruptedCuePlaybacks(sessionId: Int) {
        val now = System.currentTimeMillis()
        cueEventDao.recoverInterruptedPlaybacks(
            sessionId = sessionId,
            claimedBefore = now - INTERRUPTED_PLAYBACK_GRACE_MS,
            updatedAt = now
        )
    }

    suspend fun markCueSkipped(
        sessionId: Int,
        cueIndex: Int
    ): Boolean {
        val now = System.currentTimeMillis()

        return database.withTransaction {
            val updatedRows = cueEventDao.markSkipped(
                sessionId = sessionId,
                cueIndex = cueIndex,
                updatedAt = now
            )

            if (updatedRows > 0) {
                sessionDao.incrementSkipped(
                    sessionId = sessionId,
                    updatedAt = now
                )
            }

            updatedRows > 0
        }
    }

    suspend fun getCueState(
        sessionId: Int,
        cueIndex: Int
    ): CueEventState? {
        return cueEventDao.getCue(sessionId, cueIndex)?.state
    }

    // === Мапперы (НЕ менять) ===

    fun SleepSessionEntity.toSleepWindow(): SleepWindow {
        val zone = runCatching { ZoneId.of(zoneId) }
            .getOrDefault(ZoneId.systemDefault())

        val sleepStart: ZonedDateTime = Instant.ofEpochMilli(estimatedSleepStartTime)
            .atZone(zone)

        val wake: ZonedDateTime = Instant.ofEpochMilli(estimatedWakeTime)
            .atZone(zone)

        return SleepWindow(
            sleepStart = sleepStart,
            wake = wake
        )
    }

    fun SleepSessionEntity.sessionZone(): ZoneId {
        return runCatching { ZoneId.of(zoneId) }
            .getOrDefault(ZoneId.systemDefault())
    }

    // === ДОБАВЛЕНО: F9 — автоопределение засыпания ===

    /**
     * Записывает результат детекции засыпания по акселерометру.
     * Вызывается из SleepForegroundService.
     */
    suspend fun updateDetectedOnset(
        sessionId: Int,
        onsetTime: Long,
        latencyMinutes: Int,
        confidencePercent: Int = 60,
        source: String = "PHONE_CONTEXT_HEURISTIC",
        uncertaintyMinutes: Int = ((100 - confidencePercent) / 3).coerceIn(5, 20)
    ): Boolean {
        return sessionDao.updateDetectedOnset(
            sessionId = sessionId,
            onsetTime = onsetTime,
            latencyMinutes = latencyMinutes,
            confidencePercent = confidencePercent,
            source = source,
            uncertaintyMinutes = uncertaintyMinutes,
            updatedAt = System.currentTimeMillis()
        ) > 0
    }

    suspend fun getTypicalConfirmedOnsetLatencyMinutes(): Int? {
        val values = sessionDao.getAllSessions()
            .asSequence()
            .filter { it.onsetReviewState == "CONFIRMED" || it.onsetReviewState == "CORRECTED" }
            .mapNotNull { it.detectedOnsetLatencyMinutes }
            .filter { it in 0..240 }
            .take(14)
            .sorted()
            .toList()
        return values.getOrNull(values.size / 2)
    }
    // === ДОБАВЛЕНО: сессии короче часа не записываем ===

    companion object {
        private const val MIN_SESSION_DURATION_MS = 60L * 60L * 1000L // 1 час
        private const val INTERRUPTED_PLAYBACK_GRACE_MS = 30_000L
    }

    /**
     * Удаляет сессию и её cue-события, если фактическая длительность < 1 часа.
     * Snooze-сессии не трогаем — это продолжение реального сна.
     */
    private suspend fun deleteSessionIfShort(sessionId: Int, dismissType: DismissType) {
        if (dismissType == DismissType.SNOOZE) return

        val session = sessionDao.getById(sessionId) ?: return
        val end = session.actualWakeTime ?: return
        val start = session.detectedSleepOnsetTime ?: session.estimatedSleepStartTime
        val duration = end - start

        if (duration < MIN_SESSION_DURATION_MS) {
            cueEventDao.deleteForSession(sessionId)
            sessionDao.deleteById(sessionId)
        }
    }



}
