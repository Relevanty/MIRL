package com.personal.sleepalarm.ui.english

import com.personal.sleepalarm.ui.theme.appAccents

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.dao.EnglishProgressSummaryProjection
import com.personal.sleepalarm.data.db.dao.EnglishStudySetSummaryProjection
import com.personal.sleepalarm.data.db.entity.EnglishStudyCardEntity
import com.personal.sleepalarm.data.db.entity.EnglishStudySetEntity
import com.personal.sleepalarm.data.db.entity.EnglishWordEntity
import com.personal.sleepalarm.data.english.EnglishVocabularyRepository
import com.personal.sleepalarm.domain.english.EnglishDictionaryArticle
import com.personal.sleepalarm.domain.english.EnglishReviewGrade
import com.personal.sleepalarm.domain.english.EnglishStudyCardDraft
import com.personal.sleepalarm.domain.english.EnglishStudyDirection
import com.personal.sleepalarm.domain.english.EnglishStudySetDraft
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

@Composable
fun EnglishVocabularyRoute(
    repository: EnglishVocabularyRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vocabularyViewModel: EnglishVocabularyViewModel = viewModel(
        factory = EnglishVocabularyViewModel.Factory(repository)
    )
    val state by vocabularyViewModel.uiState.collectAsStateWithLifecycle()
    val sets by vocabularyViewModel.studySets.collectAsStateWithLifecycle()
    val summaries by vocabularyViewModel.dictionarySummary.collectAsStateWithLifecycle()
    var showSkills by rememberSaveable { mutableStateOf(false) }

    if (showSkills) {
        EnglishLearningRoute(
            repository = repository,
            onBack = { showSkills = false },
            showCardsMode = false,
            modifier = modifier
        )
    } else {
        EnglishVocabularyScreen(
            state = state,
            sets = sets,
            summaries = summaries,
            onBack = onBack,
            onSelectTab = vocabularyViewModel::selectTab,
            onDirection = vocabularyViewModel::setDirection,
            onRevealMode = vocabularyViewModel::setRevealMode,
            onSearch = vocabularyViewModel::updateSearchQuery,
            onOpenArticle = vocabularyViewModel::openArticle,
            onCloseArticle = vocabularyViewModel::closeArticle,
            onOpenSet = vocabularyViewModel::openSet,
            onCloseSet = vocabularyViewModel::closeSet,
            onCreateSet = vocabularyViewModel::createSet,
            onCreateSetWithWord = vocabularyViewModel::createSetWithDictionaryWord,
            onUpdateSet = vocabularyViewModel::updateSet,
            onDeleteSet = vocabularyViewModel::deleteSet,
            onSaveCard = vocabularyViewModel::saveCard,
            onDeleteCard = vocabularyViewModel::deleteCard,
            onAddArticleToSet = vocabularyViewModel::addArticleToSet,
            onStartDictionary = vocabularyViewModel::startDictionarySession,
            onStartSet = vocabularyViewModel::startSetSession,
            onRevealCard = vocabularyViewModel::revealCard,
            onGrade = vocabularyViewModel::gradeCurrentCard,
            onLeaveSession = vocabularyViewModel::leaveSession,
            onConsumeMessage = vocabularyViewModel::consumeTransientMessage,
            onOpenSkills = { showSkills = true },
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnglishVocabularyScreen(
    state: EnglishVocabularyUiState,
    sets: List<EnglishStudySetSummaryProjection>,
    summaries: EnglishDirectionalSummaries,
    onBack: () -> Unit,
    onSelectTab: (EnglishHubTab) -> Unit,
    onDirection: (EnglishStudyDirection) -> Unit,
    onRevealMode: (EnglishCardRevealMode) -> Unit,
    onSearch: (String) -> Unit,
    onOpenArticle: (Int) -> Unit,
    onCloseArticle: () -> Unit,
    onOpenSet: (Long) -> Unit,
    onCloseSet: () -> Unit,
    onCreateSet: (EnglishStudySetDraft) -> Unit,
    onCreateSetWithWord: (EnglishStudySetDraft, Int) -> Unit,
    onUpdateSet: (Long, EnglishStudySetDraft) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onSaveCard: (Long, Long?, EnglishStudyCardDraft) -> Unit,
    onDeleteCard: (Long) -> Unit,
    onAddArticleToSet: (Long) -> Unit,
    onStartDictionary: () -> Unit,
    onStartSet: (Long, String) -> Unit,
    onRevealCard: () -> Unit,
    onGrade: (EnglishReviewGrade) -> Unit,
    onLeaveSession: () -> Unit,
    onConsumeMessage: () -> Unit,
    onOpenSkills: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tts = remember(context) { OfflineEnglishTextToSpeech(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    var setEditor by remember { mutableStateOf<EnglishStudySetEntity?>(null) }
    var creatingSet by remember { mutableStateOf(false) }
    var cardEditor by remember { mutableStateOf<EnglishStudyCardEntity?>(null) }
    var creatingCard by remember { mutableStateOf(false) }
    var deleteSet by remember { mutableStateOf<EnglishStudySetEntity?>(null) }
    var deleteCard by remember { mutableStateOf<EnglishStudyCardEntity?>(null) }
    var showSetPicker by remember { mutableStateOf(false) }
    var pendingDictionaryWordId by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(tts) {
        onDispose { tts.close() }
    }

    val message = when {
        state.error != null -> englishVocabularyErrorText(state.error)
        state.notice != null -> englishVocabularyNoticeText(state.notice)
        else -> null
    }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onConsumeMessage()
        }
    }

    val handleBack: () -> Unit = {
        when {
            state.article != null || state.isArticleLoading -> onCloseArticle()
            state.destination is EnglishVocabularyDestination.Study -> onLeaveSession()
            state.destination is EnglishVocabularyDestination.SetDetails -> onCloseSet()
            else -> onBack()
        }
    }
    BackHandler(onBack = handleBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EnglishVocabularyTopBar(
                state = state,
                onBack = handleBack
            )
        },
        bottomBar = {
            if (state.destination is EnglishVocabularyDestination.Home) {
                EnglishHubNavigation(state.selectedTab, onSelectTab)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedContent(
                targetState = state.destination,
                label = "english-destination",
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            ) { destination ->
                when (destination) {
                    EnglishVocabularyDestination.Home -> when (state.selectedTab) {
                        EnglishHubTab.LEARN -> EnglishLearnHome(
                            state = state,
                            sets = sets,
                            summary = summaries.forDirection(state.direction),
                            onDirection = onDirection,
                            onRevealMode = onRevealMode,
                            onStartDictionary = onStartDictionary,
                            onOpenSet = onOpenSet,
                            onOpenSkills = onOpenSkills
                        )
                        EnglishHubTab.DICTIONARY -> EnglishDictionaryBrowser(
                            query = state.searchQuery,
                            results = state.searchResults,
                            loading = state.isSearching,
                            onQuery = onSearch,
                            onWord = onOpenArticle
                        )
                        EnglishHubTab.SETS -> EnglishSetsHome(
                            sets = sets,
                            onCreate = { creatingSet = true },
                            onOpen = onOpenSet
                        )
                    }
                    is EnglishVocabularyDestination.SetDetails -> EnglishSetDetails(
                        studySet = state.selectedSet,
                        cards = state.selectedSetCards,
                        direction = state.direction,
                        revealMode = state.revealMode,
                        onDirection = onDirection,
                        onRevealMode = onRevealMode,
                        onStart = { state.selectedSet?.let { onStartSet(it.id, it.title) } },
                        onEditSet = { state.selectedSet?.let { setEditor = it } },
                        onDeleteSet = { state.selectedSet?.let { deleteSet = it } },
                        onCreateCard = { creatingCard = true },
                        onEditCard = { cardEditor = it },
                        onDeleteCard = { deleteCard = it }
                    )
                    EnglishVocabularyDestination.Study -> EnglishSwipeStudySession(
                        state = state,
                        onReveal = onRevealCard,
                        onGrade = onGrade,
                        onSpeak = { state.currentCard?.english?.let(tts::speak) },
                        onFinish = onLeaveSession
                    )
                }
            }
        }
    }

    val openedArticle = state.article
    if (openedArticle != null) {
        EnglishDictionaryArticleSheet(
            article = openedArticle,
            onDismiss = onCloseArticle,
            onSpeak = { tts.speak(openedArticle.headword) },
            onAddToSet = { showSetPicker = true }
        )
    }
    if (state.isArticleLoading && state.article == null) {
        EnglishLoadingDialog(onDismiss = onCloseArticle)
    }
    if (showSetPicker && state.article != null) {
        EnglishSetPickerDialog(
            sets = sets,
            onDismiss = { showSetPicker = false },
            onSelect = {
                onAddArticleToSet(it)
                showSetPicker = false
            },
            onCreate = {
                showSetPicker = false
                pendingDictionaryWordId = openedArticle.wordId
                onCloseArticle()
                creatingSet = true
            }
        )
    }
    if (creatingSet || setEditor != null) {
        EnglishSetEditorSheet(
            existing = setEditor,
            onDismiss = {
                creatingSet = false
                setEditor = null
                pendingDictionaryWordId = null
            },
            onSave = { draft ->
                val existing = setEditor
                val pendingWordId = pendingDictionaryWordId
                when {
                    existing != null -> onUpdateSet(existing.id, draft)
                    pendingWordId != null -> onCreateSetWithWord(draft, pendingWordId)
                    else -> onCreateSet(draft)
                }
                creatingSet = false
                setEditor = null
                pendingDictionaryWordId = null
            }
        )
    }
    val selectedSetId = state.selectedSet?.id
    if ((creatingCard || cardEditor != null) && selectedSetId != null) {
        EnglishCardEditorSheet(
            existing = cardEditor,
            onDismiss = {
                creatingCard = false
                cardEditor = null
            },
            onSave = { draft ->
                onSaveCard(selectedSetId, cardEditor?.id, draft)
                creatingCard = false
                cardEditor = null
            }
        )
    }
    deleteSet?.let { target ->
        EnglishDeleteDialog(
            title = stringResource(R.string.english_delete_set_title),
            body = stringResource(R.string.english_delete_set_body, target.title),
            onDismiss = { deleteSet = null },
            onConfirm = {
                onDeleteSet(target.id)
                deleteSet = null
            }
        )
    }
    deleteCard?.let { target ->
        EnglishDeleteDialog(
            title = stringResource(R.string.english_delete_card_title),
            body = stringResource(R.string.english_delete_card_body, target.term),
            onDismiss = { deleteCard = null },
            onConfirm = {
                onDeleteCard(target.id)
                deleteCard = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnglishVocabularyTopBar(
    state: EnglishVocabularyUiState,
    onBack: () -> Unit
) {
    val title = when (state.destination) {
        EnglishVocabularyDestination.Home -> stringResource(R.string.english_vocabulary_title)
        is EnglishVocabularyDestination.SetDetails -> state.selectedSet?.title
            ?: stringResource(R.string.english_sets)
        EnglishVocabularyDestination.Study -> when (val source = state.sessionSource) {
            EnglishSessionSource.Dictionary -> stringResource(R.string.english_daily_cards)
            is EnglishSessionSource.StudySet -> source.title
            null -> stringResource(R.string.english_cards_title)
        }
    }
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.destination is EnglishVocabularyDestination.Home) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.OfflineBolt,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.appAccents.study.color
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.english_offline_short),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.english_back)
                )
            }
        },
        actions = {
            if (state.destination is EnglishVocabularyDestination.Study) {
                Text(
                    text = "${state.reviewedInSession}/${state.sessionGoal}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.appAccents.study.color,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }
    )
}

@Composable
private fun EnglishHubNavigation(
    selected: EnglishHubTab,
    onSelect: (EnglishHubTab) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == EnglishHubTab.LEARN,
            onClick = { onSelect(EnglishHubTab.LEARN) },
            icon = { Icon(Icons.Default.School, contentDescription = null) },
            label = { Text(stringResource(R.string.english_learn)) }
        )
        NavigationBarItem(
            selected = selected == EnglishHubTab.DICTIONARY,
            onClick = { onSelect(EnglishHubTab.DICTIONARY) },
            icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
            label = { Text(stringResource(R.string.english_dictionary)) }
        )
        NavigationBarItem(
            selected = selected == EnglishHubTab.SETS,
            onClick = { onSelect(EnglishHubTab.SETS) },
            icon = { Icon(Icons.Default.Style, contentDescription = null) },
            label = { Text(stringResource(R.string.english_sets)) }
        )
    }
}

@Composable
private fun EnglishLearnHome(
    state: EnglishVocabularyUiState,
    sets: List<EnglishStudySetSummaryProjection>,
    summary: EnglishProgressSummaryProjection,
    onDirection: (EnglishStudyDirection) -> Unit,
    onRevealMode: (EnglishCardRevealMode) -> Unit,
    onStartDictionary: () -> Unit,
    onOpenSet: (Long) -> Unit,
    onOpenSkills: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().englishContentWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            EnglishDailyCard(
                summary = summary,
                loading = state.isLoading,
                onStart = onStartDictionary
            )
        }
        item {
            EnglishStudyPreferences(
                direction = state.direction,
                revealMode = state.revealMode,
                onDirection = onDirection,
                onRevealMode = onRevealMode
            )
        }
        item {
            EnglishSectionHeading(
                title = stringResource(R.string.english_your_sets),
                subtitle = stringResource(R.string.english_your_sets_hint)
            )
        }
        if (sets.isEmpty()) {
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Style, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.english_sets_empty_short),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(sets.take(3), key = { it.studySet.id }) { item ->
                EnglishSetRow(item, onClick = { onOpenSet(item.studySet.id) })
            }
        }
        item {
            Card(
                onClick = onOpenSkills,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.appAccents.calm.container,
                    contentColor = MaterialTheme.appAccents.calm.onContainer
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.english_skill_training),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.english_skill_training_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun EnglishDailyCard(
    summary: EnglishProgressSummaryProjection,
    loading: Boolean,
    onStart: () -> Unit
) {
    val total = summary.totalWords.coerceAtLeast(10_000)
    val progress = if (total == 0) 0f else summary.masteredWords.toFloat() / total
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.appAccents.study.container,
            contentColor = MaterialTheme.appAccents.study.onContainer
        ),
        shape = RoundedCornerShape(30.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.appAccents.study.color.copy(alpha = 0.14f)
                ) {
                    Icon(
                        Icons.Default.AutoStories,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(30.dp),
                        tint = MaterialTheme.appAccents.study.color
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.english_daily_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.english_daily_subtitle),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = MaterialTheme.appAccents.study.color,
                trackColor = MaterialTheme.appAccents.study.onContainer.copy(alpha = 0.13f)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(
                    R.string.english_dictionary_progress_long,
                    summary.masteredWords,
                    summary.startedWords,
                    total
                ),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onStart,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.english_start_ten_cards))
            }
        }
    }
}

