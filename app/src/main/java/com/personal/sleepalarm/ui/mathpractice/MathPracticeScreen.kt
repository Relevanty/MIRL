package com.personal.sleepalarm.ui.mathpractice

import com.personal.sleepalarm.ui.theme.appAccents

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.domain.model.MathDifficulty
import com.personal.sleepalarm.ui.components.ChoiceChips
import com.personal.sleepalarm.ui.components.LabeledSlider
import com.personal.sleepalarm.ui.components.SectionCard
import com.personal.sleepalarm.ui.math.MathChallengeCard
import com.personal.sleepalarm.ui.theme.AppAccentTone
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MathPracticeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MathPracticeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val studyTone = MaterialTheme.appAccents.study
    val progressTone = MaterialTheme.appAccents.progress
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.math_practice_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = studyTone.color,
                    navigationIconContentColor = studyTone.color
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = progressTone.color)
            }
            return@Scaffold
        }

        when (state.stage) {
            MathPracticeStage.SETUP -> MathPracticeSetup(
                difficulty = state.difficulty,
                challengeCount = state.challengeCount,
                onDifficultyChange = viewModel::setDifficulty,
                onCountChange = viewModel::setChallengeCount,
                onStart = viewModel::startPractice,
                modifier = Modifier.padding(paddingValues)
            )

            MathPracticeStage.RUNNING -> MathPracticeRun(
                state = state,
                onInputChanged = viewModel::onInputChanged,
                onCheck = viewModel::checkAnswer,
                modifier = Modifier.padding(paddingValues)
            )

            MathPracticeStage.RESULT -> MathPracticeResult(
                state = state,
                onRetry = viewModel::retry,
                onEditParameters = viewModel::editParameters,
                onClose = onBack,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun MathPracticeSetup(
    difficulty: MathDifficulty,
    challengeCount: Int,
    onDifficultyChange: (MathDifficulty) -> Unit,
    onCountChange: (Int) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val studyTone = MaterialTheme.appAccents.study
    val infoTone = MaterialTheme.appAccents.info
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SectionCard(
            title = stringResource(R.string.math_practice_setup_title),
            tone = studyTone
        ) {
            Icon(
                imageVector = Icons.Default.Calculate,
                contentDescription = null,
                tint = studyTone.onContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.math_practice_setup_description),
                style = MaterialTheme.typography.bodyMedium,
                color = studyTone.onContainer.copy(alpha = 0.80f)
            )
            Spacer(Modifier.height(18.dp))
            ChoiceChips(
                label = stringResource(R.string.setting_math_difficulty),
                options = MathDifficulty.entries,
                selected = difficulty,
                optionText = { it.localizedName() },
                onSelect = onDifficultyChange
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(difficulty.hintRes()),
                style = MaterialTheme.typography.bodySmall,
                color = studyTone.onContainer.copy(alpha = 0.76f)
            )
            Spacer(Modifier.height(18.dp))
            LabeledSlider(
                label = stringResource(R.string.math_practice_count),
                value = challengeCount,
                valueText = challengeCount.toString(),
                valueRange = 1f..10f,
                steps = 8,
                onValueChange = onCountChange
            )
        }

        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(
                containerColor = studyTone.action,
                contentColor = studyTone.onAction
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(stringResource(R.string.math_practice_start))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = infoTone.action,
            contentColor = infoTone.onAction,
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = stringResource(R.string.math_practice_offline_note),
                style = MaterialTheme.typography.bodySmall,
                color = infoTone.onAction,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun MathPracticeRun(
    state: MathPracticeUiState,
    onInputChanged: (String) -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier
) {
    val run = state.run ?: return
    val progressTone = MaterialTheme.appAccents.progress
    val infoTone = MaterialTheme.appAccents.info
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(
                R.string.math_practice_progress,
                run.currentNumber,
                run.challengeCount
            ),
            style = MaterialTheme.typography.titleMedium,
            color = progressTone.color
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { run.currentNumber.toFloat() / run.challengeCount.toFloat() },
            color = progressTone.color,
            trackColor = progressTone.action,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            color = infoTone.action,
            contentColor = infoTone.onAction,
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = stringResource(
                    R.string.math_practice_elapsed,
                    formatElapsed((now - state.startedAtMillis).coerceAtLeast(0L))
                ),
                style = MaterialTheme.typography.bodySmall,
                color = infoTone.onAction,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
        Spacer(Modifier.height(18.dp))

        MathChallengeCard(
            challenge = run.currentChallenge,
            userInput = run.userInput,
            errorMessage = state.errorMessage,
            showHint = run.wrongAttempts >= 3,
            answerAccepted = run.isTransitioning || run.isComplete,
            enabled = !run.isTransitioning && !run.isComplete,
            onInputChanged = onInputChanged,
            onCheck = onCheck
        )
    }
}

@Composable
private fun MathPracticeResult(
    state: MathPracticeUiState,
    onRetry: () -> Unit,
    onEditParameters: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val run = state.run ?: return
    val successTone = MaterialTheme.appAccents.success
    val progressTone = MaterialTheme.appAccents.progress
    val studyTone = MaterialTheme.appAccents.study
    val infoTone = MaterialTheme.appAccents.info
    val duration = ((state.finishedAtMillis ?: System.currentTimeMillis()) - state.startedAtMillis)
        .coerceAtLeast(0L)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Calculate,
            contentDescription = null,
            tint = successTone.color
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.math_practice_result_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = successTone.color,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = progressTone.container,
                contentColor = progressTone.onContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ResultLine(
                    stringResource(R.string.math_practice_result_solved),
                    "${run.completedCount}/${run.challengeCount}",
                    progressTone
                )
                ResultLine(
                    stringResource(R.string.math_practice_result_attempts),
                    run.totalAttempts.toString(),
                    progressTone
                )
                ResultLine(
                    stringResource(R.string.math_practice_result_errors),
                    run.totalWrongAttempts.toString(),
                    progressTone
                )
                ResultLine(
                    stringResource(R.string.math_practice_result_time),
                    formatElapsed(duration),
                    progressTone
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = studyTone.action,
                contentColor = studyTone.onAction
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(stringResource(R.string.math_practice_retry))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onEditParameters,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = progressTone.color),
            border = BorderStroke(1.dp, progressTone.color),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.math_practice_change_parameters))
        }
        TextButton(
            onClick = onClose,
            colors = ButtonDefaults.textButtonColors(contentColor = infoTone.color)
        ) {
            Text(stringResource(R.string.action_close))
        }
    }
}

@Composable
private fun ResultLine(label: String, value: String, tone: AppAccentTone) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = tone.onContainer.copy(alpha = 0.76f))
        Text(value, color = tone.onContainer, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MathDifficulty.localizedName(): String = stringResource(
    when (this) {
        MathDifficulty.EASY -> R.string.math_difficulty_easy
        MathDifficulty.MEDIUM -> R.string.math_difficulty_medium
        MathDifficulty.HARD -> R.string.math_difficulty_hard
        MathDifficulty.EXPERT -> R.string.math_difficulty_expert
        MathDifficulty.EXTREME -> R.string.math_difficulty_extreme
    }
)

private fun MathDifficulty.hintRes(): Int = when (this) {
    MathDifficulty.EASY -> R.string.math_difficulty_easy_hint
    MathDifficulty.MEDIUM -> R.string.math_difficulty_medium_hint
    MathDifficulty.HARD -> R.string.math_difficulty_hard_hint
    MathDifficulty.EXPERT -> R.string.math_difficulty_expert_hint
    MathDifficulty.EXTREME -> R.string.math_difficulty_extreme_hint
}

private fun formatElapsed(millis: Long): String {
    val totalSeconds = millis / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
