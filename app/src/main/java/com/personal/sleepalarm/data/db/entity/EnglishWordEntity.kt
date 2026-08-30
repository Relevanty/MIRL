package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "english_words",
    indices = [
        Index(value = ["word"], unique = true),
        Index(value = ["frequencyRank"]),
        Index(value = ["level"])
    ]
)
data class EnglishWordEntity(
    @PrimaryKey val id: Int,
    val word: String,
    val translation: String,
    val hint: String,
    val pronunciation: String,
    val partOfSpeech: String,
    val level: String,
    val frequencyRank: Int
)
