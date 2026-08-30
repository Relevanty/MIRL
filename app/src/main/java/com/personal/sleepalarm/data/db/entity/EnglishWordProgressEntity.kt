package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "english_word_progress",
    indices = [Index(value = ["dueAtMillis"]), Index(value = ["lastReviewedAtMillis"])]
)
data class EnglishWordProgressEntity(
    @PrimaryKey val wordId: Int,
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
