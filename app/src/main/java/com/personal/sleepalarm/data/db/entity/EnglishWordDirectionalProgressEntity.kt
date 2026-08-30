package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Direction-specific learning state for the bundled 10,000-word dictionary. */
@Entity(
    tableName = "english_word_directional_progress",
    primaryKeys = ["wordId", "direction"],
    foreignKeys = [
        ForeignKey(
            entity = EnglishWordEntity::class,
            parentColumns = ["id"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["dueAtMillis"]), Index(value = ["lastReviewedAtMillis"])]
)
data class EnglishWordDirectionalProgressEntity(
    val wordId: Int,
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