@Composable
private fun EnglishStudyPreferences(
    direction: EnglishStudyDirection,
    revealMode: EnglishCardRevealMode,
    onDirection: (EnglishStudyDirection) -> Unit,
    onRevealMode: (EnglishCardRevealMode) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.appAccents.study.color)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.english_card_setup),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.english_front_side),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DirectionChip(EnglishStudyDirection.EN_TO_RU, direction, onDirection)
                DirectionChip(EnglishStudyDirection.RU_TO_EN, direction, onDirection)
                DirectionChip(EnglishStudyDirection.MIXED, direction, onDirection)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.english_after_tap),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RevealChip(EnglishCardRevealMode.TRANSLATION, revealMode, onRevealMode)
                RevealChip(EnglishCardRevealMode.DESCRIPTION, revealMode, onRevealMode)
                RevealChip(EnglishCardRevealMode.BOTH, revealMode, onRevealMode)
            }
        }
    }
}

@Composable
private fun DirectionChip(
    value: EnglishStudyDirection,
    selected: EnglishStudyDirection,
    onSelect: (EnglishStudyDirection) -> Unit
) {
    val label = when (value) {
        EnglishStudyDirection.EN_TO_RU -> stringResource(R.string.english_direction_en_ru)
        EnglishStudyDirection.RU_TO_EN -> stringResource(R.string.english_direction_ru_en)
        EnglishStudyDirection.MIXED -> stringResource(R.string.english_direction_mixed)
    }
    FilterChip(
        selected = value == selected,
        onClick = { onSelect(value) },
        label = { Text(label) },
        leadingIcon = if (value == selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else null
    )
}

@Composable
private fun RevealChip(
    value: EnglishCardRevealMode,
    selected: EnglishCardRevealMode,
    onSelect: (EnglishCardRevealMode) -> Unit
) {
    val label = when (value) {
        EnglishCardRevealMode.TRANSLATION -> stringResource(R.string.english_reveal_translation)
        EnglishCardRevealMode.DESCRIPTION -> stringResource(R.string.english_reveal_description)
        EnglishCardRevealMode.BOTH -> stringResource(R.string.english_reveal_both)
    }
    FilterChip(
        selected = value == selected,
        onClick = { onSelect(value) },
        label = { Text(label) },
        leadingIcon = if (value == selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else null
    )
}

@Composable
private fun EnglishSectionHeading(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.semantics { heading() }) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EnglishDictionaryBrowser(
    query: String,
    results: List<EnglishWordEntity>,
    loading: Boolean,
    onQuery: (String) -> Unit,
    onWord: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().englishContentWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            EnglishSectionHeading(
                title = stringResource(R.string.english_dictionary_10000),
                subtitle = stringResource(R.string.english_dictionary_search_hint)
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                label = { Text(stringResource(R.string.english_search_word)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isNotBlank()) {
                    {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(R.string.english_clear_search)
                            )
                        }
                    }
                } else null
            )
        }
        if (loading) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        if (!loading && results.isEmpty()) {
            item {
                EnglishEmptyPanel(
                    icon = Icons.Default.Search,
                    title = if (query.isBlank()) {
                        stringResource(R.string.english_dictionary_loading)
                    } else {
                        stringResource(R.string.english_nothing_found)
                    },
                    body = if (query.isBlank()) null else stringResource(R.string.english_try_other_query)
                )
            }
        }
        items(results, key = { it.id }) { word ->
            EnglishDictionaryWordRow(word = word, onClick = { onWord(word.id) })
        }
        item {
            Text(
                stringResource(R.string.english_dictionary_source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
        }
    }
}

@Composable
private fun EnglishDictionaryWordRow(
    word: EnglishWordEntity,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.appAccents.calm.container,
                contentColor = MaterialTheme.appAccents.calm.onContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        word.word.firstOrNull()?.uppercase().orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        word.word,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (word.pronunciation.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            word.pronunciation,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    word.translation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    englishWordMeta(word.partOfSpeech, word.level),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appAccents.study.color
                )
            }
            Icon(Icons.Default.MoreHoriz, contentDescription = null)
        }
    }
}

