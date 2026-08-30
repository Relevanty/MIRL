package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "english_dictionary_metadata")
data class EnglishDictionaryMetadataEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val datasetVersion: String
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
