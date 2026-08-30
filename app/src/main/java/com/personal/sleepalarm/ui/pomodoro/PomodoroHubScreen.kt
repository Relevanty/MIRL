package com.personal.sleepalarm.ui.pomodoro

import com.personal.sleepalarm.ui.theme.appAccents

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.OtherActivityEntity
import com.personal.sleepalarm.data.db.entity.FocusProtocolSessionEntity
import com.personal.sleepalarm.data.db.entity.SubjectEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.domain.model.FocusProtocolPhase
import com.personal.sleepalarm.domain.model.focusActivityType
import com.personal.sleepalarm.domain.model.focusItemTaskId
import com.personal.sleepalarm.domain.model.taskFocusItemId
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.domain.model.nextFocusDurationMinutes
import com.personal.sleepalarm.domain.model.remainingWorkMinutesOrNull
import com.personal.sleepalarm.domain.calculator.DailyTaskFocusCalculator
import com.personal.sleepalarm.domain.calculator.DailyTaskFocusProgress
import com.personal.sleepalarm.domain.calculator.liveTaskFocusIntervals
import com.personal.sleepalarm.domain.focusaudio.FocusSoundCatalog
import com.personal.sleepalarm.domain.focusaudio.FocusSoundKind
import com.personal.sleepalarm.domain.focusaudio.FocusSoundSelection
import com.personal.sleepalarm.domain.focusaudio.FocusSoundscapeSelection
import com.personal.sleepalarm.service.audio.FocusSoundPlaybackStatus
import com.personal.sleepalarm.service.audio.soundscapeSelection
import com.personal.sleepalarm.ui.focusprotocol.EnergyPatternCard
import com.personal.sleepalarm.ui.focusprotocol.CompletedFocusBlocksCard
import com.personal.sleepalarm.ui.focusprotocol.FocusProtocolActiveScreen
import com.personal.sleepalarm.ui.focusprotocol.FocusProtocolSetupSheet
import com.personal.sleepalarm.ui.focusprotocol.FocusProtocolTarget
import com.personal.sleepalarm.ui.focusprotocol.FocusProtocolViewModel
import com.personal.sleepalarm.ui.focusprotocol.FocusSoundDraft
import com.personal.sleepalarm.ui.focusprotocol.formatCompactDuration
import com.personal.sleepalarm.ui.theme.ThemedModalBottomSheet
import com.personal.sleepalarm.ui.activity.ManualActivitySheet
import com.personal.sleepalarm.ui.components.DailyFocusProgressCard
import com.personal.sleepalarm.ui.focusaudio.FocusSoundLayer as FocusSoundUiLayer
import com.personal.sleepalarm.ui.focusaudio.FocusSoundscapePickerSheet
import com.personal.sleepalarm.ui.focusaudio.FocusSoundscapeUiState
import com.personal.sleepalarm.ui.focusaudio.rememberDefaultFocusSoundCategories
import com.personal.sleepalarm.ui.focusaudio.toUiItems
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class HubActivityItem(
    val id: Int,
    val name: String,
    /** Persisted compatibility token; resolve through pomodoroColorForToken before drawing. */
    val color: Int,
    /** Canonical task id. Null means a standalone focus category item. */
    val taskId: Int? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(
    modifier: Modifier = Modifier,
    viewModel: PomodoroViewModel = viewModel(),
    onOpenTask: (Int) -> Unit = {},
    onCreateTask: (FocusActivityType) -> Unit = {}
) {
    val protocolViewModel: FocusProtocolViewModel = viewModel()
    val activeProtocol by protocolViewModel.activeSession.collectAsStateWithLifecycle()
    val latestProtocol by protocolViewModel.latestSession.collectAsStateWithLifecycle()
    val protocolRemaining by protocolViewModel.remainingMillis.collectAsStateWithLifecycle()
    val energyPattern by protocolViewModel.energyPattern.collectAsStateWithLifecycle()
    val recentCompletedBlocks by protocolViewModel.recentCompletedBlocks.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val workTasks by viewModel.workTasks.collectAsStateWithLifecycle()
    val otherActivities by viewModel.otherActivities.collectAsStateWithLifecycle()
    val activityRecords by viewModel.activityRecords.collectAsStateWithLifecycle()
    val progressNowMillis by viewModel.progressNowMillis.collectAsStateWithLifecycle()
    val currentDaySessions by viewModel.currentDayFocusSessions.collectAsStateWithLifecycle()
    val currentDayRange by viewModel.currentDayRange.collectAsStateWithLifecycle()
    val activityType by viewModel.activityType.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedItemId.collectAsStateWithLifecycle()
    val fallbackFocusDuration by viewModel.focusDuration.collectAsStateWithLifecycle()
    val fallbackBreakDuration by viewModel.breakDuration.collectAsStateWithLifecycle()
    val focusSoundSettings by protocolViewModel.focusSoundSettings.collectAsStateWithLifecycle()
    val focusSoundPlayback by protocolViewModel.soundscapePlayback.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var focusSoundDraft by remember { mutableStateOf(FocusSoundDraft()) }
    var showFocusSoundPicker by remember { mutableStateOf(false) }
    var focusSoundCategory by remember { mutableStateOf(FocusSoundscapeUiState.CATEGORY_ALL) }
    var focusSoundEditingLayer by remember { mutableStateOf(FocusSoundUiLayer.PRIMARY) }
    var focusSoundMixEnabled by remember { mutableStateOf(false) }
    var focusSoundTaskId by remember { mutableStateOf<Int?>(null) }
    var focusSoundDraftLoading by remember { mutableStateOf(false) }
    var loadedSoundSessionId by remember { mutableStateOf<Int?>(null) }
    var customSoundNotice by remember { mutableStateOf<String?>(null) }
    var customSoundImporting by remember { mutableStateOf(false) }
    val languageTag = Locale.getDefault().toLanguageTag()
    val focusSoundCategories = rememberDefaultFocusSoundCategories()
    val focusSoundItems = remember(
        focusSoundSettings,
        focusSoundDraft.selection,
        languageTag,
        focusSoundEditingLayer
    ) {
        focusSoundSettings.toUiItems(focusSoundDraft.selection, languageTag).map { item ->
            if (focusSoundEditingLayer == FocusSoundUiLayer.SECONDARY) {
                item.copy(isAvailable = item.categoryId == "noise")
            } else {
                item
            }
        }
    }
    val focusSoundUiState = FocusSoundscapeUiState(
        items = focusSoundItems,
        categories = focusSoundCategories,
        selectedCategoryId = focusSoundCategory,
        recentItemIds = focusSoundSettings.recentSelections.map { it.historyKey() },
        primaryItemId = focusSoundDraft.selection.primary.historyKey(),
        primaryVolume = focusSoundDraft.primaryVolumePercent / 100f,
        mixEnabled = focusSoundMixEnabled,
        secondaryItemId = focusSoundDraft.selection.secondaryLayerId,
        secondaryVolume = focusSoundDraft.selection.secondaryVolumePercent / 100f,
        editingLayer = focusSoundEditingLayer,
        canMix = focusSoundDraft.selection.primary.entry().kind in setOf(
            FocusSoundKind.AMBIENCE,
            FocusSoundKind.MELODY,
            FocusSoundKind.CUSTOM_FILE
        ),
        isPlaying = focusSoundPlayback.status == FocusSoundPlaybackStatus.PLAYING ||
            focusSoundPlayback.status == FocusSoundPlaybackStatus.LOADING,
        isImportingCustomSounds = customSoundImporting,
        noticeMessage = customSoundNotice,
        playbackErrorMessage = focusSoundPlayback.errorMessage
    )

    val applyFocusSoundDraft: (FocusSoundDraft, Boolean) -> Unit = { next, preview ->
        val safe = next.copy(selection = next.selection.normalized())
        focusSoundDraft = safe
        activeProtocol?.let { session ->
            protocolViewModel.updateActiveSoundscape(
                sessionId = session.id,
                selection = safe.selection,
                primaryVolumePercent = safe.primaryVolumePercent,
                taskId = activeTaskId(session),
                rememberForTask = safe.rememberForTask,
                previewIfInactive = preview
            )
        } ?: if (preview) {
            protocolViewModel.previewSoundscape(
                safe.selection,
                safe.primaryVolumePercent
            )
        } else Unit
    }

    val customSoundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            customSoundNotice = null
            customSoundImporting = true
            val documents = uris.distinctBy(Uri::toString).map { uri ->
                val persistablePermissionTaken = runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    true
                }.getOrDefault(false)
                uri to persistablePermissionTaken
            }
            protocolViewModel.importCustomSounds(documents) { imported, failedCount ->
                customSoundImporting = false
                customSoundNotice = context.getString(
                    R.string.focus_sound_import_result,
                    imported.size,
                    failedCount
                )
                focusSoundCategory = "custom"
                if (imported.size == 1) {
                    applyFocusSoundDraft(
                        focusSoundDraft.copy(
                            selection = focusSoundDraft.selection.copy(primary = imported.first())
                        ),
                        true
                    )
                }
            }
        }
    }

    LaunchedEffect(
        activeProtocol?.id,
        activeProtocol?.activityType,
        activeProtocol?.itemId
    ) {
        activeProtocol?.let { session ->
            val taskId = activeTaskId(session)
            val sessionSelection = session.soundscapeSelection()
            val preserveSetupChoice = loadedSoundSessionId != session.id &&
                taskId != null && focusSoundTaskId == taskId
            val setupRememberForTask = focusSoundDraft.rememberForTask
            loadedSoundSessionId = session.id
            focusSoundTaskId = taskId
            protocolViewModel.loadSoundDraft(
                taskId = taskId,
                fallbackSelection = sessionSelection,
                fallbackVolumePercent = session.soundscapeVolume
            ) { loaded ->
                val current = activeProtocol
                if (
                    current?.id == session.id &&
                    current.activityType == session.activityType &&
                    current.itemId == session.itemId
                ) {
                    val effective = if (preserveSetupChoice) {
                        FocusSoundDraft(
                            selection = sessionSelection,
                            primaryVolumePercent = session.soundscapeVolume,
                            rememberForTask = setupRememberForTask
                        )
                    } else {
                        loaded
                    }
                    focusSoundDraft = effective
                    focusSoundMixEnabled = effective.selection.secondaryLayerId != null
                    if (
                        !preserveSetupChoice &&
                        effective.rememberForTask &&
                        effective.selection != sessionSelection
                    ) {
                        protocolViewModel.updateActiveSoundscape(
                            sessionId = session.id,
                            selection = effective.selection,
                            primaryVolumePercent = effective.primaryVolumePercent,
                            taskId = taskId,
                            rememberForTask = true
                        )
                    }
                }
            }
        }
    }

    val selectFocusSound: (String, FocusSoundUiLayer) -> Unit = { soundId, layer ->
        val item = focusSoundItems.firstOrNull { it.id == soundId }
        val entry = item?.catalogId?.let(FocusSoundCatalog::find)
        when {
            item == null || entry == null || !item.isAvailable -> Unit
            layer == FocusSoundUiLayer.PRIMARY -> {
                val primarySelection = if (
                    entry.kind == FocusSoundKind.CUSTOM_FILE && item.customFile != null
                ) {
                    FocusSoundSelection.custom(item.customFile)
                } else {
                    FocusSoundSelection(entry.id)
                }
                val nextSelection = focusSoundDraft.selection.copy(
                    primary = primarySelection
                ).normalized()
                focusSoundMixEnabled = nextSelection.secondaryLayerId != null
                applyFocusSoundDraft(
                    focusSoundDraft.copy(selection = nextSelection),
                    true
                )
            }
            entry.kind == FocusSoundKind.GENERATED_NOISE -> {
                val nextSelection = focusSoundDraft.selection.copy(
                    secondaryLayerId = entry.id
                ).normalized()
                focusSoundMixEnabled = nextSelection.secondaryLayerId != null
                applyFocusSoundDraft(
                    focusSoundDraft.copy(selection = nextSelection),
                    true
                )
            }
        }
    }

    val toggleFocusSoundPlayback: () -> Unit = {
        val session = activeProtocol
        if (session != null) {
            protocolViewModel.toggleSoundscapePlayback(session)
        } else if (
            focusSoundPlayback.status == FocusSoundPlaybackStatus.PLAYING ||
            focusSoundPlayback.status == FocusSoundPlaybackStatus.LOADING
        ) {
            protocolViewModel.stopSoundscapePreview()
        } else {
            protocolViewModel.previewSoundscape(
                focusSoundDraft.selection,
                focusSoundDraft.primaryVolumePercent
            )
        }
    }

    val soundPickerContent: @Composable () -> Unit = {
        if (showFocusSoundPicker) {
            FocusSoundscapePickerSheet(
                state = focusSoundUiState,
                onDismiss = {
                    protocolViewModel.stopSoundscapePreview()
                    activeProtocol?.let { session ->
                        protocolViewModel.updateActiveSoundscape(
                            sessionId = session.id,
                            selection = focusSoundDraft.selection,
                            primaryVolumePercent = focusSoundDraft.primaryVolumePercent,
                            taskId = activeTaskId(session),
                            rememberForTask = focusSoundDraft.rememberForTask
                        )
                    } ?: protocolViewModel.stopSoundscapePreview()
                    showFocusSoundPicker = false
                },
                onCategorySelected = { focusSoundCategory = it },
                onSoundSelected = selectFocusSound,
                onToggleFavorite = protocolViewModel::toggleSoundFavorite,
                onTogglePlayback = toggleFocusSoundPlayback,
                onPrimaryVolumeChange = { fraction ->
                    val percent = (fraction * 100f).toInt().coerceIn(0, 100)
                    focusSoundDraft = focusSoundDraft.copy(primaryVolumePercent = percent)
                    protocolViewModel.adjustSoundscapeVolumes(
                        focusSoundDraft.selection,
                        percent,
                        focusSoundDraft.selection.secondaryVolumePercent
                    )
                },
                onSecondaryVolumeChange = { fraction ->
                    val percent = (fraction * 100f).toInt().coerceIn(0, 100)
                    focusSoundDraft = focusSoundDraft.copy(
                        selection = focusSoundDraft.selection.copy(
                            secondaryVolumePercent = percent
                        )
                    )
                    protocolViewModel.adjustSoundscapeVolumes(
                        focusSoundDraft.selection,
                        focusSoundDraft.primaryVolumePercent,
                        percent
                    )
                },
                onMixEnabledChange = { enabled ->
                    if (!enabled || focusSoundUiState.canMix) {
                        focusSoundMixEnabled = enabled
                        if (enabled) {
                            focusSoundEditingLayer = FocusSoundUiLayer.SECONDARY
                            focusSoundCategory = "noise"
                        } else {
                            focusSoundEditingLayer = FocusSoundUiLayer.PRIMARY
                            applyFocusSoundDraft(
                                focusSoundDraft.copy(
                                    selection = focusSoundDraft.selection.copy(
                                        secondaryLayerId = null
                                    )
                                ),
                                true
                            )
                        }
                    }
                },
                onEditingLayerChange = { layer ->
                    focusSoundEditingLayer = layer
                    if (layer == FocusSoundUiLayer.SECONDARY) focusSoundCategory = "noise"
                },
                onAddCustomSound = {
                    if (focusSoundEditingLayer == FocusSoundUiLayer.PRIMARY) {
                        customSoundLauncher.launch(arrayOf("audio/*"))
                    }
                },
                onRemoveCustomSound = { file ->
                    protocolViewModel.removeCustomSound(file)
                    customSoundNotice = context.getString(R.string.focus_sound_removed_from_library)
                    if (focusSoundDraft.selection.primary.customFile?.uriString == file.uriString) {
                        applyFocusSoundDraft(
                            focusSoundDraft.copy(
                                selection = focusSoundDraft.selection.copy(
                                    primary = FocusSoundSelection.silence()
                                )
                            ),
                            true
                        )
                    }
                },
                rememberForTask = focusSoundDraft.rememberForTask,
                onRememberForTaskChange = {
                    focusSoundDraft = focusSoundDraft.copy(rememberForTask = it)
                },
                showRememberForTask = focusSoundTaskId != null,
                playDuringRecovery = focusSoundDraft.selection.playDuringRecovery,
                onPlayDuringRecoveryChange = { enabled ->
                    applyFocusSoundDraft(
                        focusSoundDraft.copy(
                            selection = focusSoundDraft.selection.copy(
                                playDuringRecovery = enabled
                            )
                        ),
                        false
                    )
                }
            )
        }
    }
    val liveFocusIntervals = remember(activeProtocol, progressNowMillis) {
        activeProtocol?.liveTaskFocusIntervals(progressNowMillis).orEmpty()
    }
    val dailyProgressByTask = remember(
        workTasks,
        activityRecords,
        progressNowMillis,
        liveFocusIntervals
    ) {
        DailyTaskFocusCalculator.calculateForTasks(
            tasks = workTasks,
            records = activityRecords,
            nowMillis = progressNowMillis,
            zoneId = ZoneId.systemDefault(),
            liveIntervals = liveFocusIntervals
        )
    }

    val studyItems = remember(subjects, workTasks) {
        subjects.map { HubActivityItem(it.id, it.name, it.color) } +
            workTasks.filterNot { it.isDone }
                .filter { it.focusActivityType() == FocusActivityType.STUDY }
                .map {
                    HubActivityItem(
                        taskFocusItemId(it.id),
                        it.primaryLabel(),
                        STUDY_TASK_COLOR_TOKEN,
                        taskId = it.id
                    )
                }
    }
    val workItems = remember(workTasks) {
        workTasks.filterNot { it.isDone }
            .filter { it.focusActivityType() == FocusActivityType.WORK }
            .map {
            HubActivityItem(
                taskFocusItemId(it.id),
                it.primaryLabel(),
                WORK_TASK_COLOR_TOKEN,
                taskId = it.id
            )
        }
    }
    val otherItems = remember(otherActivities, workTasks) {
        otherActivities.map { HubActivityItem(it.id, it.name, it.color) } +
            workTasks.filterNot { it.isDone }
                .filter { it.focusActivityType() == FocusActivityType.OTHER }
                .map {
                    HubActivityItem(
                        taskFocusItemId(it.id),
                        it.primaryLabel(),
                        OTHER_TASK_COLOR_TOKEN,
                        taskId = it.id
                    )
                }
    }
    fun itemsFor(type: FocusActivityType): List<HubActivityItem> = when (type) {
        FocusActivityType.STUDY -> studyItems
        FocusActivityType.WORK -> workItems
        FocusActivityType.OTHER -> otherItems
    }
    fun protocolTarget(item: HubActivityItem): FocusProtocolTarget {
        val maximumFocusMinutes = item.taskId?.let { taskId ->
            workTasks.firstOrNull { it.id == taskId }?.remainingWorkMinutesOrNull()
        }
        val task = item.taskId?.let { taskId -> workTasks.firstOrNull { it.id == taskId } }
        val daily = item.taskId?.let(dailyProgressByTask::get)
        return FocusProtocolTarget(
            id = item.id,
            name = item.name,
            color = item.color,
            maximumFocusMinutes = maximumFocusMinutes,
            dailyProgress = daily,
            boutMinutes = task?.estimatedMinutes,
            isDailyRequired = task?.isDailyRequired == true
        )
    }

    activeProtocol?.let { session ->
        val targets = itemsFor(session.activityType)
            .map(::protocolTarget)
            .filter { it.maximumFocusMinutes != 0 }
        FocusProtocolActiveScreen(
            session = session,
            remainingMillis = protocolRemaining,
            dailyProgress = activeTaskId(session)?.let(dailyProgressByTask::get),
            boutElapsedMillis = currentBoutElapsedMillis(session, protocolRemaining),
            dailyRequired = activeTaskId(session)?.let { id ->
                workTasks.firstOrNull { it.id == id }?.isDailyRequired
            } == true,
            availableTargets = targets,
            onSkipReset = { protocolViewModel.skipReset(session.id) },
            onStartFocus = { protocolViewModel.startFocus(session.id) },
            onPause = { protocolViewModel.pauseFocus(session.id) },
            onResume = { protocolViewModel.resumeFocus(session.id) },
            onFinishFocus = { protocolViewModel.finishFocus(session.id) },
            onFinishRecovery = { protocolViewModel.finishRecovery(session.id) },
            onDistraction = { protocolViewModel.markDistraction(session.id) },
            onRepeatCycle = { protocolViewModel.repeatCycle(session.id) },
            onSwitchTarget = { target, outcome ->
                protocolViewModel.switchTargetAndRepeat(
                    session.id,
                    session.activityType,
                    target.id,
                    target.name,
                    outcome
                )
            },
            onFinishBlock = { protocolViewModel.finishBlock(session.id) },
            onCancel = { protocolViewModel.cancel(session.id, it) },
            onCompleteReview = { protocolViewModel.completeReview(session.id, it) },
            soundscapeState = focusSoundUiState,
            onOpenSoundscape = { showFocusSoundPicker = true },
            onToggleSoundscape = toggleFocusSoundPlayback,
            modifier = modifier
        )
        soundPickerContent()
        return
    }

    val activityItems = itemsFor(activityType)
    val selectedItem = activityItems.firstOrNull { it.id == selectedId }
    val selectedTask = selectedItem?.taskId?.let { taskId -> workTasks.firstOrNull { it.id == taskId } }
    val previousForSelected = latestProtocol?.takeIf {
        it.activityType == activityType && it.itemId == selectedItem?.id
    }
    val suggestedFocusMinutes = selectedTask?.nextFocusDurationMinutes()
        ?: previousForSelected?.focusDurationMinutes
        ?: (fallbackFocusDuration / 60_000L).toInt()
    val (dayStart, dayEnd) = currentDayRange
    val totalsByItem = remember(currentDaySessions, activityType, currentDayRange) {
        currentDaySessions.asSequence()
            .filter { it.activityType == activityType }
            .mapNotNull { session ->
                val itemId = session.taskId?.let(::taskFocusItemId) ?: when (activityType) {
                    FocusActivityType.STUDY -> session.subjectId
                    FocusActivityType.WORK -> null
                    FocusActivityType.OTHER -> session.otherActivityId
                } ?: return@mapNotNull null
                val actualEnd = session.startedAt + session.actualDurationMillis
                val end = minOf(session.completedAt ?: actualEnd, actualEnd, dayEnd)
                val start = maxOf(session.startedAt, dayStart)
                itemId to (end - start).coerceAtLeast(0L)
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, durations) -> durations.sum() }
    }
    val totalToday = totalsByItem.values.sum()
    val cyclesToday = currentDaySessions.count {
        it.activityType == activityType && !it.isBreak && it.actualDurationMillis > 0L && it.recordSource == "TIMER"
    }
    val selectedDailyProgress = selectedTask?.id?.let(dailyProgressByTask::get)
    val displayTotalsByItem = remember(activityItems, totalsByItem, dailyProgressByTask) {
        activityItems.associate { item ->
            item.id to (item.taskId?.let { dailyProgressByTask[it]?.spentMillis }
                ?: totalsByItem[item.id]
                ?: 0L)
        }
    }

    LaunchedEffect(activityType, activityItems, selectedId) {
        if (activityItems.isNotEmpty() && activityItems.none { it.id == selectedId }) {
            viewModel.selectItem(activityItems.first().id)
        }
    }

    var showSetup by remember { mutableStateOf(false) }
    var showInsights by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HubActivityItem?>(null) }
    var focusStartInProgress by remember { mutableStateOf(false) }
    var focusStartRejected by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .padding(top = 30.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.pomodoro_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.focus_hub_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ActivityTypeStrip(
            selected = activityType,
            onSelected = viewModel::selectActivityType
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HubQuickAction(
                title = "Добавить",
                subtitle = "время",
                onClick = { showManualEntry = true },
                containerColor = MaterialTheme.appAccents.other.container,
                modifier = Modifier.weight(1f)
            )
            HubQuickAction(
                title = "История",
                subtitle = "$cyclesToday циклов",
                onClick = { showInsights = true },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = categoryTitle(activityType),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.focus_hub_hold_to_edit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ActivityCarousel(
            items = activityItems,
            selectedId = selectedId,
            totals = displayTotalsByItem,
            onSelect = { viewModel.selectItem(it.id) },
            onEdit = {
                editing = it
                showEditor = true
            },
            onAdd = {
                onCreateTask(activityType)
            }
        )

        FocusLaunchCard(
            modifier = Modifier.weight(1f),
            item = selectedItem,
            outcome = previousForSelected?.outcome.orEmpty(),
            focusMinutes = suggestedFocusMinutes,
            recoveryMinutes = previousForSelected?.recoveryDurationMinutes
                ?: (fallbackBreakDuration / 60_000L).toInt(),
            cyclesToday = cyclesToday,
            totalToday = totalToday,
            dailyProgress = selectedDailyProgress,
            boutMinutes = selectedTask?.estimatedMinutes ?: suggestedFocusMinutes,
            dailyRequired = selectedTask?.isDailyRequired == true,
            onStart = {
                focusSoundTaskId = selectedItem?.taskId
                focusSoundDraft = FocusSoundDraft(
                    selection = focusSoundSettings.defaultSelection,
                    primaryVolumePercent = focusSoundSettings.volumePercent,
                    rememberForTask = false
                )
                focusSoundMixEnabled = focusSoundSettings.defaultSelection.secondaryLayerId != null
                focusSoundDraftLoading = true
                protocolViewModel.loadSoundDraft(selectedItem?.taskId) { loaded ->
                    focusSoundDraft = loaded
                    focusSoundMixEnabled = loaded.selection.secondaryLayerId != null
                    focusSoundDraftLoading = false
                }
                showSetup = true
            },
            onOpenTask = selectedItem?.taskId?.let { taskId -> { onOpenTask(taskId) } }
        )
    }

    if (showSetup) {
        FocusProtocolSetupSheet(
            activityType = activityType,
            targets = activityItems.map(::protocolTarget),
            selectedTargetId = selectedItem?.id,
            initialOutcome = previousForSelected?.outcome.orEmpty(),
            initialResetMinutes = previousForSelected?.resetDurationMinutes ?: 5,
            initialFocusMinutes = suggestedFocusMinutes,
            initialRecoveryMinutes = previousForSelected?.recoveryDurationMinutes
                ?: (fallbackBreakDuration / 60_000L).toInt(),
            bedtimeRisk = protocolViewModel::isBedtimeRisk,
            startInProgress = focusStartInProgress,
            soundscapeLoading = focusSoundDraftLoading,
            startError = if (focusStartRejected) {
                stringResource(R.string.focus_block_start_failed)
            } else {
                null
            },
            soundscapeState = focusSoundUiState,
            onOpenSoundscape = { showFocusSoundPicker = true },
            onToggleSoundscape = toggleFocusSoundPlayback,
            onTargetSelected = { target ->
                val taskId = activityItems.firstOrNull { it.id == target.id }?.taskId
                focusSoundTaskId = taskId
                focusSoundDraftLoading = true
                protocolViewModel.loadSoundDraft(taskId) { loaded ->
                    focusSoundDraft = loaded
                    focusSoundMixEnabled = loaded.selection.secondaryLayerId != null
                    focusSoundDraftLoading = false
                }
            },
            onStart = { target, outcome, reset, focus, recovery, energy ->
                focusStartInProgress = true
                focusStartRejected = false
                protocolViewModel.start(
                    activityType = activityType,
                    itemId = target.id,
                    itemName = target.name,
                    outcome = outcome,
                    resetMinutes = reset,
                    focusMinutes = focus,
                    recoveryMinutes = recovery,
                    energyBefore = energy,
                    soundscape = focusSoundDraft.selection,
                    soundscapeVolumePercent = focusSoundDraft.primaryVolumePercent,
                    rememberSoundscapeForTask = focusSoundDraft.rememberForTask,
                    persistedTaskId = focusSoundTaskId,
                    onResult = { started ->
                        focusStartInProgress = false
                        if (started) {
                            showSetup = false
                        } else {
                            focusStartRejected = true
                        }
                    }
                )
            },
            onDismiss = {
                if (!focusStartInProgress) {
                    protocolViewModel.cancelSoundDraftLoad()
                    protocolViewModel.stopSoundscapePreview()
                    focusSoundDraftLoading = false
                    focusStartRejected = false
                    showSetup = false
                }
            }
        )
    }

    soundPickerContent()

    if (showInsights) {
        ThemedModalBottomSheet(onDismissRequest = { showInsights = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.focus_hub_insights),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                DailySummaryCard(cyclesToday, totalToday)
                CompletedFocusBlocksCard(
                    blocks = recentCompletedBlocks.filter { it.activityType == activityType }
                )
                EnergyPatternCard(points = energyPattern)
                Spacer(Modifier.height(26.dp))
            }
        }
    }

    if (showManualEntry) {
        ManualActivitySheet(
            onDismiss = { showManualEntry = false },
            initialTaskId = focusItemTaskId(selectedItem?.id),
            initialActivityType = activityType,
            initialSubjectId = selectedItem?.id.takeIf {
                focusItemTaskId(it) == null && activityType == FocusActivityType.STUDY
            },
            initialOtherActivityId = selectedItem?.id.takeIf {
                focusItemTaskId(it) == null && activityType == FocusActivityType.OTHER
            },
            initialTitle = selectedItem?.name.orEmpty()
        )
    }

    if (showEditor) {
        ActivityEditorDialog(
            initial = editing,
            activityType = activityType,
            onOpenTask = editing?.taskId?.let { taskId ->
                {
                    showEditor = false
                    onOpenTask(taskId)
                }
            },
            onSave = { name, color ->
                val current = editing
                val taskId = current?.taskId
                if (taskId != null) {
                    workTasks.firstOrNull { it.id == taskId }?.let { source ->
                        viewModel.updateWorkTask(source.copy(title = name.trim()))
                    }
                } else when (activityType) {
                    FocusActivityType.STUDY -> if (current == null) {
                        viewModel.addSubject(name, color)
                    } else {
                        val source = subjects.firstOrNull { it.id == current.id }
                        if (source != null) {
                            viewModel.updateSubject(source.copy(name = name.trim(), color = color))
                        }
                    }
                    FocusActivityType.WORK -> if (current == null) {
                        viewModel.addWorkTask(name)
                    } else {
                        val source = workTasks.firstOrNull { it.id == current.id }
                        if (source != null) viewModel.updateWorkTask(source.copy(title = name.trim()))
                    }
                    FocusActivityType.OTHER -> if (current == null) {
                        viewModel.addOtherActivity(name, color)
                    } else {
                        val source = otherActivities.firstOrNull { it.id == current.id }
                        if (source != null) {
                            viewModel.updateOtherActivity(source.copy(name = name.trim(), color = color))
                        }
                    }
                }
                showEditor = false
            },
            onDelete = editing?.takeIf { it.taskId == null }?.let { item ->
                {
                    when (activityType) {
                        FocusActivityType.STUDY -> viewModel.deleteSubject(item.id)
                        FocusActivityType.WORK -> viewModel.deleteWorkTask(item.id)
                        FocusActivityType.OTHER -> viewModel.deleteOtherActivity(item.id)
                    }
                    showEditor = false
                }
            },
            onDismiss = { showEditor = false }
        )
    }
}