@Composable
private fun EnglishSetsHome(
    sets: List<EnglishStudySetSummaryProjection>,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().englishContentWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EnglishSectionHeading(
                    title = stringResource(R.string.english_your_sets),
                    subtitle = stringResource(R.string.english_sets_long_hint)
                )
                Spacer(Modifier.weight(1f))
                FilledIconButton(onClick = onCreate) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.english_create_set))
                }
            }
        }
        item {
            Button(
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.english_create_own_set))
            }
        }
        if (sets.isEmpty()) {
            item {
                EnglishEmptyPanel(
                    icon = Icons.Default.Style,
                    title = stringResource(R.string.english_no_sets),
                    body = stringResource(R.string.english_no_sets_hint)
                )
            }
        } else {
            items(sets, key = { it.studySet.id }) { set ->
                EnglishSetRow(set, onClick = { onOpen(set.studySet.id) })
            }
        }
    }
}

@Composable
private fun EnglishSetRow(
    item: EnglishStudySetSummaryProjection,
    onClick: () -> Unit
) {
    val colors = englishSetCardColors(item.studySet.colorSeed)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = colors,
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.52f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.studySet.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.studySet.description.isNotBlank()) {
                    Text(
                        item.studySet.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        R.string.english_set_progress,
                        item.cardCount,
                        item.masteredDirectionCount
                    ),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Icon(Icons.Default.PlayArrow, contentDescription = null)
        }
    }
}

