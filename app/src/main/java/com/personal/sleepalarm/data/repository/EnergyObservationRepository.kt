package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.dao.EnergyObservationDao
import com.personal.sleepalarm.data.db.dao.WorkEpisodeAssessmentDao
import com.personal.sleepalarm.data.db.entity.EnergyObservationEntity
import com.personal.sleepalarm.data.db.entity.WorkEpisodeAssessmentEntity
import kotlinx.coroutines.flow.Flow

class EnergyObservationRepository(
    private val observations: EnergyObservationDao,
    private val assessments: WorkEpisodeAssessmentDao
) {
    fun observeFrom(from: Long): Flow<List<EnergyObservationEntity>> = observations.observeFrom(from)

    fun observeForTask(taskId: Int): Flow<List<EnergyObservationEntity>> =
        observations.observeForTask(taskId)

    fun observeLatest(): Flow<EnergyObservationEntity?> = observations.observeLatest()

    suspend fun getById(id: Int): EnergyObservationEntity? = observations.getById(id)

    suspend fun getForFocusSession(sessionId: Int): List<EnergyObservationEntity> =
        observations.getForFocusSession(sessionId)

    suspend fun record(observation: EnergyObservationEntity): Int {
        require(observation.absoluteEnergy != null || observation.relativeDelta != null) {
            "An absolute energy value or a relative delta is required"
        }
        require(observation.context.isNotBlank()) { "context is required" }
        val normalized = observation.normalizedForStorage()
        return if (normalized.id == 0) {
            observations.insert(normalized).toInt()
        } else {
            observations.update(normalized)
            normalized.id
        }
    }

    suspend fun getAssessment(activityRecordId: Int): WorkEpisodeAssessmentEntity? =
        assessments.getForActivity(activityRecordId)

    fun observeAssessment(activityRecordId: Int): Flow<WorkEpisodeAssessmentEntity?> =
        assessments.observeForActivity(activityRecordId)

    suspend fun saveAssessment(assessment: WorkEpisodeAssessmentEntity): Int {
        require(assessment.activityRecordId > 0) { "activityRecordId is required" }
        val now = System.currentTimeMillis()
        val normalized = assessment.copy(
            goalOutcome = assessment.goalOutcome.trim().ifBlank { "UNKNOWN" },
            perceivedDifficulty = assessment.perceivedDifficulty?.coerceIn(1, 10),
            interruptionReason = assessment.interruptionReason?.trim()?.takeIf(String::isNotEmpty),
            profileMismatchFlags = assessment.profileMismatchFlags.trim(),
            createdAt = if (assessment.id == 0) now else assessment.createdAt,
            updatedAt = now
        )
        return if (normalized.id == 0) {
            assessments.upsert(normalized).toInt()
        } else {
            assessments.update(normalized)
            normalized.id
        }
    }
}

internal fun EnergyObservationEntity.normalizedForStorage(): EnergyObservationEntity = copy(
    absoluteEnergy = absoluteEnergy?.coerceIn(1, 10),
    relativeDelta = relativeDelta?.coerceIn(-9, 9),
    context = context.trim(),
    source = source.trim().ifBlank { "USER" },
    quality = quality.trim().ifBlank { "EXACT" },
    confidence = confidence.coerceIn(0f, 1f)
)
