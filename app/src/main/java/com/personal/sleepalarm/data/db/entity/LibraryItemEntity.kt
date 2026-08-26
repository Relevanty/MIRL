package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Элемент библиотеки: книга, фильм или музыка.
 *
 * coverUri — путь к скопированной обложке в приватном хранилище
 * (filesDir/covers/{id}.jpg), чтобы не зависеть от внешних URI.
 */
@Entity(
    tableName = "library_items",
    indices = [Index(value = ["type"])]
)
data class LibraryItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val type: LibraryItemType,

    val title: String,

    val author: String = "",

    val coverUri: String? = null,

    /** Каким рабочим ресурсом является запись: заметкой, файлом или ссылкой. */
    val resourceKind: LibraryResourceKind = LibraryResourceKind.NOTE,

    /** Копия выбранного документа в приватном хранилище MIRL. */
    val localFilePath: String? = null,

    /** Исходное имя файла для понятного отображения в интерфейсе. */
    val originalFileName: String = "",

    /** Необязательная ссылка на внешний справочный материал. */
    val referenceUrl: String = "",

    val shortDescription: String = "",

    val impression: String = "",

    val thoughts: String = "",

    val rating: Int = 0,

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis()
)

enum class LibraryResourceKind {
    NOTE,
    DOCUMENT,
    LINK
}
