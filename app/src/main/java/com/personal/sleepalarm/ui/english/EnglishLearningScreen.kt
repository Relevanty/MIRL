package com.personal.sleepalarm.ui.english

import com.personal.sleepalarm.ui.theme.appAccents

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.dao.EnglishProgressSummaryProjection
import com.personal.sleepalarm.data.db.entity.EnglishWordEntity
import com.personal.sleepalarm.data.english.EnglishVocabularyRepository
import com.personal.sleepalarm.domain.english.EnglishAnswerFeedback
import com.personal.sleepalarm.domain.english.EnglishLearningMode
import com.personal.sleepalarm.domain.english.EnglishReviewGrade

@Composable
fun EnglishLearningRoute(
    repository: EnglishVocabularyRepository,
    onBack: () -> Unit,
    showCardsMode: Boolean = true,
    modifier: Modifier = Modifier
) {
    val learningViewModel: EnglishLearningViewModel = viewModel(
        factory = EnglishLearningViewModel.Factory(repository)
    )
    EnglishLearningScreen(
        onBack = onBack,
        viewModel = learningViewModel,
        showCardsMode = showCardsMode,
        modifier = modifier
    )
}

@Composable
fun EnglishLearningScreen(
    onBack: () -> Unit,
    viewModel: EnglishLearningViewModel,
    showCardsMode: Boolean = true,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tts = remember(context) { OfflineEnglishTextToSpeech(context) }
    val ttsAvailability by tts.availability.collectAsState()
    val recognizer = remember(context, viewModel) {
        OnDeviceEnglishSpeechRecognizer(context, viewModel::submitSpeech)
    }
    val recognitionState by recognizer.state.collectAsState()
    var micPermissionDenied by remember { mutableStateOf(false) }
    val permissionGeneration = remember { EnglishAudioGenerationGuard() }
    val pronunciationActive = state.selectedMode == EnglishLearningMode.PRONUNCIATION &&
        state.currentWord != null &&
        state.evaluation == null &&
        !state.isSavingReview
    val startRecognition = rememberStartRecognitionAction(
        recognizer = recognizer,
        permissionGeneration = permissionGeneration,
        isPronunciationActive = pronunciationActive,
        onAttempt = { micPermissionDenied = false },
        onGranted = { micPermissionDenied = false },
        onDenied = { micPermissionDenied = true }
    )
    val openTtsSettings: () -> Unit = remember(context) {
        {
            openOfflineTextToSpeechSettings(context)
            Unit
        }
    }
    val leaveSession: () -> Unit = {
        permissionGeneration.invalidate()
        tts.stop()
        recognizer.cancelListening()
        micPermissionDenied = false
        viewModel.leaveSession()
    }

    BackHandler(enabled = state.selectedMode != null, onBack = leaveSession)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, tts) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tts.refreshAvailability()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.selectedMode, state.currentWord?.id) {
        permissionGeneration.invalidate()
        recognizer.cancelListening()
        micPermissionDenied = false
        tts.stop()
    }
    LaunchedEffect(state.selectedMode, state.currentWord?.id, ttsAvailability) {
        if (state.selectedMode == EnglishLearningMode.LISTENING &&
            state.currentWord != null &&
            ttsAvailability == OfflineTtsAvailability.READY
        ) {
            tts.speak(state.currentWord!!.word)
        }
    }

    DisposableEffect(tts, recognizer, permissionGeneration) {
        onDispose {
            permissionGeneration.invalidate()
            tts.close()
            recognizer.close()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            EnglishTopBar(
                inSession = state.selectedMode != null,
                onBack = if (state.selectedMode == null) {
                    onBack
                } else {
                    leaveSession
                },
                sessionCorrect = state.correctInSession,
                sessionTotal = state.reviewedInSession
            )
            AnimatedContent(
                targetState = state.selectedMode,
                label = "english-mode",
                modifier = Modifier.fillMaxSize()
            ) { mode ->
                if (mode == null) {
                    EnglishDashboard(
                        summary = summary,
                        loading = state.isLoading,
                        error = state.error,
                        ttsAvailability = ttsAvailability,
                        onMode = viewModel::startMode,
                        showCardsMode = showCardsMode,
                        onOpenTtsSettings = openTtsSettings
                    )
                } else {
                    EnglishStudySession(
                        state = state,
                        mode = mode,
                        ttsAvailability = ttsAvailability,
                        recognitionState = recognitionState,
                        micPermissionDenied = micPermissionDenied,
                        onSpeak = { state.currentWord?.word?.let(tts::speak) },
                        onOpenTtsSettings = openTtsSettings,
                        onStartRecognition = startRecognition,
                        onStopRecognition = recognizer::stopListening,
                        onReveal = viewModel::revealAnswer,
                        onAnswerChange = viewModel::updateTypedAnswer,
                        onSubmitAnswer = viewModel::submitTypedAnswer,
                        onGrade = viewModel::gradeCard,
                        onSelfReport = viewModel::selfReportPronunciation,
                        onNext = viewModel::nextWord,
                        onRetrySave = viewModel::retrySaveReview
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberStartRecognitionAction(
    recognizer: OnDeviceEnglishSpeechRecognizer,
    permissionGeneration: EnglishAudioGenerationGuard,
    isPronunciationActive: Boolean,
    onAttempt: () -> Unit,
    onGranted: () -> Unit,
    onDenied: () -> Unit
): () -> Unit {
    val context = LocalContext.current
    val latestIsActive by rememberUpdatedState(isPronunciationActive)
    val latestOnAttempt by rememberUpdatedState(onAttempt)
    val latestOnGranted by rememberUpdatedState(onGranted)
    val latestOnDenied by rememberUpdatedState(onDenied)
    var pendingPermissionToken by remember { mutableStateOf<Long?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val token = pendingPermissionToken
        pendingPermissionToken = null
        val isCurrent = token?.let(permissionGeneration::isCurrent) == true
        val canHandle = isRecognitionPermissionResultCurrent(
            hasRequestToken = token != null,
            tokenIsCurrent = isCurrent,
            pronunciationActive = latestIsActive
        )
        permissionGeneration.invalidate()
        if (!canHandle) return@rememberLauncherForActivityResult
        if (granted) {
            latestOnGranted()
            recognizer.startListening()
        } else {
            latestOnDenied()
        }
    }
    return action@{
        if (!latestIsActive) return@action
        if (pendingPermissionToken != null) return@action
        latestOnAttempt()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            recognizer.startListening()
        } else {
            pendingPermissionToken = permissionGeneration.next()
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

internal fun isRecognitionPermissionResultCurrent(
    hasRequestToken: Boolean,
    tokenIsCurrent: Boolean,
    pronunciationActive: Boolean
): Boolean = hasRequestToken && tokenIsCurrent && pronunciationActive

@Composable
private fun EnglishTopBar(
    inSession: Boolean,
    onBack: () -> Unit,
    sessionCorrect: Int,
    sessionTotal: Int
) {
    val compactBadge = LocalConfiguration.current.screenWidthDp < 360 ||
        LocalDensity.current.fontScale >= 1.3f
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(
                        if (inSession) R.string.english_close_session else R.string.english_back
                    )
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.english_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (inSession) {
                    Text(
                        text = stringResource(
                            R.string.english_session_score_format,
                            sessionCorrect,
                            sessionTotal
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            EnglishOfflineBadge(compact = compactBadge)
        }
    }
}

@Composable
private fun EnglishOfflineBadge(compact: Boolean) {
    val label = stringResource(R.string.english_offline_badge)
    Surface(
        color = MaterialTheme.appAccents.calm.container,
        contentColor = MaterialTheme.appAccents.calm.onContainer,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = if (compact) label else null,
                modifier = Modifier.size(16.dp)
            )
            if (!compact) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EnglishDashboard(
    summary: EnglishProgressSummaryProjection,
    loading: Boolean,
    error: EnglishLearningError?,
    ttsAvailability: OfflineTtsAvailability,
    onMode: (EnglishLearningMode) -> Unit,
    showCardsMode: Boolean,
    onOpenTtsSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.appAccents.study.container,
                    contentColor = MaterialTheme.appAccents.study.onContainer
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.appAccents.study.onContainer,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.english_subtitle),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.appAccents.study.onContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(
                                    R.string.english_progress_format,
                                    summary.masteredWords,
                                    summary.totalWords.coerceAtLeast(10_000.takeIf { loading } ?: 0)
                                ),
                                color = MaterialTheme.appAccents.study.onContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (summary.totalWords == 0) 0f
                            else summary.masteredWords.toFloat() / summary.totalWords
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.appAccents.study.color,
                        trackColor = MaterialTheme.appAccents.study.onContainer.copy(alpha = 0.18f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        EnglishMetric(
                            stringResource(R.string.english_started_format, summary.startedWords),
                            Modifier.weight(1f)
                        )
                        EnglishMetric(
                            stringResource(R.string.english_reviews_format, summary.totalReviews),
                            Modifier.weight(1f)
                        )
                        val accuracy = if (summary.totalReviews == 0) 0 else {
                            summary.correctReviews * 100 / summary.totalReviews
                        }
                        EnglishMetric(
                            stringResource(R.string.english_accuracy_format, accuracy),
                            Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (loading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.english_loading),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        error?.let { learningError ->
            item {
                Text(
                    text = learningErrorText(learningError),
                    color = MaterialTheme.appAccents.urgent.color,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            EnglishModeGrid(
                loading = loading,
                ttsAvailability = ttsAvailability,
                showCardsMode = showCardsMode,
                onMode = onMode
            )
        }
        if (ttsAvailability != OfflineTtsAvailability.READY) {
            item {
                EnglishTtsStatus(
                    availability = ttsAvailability,
                    onOpenSettings = onOpenTtsSettings
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.english_dictionary_source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun EnglishMetric(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.appAccents.study.onContainer,
        textAlign = TextAlign.Center
    )
}

private data class EnglishModeCardModel(
    val mode: EnglishLearningMode,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val enabled: Boolean
)

@Composable
private fun EnglishModeGrid(
    loading: Boolean,
    ttsAvailability: OfflineTtsAvailability,
    showCardsMode: Boolean,
    onMode: (EnglishLearningMode) -> Unit
) {
    val cards = listOf(
        EnglishModeCardModel(
            EnglishLearningMode.CARDS,
            Icons.Default.Style,
            stringResource(R.string.english_mode_cards),
            stringResource(R.string.english_mode_cards_hint),
            !loading
        ),
        EnglishModeCardModel(
            EnglishLearningMode.WRITING,
            Icons.Default.Edit,
            stringResource(R.string.english_mode_writing),
            stringResource(R.string.english_mode_writing_hint),
            !loading
        ),
        EnglishModeCardModel(
            EnglishLearningMode.PRONUNCIATION,
            Icons.Default.Mic,
            stringResource(R.string.english_mode_pronunciation),
            stringResource(R.string.english_mode_pronunciation_hint),
            !loading
        ),
        EnglishModeCardModel(
            EnglishLearningMode.LISTENING,
            Icons.Default.Headphones,
            stringResource(R.string.english_mode_listening),
            stringResource(R.string.english_mode_listening_hint),
            !loading && ttsAvailability != OfflineTtsAvailability.OFFLINE_VOICE_MISSING &&
                ttsAvailability != OfflineTtsAvailability.ERROR
        )
    ).filter { showCardsMode || it.mode != EnglishLearningMode.CARDS }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = englishModeGridColumns(maxWidth.value, LocalDensity.current.fontScale)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cards.chunked(columns).forEach { rowCards ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowCards.forEach { card ->
                        EnglishModeCard(
                            mode = card.mode,
                            icon = card.icon,
                            title = card.title,
                            subtitle = card.subtitle,
                            enabled = card.enabled,
                            onClick = onMode,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - rowCards.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

internal fun englishModeGridColumns(maxWidthDp: Float, fontScale: Float): Int =
    if (maxWidthDp < 390f || fontScale >= 1.3f) 1 else 2

@Composable
private fun EnglishModeCard(
    mode: EnglishLearningMode,
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: (EnglishLearningMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { onClick(mode) },
        enabled = enabled,
        modifier = modifier.heightIn(min = 152.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(20.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EnglishStudySession(
    state: EnglishLearningUiState,
    mode: EnglishLearningMode,
    ttsAvailability: OfflineTtsAvailability,
    recognitionState: OnDeviceRecognitionState,
    micPermissionDenied: Boolean,
    onSpeak: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onStartRecognition: () -> Unit,
    onStopRecognition: () -> Unit,
    onReveal: () -> Unit,
    onAnswerChange: (String) -> Unit,
    onSubmitAnswer: () -> Unit,
    onGrade: (EnglishReviewGrade) -> Unit,
    onSelfReport: (Boolean) -> Unit,
    onNext: () -> Unit,
    onRetrySave: () -> Unit
) {
    val word = state.currentWord
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.english_loading), color = MaterialTheme.colorScheme.onBackground)
                }
            }
            word == null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.appAccents.success.color,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.english_no_due),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        EnglishWordCard(
                            word = word,
                            mode = mode,
                            answerVisible = state.isAnswerVisible,
                            typedAnswer = state.typedAnswer,
                            evaluation = state.evaluation?.feedback,
                            ttsAvailability = ttsAvailability,
                            recognitionState = recognitionState,
                            micPermissionDenied = micPermissionDenied,
                            inputEnabled = state.evaluation == null && !state.isSavingReview,
                            onSpeak = onSpeak,
                            onOpenTtsSettings = onOpenTtsSettings,
                            onStartRecognition = onStartRecognition,
                            onStopRecognition = onStopRecognition,
                            onReveal = onReveal,
                            onAnswerChange = onAnswerChange,
                            onSubmitAnswer = onSubmitAnswer,
                            onSelfReport = onSelfReport
                        )
                    }
                    if (mode == EnglishLearningMode.CARDS && state.isAnswerVisible && state.evaluation == null) {
                        item { GradeButtons(onGrade) }
                    }
                    state.evaluation?.let { evaluation ->
                        item {
                            EnglishResultCard(
                                feedback = evaluation.feedback,
                                expected = word.word,
                                isCorrect = evaluation.isCorrect,
                                saving = state.isSavingReview,
                                saveFailed = state.reviewSaveFailed,
                                onRetrySave = onRetrySave,
                                onNext = onNext
                            )
                        }
                    }
                    state.error?.takeIf { state.reviewSaveFailed }?.let { error ->
                        item {
                            Text(learningErrorText(error), color = MaterialTheme.appAccents.urgent.color)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnglishWordCard(
    word: EnglishWordEntity,
    mode: EnglishLearningMode,
    answerVisible: Boolean,
    typedAnswer: String,
    evaluation: EnglishAnswerFeedback?,
    ttsAvailability: OfflineTtsAvailability,
    recognitionState: OnDeviceRecognitionState,
    micPermissionDenied: Boolean,
    inputEnabled: Boolean,
    onSpeak: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onStartRecognition: () -> Unit,
    onStopRecognition: () -> Unit,
    onReveal: () -> Unit,
    onAnswerChange: (String) -> Unit,
    onSubmitAnswer: () -> Unit,
    onSelfReport: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(
                    R.string.english_frequency_level_format,
                    frequencyLevelLabel(word.level)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.appAccents.study.color
            )
            Spacer(Modifier.height(18.dp))

            when (mode) {
                EnglishLearningMode.CARDS -> {
                    EnglishWordHeading(word, onSpeak, onOpenTtsSettings, ttsAvailability)
                    Spacer(Modifier.height(20.dp))
                    AnimatedVisibility(visible = answerVisible) {
                        TranslationAndHint(word)
                    }
                    if (!answerVisible) {
                        Button(onClick = onReveal, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.english_show_translation))
                        }
                    }
                }
                EnglishLearningMode.WRITING -> {
                    Text(
                        text = word.translation,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                    WordHint(word)
                    EnglishAnswerField(typedAnswer, inputEnabled, onAnswerChange, onSubmitAnswer)
                }
                EnglishLearningMode.PRONUNCIATION -> {
                    EnglishWordHeading(word, onSpeak, onOpenTtsSettings, ttsAvailability)
                    Spacer(Modifier.height(16.dp))
                    PronunciationControls(
                        recognitionState = recognitionState,
                        micPermissionDenied = micPermissionDenied,
                        enabled = evaluation == null,
                        onStart = onStartRecognition,
                        onStop = onStopRecognition,
                        onSelfReport = onSelfReport
                    )
                }
                EnglishLearningMode.LISTENING -> {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.appAccents.study.color,
                        modifier = Modifier.size(58.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.english_listen_prompt),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onSpeak, enabled = ttsAvailability == OfflineTtsAvailability.READY) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.english_speak_word))
                    }
                    EnglishTtsStatus(ttsAvailability, onOpenTtsSettings)
                    EnglishAnswerField(typedAnswer, inputEnabled, onAnswerChange, onSubmitAnswer)
                    if (answerVisible) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = word.translation,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnglishWordHeading(
    word: EnglishWordEntity,
    onSpeak: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    ttsAvailability: OfflineTtsAvailability
) {
    Text(
        text = word.word,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold
    )
    if (word.pronunciation.isNotBlank()) {
        Text(
            text = word.pronunciation,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    IconButton(
        onClick = onSpeak,
        enabled = ttsAvailability == OfflineTtsAvailability.READY,
        modifier = Modifier.size(52.dp)
    ) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.english_speak_word))
    }
    EnglishTtsStatus(ttsAvailability, onOpenTtsSettings)
}

@Composable
private fun TranslationAndHint(word: EnglishWordEntity) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.english_translation),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.appAccents.study.color
        )
        Text(
            word.translation,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        WordHint(word)
    }
}

@Composable
private fun WordHint(word: EnglishWordEntity) {
    if (word.hint.isBlank()) return
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.english_hint),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = word.hint,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun EnglishAnswerField(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Spacer(Modifier.height(18.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.english_type_word)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() })
    )
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = onSubmit,
        enabled = enabled && value.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.english_check))
    }
}

@Composable
private fun PronunciationControls(
    recognitionState: OnDeviceRecognitionState,
    micPermissionDenied: Boolean,
    enabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSelfReport: (Boolean) -> Unit
) {
    val recognizerAvailable =
        recognitionState.availability == OnDeviceRecognitionAvailability.AVAILABLE
    val offerSelfReport = shouldOfferPronunciationSelfReport(
        recognizerAvailable = recognizerAvailable,
        micPermissionDenied = micPermissionDenied,
        recognitionError = recognitionState.error
    )
    if (recognizerAvailable) {
        Button(
            onClick = if (recognitionState.isListening) onStop else onStart,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Mic, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(
                    if (recognitionState.isListening) R.string.english_stop_microphone
                    else R.string.english_start_microphone
                )
            )
        }
        if (recognitionState.isListening) {
            Text(
                stringResource(R.string.english_listening),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.appAccents.study.color,
                style = MaterialTheme.typography.labelLarge
            )
        }
        if (micPermissionDenied) {
            Text(
                stringResource(R.string.english_microphone_permission),
                color = MaterialTheme.appAccents.urgent.color,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
        if (recognitionState.partialText.isNotBlank()) {
            Text(
                stringResource(R.string.english_recognized_format, recognitionState.partialText),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        recognitionState.error?.let { error ->
            Text(
                recognitionErrorText(error),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.appAccents.urgent.color,
                style = MaterialTheme.typography.bodySmall
            )
        }
    } else {
        Text(
            text = stringResource(R.string.english_on_device_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
    if (offerSelfReport) {
        Spacer(Modifier.height(12.dp))
        PronunciationSelfReportButtons(enabled = enabled, onSelfReport = onSelfReport)
    }
}

internal fun shouldOfferPronunciationSelfReport(
    recognizerAvailable: Boolean,
    micPermissionDenied: Boolean,
    recognitionError: OnDeviceRecognitionError?
): Boolean = !recognizerAvailable || micPermissionDenied ||
    recognitionError == OnDeviceRecognitionError.PERMISSION

@Composable
private fun PronunciationSelfReportButtons(
    enabled: Boolean,
    onSelfReport: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onSelfReport(false) },
            enabled = enabled,
            modifier = Modifier.weight(1f)
        ) { Text(stringResource(R.string.english_self_retry), textAlign = TextAlign.Center) }
        Button(
            onClick = { onSelfReport(true) },
            enabled = enabled,
            modifier = Modifier.weight(1f)
        ) { Text(stringResource(R.string.english_self_correct), textAlign = TextAlign.Center) }
    }
}

@Composable
private fun EnglishTtsStatus(
    availability: OfflineTtsAvailability,
    onOpenSettings: () -> Unit
) {
    val text = when (availability) {
        OfflineTtsAvailability.INITIALIZING -> R.string.english_tts_initializing
        OfflineTtsAvailability.OFFLINE_VOICE_MISSING -> R.string.english_tts_missing
        OfflineTtsAvailability.ERROR -> R.string.english_tts_error
        OfflineTtsAvailability.READY -> null
    }
    text?.let {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = if (availability == OfflineTtsAvailability.INITIALIZING) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.appAccents.warning.color
                },
                textAlign = TextAlign.Center
            )
            if (availability == OfflineTtsAvailability.OFFLINE_VOICE_MISSING ||
                availability == OfflineTtsAvailability.ERROR
            ) {
                OutlinedButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.english_open_tts_settings))
                }
            }
        }
    }
}

@Composable
private fun recognitionErrorText(error: OnDeviceRecognitionError): String = stringResource(
    when (error) {
        OnDeviceRecognitionError.NO_MATCH -> R.string.english_recognition_no_match
        OnDeviceRecognitionError.SPEECH_TIMEOUT -> R.string.english_recognition_timeout
        OnDeviceRecognitionError.AUDIO -> R.string.english_recognition_audio_error
        OnDeviceRecognitionError.PERMISSION -> R.string.english_recognition_permission_error
        OnDeviceRecognitionError.BUSY -> R.string.english_recognition_busy
        OnDeviceRecognitionError.SERVICE_UNAVAILABLE -> R.string.english_recognition_unavailable
        OnDeviceRecognitionError.START_FAILED -> R.string.english_recognition_start_failed
    }
)

@Composable
private fun learningErrorText(error: EnglishLearningError): String = stringResource(
    when (error) {
        EnglishLearningError.OPEN_DICTIONARY -> R.string.english_error_open_dictionary
        EnglishLearningError.LOAD_WORD -> R.string.english_error_load_word
        EnglishLearningError.SAVE_REVIEW -> R.string.english_error_save_review
    }
)

@Composable
private fun GradeButtons(onGrade: (EnglishReviewGrade) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onGrade(EnglishReviewGrade.AGAIN) },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.english_grade_again)) }
            OutlinedButton(
                onClick = { onGrade(EnglishReviewGrade.HARD) },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.english_grade_hard)) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { onGrade(EnglishReviewGrade.GOOD) },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.english_grade_good)) }
            Button(
                onClick = { onGrade(EnglishReviewGrade.EASY) },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.english_grade_easy)) }
        }
    }
}

