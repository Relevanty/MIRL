package com.personal.sleepalarm.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.personal.sleepalarm.data.db.entity.LibraryItemEntity
import com.personal.sleepalarm.data.db.entity.LibraryItemTagCrossRef
import com.personal.sleepalarm.data.db.entity.LibraryItemType
import com.personal.sleepalarm.data.db.entity.LibraryTagEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO библиотеки: элементы, теги и связи.
 */
@Dao
interface LibraryDao {

    // === Элементы ===

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: LibraryItemEntity): Long

    @Query("UPDATE library_items SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchItem(id: Int, updatedAt: Long)

    @Query("DELETE FROM library_items WHERE id = :id")
    suspend fun deleteItem(id: Int)

    @Query("SELECT * FROM library_items ORDER BY updatedAt DESC")
    fun observeItems(): Flow<List<LibraryItemEntity>>

    @Query("SELECT * FROM library_items WHERE type = :type ORDER BY updatedAt DESC")
    fun observeItemsByType(type: LibraryItemType): Flow<List<LibraryItemEntity>>

    @Query(
        """
        SELECT * FROM library_items
        WHERE title LIKE '%' || :query || '%'
           OR author LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        """
    )
    fun searchItems(query: String): Flow<List<LibraryItemEntity>>

    @Query("SELECT * FROM library_items WHERE id = :id")
    suspend fun getItem(id: Int): LibraryItemEntity?

    @Query("SELECT * FROM library_items WHERE id = :id")
    fun observeItem(id: Int): Flow<LibraryItemEntity?>

    // === Теги ===

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: LibraryTagEntity): Long

    @Query("SELECT * FROM library_tags WHERE name = :name LIMIT 1")
    suspend fun getTagByName(name: String): LibraryTagEntity?

    @Query("SELECT * FROM library_tags ORDER BY name ASC")
    fun observeAllTags(): Flow<List<LibraryTagEntity>>

    // === Связи ===

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: LibraryItemTagCrossRef)

    @Query("DELETE FROM library_item_tags WHERE itemId = :itemId AND tagId = :tagId")
    suspend fun deleteCrossRef(itemId: Int, tagId: Int)

    @Query("DELETE FROM library_item_tags WHERE itemId = :itemId")
    suspend fun deleteAllCrossRefsForItem(itemId: Int)

    @Query("SELECT * FROM library_items")
    suspend fun getAllItems(): List<LibraryItemEntity>

    @Query("SELECT * FROM library_tags")
    suspend fun getAllTags(): List<LibraryTagEntity>

    @Query("SELECT * FROM library_item_tags")
    suspend fun getAllCrossRefs(): List<LibraryItemTagCrossRef>

    @Query("DELETE FROM library_item_tags")
    suspend fun deleteAllCrossRefs()

    @Query("DELETE FROM library_tags")
    suspend fun deleteAllTags()

    @Query("DELETE FROM library_items")
    suspend fun deleteAllItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllItems(items: List<LibraryItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTags(tags: List<LibraryTagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCrossRefs(refs: List<LibraryItemTagCrossRef>)

    /** Теги одного элемента. */
    @Query(
        """
        SELECT t.* FROM library_tags t
        INNER JOIN library_item_tags c ON t.id = c.tagId
        WHERE c.itemId = :itemId
        ORDER BY t.name ASC
        """
    )
    fun observeTagsForItem(itemId: Int): Flow<List<LibraryTagEntity>>


    /** Все связи элемент-тег (для построения графа). */
    @Query("SELECT * FROM library_item_tags")
    fun observeAllCrossRefs(): Flow<List<LibraryItemTagCrossRef>>

    /** Элементы с данным тегом (для будущего графа). */
    @Query(
        """
        SELECT i.* FROM library_items i
        INNER JOIN library_item_tags c ON i.id = c.itemId
        WHERE c.tagId = :tagId
        ORDER BY i.updatedAt DESC
        """
    )
    fun observeItemsForTag(tagId: Int): Flow<List<LibraryItemEntity>>
}
