package com.personal.sleepalarm.ui.english

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.data.db.dao.EnglishProgressSummaryProjection
import com.personal.sleepalarm.data.db.dao.EnglishStudySetSummaryProjection
import com.personal.sleepalarm.data.db.entity.EnglishStudyCardEntity
import com.personal.sleepalarm.data.db.entity.EnglishStudySetEntity
import com.personal.sleepalarm.data.db.entity.EnglishWordEntity
import com.personal.sleepalarm.data.english.EnglishDictionaryWordCandidate
import com.personal.sleepalarm.data.english.EnglishStudyCardCandidate
import com.personal.sleepalarm.data.english.EnglishVocabularyRepository
import com.personal.sleepalarm.domain.english.EnglishDictionaryArticle
import com.personal.sleepalarm.domain.english.EnglishLearningMode
import com.personal.sleepalarm.domain.english.EnglishReviewGrade
import com.personal.sleepalarm.domain.english.EnglishStudyCardDraft
import com.personal.sleepalarm.domain.english.EnglishStudyDirection
import com.personal.sleepalarm.domain.english.EnglishStudySetDraft
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EnglishHubTab {
    LEARN,
    DICTIONARY,
    SETS
}

enum class EnglishCardRevealMode {
    TRANSLATION,
    DESCRIPTION,
    BOTH
}

sealed interface EnglishVocabularyDestination {
    data object Home : EnglishVocabularyDestination
    data class SetDetails(val setId: Long) : EnglishVocabularyDestination
    data object Study : EnglishVocabularyDestination
}

sealed interface EnglishSessionSource {
    data object Dictionary : EnglishSessionSource
    data class StudySet(val setId: Long, val title: String) : EnglishSessionSource
}

sealed interface EnglishStudyCardReference {
    data class DictionaryWord(val wordId: Int) : EnglishStudyCardReference
    data class StudyCard(val cardId: Long) : EnglishStudyCardReference
}

data class EnglishStudyCardUi(
    val stableKey: String,
    val reference: EnglishStudyCardReference,
    val direction: EnglishStudyDirection,
    val prompt: String,
    val answers: List<String>,
    val english: String,
    val russian: String,
    val definition: String,
    val example: String,
    val exampleTranslation: String,
    val notes: String,
    val pronunciation: String,
    val partOfSpeech: String,
    val level: String
)

enum class EnglishVocabularyError {
    OPEN_DICTIONARY,
    SEARCH,
    LOAD_ARTICLE,
    LOAD_SESSION,
    SAVE_REVIEW,
    SAVE_SET,
    DELETE_SET,
    SAVE_CARD,
    DELETE_CARD,
    ADD_TO_SET
}

enum class EnglishVocabularyNotice {
    SET_SAVED,
    SET_DELETED,
    CARD_SAVED,
    CARD_DELETED,
    ADDED_TO_SET,
    ALREADY_IN_SET
}

data class EnglishVocabularyUiState(
    val isLoading: Boolean = true,
    val destination: EnglishVocabularyDestination = EnglishVocabularyDestination.Home,
    val selectedTab: EnglishHubTab = EnglishHubTab.LEARN,
    val direction: EnglishStudyDirection = EnglishStudyDirection.EN_TO_RU,
    val revealMode: EnglishCardRevealMode = EnglishCardRevealMode.BOTH,
    val searchQuery: String = "",
    val searchResults: List<EnglishWordEntity> = emptyList(),
    val isSearching: Boolean = false,
    val article: EnglishDictionaryArticle? = null,
    val isArticleLoading: Boolean = false,
    val selectedSet: EnglishStudySetEntity? = null,
    val selectedSetCards: List<EnglishStudyCardEntity> = emptyList(),
    val sessionSource: EnglishSessionSource? = null,
    val currentCard: EnglishStudyCardUi? = null,
    val isCardRevealed: Boolean = false,
    val isSavingReview: Boolean = false,
    val reviewedInSession: Int = 0,
    val correctInSession: Int = 0,
    val sessionGoal: Int = DEFAULT_SESSION_GOAL,
    val sessionComplete: Boolean = false,
    val error: EnglishVocabularyError? = null,
    val notice: EnglishVocabularyNotice? = null
) {
    companion object {
        const val DEFAULT_SESSION_GOAL = 10
    }
}

