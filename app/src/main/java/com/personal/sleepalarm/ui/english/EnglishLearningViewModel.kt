package com.personal.sleepalarm.ui.english

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.data.db.dao.EnglishProgressSummaryProjection
import com.personal.sleepalarm.data.db.entity.EnglishWordEntity
import com.personal.sleepalarm.data.english.EnglishVocabularyRepository
import com.personal.sleepalarm.domain.english.EnglishAnswerEvaluation
import com.personal.sleepalarm.domain.english.EnglishAnswerEvaluator
import com.personal.sleepalarm.domain.english.EnglishAnswerFeedback
import com.personal.sleepalarm.domain.english.EnglishLearningMode
import com.personal.sleepalarm.domain.english.EnglishReviewGrade
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EnglishLearningUiState(
    val isLoading: Boolean = true,
    val error: EnglishLearningError? = null,
    val selectedMode: EnglishLearningMode? = null,
    val currentWord: EnglishWordEntity? = null,
    val isAnswerVisible: Boolean = false,
    val typedAnswer: String = "",
    val evaluation: EnglishAnswerEvaluation? = null,
    val isSavingReview: Boolean = false,
    val reviewSaveFailed: Boolean = false,
    val reviewedInSession: Int = 0,
    val correctInSession: Int = 0
)

enum class EnglishLearningError {
    OPEN_DICTIONARY,
    LOAD_WORD,
    SAVE_REVIEW
}

