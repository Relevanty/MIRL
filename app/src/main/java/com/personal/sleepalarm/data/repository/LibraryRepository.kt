package com.personal.sleepalarm.data.repository

import androidx.room.withTransaction
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.dao.LibraryDao
import com.personal.sleepalarm.data.db.entity.LibraryItemEntity
import com.personal.sleepalarm.data.db.entity.LibraryItemTagCrossRef
import com.personal.sleepalarm.data.db.entity.LibraryItemType
import com.personal.sleepalarm.data.db.entity.LibraryTagEntity
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий библиотеки: элементы, теги, связи, обложки.
 */
class LibraryRepository(
    private val database: AppDatabase,
    private val dao: LibraryDao
) {

    fun observeItems(): Flow<List<LibraryItemEntity>> = dao.observeItems()

    fun observeItemsByType(type: LibraryItemType): Flow<List<LibraryItemEntity>> =
        dao.observeItemsByType(type)

    fun searchItems(query: String): Flow<List<LibraryItemEntity>> =
        dao.searchItems(query)

    fun observeItem(id: Int): Flow<LibraryItemEntity?> = dao.observeItem(id)

    fun observeTagsForItem(id: Int): Flow<List<LibraryTagEntity>> =
        dao.observeTagsForItem(id)

    /**
     * Сохраняет элемент и синхронизирует теги (атомарно).
     * Возвращает id сохранённого элемента.
     */
    suspend fun saveItem(item: LibraryItemEntity, tagNames: List<String>): Int {
        return database.withTransaction {
            val id = dao.insertItem(item).toInt()

            // Пересоздаём связи: удаляем старые и ставим новые.
            dao.deleteAllCrossRefsForItem(id)

            tagNames.distinct().forEach { raw ->
                val name = raw.trim()
                if (name.isNotEmpty()) {
                    // IGNORE: если тег уже есть — не создаём дубликат.
                    dao.insertTag(LibraryTagEntity(name = name))
                    val tag = dao.getTagByName(name)
                    if (tag != null) {
                        dao.insertCrossRef(LibraryItemTagCrossRef(itemId = id, tagId = tag.id))
                    }
                }
            }

            id
        }
    }

    /** Удаляет элемент и его обложку. Связи чистятся каскадно (FK). */
    suspend fun deleteItem(item: LibraryItemEntity) {
        dao.deleteItem(item.id)
        CoverHelperDelete(item.coverUri)
    }

    private fun CoverHelperDelete(path: String?) {
        com.personal.sleepalarm.util.CoverHelper.deleteCover(path)
    }
}