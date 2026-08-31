package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.dao.RecommendationDecisionDao
import com.personal.sleepalarm.data.db.entity.RecommendationDecisionEntity
import kotlinx.coroutines.flow.Flow

class RecommendationRepository(private val dao: RecommendationDecisionDao) {
    fun observeRecent(limit: Int = 20): Flow<List<RecommendationDecisionEntity>> =
        dao.observeRecent(limit.coerceIn(1, 200))

    suspend fun getById(id: Int): RecommendationDecisionEntity? = dao.getById(id)

    suspend fun record(decision: RecommendationDecisionEntity): Int {
        require(decision.modelVersion.isNotBlank()) { "modelVersion is required" }
        require(decision.strategy.isNotBlank()) { "strategy is required" }
        val normalized = decision.normalizedForStorage()
        return if (normalized.id == 0) {
            dao.insert(normalized).toInt()
        } else {
            dao.update(normalized)
            normalized.id
        }
    }

    suspend fun recordFeedback(
        id: Int,
        accepted: Boolean,
        dismissed: Boolean,
        reordered: Boolean,
        feedbackReason: String? = null,
        resultingActivityRecordId: Int? = null
    ): Boolean {
        val current = dao.getById(id) ?: return false
        dao.update(
            current.copy(
                accepted = accepted,
                dismissed = dismissed,
                reordered = reordered,
                feedbackReason = feedbackReason?.trim()?.takeIf(String::isNotEmpty),
                resultingActivityRecordId = resultingActivityRecordId,
                updatedAt = System.currentTimeMillis()
            )
        )
        return true
    }
}

internal fun RecommendationDecisionEntity.normalizedForStorage(): RecommendationDecisionEntity = copy(
    modelVersion = modelVersion.trim(),
    strategy = strategy.trim(),
    stateSnapshotJson = stateSnapshotJson.trim().ifBlank { "{}" },
    candidateTaskIds = candidateTaskIds.trim().ifBlank { "[]" },
    componentScores = componentScores.trim().ifBlank { "{}" },
    reasonCodes = reasonCodes.trim().ifBlank { "[]" },
    confidence = confidence.coerceIn(0f, 1f),
    feedbackReason = feedbackReason?.trim()?.takeIf(String::isNotEmpty),
    updatedAt = System.currentTimeMillis()
)
