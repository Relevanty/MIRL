package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "english_study_sets",
    indices = [Index(value = ["updatedAtMillis"])]
)
data class EnglishStudySetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String,
    val colorSeed: Int,
    val defaultDirection: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
