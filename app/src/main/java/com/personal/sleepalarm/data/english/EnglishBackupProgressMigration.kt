package com.personal.sleepalarm.data.english

import com.personal.sleepalarm.data.db.entity.EnglishWordDirectionalProgressEntity
import com.personal.sleepalarm.data.db.entity.EnglishWordProgressEntity
import com.personal.sleepalarm.domain.english.EnglishStudyDirection

/** Converts the pre-v13 aggregate schedule without losing either study direction. */
internal fun EnglishWordProgressEntity.toDirectionalProgressTracks(): List<EnglishWordDirectionalProgressEntity> =
    listOf(
        toDirectionalProgress(EnglishStudyDirection.EN_TO_RU),
        toDirectionalProgress(EnglishStudyDirection.RU_TO_EN)
    )

private fun EnglishWordProgressEntity.toDirectionalProgress(
    direction: EnglishStudyDirection
) = EnglishWordDirectionalProgressEntity(
    wordId = wordId,
    direction = direction.requireConcrete().name,
    dueAtMillis = dueAtMillis,
    intervalMinutes = intervalMinutes,
    easePermille = easePermille,
    repetitions = repetitions,
    lapses = lapses,
    reviewCount = reviewCount,
    correctCount = correctCount,
    cardReviews = cardReviews,
    writingReviews = writingReviews,
    pronunciationReviews = pronunciationReviews,
    listeningReviews = listeningReviews,
    lastGrade = lastGrade,
    lastMode = lastMode,
    lastReviewedAtMillis = lastReviewedAtMillis
)
