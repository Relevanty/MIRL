package com.personal.sleepalarm.ui.library


import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.LibraryItemEntity
import com.personal.sleepalarm.data.db.entity.LibraryItemType
import com.personal.sleepalarm.data.db.entity.LibraryResourceKind
import com.personal.sleepalarm.data.repository.LibraryRepository
import com.personal.sleepalarm.util.CoverHelper
import com.personal.sleepalarm.util.ResourceFileHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Черновик элемента библиотеки (создание / редактирование).
 */
data class LibraryEditState(
    val itemId: Int? = null,
    val type: LibraryItemType = LibraryItemType.BOOK,
    val title: String = "",
    val author: String = "",
    val coverPath: String? = null,
    val resourceKind: LibraryResourceKind = LibraryResourceKind.NOTE,
    val localFilePath: String? = null,
    val originalFileName: String = "",
    val referenceUrl: String = "",
    val shortDescription: String = "",
    val impression: String = "",
    val thoughts: String = "",
    val rating: Int = 0,
    val tags: List<String> = emptyList(),
    val tagInput: String = ""
)

internal fun LibraryEditState.clearedForCreate(): LibraryEditState = LibraryEditState()

/**
 * ViewModel создания/редактирования элемента библиотеки.
 */
class LibraryEditViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val database = AppDatabase.getInstance(context)
    private val repository = LibraryRepository(database, database.libraryDao())

    private val _state = MutableStateFlow(LibraryEditState())
    val state: StateFlow<LibraryEditState> = _state
    private var loadJob: Job? = null
    private var persistedCoverPath: String? = null
    private var persistedResourcePath: String? = null

    /** Загружает существующий элемент для редактирования + его теги. */
    fun load(id: Int) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val item = repository.observeItem(id).first() ?: return@launch
            val tags = repository.observeTagsForItem(id).first()
            persistedCoverPath = item.coverUri
            persistedResourcePath = item.localFilePath

            _state.value = LibraryEditState(
                itemId = item.id,
                type = item.type,
                title = item.title,
                author = item.author,
                coverPath = item.coverUri,
                resourceKind = item.resourceKind,
                localFilePath = item.localFilePath,
                originalFileName = item.originalFileName,
                referenceUrl = item.referenceUrl,
                shortDescription = item.shortDescription,
                impression = item.impression,
                thoughts = item.thoughts,
                rating = item.rating,
                tags = tags.map { it.name }
            )
        }
    }

    /** Начинает создание нового элемента, не переиспользуя данные прошлого редактора. */
    fun resetForCreate() {
        discardChanges()
        loadJob?.cancel()
        loadJob = null
        persistedCoverPath = null
        persistedResourcePath = null
        _state.update { it.clearedForCreate() }
    }

    fun setType(type: LibraryItemType) = _state.update { it.copy(type = type) }
    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun setAuthor(v: String) = _state.update { it.copy(author = v) }
    fun setResourceKind(v: LibraryResourceKind) = _state.update { it.copy(resourceKind = v) }
    fun setReferenceUrl(v: String) = _state.update { it.copy(referenceUrl = v) }
    fun setShortDescription(v: String) = _state.update { it.copy(shortDescription = v) }
    fun setImpression(v: String) = _state.update { it.copy(impression = v) }
    fun setThoughts(v: String) = _state.update { it.copy(thoughts = v) }
    fun setRating(v: Int) = _state.update { it.copy(rating = v.coerceIn(0, 5)) }
    fun setTagInput(v: String) = _state.update { it.copy(tagInput = v) }

    /** Выбирает обложку из хранилища и копирует в приватную папку. */
    fun pickCover(uri: Uri) {
        viewModelScope.launch {
            val path = CoverHelper.copyCover(context, uri) ?: return@launch
            _state.value.coverPath
                ?.takeIf { it != persistedCoverPath }
                ?.let(CoverHelper::deleteCover)
            _state.update { it.copy(coverPath = path) }
        }
    }

    /** Копирует документ внутрь приложения, поэтому он останется доступным офлайн. */
    fun pickResource(uri: Uri) {
        viewModelScope.launch {
            val copied = ResourceFileHelper.copyIntoApp(context, uri) ?: return@launch
            _state.value.localFilePath
                ?.takeIf { it != persistedResourcePath }
                ?.let(ResourceFileHelper::delete)
            _state.update {
                it.copy(
                    resourceKind = LibraryResourceKind.DOCUMENT,
                    localFilePath = copied.path,
                    originalFileName = copied.displayName
                )
            }
        }
    }

    fun removeResource() {
        _state.value.localFilePath
            ?.takeIf { it != persistedResourcePath }
            ?.let(ResourceFileHelper::delete)
        _state.update {
            it.copy(localFilePath = null, originalFileName = "", resourceKind = LibraryResourceKind.NOTE)
        }
    }

    /**
     * Удаляет текущий редактируемый элемент вместе с обложкой.
     * Связи элемент-тег чистятся каскадно (FK ON DELETE CASCADE).
     */
    fun deleteCurrent() {
        val id = _state.value.itemId ?: return
        viewModelScope.launch {
            discardStagedCopies()
            val item = repository.observeItem(id).first() ?: return@launch
            repository.deleteItem(item)
        }
    }


    fun removeCover() {
        _state.value.coverPath
            ?.takeIf { it != persistedCoverPath }
            ?.let(CoverHelper::deleteCover)
        _state.update { it.copy(coverPath = null) }
    }

    fun addTag() {
        val name = _state.value.tagInput.trim()
        if (name.isEmpty()) return
        _state.update { s ->
            s.copy(
                tags = if (name in s.tags) s.tags else s.tags + name,
                tagInput = ""
            )
        }
    }

    fun removeTag(name: String) {
        _state.update { s -> s.copy(tags = s.tags - name) }
    }

    /** Сохраняет элемент. Возвращает true при успехе. */
    fun save(): Boolean {
        val s = _state.value
        if (s.title.isBlank()) return false

        viewModelScope.launch {
            val existing = s.itemId?.let { repository.observeItem(it).first() }

            repository.saveItem(
                LibraryItemEntity(
                    id = s.itemId ?: 0,
                    type = s.type,
                    title = s.title.trim(),
                    author = s.author.trim(),
                    coverUri = s.coverPath,
                    resourceKind = s.resourceKind,
                    localFilePath = s.localFilePath,
                    originalFileName = s.originalFileName,
                    referenceUrl = s.referenceUrl.trim(),
                    shortDescription = s.shortDescription,
                    impression = s.impression,
                    thoughts = s.thoughts,
                    rating = s.rating,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                ),
                s.tags
            )
            if (persistedCoverPath != s.coverPath) CoverHelper.deleteCover(persistedCoverPath)
            if (persistedResourcePath != s.localFilePath) ResourceFileHelper.delete(persistedResourcePath)
            persistedCoverPath = s.coverPath
            persistedResourcePath = s.localFilePath
        }
        return true
    }

    /** Called when the editor closes without saving. Persisted assets stay intact. */
    fun discardChanges() {
        discardStagedCopies()
    }

    private fun discardStagedCopies() {
        _state.value.coverPath
            ?.takeIf { it != persistedCoverPath }
            ?.let(CoverHelper::deleteCover)
        _state.value.localFilePath
            ?.takeIf { it != persistedResourcePath }
            ?.let(ResourceFileHelper::delete)
    }

    override fun onCleared() {
        discardStagedCopies()
        super.onCleared()
    }
}
