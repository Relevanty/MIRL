package com.personal.sleepalarm.ui.tasks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.LibraryItemEntity
import com.personal.sleepalarm.data.db.entity.ProjectEntity
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.domain.model.effectiveWorkBudgetMinutes
import com.personal.sleepalarm.domain.model.remainingWorkMillisOrNull
import com.personal.sleepalarm.domain.calculator.DailyTaskFocusCalculator
import com.personal.sleepalarm.domain.calculator.DailyTaskFocusProgress
import com.personal.sleepalarm.domain.calculator.liveTaskFocusIntervals
import com.personal.sleepalarm.ui.activity.ManualActivitySheet
import com.personal.sleepalarm.ui.components.DailyFocusProgressCard
import com.personal.sleepalarm.ui.theme.ThemedModalBottomSheet
import com.personal.sleepalarm.ui.theme.appAccents
import com.personal.sleepalarm.ui.theme.AppAccentTone
import java.time.ZoneId
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import com.personal.sleepalarm.domain.calculator.TaskDeadlinePlanCalculator
import com.personal.sleepalarm.ui.components.TaskDeadlinePlanSummary

/** Живая матрица задач, полная карточка и доска завершённого. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel = viewModel(),
    onAddReminder: (taskId: Int) -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onStartFocus: (TaskEntity) -> Unit = {},
    onOpenLibraryItem: (Int) -> Unit = {},
    openTaskId: Int? = null,
    onOpenTaskRequestConsumed: (Int) -> Unit = {},
    createTaskCategory: String? = null,
    onCreateTaskRequestConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activityRecords by viewModel.activityRecords.collectAsStateWithLifecycle()
    val activeFocusProtocol by viewModel.activeFocusProtocol.collectAsStateWithLifecycle()
    val progressNowMillis by viewModel.progressNowMillis.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val libraryItems by viewModel.libraryItems.collectAsStateWithLifecycle()
    val libraryLinks by viewModel.libraryLinks.collectAsStateWithLifecycle()
    val demandProfiles by viewModel.demandProfiles.collectAsStateWithLifecycle()
    val taskDependencies by viewModel.taskDependencies.collectAsStateWithLifecycle()
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var detailTaskId by remember { mutableStateOf<Int?>(null) }
    var showCompletedBoard by remember { mutableStateOf(false) }
    var expandedQuadrant by remember { mutableStateOf<TaskQuadrant?>(null) }
    var manualActivityTaskId by remember { mutableStateOf<Int?>(null) }
    var editingActivity by remember { mutableStateOf<ActivityRecordEntity?>(null) }
    var showProjects by remember { mutableStateOf(false) }
    var isDraggingTask by remember { mutableStateOf(false) }
    var deletingTask by remember { mutableStateOf<TaskEntity?>(null) }
    val detailTask = state.generalTasks.firstOrNull { it.id == detailTaskId }
    val liveFocusIntervals = remember(activeFocusProtocol, progressNowMillis) {
        activeFocusProtocol?.liveTaskFocusIntervals(progressNowMillis).orEmpty()
    }
    val dailyProgressByTask = remember(
        state.generalTasks,
        activityRecords,
        progressNowMillis,
        liveFocusIntervals
    ) {
        DailyTaskFocusCalculator.calculateForTasks(
            tasks = state.generalTasks,
            records = activityRecords,
            nowMillis = progressNowMillis,
            zoneId = ZoneId.systemDefault(),
            liveIntervals = liveFocusIntervals
        )
    }

    LaunchedEffect(openTaskId, state.generalTasks) {
        val requestedId = openTaskId ?: return@LaunchedEffect
        val task = state.generalTasks.firstOrNull { it.id == requestedId }
            ?: return@LaunchedEffect
        detailTaskId = task.id
        onOpenTaskRequestConsumed(task.id)
    }

    LaunchedEffect(createTaskCategory) {
        val category = createTaskCategory?.takeIf { it in setOf("STUDY", "WORK", "OTHER") }
            ?: return@LaunchedEffect
        editingTask = TaskEntity(title = "", matrixQuadrant = 2, category = category)
        detailTaskId = null
        expandedQuadrant = null
        showCompletedBoard = false
        onCreateTaskRequestConsumed()
    }

    if (showProjects) {
        ProjectsScreen(
            projects = projects,
            tasks = state.generalTasks,
            onBack = { showProjects = false },
            onSave = viewModel::saveProject,
            onArchive = viewModel::archiveProject,
            onOpenTask = {
                detailTaskId = it.id
                showProjects = false
            },
            modifier = modifier
        )
        return
    } else if (editingTask != null) {
        TaskEditorScreen(
            initialTask = editingTask,
            initialDemandProfile = demandProfiles.firstOrNull { it.taskId == editingTask?.id },
            availableDependencyTasks = state.generalTasks,
            initialDependencyIds = taskDependencies
                .filter { it.taskId == editingTask?.id }
                .mapTo(mutableSetOf()) { it.dependsOnTaskId },
            onBack = {
                editingTask = null
            },
            onSave = { task, profile, dependencyIds ->
                viewModel.saveTask(task, profile, dependencyIds)
                editingTask = null
            },
            onImportImage = viewModel::importTaskImage,
            projects = projects,
            modifier = modifier
        )
        return
    }

    if (expandedQuadrant != null) {
        val quadrant = expandedQuadrant!!
        ExpandedTaskQuadrant(
            quadrant = quadrant,
            allTasks = state.activeMatrixTasks,
            projects = projects,
            dailyProgress = dailyProgressByTask,
            onBack = { expandedQuadrant = null },
            onSelectQuadrant = { expandedQuadrant = it },
            onOpenTask = { detailTaskId = it.id },
            onFocus = { task -> onStartFocus(task) },
            onMove = { task, direction -> viewModel.moveTaskWithinQuadrant(task, direction) },
            onCreate = {
                editingTask = TaskEntity(title = "", matrixQuadrant = quadrant.storageValue, category = "")
                expandedQuadrant = null
            },
            modifier = modifier
        )
    } else if (showCompletedBoard) {
        CompletedTasksBoard(
            tasks = state.completedMatrixTasks,
            activities = activityRecords,
            projects = projects,
            onBack = { showCompletedBoard = false },
            onOpenTask = { detailTaskId = it.id },
            onRestore = viewModel::restoreTask,
            onDuplicate = viewModel::duplicateCompletedTask,
            onDelete = viewModel::deleteTask,
            modifier = modifier
        )
    } else {
        Scaffold(
            modifier = modifier.fillMaxSize()
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                TaskMatrixHeader(
                    activeCount = state.activeMatrixTasks.size,
                    projectCount = projects.count { !it.isArchived },
                    completedCount = state.completedMatrixTasks.size,
                    onOpenLibrary = onOpenLibrary,
                    onOpenBoard = { showCompletedBoard = true },
                    onOpenProjects = { showProjects = true }
                )
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    EisenhowerMatrix(
                        tasks = state.activeMatrixTasks,
                        onOpenTask = { detailTaskId = it.id },
                        onMoveTask = viewModel::moveTask,
                        onReorderTask = viewModel::moveTaskToIndex,
                        onCompleteTask = viewModel::completeTask,
                        onCreateInQuadrant = {
                            editingTask = TaskEntity(title = "", matrixQuadrant = it.storageValue, category = "")
                        },
                        onOpenQuadrant = { expandedQuadrant = it },
                        onDraggingChanged = { isDraggingTask = it },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isDraggingTask) {
                        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp)) {
                            CompletedDock(
                                completedCount = state.completedMatrixTasks.size,
                                onClick = { showCompletedBoard = true }
                            )
                        }
                    }
                }
            }
        }
    }

    detailTask?.let { task ->
        ThemedModalBottomSheet(onDismissRequest = { detailTaskId = null }) {
            TaskDetailSheet(
                task = task,
                onEdit = {
                    editingTask = task
                    detailTaskId = null
                },
                onFocus = {
                    onStartFocus(task)
                    detailTaskId = null
                },
                onReminder = {
                    onAddReminder(task.id)
                    detailTaskId = null
                },
                onAddTime = { manualActivityTaskId = task.id },
                activities = activityRecords.filter { it.taskId == task.id },
                dailyProgress = dailyProgressByTask[task.id],
                onEditActivity = { editingActivity = it },
                libraryItems = libraryItems,
                linkedLibraryIds = libraryLinks.filter { it.taskId == task.id }.mapTo(linkedSetOf()) { it.libraryItemId },
                onToggleLibrary = { viewModel.toggleLibraryLink(task.id, it) },
                onOpenLibrary = onOpenLibraryItem,
                onComplete = {
                    if (task.isDone) viewModel.restoreTask(task) else viewModel.completeTask(task)
                    detailTaskId = null
                },
                onDelete = {
                    deletingTask = task
                    detailTaskId = null
                },
                onToggleChecklistItem = { viewModel.toggleChecklistItem(task, it) }
            )
        }
    }

    deletingTask?.let { task ->
        AlertDialog(
            onDismissRequest = { deletingTask = null },
            title = { Text("Удалить задачу?") },
            text = { Text("Карточка, её изображение и связь с напоминанием будут удалены.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTask(task)
                        deletingTask = null
                    }
                ) { Text("Удалить", color = MaterialTheme.appAccents.urgent.color) }
            },
            dismissButton = {
                TextButton(onClick = { deletingTask = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (manualActivityTaskId != null || editingActivity != null) {
        ManualActivitySheet(
            initialTaskId = manualActivityTaskId,
            editing = editingActivity,
            onDismiss = {
                manualActivityTaskId = null
                editingActivity = null
            }
        )
    }
}

@Composable
private fun TaskMatrixHeader(
    activeCount: Int,
    projectCount: Int,
    completedCount: Int,
    onOpenLibrary: () -> Unit,
    onOpenBoard: () -> Unit,
    onOpenProjects: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.task_matrix_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.task_matrix_active_count, activeCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onOpenProjects,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.appAccents.work.container,
                contentColor = MaterialTheme.appAccents.work.onContainer,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(Icons.Default.AccountTree, null, Modifier.size(20.dp), tint = MaterialTheme.appAccents.work.onContainer)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.task_projects_hub), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.task_projects_count, projectCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appAccents.work.onContainer.copy(alpha = 0.78f)
                        )
                    }
                }
            }
            Surface(
                onClick = onOpenLibrary,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.appAccents.leisure.container,
                contentColor = MaterialTheme.appAccents.leisure.onContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.appAccents.leisure.onContainer
                    )
                    Text(
                        text = stringResource(R.string.misc_library),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
            Surface(
                onClick = onOpenBoard,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.appAccents.success.container,
                contentColor = MaterialTheme.appAccents.success.onContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, stringResource(R.string.task_open_done_board), Modifier.size(20.dp))
                    Text(completedCount.toString(), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun CompletedDock(completedCount: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.appAccents.success.container,
        contentColor = MaterialTheme.appAccents.success.onContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BoardCat(Modifier.size(38.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.task_done_dock), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.task_done_dock_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appAccents.success.onContainer.copy(alpha = 0.78f)
                )
            }
            Text(completedCount.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.appAccents.success.onContainer)
        }
    }
}

@Composable
private fun TaskDetailSheet(
    task: TaskEntity,
    onEdit: () -> Unit,
    onFocus: () -> Unit,
    onReminder: () -> Unit,
    onAddTime: () -> Unit,
    activities: List<ActivityRecordEntity>,
    dailyProgress: DailyTaskFocusProgress?,
    onEditActivity: (ActivityRecordEntity) -> Unit,
    libraryItems: List<LibraryItemEntity>,
    linkedLibraryIds: Set<Int>,
    onToggleLibrary: (Int) -> Unit,
    onOpenLibrary: (Int) -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onToggleChecklistItem: (Int) -> Unit
) {
    val deadlineNow by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.primaryLabel(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    TaskQuadrant.fromStorage(task.matrixQuadrant).displayName(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.appAccents.focus.color
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.task_edit)) }
        }

        task.imagePath?.let {
            LocalTaskImage(path = it, modifier = Modifier.fillMaxWidth().height(180.dp))
        }
        DetailText(stringResource(R.string.task_field_next_action), task.nextAction)
        DetailText(stringResource(R.string.task_field_done_definition), task.definitionOfDone)
        DetailText(stringResource(R.string.task_field_why), task.whyImportant)
        DetailText(stringResource(R.string.task_field_description), task.description)
        DetailText(stringResource(R.string.task_field_dependencies), task.dependencies)
        DetailText(stringResource(R.string.task_field_obstacle), task.obstacle)
        DetailText(stringResource(R.string.task_field_if_then), task.ifThenPlan)
        DetailText(stringResource(R.string.task_field_materials), task.materials)

        dailyProgress?.let { progress ->
            DailyFocusProgressCard(
                progress = progress,
                boutElapsedMillis = 0L,
                boutMinutes = task.estimatedMinutes,
                requiredToday = task.isDailyRequired,
                showBoutProgress = false
            )
        }

        val checklistItems = parseTaskChecklist(task.checklist)
        if (checklistItems.isNotEmpty()) {
            Text(
                stringResource(R.string.task_field_checklist),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.appAccents.focus.color
            )
            checklistItems.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = item.isDone, onCheckedChange = { onToggleChecklistItem(index) })
                    Text(
                        item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TaskMetaChip(stringResource(R.string.daily_focus_bout_duration, task.estimatedMinutes))
            if (task.isDailyRequired) {
                TaskMetaChip(stringResource(R.string.daily_focus_required_badge))
            }
            TaskMetaChip(
                if (task.effectiveWorkBudgetMinutes() > 0) {
                    formatSpentTime(task.spentMillis, task.effectiveWorkBudgetMinutes())
                } else {
                    stringResource(
                        R.string.daily_focus_total_spent_unlimited,
                        task.spentMillis / 60_000L
                    )
                }
            )
            task.dueAtMillis?.let { TaskMetaChip(formatDetailDate(it)) }
            if (task.contextTag.isNotBlank()) TaskMetaChip(task.contextTag)
        }
        if (task.dueAtMillis != null) {
            TaskDeadlinePlanSummary(TaskDeadlinePlanCalculator.calculate(task, deadlineNow, ZoneId.systemDefault()))
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TaskMetaChip(TaskEnergy.fromStorage(task.energyLevel).detailName())
            if (task.projectTag.isNotBlank()) TaskMetaChip(task.projectTag)
            if (task.assignee.isNotBlank()) TaskMetaChip(task.assignee)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onFocus, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PlayArrow, null)
                Text(stringResource(R.string.task_start_focus))
            }
            IconButton(onClick = onReminder) {
                Icon(Icons.Default.Notifications, stringResource(R.string.tasks_add_reminder))
            }
            IconButton(onClick = onComplete) {
                Icon(
                    if (task.isDone) Icons.Default.Close else Icons.Default.CheckCircle,
                    stringResource(if (task.isDone) R.string.task_restore else R.string.task_complete)
                )
            }
        }
        OutlinedButton(onClick = onAddTime, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AccessTime, null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.activity_add_spent))
        }
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.appAccents.urgent.color)
            Spacer(Modifier.size(8.dp))
            Text("Удалить задачу", color = MaterialTheme.appAccents.urgent.color)
        }

        if (activities.isNotEmpty()) {
            Text(
                stringResource(R.string.activity_history),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.appAccents.work.color
            )
            activities.take(6).forEach { activity ->
                Surface(
                    onClick = { onEditActivity(activity) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.appAccents.progress.container,
                    contentColor = MaterialTheme.appAccents.progress.onContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(activity.result.ifBlank { activity.title }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                formatActivityMoment(activity),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.appAccents.progress.onContainer.copy(alpha = 0.76f)
                            )
                        }
                        if (activity.source == "MANUAL") {
                            Text(
                                stringResource(R.string.activity_added_manually),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.appAccents.info.color
                            )
                        }
                    }
                }
            }
        }

        if (libraryItems.isNotEmpty()) {
            Text(
                stringResource(R.string.task_library_materials),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.appAccents.leisure.color
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                libraryItems.forEach { item ->
                    val linked = item.id in linkedLibraryIds
                    FilterChip(
                        selected = linked,
                        onClick = {
                            if (linked) onOpenLibrary(item.id) else onToggleLibrary(item.id)
                        },
                        label = { Text(item.title, maxLines = 1) },
                        trailingIcon = if (linked) {
                            {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.task_unlink_material),
                                    modifier = Modifier.size(18.dp).clickable { onToggleLibrary(item.id) }
                                )
                            }
                        } else null
                    )
                }
            }
        }
    }
}

private fun formatActivityMoment(record: ActivityRecordEntity): String {
    val zone = java.time.ZoneId.systemDefault()
    val start = java.time.Instant.ofEpochMilli(record.startedAt).atZone(zone)
    val minutes = record.durationMillis / 60_000L
    return start.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM · HH:mm")) + " · ${minutes} мин"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandedTaskQuadrant(
    quadrant: TaskQuadrant,
    allTasks: List<TaskEntity>,
    projects: List<ProjectEntity>,
    dailyProgress: Map<Int, DailyTaskFocusProgress>,
    onBack: () -> Unit,
    onSelectQuadrant: (TaskQuadrant) -> Unit,
    onOpenTask: (TaskEntity) -> Unit,
    onFocus: (TaskEntity) -> Unit,
    onMove: (TaskEntity, Int) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember(quadrant) { mutableStateOf("") }
    var sortMode by remember(quadrant) { mutableStateOf("ORDER") }
    var overdueOnly by remember(quadrant) { mutableStateOf(false) }
    var gridMode by remember(quadrant) { mutableStateOf(false) }
    var projectFilter by remember(quadrant) { mutableStateOf<Int?>(null) }
    val tasks = allTasks.filter { it.matrixQuadrant == quadrant.storageValue }
    val now = System.currentTimeMillis()
    val visibleTasks = remember(tasks, query, sortMode, overdueOnly, projectFilter) {
        tasks.asSequence()
            .filter { task ->
                query.isBlank() || listOf(task.title, task.description, task.nextAction, task.tags, task.projectTag)
                    .any { it.contains(query, ignoreCase = true) }
            }
            .filter { !overdueOnly || (it.dueAtMillis ?: Long.MAX_VALUE) < now }
            .filter { projectFilter == null || it.projectId == projectFilter }
            .let { sequence ->
                when (sortMode) {
                    "DEADLINE" -> sequence.sortedBy { it.dueAtMillis ?: Long.MAX_VALUE }
                    "REMAINING" -> sequence.sortedByDescending {
                        it.remainingWorkMillisOrNull() ?: Long.MAX_VALUE
                    }
                    else -> sequence.sortedWith(compareBy<TaskEntity> { it.sortOrder }.thenByDescending { it.createdAt })
                }
            }.toList()
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(quadrant.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.task_expanded_count, tasks.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { gridMode = !gridMode }) {
                        Icon(if (gridMode) Icons.Default.ViewList else Icons.Default.GridView, null)
                    }
                    IconButton(onClick = onCreate) {
                        Icon(Icons.Default.Add, stringResource(R.string.tasks_add))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TaskQuadrant.entries.forEach { item ->
                    val count = allTasks.count { it.matrixQuadrant == item.storageValue }
                    FilterChip(
                        selected = item == quadrant,
                        onClick = { if (item == quadrant) onBack() else onSelectQuadrant(item) },
                        label = { Text("${item.displayName()} $count", maxLines = 1) }
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.task_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(selected = sortMode == "ORDER", onClick = { sortMode = "ORDER" }, label = { Text(stringResource(R.string.task_sort_manual)) })
                FilterChip(selected = sortMode == "DEADLINE", onClick = { sortMode = "DEADLINE" }, label = { Text(stringResource(R.string.task_sort_deadline)) })
                FilterChip(selected = sortMode == "REMAINING", onClick = { sortMode = "REMAINING" }, label = { Text(stringResource(R.string.task_sort_remaining)) })
                FilterChip(selected = overdueOnly, onClick = { overdueOnly = !overdueOnly }, label = { Text(stringResource(R.string.task_filter_overdue)) })
                FilterChip(selected = projectFilter == null, onClick = { projectFilter = null }, label = { Text("Все проекты") })
                projects.filterNot { it.isArchived }.forEach { project ->
                    FilterChip(
                        selected = projectFilter == project.id,
                        onClick = { projectFilter = project.id },
                        label = { Text(project.title, maxLines = 1) }
                    )
                }
            }
            if (visibleTasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.task_quadrant_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (gridMode) {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleTasks.size, key = { visibleTasks[it].id }) { index ->
                        val task = visibleTasks[index]
                        val taskTone = taskAccentTone(task)
                        Surface(
                            onClick = { onOpenTask(task) },
                            shape = RoundedCornerShape(22.dp),
                            color = taskTone.container,
                            contentColor = taskTone.onContainer
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TaskBallPreview(task, dailyProgress[task.id], taskTone)
                                IconButton(onClick = { onFocus(task) }) {
                                    Icon(Icons.Default.PlayArrow, "Начать фокус")
                                }
                                Text(
                                    if (task.effectiveWorkBudgetMinutes() > 0) {
                                        formatSpentTime(task.spentMillis, task.effectiveWorkBudgetMinutes())
                                    } else {
                                        stringResource(
                                            R.string.daily_focus_total_spent_unlimited,
                                            task.spentMillis / 60_000L
                                        )
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = taskTone.onContainer.copy(alpha = 0.78f),
                                    maxLines = 2,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 8.dp, 12.dp, 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleTasks, key = { it.id }) { task ->
                        ReorderableTaskRow(
                            task = task,
                            dailyProgress = dailyProgress[task.id],
                            onOpen = { onOpenTask(task) },
                            onFocus = { onFocus(task) },
                            projectTitle = projects.firstOrNull { it.id == task.projectId }?.title,
                            onMove = { direction -> onMove(task, direction) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderableTaskRow(
    task: TaskEntity,
    dailyProgress: DailyTaskFocusProgress?,
    onOpen: () -> Unit,
    onFocus: () -> Unit,
    projectTitle: String?,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragY by remember(task.id) { mutableFloatStateOf(0f) }
    var dragging by remember(task.id) { mutableStateOf(false) }
    val animatedY by animateFloatAsState(
        targetValue = if (dragging) dragY else 0f,
        // Track the finger directly; use the spring only for the final landing.
        animationSpec = if (dragging) snap() else spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "task_row_settle"
    )
    fun commitDrag() {
        calculateReorderDirection(dragY)?.let(onMove)
        dragging = false
        dragY = 0f
    }
    val taskTone = taskAccentTone(task)
    Surface(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = animatedY },
        shape = RoundedCornerShape(20.dp),
        color = taskTone.container,
        contentColor = taskTone.onContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(task.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { dragging = true },
                        onDrag = { change, amount ->
                            change.consume()
                            dragY += amount.y
                        },
                        onDragCancel = {
                            // System pointer cancellation commits the current
                            // placement; there is no separate user-facing undo.
                            commitDrag()
                        },
                        onDragEnd = ::commitDrag
                    )
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TaskBallPreview(task, dailyProgress, taskTone)
            Column(Modifier.weight(1f)) {
                Text(
                    task.primaryLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                task.description
                    .takeIf { it.isNotBlank() && it.trim() != task.primaryLabel() }
                    ?.let { description ->
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = taskTone.onContainer.copy(alpha = 0.78f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                Text(
                    dailyProgress?.let {
                        stringResource(
                            R.string.daily_focus_today_value,
                            it.spentMinutes,
                            it.targetMinutes
                        )
                    } ?: if (task.effectiveWorkBudgetMinutes() > 0) {
                        formatSpentTime(task.spentMillis, task.effectiveWorkBudgetMinutes())
                    } else {
                        stringResource(
                            R.string.daily_focus_total_spent_unlimited,
                            task.spentMillis / 60_000L
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = taskTone.onContainer.copy(alpha = 0.78f)
                )
                task.nextAction.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = taskTone.onContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val meta = listOfNotNull(projectTitle, task.category.takeIf(String::isNotBlank)).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = taskTone.onContainer.copy(alpha = 0.72f))
                }
            }
            IconButton(onClick = onFocus) { Icon(Icons.Default.PlayArrow, "Начать фокус") }
        }
    }
}

internal fun calculateReorderDirection(dragY: Float, thresholdPx: Float = 34f): Int? = when {
    dragY > thresholdPx -> 1
    dragY < -thresholdPx -> -1
    else -> null
}

@Composable
private fun TaskBallPreview(
    task: TaskEntity,
    dailyProgress: DailyTaskFocusProgress? = null,
    tone: AppAccentTone = MaterialTheme.appAccents.focus
) {
    val progress = dailyProgress?.progressFraction ?: 0f
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
        androidx.compose.material3.CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 4.dp,
            trackColor = tone.action,
            color = tone.onContainer
        )
        if (task.imagePath != null) {
            LocalTaskImage(task.imagePath, Modifier.size(38.dp), circular = true)
        } else {
            Icon(
                Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = tone.onContainer.copy(alpha = 0.72f)
            )
        }
        if (task.isDailyRequired) {
            val requiredTone = MaterialTheme.appAccents.warning
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).size(18.dp),
                shape = CircleShape,
                color = requiredTone.color
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "!",
                        style = MaterialTheme.typography.labelSmall,
                        color = requiredTone.onColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailText(label: String, value: String) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.appAccents.calm.color)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TaskMetaChip(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.appAccents.schedule.action,
        contentColor = MaterialTheme.appAccents.schedule.onAction
    ) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun taskAccentTone(task: TaskEntity): AppAccentTone = when (
    TaskQuadrant.fromStorage(task.matrixQuadrant)
) {
    TaskQuadrant.NOW -> MaterialTheme.appAccents.urgent
    TaskQuadrant.SCHEDULE -> MaterialTheme.appAccents.schedule
    TaskQuadrant.DELEGATE -> MaterialTheme.appAccents.work
    TaskQuadrant.LET_GO -> MaterialTheme.appAccents.other
}

private fun formatSpentTime(spentMillis: Long, estimateMinutes: Int): String {
    val spentMinutes = (spentMillis / 60_000L).toInt()
    if (estimateMinutes <= 0) return "$spentMinutes мин"
    val remaining = (estimateMinutes - spentMinutes).coerceAtLeast(0)
    return "${spentMinutes}/${estimateMinutes} мин · осталось $remaining"
}

@Composable
internal fun TaskQuadrant.displayName(): String = when (this) {
    TaskQuadrant.NOW -> stringResource(R.string.task_quadrant_now)
    TaskQuadrant.SCHEDULE -> stringResource(R.string.task_quadrant_schedule)
    TaskQuadrant.DELEGATE -> stringResource(R.string.task_quadrant_delegate)
    TaskQuadrant.LET_GO -> stringResource(R.string.task_quadrant_let_go)
}

@Composable
private fun TaskEnergy.detailName(): String = when (this) {
    TaskEnergy.LOW -> stringResource(R.string.task_energy_low)
    TaskEnergy.MEDIUM -> stringResource(R.string.task_energy_medium)
    TaskEnergy.HIGH -> stringResource(R.string.task_energy_high)
}

private fun formatDetailDate(millis: Long): String = java.time.Instant.ofEpochMilli(millis)
    .atZone(java.time.ZoneId.systemDefault())
    .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm"))