@Composable
private fun EnglishSetDetails(
    studySet: EnglishStudySetEntity?,
    cards: List<EnglishStudyCardEntity>,
    direction: EnglishStudyDirection,
    revealMode: EnglishCardRevealMode,
    onDirection: (EnglishStudyDirection) -> Unit,
    onRevealMode: (EnglishCardRevealMode) -> Unit,
    onStart: () -> Unit,
    onEditSet: () -> Unit,
    onDeleteSet: () -> Unit,
    onCreateCard: () -> Unit,
    onEditCard: (EnglishStudyCardEntity) -> Unit,
    onDeleteCard: (EnglishStudyCardEntity) -> Unit
) {
    if (studySet == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().englishContentWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = englishSetCardColors(studySet.colorSeed),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                studySet.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (studySet.description.isNotBlank()) {
                                Spacer(Modifier.height(5.dp))
                                Text(studySet.description, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        IconButton(onClick = onEditSet) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.english_edit_set))
                        }
                        IconButton(onClick = onDeleteSet) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.english_delete_set))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.english_cards_count, cards.size),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onStart,
                        enabled = cards.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.english_learn_set))
                    }
                }
            }
        }
        item {
            EnglishStudyPreferences(direction, revealMode, onDirection, onRevealMode)
        }
        item {
            FilledTonalButton(
                onClick = onCreateCard,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.english_add_card))
            }
        }
        if (cards.isEmpty()) {
            item {
                EnglishEmptyPanel(
                    icon = Icons.Default.Add,
                    title = stringResource(R.string.english_set_empty),
                    body = stringResource(R.string.english_set_empty_hint)
                )
            }
        } else {
            items(cards, key = { it.id }) { card ->
                EnglishEditableCardRow(
                    card = card,
                    onEdit = { onEditCard(card) },
                    onDelete = { onDeleteCard(card) }
                )
            }
        }
    }
}