class EnglishVocabularyViewModel(
    private val repository: EnglishVocabularyRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val _uiState = MutableStateFlow(EnglishVocabularyUiState())
    val uiState: StateFlow<EnglishVocabularyUiState> = _uiState.asStateFlow()

    val studySets: StateFlow<List<EnglishStudySetSummaryProjection>> = repository.observeStudySets()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val enToRuSummary = repository
        .observeDictionarySummary(EnglishStudyDirection.EN_TO_RU)
        .catch { emit(EMPTY_SUMMARY) }
    private val ruToEnSummary = repository
        .observeDictionarySummary(EnglishStudyDirection.RU_TO_EN)
        .catch { emit(EMPTY_SUMMARY) }

    val dictionarySummary: StateFlow<EnglishDirectionalSummaries> = combine(
        enToRuSummary,
        ruToEnSummary
    ) { enToRu, ruToEn -> EnglishDirectionalSummaries(enToRu, ruToEn) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            EnglishDirectionalSummaries(EMPTY_SUMMARY, EMPTY_SUMMARY)
        )

    private var searchJob: Job? = null
    private var articleJob: Job? = null
    private var setJob: Job? = null
    private var cardsJob: Job? = null
    private var sessionLoadJob: Job? = null
    private var reviewJob: Job? = null
    private var generation = 0L
    private val retryQueue = ArrayDeque<EnglishStudyCardUi>()

    init {
        viewModelScope.launch {
            runCatching { repository.ensureSeeded() }
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, error = null) }
                    loadDictionaryBrowse()
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isLoading = false, error = EnglishVocabularyError.OPEN_DICTIONARY)
                    }
                }
        }
    }

    fun selectTab(tab: EnglishHubTab) {
        if (_uiState.value.destination !is EnglishVocabularyDestination.Home) return
        _uiState.update { it.copy(selectedTab = tab, error = null) }
        if (tab == EnglishHubTab.DICTIONARY && _uiState.value.searchResults.isEmpty()) {
            loadDictionaryBrowse()
        }
    }

    fun setDirection(direction: EnglishStudyDirection) {
        if (_uiState.value.destination is EnglishVocabularyDestination.Study) return
        _uiState.update { it.copy(direction = direction) }
    }

    fun setRevealMode(mode: EnglishCardRevealMode) {
        if (_uiState.value.destination is EnglishVocabularyDestination.Study) return
        _uiState.update { it.copy(revealMode = mode) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearching = true, error = null) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(180)
            runCatching {
                if (query.isBlank()) repository.browseDictionary(limit = 40)
                else repository.searchDictionary(query, limit = 50)
            }.onSuccess { results ->
                _uiState.update { it.copy(searchResults = results, isSearching = false) }
            }.onFailure {
                _uiState.update {
                    it.copy(isSearching = false, error = EnglishVocabularyError.SEARCH)
                }
            }
        }
    }

    fun openArticle(wordId: Int) {
        articleJob?.cancel()
        articleJob = viewModelScope.launch {
            _uiState.update { it.copy(article = null, isArticleLoading = true, error = null) }
            runCatching { repository.dictionaryArticle(wordId) }
                .onSuccess { article ->
                    _uiState.update { it.copy(article = article, isArticleLoading = false) }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isArticleLoading = false, error = EnglishVocabularyError.LOAD_ARTICLE)
                    }
                }
        }
    }

    fun closeArticle() {
        articleJob?.cancel()
        _uiState.update { it.copy(article = null, isArticleLoading = false) }
    }

    fun openSet(setId: Long) {
        generation += 1L
        setJob?.cancel()
        cardsJob?.cancel()
        _uiState.update {
            it.copy(
                destination = EnglishVocabularyDestination.SetDetails(setId),
                selectedSet = null,
                selectedSetCards = emptyList(),
                error = null
            )
        }
        setJob = viewModelScope.launch {
            repository.observeStudySet(setId).collect { studySet ->
                _uiState.update { state ->
                    state.copy(
                        selectedSet = studySet,
                        direction = studySet?.let {
                            EnglishStudyDirection.fromStorage(it.defaultDirection, state.direction)
                        } ?: state.direction
                    )
                }
            }
        }
        cardsJob = viewModelScope.launch {
            repository.observeStudyCards(setId).collect { cards ->
                _uiState.update { it.copy(selectedSetCards = cards) }
            }
        }
    }

    fun closeSet() {
        setJob?.cancel()
        cardsJob?.cancel()
        _uiState.update {
            it.copy(
                destination = EnglishVocabularyDestination.Home,
                selectedTab = EnglishHubTab.SETS,
                selectedSet = null,
                selectedSetCards = emptyList(),
                error = null
            )
        }
    }

    fun createSet(draft: EnglishStudySetDraft) {
        viewModelScope.launch {
            runCatching { repository.createStudySet(draft) }
                .onSuccess { id ->
                    _uiState.update { it.copy(notice = EnglishVocabularyNotice.SET_SAVED) }
                    openSet(id)
                }
                .onFailure {
                    _uiState.update { it.copy(error = EnglishVocabularyError.SAVE_SET) }
                }
        }
    }

    fun createSetWithDictionaryWord(draft: EnglishStudySetDraft, wordId: Int) {
        viewModelScope.launch {
            runCatching {
                val setId = repository.createStudySet(draft)
                repository.addDictionaryWordToSet(setId, wordId)
                setId
            }.onSuccess { id ->
                _uiState.update { it.copy(notice = EnglishVocabularyNotice.ADDED_TO_SET) }
                openSet(id)
            }.onFailure {
                _uiState.update { it.copy(error = EnglishVocabularyError.ADD_TO_SET) }
            }
        }
    }

    fun updateSet(setId: Long, draft: EnglishStudySetDraft) {
        viewModelScope.launch {
            runCatching { repository.updateStudySet(setId, draft) }
                .onSuccess {
                    _uiState.update { it.copy(notice = EnglishVocabularyNotice.SET_SAVED) }
                }
                .onFailure {
                    _uiState.update { it.copy(error = EnglishVocabularyError.SAVE_SET) }
                }
        }
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteStudySet(setId) }
                .onSuccess {
                    closeSet()
                    _uiState.update { it.copy(notice = EnglishVocabularyNotice.SET_DELETED) }
                }
                .onFailure {
                    _uiState.update { it.copy(error = EnglishVocabularyError.DELETE_SET) }
                }
        }
    }

    fun saveCard(setId: Long, cardId: Long?, draft: EnglishStudyCardDraft) {
        viewModelScope.launch {
            runCatching {
                if (cardId == null) repository.createStudyCard(setId, draft)
                else repository.updateStudyCard(cardId, draft)
            }.onSuccess {
                _uiState.update { it.copy(notice = EnglishVocabularyNotice.CARD_SAVED) }
            }.onFailure {
                _uiState.update { it.copy(error = EnglishVocabularyError.SAVE_CARD) }
            }
        }
    }

    fun deleteCard(cardId: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteStudyCard(cardId) }
                .onSuccess {
                    _uiState.update { it.copy(notice = EnglishVocabularyNotice.CARD_DELETED) }
                }
                .onFailure {
                    _uiState.update { it.copy(error = EnglishVocabularyError.DELETE_CARD) }
                }
        }
    }

    fun addArticleToSet(setId: Long) {
        val article = _uiState.value.article ?: return
        viewModelScope.launch {
            runCatching { repository.addDictionaryWordToSet(setId, article.wordId) }
                .onSuccess {
                    _uiState.update { it.copy(notice = EnglishVocabularyNotice.ADDED_TO_SET) }
                }
                .onFailure {
                    _uiState.update { it.copy(error = EnglishVocabularyError.ADD_TO_SET) }
                }
        }
    }

    fun startDictionarySession() {
        startSession(EnglishSessionSource.Dictionary)
    }

    fun startSetSession(setId: Long, title: String) {
        startSession(EnglishSessionSource.StudySet(setId, title))
    }

    private fun startSession(source: EnglishSessionSource) {
        generation += 1L
        sessionLoadJob?.cancel()
        retryQueue.clear()
        _uiState.update {
            it.copy(
                destination = EnglishVocabularyDestination.Study,
                sessionSource = source,
                currentCard = null,
                isCardRevealed = false,
                reviewedInSession = 0,
                correctInSession = 0,
                sessionComplete = false,
                isLoading = true,
                error = null
            )
        }
        loadNextStudyCard()
    }

    fun revealCard() {
        if (_uiState.value.currentCard == null || _uiState.value.isSavingReview) return
        _uiState.update { it.copy(isCardRevealed = true) }
    }

    fun gradeCurrentCard(grade: EnglishReviewGrade) {
        val state = _uiState.value
        val card = state.currentCard ?: return
        if (!state.isCardRevealed || state.isSavingReview) return
        val saveGeneration = generation
        _uiState.update { it.copy(isSavingReview = true, error = null) }
        reviewJob = viewModelScope.launch {
            val correct = grade != EnglishReviewGrade.AGAIN
            val result = runCatching {
                when (val reference = card.reference) {
                    is EnglishStudyCardReference.DictionaryWord -> repository.recordDictionaryReview(
                        wordId = reference.wordId,
                        direction = card.direction,
                        mode = EnglishLearningMode.CARDS,
                        grade = grade,
                        isCorrect = correct,
                        nowMillis = nowMillis()
                    )
                    is EnglishStudyCardReference.StudyCard -> repository.recordStudyCardReview(
                        cardId = reference.cardId,
                        direction = card.direction,
                        mode = EnglishLearningMode.CARDS,
                        grade = grade,
                        isCorrect = correct,
                        nowMillis = nowMillis()
                    )
                }
            }
            if (saveGeneration != generation) return@launch
            result.onSuccess {
                if (!correct) retryQueue.addLast(card)
                val reviewed = _uiState.value.reviewedInSession + 1
                _uiState.update {
                    it.copy(
                        reviewedInSession = reviewed,
                        correctInSession = it.correctInSession + if (correct) 1 else 0,
                        isSavingReview = false
                    )
                }
                if (reviewed >= _uiState.value.sessionGoal) finishSession() else loadNextStudyCard()
            }.onFailure {
                _uiState.update {
                    it.copy(isSavingReview = false, error = EnglishVocabularyError.SAVE_REVIEW)
                }
            }
        }
    }

    fun leaveSession() {
        generation += 1L
        sessionLoadJob?.cancel()
        retryQueue.clear()
        val source = _uiState.value.sessionSource
        _uiState.update {
            it.copy(
                destination = when (source) {
                    is EnglishSessionSource.StudySet -> EnglishVocabularyDestination.SetDetails(source.setId)
                    else -> EnglishVocabularyDestination.Home
                },
                selectedTab = if (source is EnglishSessionSource.StudySet) EnglishHubTab.SETS else EnglishHubTab.LEARN,
                sessionSource = null,
                currentCard = null,
                isCardRevealed = false,
                isSavingReview = false,
                sessionComplete = false,
                error = null
            )
        }
    }

    fun consumeTransientMessage() {
        _uiState.update { it.copy(error = null, notice = null) }
    }

    private fun loadNextStudyCard() {
        val state = _uiState.value
        val source = state.sessionSource ?: return
        val loadGeneration = generation
        sessionLoadJob?.cancel()
        sessionLoadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, currentCard = null, isCardRevealed = false, error = null)
            }
            val retry = retryQueue.takeIf {
                it.isNotEmpty() && _uiState.value.reviewedInSession >= 3 &&
                    _uiState.value.reviewedInSession % 4 == 0
            }?.removeFirstOrNull()
            val result = if (retry != null) Result.success(retry) else runCatching {
                when (source) {
                    EnglishSessionSource.Dictionary -> repository.nextDictionaryWord(
                        requestedDirection = _uiState.value.direction,
                        mixedTurn = _uiState.value.reviewedInSession,
                        nowMillis = nowMillis()
                    )?.toUi()
                    is EnglishSessionSource.StudySet -> repository.nextStudyCard(
                        setId = source.setId,
                        requestedDirection = _uiState.value.direction,
                        mixedTurn = _uiState.value.reviewedInSession,
                        nowMillis = nowMillis()
                    )?.toUi()
                }
            }
            if (loadGeneration != generation) return@launch
            result.onSuccess { card ->
                val nextCard = card ?: retryQueue.removeFirstOrNull()
                if (nextCard == null) finishSession()
                else _uiState.update {
                    it.copy(isLoading = false, currentCard = nextCard, isCardRevealed = false)
                }
            }.onFailure {
                _uiState.update {
                    it.copy(isLoading = false, error = EnglishVocabularyError.LOAD_SESSION)
                }
            }
        }
    }

    private fun finishSession() {
        _uiState.update {
            it.copy(
                isLoading = false,
                currentCard = null,
                isCardRevealed = false,
                isSavingReview = false,
                sessionComplete = true
            )
        }
    }

    private fun loadDictionaryBrowse() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            runCatching { repository.browseDictionary(limit = 40) }
                .onSuccess { words ->
                    _uiState.update { it.copy(searchResults = words, isSearching = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isSearching = false, error = EnglishVocabularyError.SEARCH) }
                }
        }
    }

    private fun EnglishDictionaryWordCandidate.toUi() = EnglishStudyCardUi(
        stableKey = "dictionary:${word.id}:${direction.name}",
        reference = EnglishStudyCardReference.DictionaryWord(word.id),
        direction = direction,
        prompt = prompt.prompt,
        answers = prompt.expectedAnswers,
        english = word.word,
        russian = word.translation,
        definition = prompt.definition,
        example = prompt.example,
        exampleTranslation = prompt.exampleTranslation,
        notes = prompt.notes,
        pronunciation = word.pronunciation,
        partOfSpeech = word.partOfSpeech,
        level = word.level
    )

    private fun EnglishStudyCardCandidate.toUi() = EnglishStudyCardUi(
        stableKey = "set:${card.id}:${direction.name}",
        reference = EnglishStudyCardReference.StudyCard(card.id),
        direction = direction,
        prompt = prompt.prompt,
        answers = prompt.expectedAnswers,
        english = card.term,
        russian = card.translation,
        definition = prompt.definition,
        example = prompt.example,
        exampleTranslation = prompt.exampleTranslation,
        notes = prompt.notes,
        pronunciation = "",
        partOfSpeech = "",
        level = ""
    )

    class Factory(
        private val repository: EnglishVocabularyRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(EnglishVocabularyViewModel::class.java))
            return EnglishVocabularyViewModel(repository) as T
        }
    }

    companion object {
        private val EMPTY_SUMMARY = EnglishProgressSummaryProjection(
            totalWords = 10_000,
            startedWords = 0,
            masteredWords = 0,
            totalReviews = 0,
            correctReviews = 0
        )
    }
}

data class EnglishDirectionalSummaries(
    val enToRu: EnglishProgressSummaryProjection,
    val ruToEn: EnglishProgressSummaryProjection
) {
    fun forDirection(direction: EnglishStudyDirection): EnglishProgressSummaryProjection = when (direction) {
        EnglishStudyDirection.EN_TO_RU -> enToRu
        EnglishStudyDirection.RU_TO_EN -> ruToEn
        EnglishStudyDirection.MIXED -> {
            val reviews = enToRu.totalReviews + ruToEn.totalReviews
            val correct = enToRu.correctReviews + ruToEn.correctReviews
            EnglishProgressSummaryProjection(
                totalWords = maxOf(enToRu.totalWords, ruToEn.totalWords, 10_000),
                startedWords = maxOf(enToRu.startedWords, ruToEn.startedWords),
                masteredWords = maxOf(enToRu.masteredWords, ruToEn.masteredWords),
                totalReviews = reviews,
                correctReviews = correct
            )
        }
    }
}
