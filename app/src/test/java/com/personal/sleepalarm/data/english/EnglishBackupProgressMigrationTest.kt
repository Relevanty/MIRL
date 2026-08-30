package com.personal.sleepalarm.data.english

import com.personal.sleepalarm.data.db.entity.EnglishWordProgressEntity
import com.personal.sleepalarm.domain.english.EnglishStudyDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class EnglishBackupProgressMigrationTest {
    @Test
    fun `legacy backup schedule is copied to both directions without losing counters`() {
        val legacy = EnglishWordProgressEntity(
            wordId = 42,
            dueAtMillis = 123_456L,
            intervalMinutes = 2_880L,
            easePermille = 2_650,
            repetitions = 7,
            lapses = 2,
            reviewCount = 12,
            correctCount = 10,
            cardReviews = 3,
            writingReviews = 4,
            pronunciationReviews = 2,
            listeningReviews = 3,
            lastGrade = "GOOD",
            lastMode = "WRITING",
            lastReviewedAtMillis = 120_000L
        )

        val migrated = legacy.toDirectionalProgressTracks()

        assertEquals(
            setOf(EnglishStudyDirection.EN_TO_RU.name, EnglishStudyDirection.RU_TO_EN.name),
            migrated.mapTo(mutableSetOf()) { it.direction }
        )
        assertEquals(2, migrated.size)
        migrated.forEach { progress ->
            assertEquals(legacy.wordId, progress.wordId)
            assertEquals(legacy.dueAtMillis, progress.dueAtMillis)
            assertEquals(legacy.intervalMinutes, progress.intervalMinutes)
            assertEquals(legacy.easePermille, progress.easePermille)
            assertEquals(legacy.repetitions, progress.repetitions)
            assertEquals(legacy.lapses, progress.lapses)
            assertEquals(legacy.reviewCount, progress.reviewCount)
            assertEquals(legacy.correctCount, progress.correctCount)
            assertEquals(legacy.cardReviews, progress.cardReviews)
            assertEquals(legacy.writingReviews, progress.writingReviews)
            assertEquals(legacy.pronunciationReviews, progress.pronunciationReviews)
            assertEquals(legacy.listeningReviews, progress.listeningReviews)
            assertEquals(legacy.lastGrade, progress.lastGrade)
            assertEquals(legacy.lastMode, progress.lastMode)
            assertEquals(legacy.lastReviewedAtMillis, progress.lastReviewedAtMillis)
        }
    }
}