@Composable
private fun EnglishEditableCardRow(
    card: EnglishStudyCardEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(card.term, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    card.translation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (card.definition.isNotBlank()) {
                    Text(
                        card.definition,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.english_edit_card))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.english_delete_card))
            }
        }
    }
}

@Composable
private fun EnglishEmptyPanel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String?
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.appAccents.study.color)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (!body.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EnglishSwipeStudySession(
    state: EnglishVocabularyUiState,
    onReveal: () -> Unit,
    onGrade: (EnglishReviewGrade) -> Unit,
    onSpeak: () -> Unit,
    onFinish: () -> Unit
) {
    when {
        state.sessionComplete -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(30.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.appAccents.study.container,
                        contentColor = MaterialTheme.appAccents.study.onContainer
                    ),
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.english_round_complete),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(
                                R.string.english_round_score,
                                state.correctInSession,
                                state.reviewedInSession
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.english_done))
                        }
                    }
                }
            }
        }
        state.isLoading || state.currentCard == null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.english_next_card_loading))
                }
            }
        }
        else -> {
            val card = requireNotNull(state.currentCard)
            var locallyCommitting by remember(card.stableKey) { mutableStateOf(false) }
            LaunchedEffect(state.isSavingReview, state.error) {
                if (!state.isSavingReview && state.error == EnglishVocabularyError.SAVE_REVIEW) {
                    locallyCommitting = false
                }
            }
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                if (card.direction == EnglishStudyDirection.EN_TO_RU) {
                                    stringResource(R.string.english_direction_en_ru)
                                } else {
                                    stringResource(R.string.english_direction_ru_en)
                                }
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(
                            R.string.english_session_score_format,
                            state.correctInSession,
                            state.reviewedInSession
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                key(card.stableKey) {
                    EnglishSwipeCard(
                        card = card,
                        revealMode = state.revealMode,
                        revealed = state.isCardRevealed,
                        saving = state.isSavingReview,
                        saveFailed = state.error == EnglishVocabularyError.SAVE_REVIEW,
                        onReveal = onReveal,
                        onGrade = onGrade,
                        onCommitStart = { locallyCommitting = true },
                        onSpeak = onSpeak
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (!state.isCardRevealed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.TouchApp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            stringResource(R.string.english_tap_to_reveal),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    EnglishGradeControls(
                        enabled = !state.isSavingReview && !locallyCommitting,
                        onGrade = {
                            locallyCommitting = true
                            onGrade(it)
                        }
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun EnglishSwipeCard(
    card: EnglishStudyCardUi,
    revealMode: EnglishCardRevealMode,
    revealed: Boolean,
    saving: Boolean,
    saveFailed: Boolean,
    onReveal: () -> Unit,
    onGrade: (EnglishReviewGrade) -> Unit,
    onCommitStart: () -> Unit,
    onSpeak: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var dragOffset by remember(card.stableKey) { mutableFloatStateOf(0f) }
    var thresholdReached by remember(card.stableKey) { mutableStateOf(false) }
    var committed by remember(card.stableKey) { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidthPx = with(density) { maxWidth.toPx() }
        val threshold = cardWidthPx * 0.27f
        val dragProgress = (abs(dragOffset) / threshold).coerceIn(0f, 1f)

        fun settle(target: Float, grade: EnglishReviewGrade?) {
            if (committed || saving) return
            scope.launch {
                if (grade != null) {
                    committed = true
                    onCommitStart()
                }
                animate(
                    initialValue = dragOffset,
                    targetValue = target,
                    animationSpec = if (grade == null) spring(dampingRatio = 0.7f) else tween(210)
                ) { value, _ -> dragOffset = value }
                if (grade != null) onGrade(grade)
            }
        }

        LaunchedEffect(saveFailed, saving) {
            if (saveFailed && !saving && committed) {
                committed = false
                animate(
                    initialValue = dragOffset,
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.72f)
                ) { value, _ -> dragOffset = value }
            }
        }

        if (dragOffset < 0f) {
            SwipeDecisionBackground(
                known = false,
                alpha = dragProgress,
                modifier = Modifier.matchParentSize()
            )
        } else if (dragOffset > 0f) {
            SwipeDecisionBackground(
                known = true,
                alpha = dragProgress,
                modifier = Modifier.matchParentSize()
            )
        }

        val revealActionLabel = stringResource(R.string.english_show_card_answer)
        val unknownActionLabel = stringResource(R.string.english_mark_unknown)
        val knownActionLabel = stringResource(R.string.english_mark_known)
        val cardState = if (revealed) {
            stringResource(R.string.english_card_back_visible)
        } else {
            stringResource(R.string.english_card_front_visible)
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp)
                .graphicsLayer {
                    translationX = dragOffset
                    rotationZ = (dragOffset / cardWidthPx.coerceAtLeast(1f)) * 7f
                }
                .semantics {
                    role = Role.Button
                    stateDescription = cardState
                    customActions = buildList {
                        if (!revealed) {
                            add(CustomAccessibilityAction(revealActionLabel) {
                                onReveal()
                                true
                            })
                        } else {
                            add(CustomAccessibilityAction(unknownActionLabel) {
                                settle(-cardWidthPx * 1.25f, EnglishReviewGrade.AGAIN)
                                true
                            })
                            add(CustomAccessibilityAction(knownActionLabel) {
                                settle(cardWidthPx * 1.25f, EnglishReviewGrade.GOOD)
                                true
                            })
                        }
                    }
                }
                .pointerInput(card.stableKey, revealed, saving) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val grade = englishSwipeGrade(
                                offsetPx = dragOffset,
                                thresholdPx = threshold,
                                revealed = revealed
                            )
                            if (grade == null) {
                                settle(0f, null)
                            } else {
                                val destination = sign(dragOffset).let { direction ->
                                    if (direction == 0f) 1f else direction
                                } * cardWidthPx * 1.25f
                                settle(destination, grade)
                            }
                            thresholdReached = false
                        },
                        onDragCancel = {
                            settle(0f, null)
                            thresholdReached = false
                        }
                    ) { change, amount ->
                        if (!saving && !committed) {
                            change.consume()
                            dragOffset = (dragOffset + amount).coerceIn(-cardWidthPx, cardWidthPx)
                            val nowReached = revealed && abs(dragOffset) >= threshold
                            if (nowReached && !thresholdReached) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            thresholdReached = nowReached
                        }
                    }
                }
                .clickable(enabled = !saving && !committed) {
                    if (!revealed) onReveal()
                },
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.appAccents.study.color.copy(alpha = 0.12f)
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(24.dp),
                        tint = MaterialTheme.appAccents.study.color
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    card.prompt,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (card.direction == EnglishStudyDirection.EN_TO_RU && card.pronunciation.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        card.pronunciation,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (card.partOfSpeech.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        englishWordMeta(card.partOfSpeech, card.level),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.appAccents.study.color
                    )
                }
                Spacer(Modifier.height(16.dp))
                OutlinedIconButton(onClick = onSpeak, enabled = !saving) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.english_speak_word))
                }
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(18.dp))
                AnimatedContent(targetState = revealed, label = "card-reveal") { answerVisible ->
                    if (answerVisible) {
                        EnglishCardBack(card, revealMode)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.english_touch_card),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                stringResource(R.string.english_touch_card_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeDecisionBackground(
    known: Boolean,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    val container = if (known) {
        MaterialTheme.appAccents.success.container
    } else {
        MaterialTheme.appAccents.urgent.container
    }
    val content = if (known) {
        MaterialTheme.appAccents.success.onContainer
    } else {
        MaterialTheme.appAccents.urgent.onContainer
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(30.dp))
            .background(container.copy(alpha = (0.35f + alpha * 0.65f).coerceIn(0f, 1f)))
            .padding(horizontal = 28.dp),
        contentAlignment = if (known) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (known) Icons.Default.CheckCircle else Icons.Default.Clear,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(42.dp)
            )
            Text(
                if (known) stringResource(R.string.english_know) else stringResource(R.string.english_do_not_know),
                color = content,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EnglishCardBack(
    card: EnglishStudyCardUi,
    revealMode: EnglishCardRevealMode
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (revealMode != EnglishCardRevealMode.DESCRIPTION) {
            Text(
                stringResource(R.string.english_answer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.appAccents.study.color
            )
            Text(
                card.answers.joinToString(" · "),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        if (revealMode == EnglishCardRevealMode.BOTH && card.definition.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(14.dp))
        }
        if (revealMode != EnglishCardRevealMode.TRANSLATION) {
            if (card.definition.isNotBlank()) {
                Text(
                    stringResource(R.string.english_definition),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appAccents.study.color
                )
                Text(card.definition, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            } else if (revealMode == EnglishCardRevealMode.DESCRIPTION) {
                Text(
                    stringResource(R.string.english_no_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (card.example.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    card.example,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center
                )
                if (card.exampleTranslation.isNotBlank()) {
                    Text(
                        card.exampleTranslation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (card.notes.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    card.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EnglishGradeControls(
    enabled: Boolean,
    onGrade: (EnglishReviewGrade) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { onGrade(EnglishReviewGrade.AGAIN) },
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                border = BorderStroke(1.dp, MaterialTheme.appAccents.urgent.color)
            ) {
                Icon(Icons.Default.Clear, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.english_do_not_know))
            }
            Button(
                onClick = { onGrade(EnglishReviewGrade.GOOD) },
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.english_know))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            AssistChip(
                onClick = { onGrade(EnglishReviewGrade.HARD) },
                enabled = enabled,
                label = { Text(stringResource(R.string.english_grade_hard)) }
            )
            Spacer(Modifier.width(10.dp))
            AssistChip(
                onClick = { onGrade(EnglishReviewGrade.EASY) },
                enabled = enabled,
                label = { Text(stringResource(R.string.english_grade_easy)) }
            )
        }
        Text(
            stringResource(R.string.english_swipe_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnglishDictionaryArticleSheet(
    article: EnglishDictionaryArticle,
    onDismiss: () -> Unit,
    onSpeak: () -> Unit,
    onAddToSet: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        article.headword,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (article.pronunciation.isNotBlank()) {
                        Text(
                            article.pronunciation,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        englishWordMeta(article.partOfSpeech, article.frequencyLevel),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.appAccents.study.color
                    )
                }
                FilledTonalButton(onClick = onSpeak) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.english_listen_short))
                }
            }
            Spacer(Modifier.height(18.dp))
            article.senses.forEachIndexed { index, sense ->
                if (index > 0) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(16.dp))
                }
                Text(
                    stringResource(R.string.english_meaning_number, index + 1),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.appAccents.study.color
                )
                if (sense.translations.isNotEmpty()) {
                    Text(
                        sense.translations.joinToString(" · "),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (sense.definition.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        sense.definition,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (sense.example.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(sense.example, fontStyle = FontStyle.Italic)
                            if (sense.exampleTranslation.isNotBlank()) {
                                Text(
                                    sense.exampleTranslation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAddToSet,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.english_add_to_set))
            }
        }
    }
}

@Composable
private fun EnglishSetPickerDialog(
    sets: List<EnglishStudySetSummaryProjection>,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onCreate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.english_choose_set)) },
        text = {
            if (sets.isEmpty()) {
                Text(stringResource(R.string.english_create_set_first))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sets, key = { it.studySet.id }) { item ->
                        Card(
                            onClick = { onSelect(item.studySet.id) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = englishSetCardColors(item.studySet.colorSeed),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(item.studySet.title, fontWeight = FontWeight.Bold)
                                Text(
                                    stringResource(R.string.english_cards_count, item.cardCount),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (sets.isEmpty()) {
                Button(onClick = onCreate) { Text(stringResource(R.string.english_create_set)) }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.english_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnglishSetEditorSheet(
    existing: EnglishStudySetEntity?,
    onDismiss: () -> Unit,
    onSave: (EnglishStudySetDraft) -> Unit
) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var colorSeed by remember(existing?.id) { mutableStateOf(existing?.colorSeed ?: 0) }
    var direction by remember(existing?.id) {
        mutableStateOf(EnglishStudyDirection.fromStorage(existing?.defaultDirection))
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                if (existing == null) stringResource(R.string.english_new_set)
                else stringResource(R.string.english_edit_set),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 80) title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.english_set_name)) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.english_set_description)) },
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.english_set_accent), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { seed ->
                    val colors = englishSetSeedColors(seed)
                    Surface(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .clickable { colorSeed = seed }
                            .semantics { role = Role.RadioButton },
                        shape = CircleShape,
                        color = colors.first,
                        border = if (colorSeed == seed) {
                            BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
                        } else null
                    ) {
                        if (colorSeed == seed) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = colors.second)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.english_default_direction), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DirectionChip(EnglishStudyDirection.EN_TO_RU, direction) { direction = it }
                DirectionChip(EnglishStudyDirection.RU_TO_EN, direction) { direction = it }
                DirectionChip(EnglishStudyDirection.MIXED, direction) { direction = it }
            }
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    onSave(
                        EnglishStudySetDraft(
                            title = title,
                            description = description,
                            colorSeed = colorSeed,
                            defaultDirection = direction
                        )
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text(stringResource(R.string.english_save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnglishCardEditorSheet(
    existing: EnglishStudyCardEntity?,
    onDismiss: () -> Unit,
    onSave: (EnglishStudyCardDraft) -> Unit
) {
    var term by remember(existing?.id) { mutableStateOf(existing?.term.orEmpty()) }
    var translation by remember(existing?.id) { mutableStateOf(existing?.translation.orEmpty()) }
    var definition by remember(existing?.id) { mutableStateOf(existing?.definition.orEmpty()) }
    var example by remember(existing?.id) { mutableStateOf(existing?.example.orEmpty()) }
    var exampleTranslation by remember(existing?.id) { mutableStateOf(existing?.exampleTranslation.orEmpty()) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp)
        ) {
            Text(
                if (existing == null) stringResource(R.string.english_new_card)
                else stringResource(R.string.english_edit_card),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(R.string.english_card_editor_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            EnglishEditorField(
                value = term,
                onValueChange = { if (it.length <= 120) term = it },
                label = stringResource(R.string.english_word_english),
                singleLine = true
            )
            EnglishEditorField(
                value = translation,
                onValueChange = { if (it.length <= 240) translation = it },
                label = stringResource(R.string.english_translation_russian),
                singleLine = false
            )
            EnglishEditorField(
                value = definition,
                onValueChange = { if (it.length <= 2_000) definition = it },
                label = stringResource(R.string.english_definition_optional),
                singleLine = false
            )
            EnglishEditorField(
                value = example,
                onValueChange = { if (it.length <= 1_000) example = it },
                label = stringResource(R.string.english_example_english),
                singleLine = false
            )
            EnglishEditorField(
                value = exampleTranslation,
                onValueChange = { if (it.length <= 1_000) exampleTranslation = it },
                label = stringResource(R.string.english_example_russian),
                singleLine = false
            )
            EnglishEditorField(
                value = notes,
                onValueChange = { if (it.length <= 4_000) notes = it },
                label = stringResource(R.string.english_notes_optional),
                singleLine = false
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    onSave(
                        EnglishStudyCardDraft(
                            term = term,
                            translation = translation,
                            definition = definition,
                            example = example,
                            exampleTranslation = exampleTranslation,
                            notes = notes,
                            dictionaryWordId = existing?.dictionaryWordId
                        )
                    )
                },
                enabled = term.isNotBlank() && translation.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text(stringResource(R.string.english_save_card))
            }
        }
    }
}

@Composable
private fun EnglishEditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        maxLines = if (singleLine) 1 else 5,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun EnglishDeleteDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.english_delete)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.english_cancel)) }
        }
    )
}

@Composable
private fun EnglishLoadingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.english_dictionary_article)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.english_article_loading))
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun englishSetCardColors(seed: Int): androidx.compose.material3.CardColors {
    val pair = englishSetSeedColors(seed)
    return CardDefaults.cardColors(containerColor = pair.first, contentColor = pair.second)
}