@Composable
private fun EnglishResultCard(
    feedback: EnglishAnswerFeedback,
    expected: String,
    isCorrect: Boolean,
    saving: Boolean,
    saveFailed: Boolean,
    onRetrySave: () -> Unit,
    onNext: () -> Unit
) {
    Card(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(
            containerColor = if (isCorrect) {
                MaterialTheme.appAccents.success.container
            } else {
                MaterialTheme.appAccents.urgent.container
            }
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = feedbackText(feedback, expected),
                style = MaterialTheme.typography.titleMedium,
                color = if (isCorrect) {
                    MaterialTheme.appAccents.success.onContainer
                } else {
                    MaterialTheme.appAccents.urgent.onContainer
                },
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            if (saveFailed) {
                Button(onClick = onRetrySave, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.english_retry_save))
                }
            } else {
                Button(
                    onClick = onNext,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.english_next))
                }
            }
        }
    }
}

@Composable
private fun feedbackText(feedback: EnglishAnswerFeedback, expected: String): String = when (feedback) {
    EnglishAnswerFeedback.CORRECT -> stringResource(R.string.english_feedback_correct)
    EnglishAnswerFeedback.MINOR_TYPO -> stringResource(R.string.english_feedback_typo)
    EnglishAnswerFeedback.INCORRECT -> stringResource(R.string.english_feedback_incorrect, expected)
    EnglishAnswerFeedback.SPEECH_CORRECT -> stringResource(R.string.english_feedback_speech_correct)
    EnglishAnswerFeedback.SPEECH_INCORRECT -> stringResource(R.string.english_feedback_speech_incorrect, expected)
    EnglishAnswerFeedback.SELF_REPORTED_CORRECT -> stringResource(R.string.english_feedback_self_correct)
    EnglishAnswerFeedback.TRY_AGAIN -> stringResource(R.string.english_feedback_try_again)
    EnglishAnswerFeedback.CARD_SAVED -> stringResource(R.string.english_feedback_saved)
}

@Composable
private fun frequencyLevelLabel(level: String): String = stringResource(
    when (level) {
        "BASE" -> R.string.english_level_base
        "COMMON" -> R.string.english_level_common
        "CONFIDENT" -> R.string.english_level_confident
        "ADVANCED" -> R.string.english_level_advanced
        else -> R.string.english_level_rare
    }
)