@Composable
private fun HubQuickAction(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor.copy(alpha = 0.72f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualFocusDialog(
    onDismiss: () -> Unit,
    onSave: (Long, Int) -> Unit
) {
    val zone = ZoneId.systemDefault()
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var timeText by remember { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var durationText by remember { mutableStateOf("25") }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.focus_manual_entry_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.focus_manual_entry_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy")))
                }
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it },
                    label = { Text(stringResource(R.string.focus_manual_time)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.focus_manual_duration)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val time = runCatching {
                    LocalTime.parse(timeText.trim(), DateTimeFormatter.ofPattern("H:mm"))
                }.getOrNull() ?: return@TextButton
                val minutes = durationText.toIntOrNull()?.coerceIn(1, PomodoroViewModel.MAX_FOCUS_MINUTES.toInt())
                    ?: return@TextButton
                val start = LocalDateTime.of(selectedDate, time).atZone(zone).toInstant().toEpochMilli()
                val boundedStart = minOf(start, System.currentTimeMillis() - minutes * 60_000L)
                onSave(boundedStart, minutes)
            }) { Text(stringResource(R.string.task_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        selectedDate = java.time.Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.task_date_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun ActivityTypeStrip(
    selected: FocusActivityType,
    onSelected: (FocusActivityType) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(FocusActivityType.entries) { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelected(type) },
                label = { Text(activityTypeTitle(type)) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActivityCarousel(
    items: List<HubActivityItem>,
    selectedId: Int?,
    totals: Map<Int, Long>,
    onSelect: (HubActivityItem) -> Unit,
    onEdit: (HubActivityItem) -> Unit,
    onAdd: () -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.id }) { item ->
            val selected = item.id == selectedId
            val itemColor = pomodoroColorForToken(item.color)
            val width by animateDpAsState(if (selected) 148.dp else 132.dp, label = "subjectWidth")
            val background by animateColorAsState(
                if (selected) itemColor.copy(alpha = 0.24f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                label = "subjectColor"
            )
            Column(
                modifier = Modifier
                    .width(width)
                    .height(92.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(background)
                    .then(
                        if (selected) Modifier.border(
                            1.5.dp,
                            itemColor,
                            RoundedCornerShape(22.dp)
                        ) else Modifier
                    )
                    .combinedClickable(
                        onClick = { onSelect(item) },
                        onLongClick = { onEdit(item) }
                    )
                    .padding(13.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(itemColor)
                    )
                    Text(
                        text = if (item.taskId != null) stringResource(R.string.focus_hub_task_badge)
                        else if (selected) "ฅ" else "·",
                        color = itemColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text(
                    text = item.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
                Text(
                    text = formatCompactDuration(totals[item.id] ?: 0L),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(92.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(22.dp)
                    )
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+\nฅ",
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.appAccents.focus.color,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FocusLaunchCard(
    modifier: Modifier = Modifier,
    item: HubActivityItem?,
    outcome: String,
    focusMinutes: Int,
    recoveryMinutes: Int,
    cyclesToday: Int,
    totalToday: Long,
    dailyProgress: DailyTaskFocusProgress?,
    boutMinutes: Int,
    dailyRequired: Boolean,
    onStart: () -> Unit,
    onOpenTask: (() -> Unit)? = null
) {
    val readyHint = stringResource(R.string.focus_hub_ready_hint)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.appAccents.focus.container,
            contentColor = MaterialTheme.appAccents.focus.onContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item?.name ?: stringResource(R.string.focus_hub_choose_item),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (outcome.isBlank()) readyHint else outcome,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // The animated companion belongs to an active focus protocol. The
            // decorative idle copy used to be squeezed underneath these controls.
            Spacer(modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill(stringResource(R.string.focus_block_focus_pill, focusMinutes))
                InfoPill(stringResource(R.string.focus_block_rest_pill, recoveryMinutes))
            }

            dailyProgress?.let { progress ->
                DailyFocusProgressCard(
                    progress = progress,
                    boutElapsedMillis = 0L,
                    boutMinutes = boutMinutes,
                    requiredToday = dailyRequired
                )
            }

            Button(
                onClick = onStart,
                enabled = item != null && focusMinutes > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.focus_block_begin))
            }

            onOpenTask?.let { openTask ->
                TextButton(onClick = openTask, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.focus_hub_open_task))
                }
            }

            Text(
                text = stringResource(
                    R.string.focus_hub_today_summary,
                    cyclesToday,
                    formatCompactDuration(totalToday)
                ),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun activeTaskId(session: FocusProtocolSessionEntity): Int? =
    focusItemTaskId(session.itemId)
        ?: session.itemId.takeIf { session.activityType == FocusActivityType.WORK && it > 0 }

private fun currentBoutElapsedMillis(
    session: FocusProtocolSessionEntity,
    remainingMillis: Long
): Long {
    val duration = session.focusDurationMinutes.coerceAtLeast(0) * 60_000L
    return when (session.phase) {
        FocusProtocolPhase.FOCUS,
        FocusProtocolPhase.FOCUS_PAUSED -> (duration - remainingMillis).coerceIn(0L, duration)
        FocusProtocolPhase.RECOVERY,
        FocusProtocolPhase.CYCLE_READY,
        FocusProtocolPhase.REVIEW -> session.focusElapsedMillis.coerceIn(0L, duration)
        else -> 0L
    }
}

@Composable
private fun InfoPill(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
private fun DailySummaryCard(cycles: Int, total: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.appAccents.success.container,
            contentColor = MaterialTheme.appAccents.success.onContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$cycles", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.focus_block_cycles_label), style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatCompactDuration(total),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(stringResource(R.string.focus_block_focus_time_label), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ActivityEditorDialog(
    initial: HubActivityItem?,
    activityType: FocusActivityType,
    onOpenTask: (() -> Unit)?,
    onSave: (String, Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableIntStateOf(initial?.color ?: SUBJECT_COLORS[1]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.focus_hub_new_item) else initial.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.library_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (
                    activityType != FocusActivityType.WORK &&
                    (initial == null || initial.taskId == null)
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(SUBJECT_COLORS) { colorToken ->
                            val displayColor = pomodoroColorForToken(colorToken)
                            Box(
                                modifier = Modifier
                                    .size(if (colorToken == color) 34.dp else 30.dp)
                                    .clip(CircleShape)
                                    .background(displayColor)
                                    .then(
                                        if (colorToken == color) Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.onSurface,
                                            CircleShape
                                        ) else Modifier
                                    )
                                    .clickable { color = colorToken }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, color) }) {
                Text(stringResource(R.string.library_save))
            }
        },
        dismissButton = {
            Row {
                onOpenTask?.let {
                    TextButton(onClick = it) {
                        Text(stringResource(R.string.focus_hub_open_task))
                    }
                }
                onDelete?.let {
                    TextButton(onClick = it) {
                        Text(
                            stringResource(R.string.library_delete),
                            color = MaterialTheme.appAccents.urgent.color
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}

@Composable
private fun categoryTitle(type: FocusActivityType): String = stringResource(
    when (type) {
        FocusActivityType.STUDY -> R.string.pomodoro_study_targets
        FocusActivityType.WORK -> R.string.pomodoro_tasks
        FocusActivityType.OTHER -> R.string.pomodoro_other_targets
    }
)

@Composable
private fun activityTypeTitle(type: FocusActivityType): String = stringResource(
    when (type) {
        FocusActivityType.STUDY -> R.string.pomodoro_activity_study
        FocusActivityType.WORK -> R.string.pomodoro_activity_work
        FocusActivityType.OTHER -> R.string.pomodoro_activity_other
    }
)
