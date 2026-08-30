package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "english_card_progress",
    primaryKeys = ["cardId", "direction"],
    foreignKeys = [
        ForeignKey(
            entity = EnglishStudyCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["dueAtMillis"]), Index(value = ["lastReviewedAtMillis"])]
)
data class EnglishCardProgressEntity(
    val cardId: Long,
    val direction: String,
    val dueAtMillis: Long,
    val intervalMinutes: Long,
    val easePermille: Int,
    val repetitions: Int,
    val lapses: Int,
    val reviewCount: Int,
    val correctCount: Int,
    val cardReviews: Int,
    val writingReviews: Int,
    val pronunciationReviews: Int,
    val listeningReviews: Int,
    val lastGrade: String,
    val lastMode: String,
    val lastReviewedAtMillis: Long
)
