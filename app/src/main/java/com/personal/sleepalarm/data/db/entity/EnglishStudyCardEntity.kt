package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "english_study_cards",
    foreignKeys = [
        ForeignKey(
            entity = EnglishStudySetEntity::class,
            parentColumns = ["id"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["setId"]),
        Index(value = ["dictionaryWordId"]),
        Index(value = ["setId", "position"])
    ]
)
data class EnglishStudyCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val setId: Long,
    val dictionaryWordId: Int?,
    val term: String,
    val translation: String,
    val definition: String,
    val example: String,
    val exampleTranslation: String,
    val notes: String,
    val position: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