@Composable
private fun englishSetSeedColors(seed: Int) = when (Math.floorMod(seed, 4)) {
    0 -> MaterialTheme.appAccents.study.container to MaterialTheme.appAccents.study.onContainer
    1 -> MaterialTheme.appAccents.calm.container to MaterialTheme.appAccents.calm.onContainer
    2 -> MaterialTheme.appAccents.focus.container to MaterialTheme.appAccents.focus.onContainer
    else -> MaterialTheme.appAccents.other.container to MaterialTheme.appAccents.other.onContainer
}

private fun Modifier.englishContentWidth(): Modifier = this
    .widthIn(max = 760.dp)
    .fillMaxWidth()

private fun englishWordMeta(partOfSpeech: String, level: String): String = listOf(
    partOfSpeech.trim(),
    level.trim().lowercase().replaceFirstChar { it.titlecase() }
).filter { it.isNotBlank() }.joinToString(" · ")

internal fun englishSwipeGrade(
    offsetPx: Float,
    thresholdPx: Float,
    revealed: Boolean
): EnglishReviewGrade? {
    if (!revealed || thresholdPx <= 0f || abs(offsetPx) < thresholdPx) return null
    return if (offsetPx < 0f) EnglishReviewGrade.AGAIN else EnglishReviewGrade.GOOD
}

