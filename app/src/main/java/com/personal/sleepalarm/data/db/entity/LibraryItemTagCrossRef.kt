package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Связь многие-ко-многим между элементами и тегами.
 *
 * При удалении элемента или тега связи чистятся каскадно.
 * Два элемента с общим тегом считаются связанными (будущий граф).
 */
@Entity(
    tableName = "library_item_tags",
    primaryKeys = ["itemId", "tagId"],
    indices = [Index(value = ["tagId"])],
    foreignKeys = [
        ForeignKey(
            entity = LibraryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LibraryTagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class LibraryItemTagCrossRef(
    val itemId: Int,
    val tagId: Int
)