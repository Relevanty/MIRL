package com.personal.sleepalarm.ui.mathpractice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.R
import com.personal.sleepalarm.app.App
import com.personal.sleepalarm.domain.math.MathChallengeRunEffect
import com.personal.sleepalarm.domain.math.MathChallengeRunEngine
import com.personal.sleepalarm.domain.math.MathChallengeRunState
import com.personal.sleepalarm.domain.math.MAX_MATH_CHALLENGE_COUNT
import com.personal.sleepalarm.domain.math.MIN_MATH_CHALLENGE_COUNT
import com.personal.sleepalarm.domain.model.MathAnswerParseError
import com.personal.sleepalarm.domain.model.MathDifficulty
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class MathPracticeStage { SETUP, RUNNING, RESULT }

data class MathPracticeUiState(
    val isLoading: Boolean = true,
    val stage: MathPracticeStage = MathPracticeStage.SETUP,
    val difficulty: MathDifficulty = MathDifficulty.MEDIUM,
    val challengeCount: Int = 1,
    val run: MathChallengeRunState? = null,
    val errorMessage: String? = null,
    val startedAtMillis: Long = 0L,
    val finishedAtMillis: Long? = null
)

class MathPracticeViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val profileRepository = (application as App).serviceLocator.profileRepository

    private val _state = MutableStateFlow(MathPracticeUiState())
    val state: StateFlow<MathPracticeUiState> = _state

    private var advanceJob: Job? = null

    init {
        viewModelScope.launch {
            val profile = profileRepository.getProfile()
            val savedDifficulty = savedStateHandle.get<String>(KEY_DIFFICULTY)
                ?.let { name -> runCatching { MathDifficulty.valueOf(name) }.getOrNull() }
            val difficulty = savedDifficulty ?: profile.mathDifficulty
            val count = (savedStateHandle.get<Int>(KEY_COUNT) ?: profile.mathChallengeCount)
                .coerceIn(MIN_MATH_CHALLENGE_COUNT, MAX_MATH_CHALLENGE_COUNT)
            val requestedStage = savedStateHandle.get<String>(KEY_STAGE)
                ?.let { name -> runCatching { MathPracticeStage.valueOf(name) }.getOrNull() }
                ?: MathPracticeStage.SETUP
            val restoredRun = restoreRun(difficulty, count)
            val stage = when {
                restoredRun == null -> MathPracticeStage.SETUP
                restoredRun.isComplete -> MathPracticeStage.RESULT
                requestedStage == MathPracticeStage.RESULT -> MathPracticeStage.RUNNING
                else -> requestedStage
            }

            _state.value = MathPracticeUiState(
                isLoading = false,
                stage = stage,
                difficulty = difficulty,
                challengeCount = count,
                run = restoredRun,
                startedAtMillis = savedStateHandle.get<Long>(KEY_STARTED_AT) ?: 0L,
                finishedAtMillis = savedStateHandle.get<Long>(KEY_FINISHED_AT)
            )
            if (restoredRun?.isTransitioning == true) scheduleAdvance()
        }
    }

    fun setDifficulty(difficulty: MathDifficulty) {
        if (_state.value.stage != MathPracticeStage.SETUP) return
        _state.update { it.copy(difficulty = difficulty) }
        savedStateHandle[KEY_DIFFICULTY] = difficulty.name
    }

    fun setChallengeCount(count: Int) {
        if (_state.value.stage != MathPracticeStage.SETUP) return
        val safeCount = count.coerceIn(MIN_MATH_CHALLENGE_COUNT, MAX_MATH_CHALLENGE_COUNT)
        _state.update { it.copy(challengeCount = safeCount) }
        savedStateHandle[KEY_COUNT] = safeCount
    }

    fun startPractice() {
        val current = _state.value
        if (current.isLoading) return
        val run = MathChallengeRunEngine.start(
            difficulty = current.difficulty,
            challengeCount = current.challengeCount,
            seed = Random.nextInt()
        )
        val startedAt = System.currentTimeMillis()
        _state.value = current.copy(
            stage = MathPracticeStage.RUNNING,
            run = run,
            errorMessage = null,
            startedAtMillis = startedAt,
            finishedAtMillis = null
        )
        persistRun(run, MathPracticeStage.RUNNING, startedAt, null)
    }

    fun onInputChanged(text: String) {
        val current = _state.value
        val run = current.run ?: return
        val updated = MathChallengeRunEngine.updateInput(run, text)
        if (updated == run) return
        _state.value = current.copy(run = updated, errorMessage = null)
        persistCurrent(updated)
    }

    fun checkAnswer() {
        val current = _state.value
        if (current.stage != MathPracticeStage.RUNNING) return
        val run = current.run ?: return
        val update = MathChallengeRunEngine.check(run)
        val error = when (val effect = update.effect) {
            is MathChallengeRunEffect.Invalid -> getApplication<Application>().getString(
                if (effect.reason == MathAnswerParseError.EMPTY) {
                    R.string.alarm_error_empty_answer
                } else {
                    R.string.alarm_error_answer_format
                }
            )
            MathChallengeRunEffect.Incorrect ->
                getApplication<Application>().getString(R.string.alarm_wrong_answer)
            else -> null
        }

        val finishedAt = if (update.effect == MathChallengeRunEffect.Completed) {
            System.currentTimeMillis()
        } else {
            null
        }
        val stage = if (finishedAt != null) MathPracticeStage.RESULT else current.stage
        _state.value = current.copy(
            stage = stage,
            run = update.state,
            errorMessage = error,
            finishedAtMillis = finishedAt
        )
        persistRun(update.state, stage, current.startedAtMillis, finishedAt)

        if (update.effect == MathChallengeRunEffect.Advance) scheduleAdvance()
    }

    fun retry() = startPractice()

    fun editParameters() {
        advanceJob?.cancel()
        val current = _state.value
        _state.value = current.copy(
            stage = MathPracticeStage.SETUP,
            run = null,
            errorMessage = null,
            startedAtMillis = 0L,
            finishedAtMillis = null
        )
        savedStateHandle[KEY_STAGE] = MathPracticeStage.SETUP.name
        clearSavedRun()
    }

    private fun scheduleAdvance() {
        advanceJob?.cancel()
        advanceJob = viewModelScope.launch {
            delay(ADVANCE_DELAY_MS)
            val current = _state.value
            if (current.stage != MathPracticeStage.RUNNING) return@launch
            val run = current.run ?: return@launch
            val advanced = MathChallengeRunEngine.advance(run)
            if (advanced == run) return@launch
            _state.value = current.copy(run = advanced, errorMessage = null)
            persistCurrent(advanced)
        }
    }

    private fun restoreRun(
        difficulty: MathDifficulty,
        count: Int
    ): MathChallengeRunState? {
        val seed = savedStateHandle.get<Int>(KEY_SEED) ?: return null
        return MathChallengeRunEngine.restore(
            difficulty = difficulty,
            challengeCount = count,
            seed = seed,
            currentIndex = savedStateHandle.get<Int>(KEY_INDEX) ?: 0,
            completedCount = savedStateHandle.get<Int>(KEY_COMPLETED) ?: 0,
            userInput = savedStateHandle.get<String>(KEY_INPUT).orEmpty(),
            wrongAttempts = savedStateHandle.get<Int>(KEY_WRONG) ?: 0,
            totalWrongAttempts = savedStateHandle.get<Int>(KEY_TOTAL_WRONG) ?: 0,
            totalAttempts = savedStateHandle.get<Int>(KEY_TOTAL_ATTEMPTS) ?: 0,
            isTransitioning = savedStateHandle.get<Boolean>(KEY_TRANSITIONING) ?: false,
            isComplete = savedStateHandle.get<Boolean>(KEY_COMPLETE) ?: false
        )
    }

    private fun persistCurrent(run: MathChallengeRunState) {
        val current = _state.value
        persistRun(run, current.stage, current.startedAtMillis, current.finishedAtMillis)
    }

    private fun persistRun(
        run: MathChallengeRunState,
        stage: MathPracticeStage,
        startedAtMillis: Long,
        finishedAtMillis: Long?
    ) {
        savedStateHandle[KEY_STAGE] = stage.name
        savedStateHandle[KEY_DIFFICULTY] = run.difficulty.name
        savedStateHandle[KEY_COUNT] = run.challengeCount
        savedStateHandle[KEY_SEED] = run.seed
        savedStateHandle[KEY_INDEX] = run.currentIndex
        savedStateHandle[KEY_COMPLETED] = run.completedCount
        savedStateHandle[KEY_INPUT] = run.userInput
        savedStateHandle[KEY_WRONG] = run.wrongAttempts
        savedStateHandle[KEY_TOTAL_WRONG] = run.totalWrongAttempts
        savedStateHandle[KEY_TOTAL_ATTEMPTS] = run.totalAttempts
        savedStateHandle[KEY_TRANSITIONING] = run.isTransitioning
        savedStateHandle[KEY_COMPLETE] = run.isComplete
        savedStateHandle[KEY_STARTED_AT] = startedAtMillis
        savedStateHandle[KEY_FINISHED_AT] = finishedAtMillis
    }

    private fun clearSavedRun() {
        listOf(
            KEY_SEED,
            KEY_INDEX,
            KEY_COMPLETED,
            KEY_INPUT,
            KEY_WRONG,
            KEY_TOTAL_WRONG,
            KEY_TOTAL_ATTEMPTS,
            KEY_TRANSITIONING,
            KEY_COMPLETE,
            KEY_STARTED_AT,
            KEY_FINISHED_AT
        ).forEach { key -> savedStateHandle.remove<Any>(key) }
    }

    override fun onCleared() {
        advanceJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val ADVANCE_DELAY_MS = 550L
        private const val KEY_STAGE = "math_practice_stage"
        private const val KEY_DIFFICULTY = "math_practice_difficulty"
        private const val KEY_COUNT = "math_practice_count"
        private const val KEY_SEED = "math_practice_seed"
        private const val KEY_INDEX = "math_practice_index"
        private const val KEY_COMPLETED = "math_practice_completed"
        private const val KEY_INPUT = "math_practice_input"
        private const val KEY_WRONG = "math_practice_wrong"
        private const val KEY_TOTAL_WRONG = "math_practice_total_wrong"
        private const val KEY_TOTAL_ATTEMPTS = "math_practice_total_attempts"
        private const val KEY_TRANSITIONING = "math_practice_transitioning"
        private const val KEY_COMPLETE = "math_practice_complete"
        private const val KEY_STARTED_AT = "math_practice_started_at"
        private const val KEY_FINISHED_AT = "math_practice_finished_at"
    }
}
