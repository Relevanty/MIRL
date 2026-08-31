package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.dao.ContextSnapshotDao
import com.personal.sleepalarm.data.db.dao.ExternalContextDao
import com.personal.sleepalarm.data.db.entity.ContextSnapshotEntity
import com.personal.sleepalarm.data.db.entity.ExternalContextEntity
import kotlinx.coroutines.flow.Flow

class AdaptiveContextRepository(
    private val snapshots: ContextSnapshotDao,
    private val externalContexts: ExternalContextDao
) {
    fun observeLatestSnapshot(): Flow<ContextSnapshotEntity?> = snapshots.observeLatest()

    fun observeSnapshotsFrom(from: Long): Flow<List<ContextSnapshotEntity>> =
        snapshots.observeFrom(from)

    suspend fun getSnapshot(id: Int): ContextSnapshotEntity? = snapshots.getById(id)

    suspend fun recordSnapshot(snapshot: ContextSnapshotEntity): Int {
        require(snapshot.zoneId.isNotBlank()) { "zoneId is required" }
        require(snapshot.localDate.isNotBlank()) { "localDate is required" }
        return snapshots.insert(snapshot.normalizedForStorage()).toInt()
    }

    fun observeExternalContext(localDate: String, regionKey: String): Flow<ExternalContextEntity?> =
        externalContexts.observeLatest(localDate, regionKey)

    suspend fun getExternalContext(localDate: String, regionKey: String): ExternalContextEntity? =
        externalContexts.getLatest(localDate, regionKey)

    suspend fun saveExternalContext(context: ExternalContextEntity): Int {
        require(context.localDate.isNotBlank()) { "localDate is required" }
        require(context.regionKey.isNotBlank()) { "regionKey is required" }
        require(context.source.isNotBlank()) { "source is required" }
        val normalized = context.normalizedForStorage()
        val existingId = if (normalized.id == 0) {
            externalContexts.getForKey(
                normalized.localDate,
                normalized.regionKey,
                normalized.source
            )?.id
        } else {
            normalized.id
        }
        return if (existingId == null || existingId == 0) {
            externalContexts.upsert(normalized).toInt()
        } else {
            externalContexts.update(normalized.copy(id = existingId))
            existingId
        }
    }
}

internal fun ContextSnapshotEntity.normalizedForStorage(): ContextSnapshotEntity = copy(
    zoneId = zoneId.trim(),
    localDate = localDate.trim(),
    minutesSinceWake = minutesSinceWake?.coerceAtLeast(0),
    hoursAwake = hoursAwake?.coerceIn(0f, 72f),
    sleepDurationMinutes = sleepDurationMinutes?.coerceAtLeast(0),
    sleepDebtMinutes = sleepDebtMinutes?.coerceAtLeast(0),
    sleepRegularity = sleepRegularity?.coerceIn(0f, 1f),
    dayOfWeek = dayOfWeek.coerceIn(1, 7),
    calendarWindowMinutes = calendarWindowMinutes?.coerceAtLeast(0),
    recentFocusMinutes = recentFocusMinutes.coerceAtLeast(0),
    recentWorkModes = recentWorkModes.trim(),
    recentBreakMinutes = recentBreakMinutes.coerceAtLeast(0),
    lastObservationAgeMinutes = lastObservationAgeMinutes?.coerceAtLeast(0),
    personalPeriodFlags = personalPeriodFlags.trim(),
    version = version.coerceAtLeast(1)
)

internal fun ExternalContextEntity.normalizedForStorage(): ExternalContextEntity = copy(
    localDate = localDate.trim(),
    regionKey = regionKey.trim(),
    source = source.trim(),
    daylightMinutes = daylightMinutes?.coerceIn(0, 24 * 60),
    cloudCoverPercent = cloudCoverPercent?.coerceIn(0, 100),
    precipitationProbability = precipitationProbability?.coerceIn(0, 100),
    outdoorSuitability = outdoorSuitability?.coerceIn(0f, 1f),
    expiresAt = expiresAt.coerceAtLeast(fetchedAt),
    provenance = provenance.trim(),
    rawPayloadHash = rawPayloadHash?.trim()?.takeIf(String::isNotEmpty)
)