@Composable
private fun englishVocabularyErrorText(error: EnglishVocabularyError): String = stringResource(
    when (error) {
        EnglishVocabularyError.OPEN_DICTIONARY -> R.string.english_error_open_dictionary
        EnglishVocabularyError.SEARCH -> R.string.english_error_search
        EnglishVocabularyError.LOAD_ARTICLE -> R.string.english_error_article
        EnglishVocabularyError.LOAD_SESSION -> R.string.english_error_load_word
        EnglishVocabularyError.SAVE_REVIEW -> R.string.english_error_save_review
        EnglishVocabularyError.SAVE_SET -> R.string.english_error_save_set
        EnglishVocabularyError.DELETE_SET -> R.string.english_error_delete_set
        EnglishVocabularyError.SAVE_CARD -> R.string.english_error_save_card
        EnglishVocabularyError.DELETE_CARD -> R.string.english_error_delete_card
        EnglishVocabularyError.ADD_TO_SET -> R.string.english_error_add_to_set
    }
)

@Composable
private fun englishVocabularyNoticeText(notice: EnglishVocabularyNotice): String = stringResource(
    when (notice) {
        EnglishVocabularyNotice.SET_SAVED -> R.string.english_notice_set_saved
        EnglishVocabularyNotice.SET_DELETED -> R.string.english_notice_set_deleted
        EnglishVocabularyNotice.CARD_SAVED -> R.string.english_notice_card_saved
        EnglishVocabularyNotice.CARD_DELETED -> R.string.english_notice_card_deleted
        EnglishVocabularyNotice.ADDED_TO_SET -> R.string.english_notice_added_to_set
        EnglishVocabularyNotice.ALREADY_IN_SET -> R.string.english_notice_already_in_set
    }
)
