package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Тег библиотеки. Имя уникально (не допускаем дубликатов).
 * Теги создают связи между элементами (задел под будущий граф).
 */
@Entity(
    tableName = "library_tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class LibraryTagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val name: String
)