package com.personal.sleepalarm.ui.library


import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.LibraryItemEntity
import com.personal.sleepalarm.data.db.entity.LibraryItemType
import com.personal.sleepalarm.data.db.entity.LibraryTagEntity
import com.personal.sleepalarm.data.repository.LibraryRepository
import com.personal.sleepalarm.domain.model.primaryLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Состояние списка библиотеки.
 */
data class LibraryListState(
    val items: List<LibraryItemEntity> = emptyList(),
    val filterType: LibraryItemType? = null,
    val query: String = "",
    val linkedTaskLabels: Map<Int, List<String>> = emptyMap()
)

/**
 * ViewModel списка библиотеки: фильтр по типу + поиск.
 */
class LibraryViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application.applicationContext)
    private val repository = LibraryRepository(database, database.libraryDao())

    private val _filter = MutableStateFlow<LibraryItemType?>(null)
    private val _query = MutableStateFlow("")

    private val visibleItems = combine(_filter, _query) { f, q -> f to q }
        .flatMapLatest { (filter, query) ->
            val flow = when {
                query.isNotBlank() -> repository.searchItems(query.trim())
                filter != null -> repository.observeItemsByType(filter)
                else -> repository.observeItems()
            }
            flow.map { items -> Triple(items, filter, query) }
        }

    val uiState: StateFlow<LibraryListState> = combine(
        visibleItems,
        database.taskDao().observeAll(),
        database.taskLibraryLinkDao().observeAll()
    ) { (items, filter, query), tasks, links ->
        val taskById = tasks.associateBy { it.id }
        LibraryListState(
            items = items,
            filterType = filter,
            query = query,
            linkedTaskLabels = links.groupBy { it.libraryItemId }.mapValues { (_, itemLinks) ->
                itemLinks.mapNotNull { taskById[it.taskId]?.primaryLabel() }.distinct()
            }
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryListState()
        )

    fun setFilter(type: LibraryItemType?) {
        _filter.value = type
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    /** Теги одного элемента (для карточки в списке). */
    fun tagsForItem(id: Int): Flow<List<LibraryTagEntity>> =
        repository.observeTagsForItem(id)

    fun deleteItem(item: LibraryItemEntity) {
        viewModelScope.launch { repository.deleteItem(item) }
    }

    fun swapItems(first: LibraryItemEntity, second: LibraryItemEntity) {
        viewModelScope.launch { repository.swapItems(first, second) }
    }
}
