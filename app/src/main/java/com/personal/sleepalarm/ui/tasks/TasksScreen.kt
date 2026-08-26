package com.personal.sleepalarm.ui.tasks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
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
import com.personal.sleepalarm.ui.activity.ManualActivitySheet
import com.personal.sleepalarm.ui.theme.ThemedModalBottomSheet

/** Живая матрица задач, полная карточка и доска завершённого. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel = viewModel(),
    onAddReminder: (taskId: Int) -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onStartFocus: (TaskEntity) -> Unit = {},
    openTaskId: Int? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activityRecords by viewModel.activityRecords.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val canUndoMove by viewModel.canUndoMove.collectAsStateWithLifecycle()
    val libraryItems by viewModel.libraryItems.collectAsStateWithLifecycle()
    val libraryLinks by viewModel.libraryLinks.collectAsStateWithLifecycle()
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

    LaunchedEffect(openTaskId, state.generalTasks) {
        if (openTaskId != null && state.generalTasks.any { it.id == openTaskId }) {
            detailTaskId = openTaskId
        }
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
            onBack = {
                editingTask = null
            },
            onSave = { task ->
                viewModel.saveTask(task)
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
            onBack = { expandedQuadrant = null },
            onSelectQuadrant = { expandedQuadrant = it },
            onOpenTask = { detailTaskId = it.id },
            onFocus = { task -> onStartFocus(task) },
            onMove = { task, direction -> viewModel.moveTaskWithinQuadrant(task, direction) },
            onCreate = {
                editingTask = TaskEntity(title = "", matrixQuadrant = quadrant.storageValue)
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
                    onOpenCalendar = onOpenCalendar,
                    onOpenBoard = { showCompletedBoard = true },
                    onOpenProjects = { showProjects = true },
                    onCreate = {
                        editingTask = TaskEntity(title = "", matrixQuadrant = TaskQuadrant.SCHEDULE.storageValue)
                    }
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
                            editingTask = TaskEntity(title = "", matrixQuadrant = it.storageValue)
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
                if (canUndoMove && !isDraggingTask) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = viewModel::undoLastMove) {
                            Text(stringResource(R.string.task_undo_move))
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
                onEditActivity = { editingActivity = it },
                libraryItems = libraryItems,
                linkedLibraryIds = libraryLinks.filter { it.taskId == task.id }.mapTo(linkedSetOf()) { it.libraryItemId },
                onToggleLibrary = { viewModel.toggleLibraryLink(task.id, it) },
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
                ) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
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
    onOpenCalendar: () -> Unit,
    onOpenBoard: () -> Unit,
    onOpenProjects: () -> Unit,
    onCreate: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            Button(onClick = onCreate) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.size(5.dp))
                Text("Задача")
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onOpenCalendar, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("Календарь", maxLines = 1)
            }
            TextButton(onClick = onOpenProjects, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AccountTree, null, Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("Проекты", maxLines = 1)
            }
            TextButton(onClick = onOpenBoard, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("Готово", maxLines = 1)
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(completedCount.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
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
    onEditActivity: (ActivityRecordEntity) -> Unit,
    libraryItems: List<LibraryItemEntity>,
    linkedLibraryIds: Set<Int>,
    onToggleLibrary: (Int) -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onToggleChecklistItem: (Int) -> Unit
) {
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
                    TaskQuadrant.fromStorage(task.matrixQuadrant).displayName(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
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

        val checklistItems = parseTaskChecklist(task.checklist)
        if (checklistItems.isNotEmpty()) {
            Text(
                stringResource(R.string.task_field_checklist),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
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
            TaskMetaChip("${task.plannedFocusMinutes} ${stringResource(R.string.task_minutes_short)}")
            TaskMetaChip(formatSpentTime(task.spentMillis, effectiveBudgetMinutes(task)))
            task.dueAtMillis?.let { TaskMetaChip(formatDetailDate(it)) }
            if (task.contextTag.isNotBlank()) TaskMetaChip(task.contextTag)
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
            Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.size(8.dp))
            Text("Удалить задачу", color = MaterialTheme.colorScheme.error)
        }

        if (activities.isNotEmpty()) {
            Text(
                stringResource(R.string.activity_history),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            activities.take(6).forEach { activity ->
                Surface(
                    onClick = { onEditActivity(activity) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (activity.source == "MANUAL") {
                            Text(
                                stringResource(R.string.activity_added_manually),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
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
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                libraryItems.forEach { item ->
                    FilterChip(
                        selected = item.id in linkedLibraryIds,
                        onClick = { onToggleLibrary(item.id) },
                        label = { Text(item.title, maxLines = 1) }
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
                        val budget = it.workBudgetMinutes.takeIf { value -> value > 0 } ?: it.estimatedMinutes
                        (budget * 60_000L - it.spentMillis).coerceAtLeast(0L)
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
                        Surface(
                            onClick = { onOpenTask(task) },
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TaskBallPreview(task)
                                IconButton(onClick = { onFocus(task) }) {
                                    Icon(Icons.Default.PlayArrow, "Начать фокус")
                                }
                                Text(
                                    formatSpentTime(task.spentMillis, effectiveBudgetMinutes(task)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "task_row_settle"
    )
    Surface(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = animatedY },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                            dragging = false
                            dragY = 0f
                        },
                        onDragEnd = {
                            val direction = when {
                                dragY > 34f -> 1
                                dragY < -34f -> -1
                                else -> 0
                            }
                            if (direction != 0) onMove(direction)
                            dragging = false
                            dragY = 0f
                        }
                    )
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TaskBallPreview(task)
            Column(Modifier.weight(1f)) {
                val descriptionPreview = task.description
                    .ifBlank { task.nextAction }
                    .ifBlank { stringResource(R.string.task_untitled) }
                Text(
                    descriptionPreview,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatSpentTime(task.spentMillis, effectiveBudgetMinutes(task)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                task.nextAction.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val meta = listOfNotNull(projectTitle, task.category.takeIf(String::isNotBlank)).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            IconButton(onClick = onFocus) { Icon(Icons.Default.PlayArrow, "Начать фокус") }
        }
    }
}

@Composable
private fun TaskBallPreview(task: TaskEntity) {
    val budget = effectiveBudgetMinutes(task)
    val progress = if (budget <= 0) 0f
    else (task.spentMillis / 60_000f / budget).coerceIn(0f, 1f)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
        androidx.compose.material3.CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 4.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            color = MaterialTheme.colorScheme.primary
        )
        if (task.imagePath != null) {
            LocalTaskImage(task.imagePath, Modifier.size(38.dp), circular = true)
        } else {
            Icon(
                Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailText(label: String, value: String) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TaskMetaChip(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}

private fun formatSpentTime(spentMillis: Long, estimateMinutes: Int): String {
    val spentMinutes = (spentMillis / 60_000L).toInt()
    val remaining = (estimateMinutes - spentMinutes).coerceAtLeast(0)
    return "${spentMinutes}/${estimateMinutes} мин · осталось $remaining"
}

private fun effectiveBudgetMinutes(task: TaskEntity): Int =
    task.workBudgetMinutes.takeIf { it > 0 } ?: task.estimatedMinutes

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
    .toLocalDate()
    .format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.SHORT))
