package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "english_word_senses",
    primaryKeys = ["wordId", "senseOrder"],
    foreignKeys = [
        ForeignKey(
            entity = EnglishWordEntity::class,
            parentColumns = ["id"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["wordId"])]
)
data class EnglishWordSenseEntity(
    val wordId: Int,
    val senseOrder: Int,
    val definition: String,
    val translations: String,
    val example: String,
    val exampleTranslation: String,
    val synonyms: String,
    val usageLabels: String
)