class EnglishLearningViewModel(
    private val repository: EnglishVocabularyRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val _uiState = MutableStateFlow(EnglishLearningUiState())
    val uiState: StateFlow<EnglishLearningUiState> = _uiState.asStateFlow()

    val summary: StateFlow<EnglishProgressSummaryProjection> = repository.observeSummary()
        .catch { emit(EMPTY_SUMMARY) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EMPTY_SUMMARY)

    private var loadJob: Job? = null
    private var sessionGeneration: Long = 0L

    init {
        viewModelScope.launch {
            runCatching { repository.ensureSeeded() }
                .onSuccess { _uiState.update { it.copy(isLoading = false) } }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = EnglishLearningError.OPEN_DICTIONARY
                        )
                    }
                }
        }
    }

    fun startMode(mode: EnglishLearningMode) {
        if (_uiState.value.isLoading) return
        sessionGeneration += 1L
        _uiState.update {
            it.copy(
                selectedMode = mode,
                reviewedInSession = 0,
                correctInSession = 0,
                error = null
            )
        }
        loadNextWord()
    }

    fun leaveSession() {
        sessionGeneration += 1L
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                selectedMode = null,
                currentWord = null,
                isAnswerVisible = false,
                typedAnswer = "",
                evaluation = null,
                isSavingReview = false,
                reviewSaveFailed = false,
                error = null
            )
        }
    }

    fun revealAnswer() {
        _uiState.update { it.copy(isAnswerVisible = true) }
    }

    fun updateTypedAnswer(value: String) {
        if (_uiState.value.evaluation != null) return
        _uiState.update { it.copy(typedAnswer = value) }
    }

    fun submitTypedAnswer() {
        val state = _uiState.value
        val word = state.currentWord ?: return
        val mode = state.selectedMode ?: return
        if (mode != EnglishLearningMode.WRITING && mode != EnglishLearningMode.LISTENING) return
        if (state.typedAnswer.isBlank() || state.evaluation != null) return
        saveEvaluation(
            EnglishAnswerEvaluator.evaluateTyped(word.word, state.typedAnswer)
        )
    }

    fun submitSpeech(hypotheses: List<String>) {
        val state = _uiState.value
        val word = state.currentWord ?: return
        if (state.selectedMode != EnglishLearningMode.PRONUNCIATION || state.evaluation != null) return
        saveEvaluation(EnglishAnswerEvaluator.evaluateSpeech(word.word, hypotheses))
    }

    fun gradeCard(grade: EnglishReviewGrade) {
        val state = _uiState.value
        if (state.selectedMode != EnglishLearningMode.CARDS || state.evaluation != null) return
        saveGrade(grade, grade != EnglishReviewGrade.AGAIN)
    }

    fun selfReportPronunciation(success: Boolean) {
        val state = _uiState.value
        val word = state.currentWord ?: return
        if (state.selectedMode != EnglishLearningMode.PRONUNCIATION || state.evaluation != null) return
        saveEvaluation(
            EnglishAnswerEvaluation(
                grade = if (success) EnglishReviewGrade.GOOD else EnglishReviewGrade.AGAIN,
                isCorrect = success,
                normalizedExpected = word.word,
                normalizedActual = "",
                feedback = if (success) EnglishAnswerFeedback.SELF_REPORTED_CORRECT else EnglishAnswerFeedback.TRY_AGAIN
            )
        )
    }

    fun nextWord() {
        val state = _uiState.value
        if (state.evaluation == null || state.isSavingReview || state.reviewSaveFailed) return
        loadNextWord()
    }

    fun retrySaveReview() {
        val state = _uiState.value
        val evaluation = state.evaluation ?: return
        if (!state.reviewSaveFailed || state.isSavingReview) return
        persistReview(evaluation.grade, evaluation.isCorrect)
    }

    private fun saveEvaluation(evaluation: EnglishAnswerEvaluation) {
        val state = _uiState.value
        if (state.isSavingReview || state.evaluation != null) return
        _uiState.update {
            it.copy(
                evaluation = evaluation,
                isAnswerVisible = true,
                isSavingReview = true,
                reviewSaveFailed = false,
                error = null
            )
        }
        persistReview(evaluation.grade, evaluation.isCorrect)
    }

    private fun saveGrade(
        grade: EnglishReviewGrade,
        isCorrect: Boolean,
        keepEvaluation: Boolean = false
    ) {
        val state = _uiState.value
        val word = state.currentWord ?: return
        val mode = state.selectedMode ?: return
        if (state.isSavingReview || state.evaluation != null) return
        if (!keepEvaluation) {
            _uiState.update {
                it.copy(
                    evaluation = EnglishAnswerEvaluation(
                        grade = grade,
                        isCorrect = isCorrect,
                        normalizedExpected = word.word,
                        normalizedActual = "",
                        feedback = EnglishAnswerFeedback.CARD_SAVED
                    ),
                    isSavingReview = true,
                    reviewSaveFailed = false,
                    error = null
                )
            }
        }
        persistReview(grade, isCorrect)
    }

    private fun persistReview(grade: EnglishReviewGrade, isCorrect: Boolean) {
        val state = _uiState.value
        val word = state.currentWord ?: return
        val mode = state.selectedMode ?: return
        val saveGeneration = sessionGeneration
        if (!state.isSavingReview) {
            _uiState.update {
                it.copy(isSavingReview = true, reviewSaveFailed = false, error = null)
            }
        }
        viewModelScope.launch {
            val result = runCatching {
                repository.recordReview(word, mode, grade, isCorrect, nowMillis())
            }
            // The DB write belongs to the old session and is intentionally allowed
            // to finish, but its UI result must never leak into a newly opened mode.
            if (saveGeneration != sessionGeneration) return@launch
            result.onSuccess {
                _uiState.update {
                    if (it.selectedMode != mode || it.currentWord?.id != word.id) it
                    else it.copy(
                            isSavingReview = false,
                            reviewSaveFailed = false,
                            reviewedInSession = it.reviewedInSession + 1,
                            correctInSession = it.correctInSession + if (isCorrect) 1 else 0
                        )
                }
            }.onFailure {
                _uiState.update {
                    if (it.selectedMode != mode || it.currentWord?.id != word.id) it
                    else it.copy(
                            isSavingReview = false,
                            reviewSaveFailed = true,
                            error = EnglishLearningError.SAVE_REVIEW
                        )
                }
            }
        }
    }

    private fun loadNextWord() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    currentWord = null,
                    isAnswerVisible = false,
                    typedAnswer = "",
                    evaluation = null,
                    isSavingReview = false,
                    reviewSaveFailed = false,
                    error = null
                )
            }
            runCatching {
                repository.ensureSeeded()
                repository.nextWord(nowMillis())
            }.onSuccess { word ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentWord = word,
                        error = null
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(isLoading = false, error = EnglishLearningError.LOAD_WORD)
                }
            }
        }
    }

    class Factory(
        private val repository: EnglishVocabularyRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(EnglishLearningViewModel::class.java))
            return EnglishLearningViewModel(repository) as T
        }
    }

    private companion object {
        val EMPTY_SUMMARY = EnglishProgressSummaryProjection(
            totalWords = 0,
            startedWords = 0,
            masteredWords = 0,
            totalReviews = 0,
            correctReviews = 0
        )
    }
}
