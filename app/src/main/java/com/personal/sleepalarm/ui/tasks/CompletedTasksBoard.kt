package com.personal.sleepalarm.ui.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.ProjectEntity
import com.personal.sleepalarm.domain.calculator.ActivityProgressCalculator
import com.personal.sleepalarm.domain.model.effectiveWorkBudgetMinutes
import com.personal.sleepalarm.ui.theme.appAccents
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompletedTasksBoard(
    tasks: List<TaskEntity>,
    activities: List<ActivityRecordEntity>,
    projects: List<ProjectEntity>,
    onBack: () -> Unit,
    onOpenTask: (TaskEntity) -> Unit,
    onRestore: (TaskEntity) -> Unit,
    onDuplicate: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    var period by remember { mutableStateOf("WEEK") }
    var projectId by remember { mutableStateOf<Int?>(null) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val filteredTasks = tasks.filter { task ->
        val date = task.completedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val inPeriod = when (period) {
            "TODAY" -> date == today
            "WEEK" -> date != null && !date.isBefore(today.minusDays(6))
            "MONTH" -> date != null && !date.isBefore(today.minusDays(29))
            else -> true
        }
        inPeriod && (projectId == null || task.projectId == projectId)
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.task_board_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back)) }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 12.dp)
        ) {
            BoardSummary(filteredTasks, activities)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("TODAY" to "Сегодня", "WEEK" to "7 дней", "MONTH" to "30 дней", "ALL" to "Все").forEach { (value, label) ->
                    FilterChip(selected = period == value, onClick = { period = value }, label = { Text(label) })
                }
                FilterChip(selected = projectId == null, onClick = { projectId = null }, label = { Text("Все проекты") })
                projects.forEach { project ->
                    FilterChip(
                        selected = projectId == project.id,
                        onClick = { projectId = project.id },
                        label = { Text(project.title, maxLines = 1) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (filteredTasks.isEmpty()) {
                EmptyBoard(modifier = Modifier.weight(1f))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        CompletedTaskNote(
                            task,
                            activities.filter { it.taskId == task.id },
                            projects.firstOrNull { it.id == task.projectId }?.title,
                            onOpenTask,
                            onRestore,
                            onDuplicate,
                            onDelete
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardSummary(tasks: List<TaskEntity>, activities: List<ActivityRecordEntity>) {
    val ids = tasks.mapTo(mutableSetOf()) { it.id }
    val taskActivities = activities.filter { it.taskId in ids }
    val spentMinutes = ActivityProgressCalculator.uniqueCountedMillis(taskActivities) / 60_000L
    val plannedMinutes = tasks.sumOf(TaskEntity::effectiveWorkBudgetMinutes)
    val pomodoroCount = taskActivities.count { it.countsTowardProgress && it.source == "TIMER" }
    val successTone = MaterialTheme.appAccents.success
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = successTone.container.copy(alpha = 0.82f),
        contentColor = successTone.onContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BoardCat(Modifier.size(54.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.task_board_count, tasks.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Факт ${spentMinutes / 60} ч ${spentMinutes % 60} мин · план ${plannedMinutes / 60} ч ${plannedMinutes % 60} мин · $pomodoroCount фокусов",
                    style = MaterialTheme.typography.bodySmall,
                    color = successTone.onContainer.copy(alpha = 0.78f)
                )
            }
        }
    }
}

@Composable
private fun CompletedTaskNote(
    task: TaskEntity,
    activities: List<ActivityRecordEntity>,
    projectTitle: String?,
    onOpenTask: (TaskEntity) -> Unit,
    onRestore: (TaskEntity) -> Unit,
    onDuplicate: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit
) {
    val accents = MaterialTheme.appAccents
    val tone = when (TaskQuadrant.fromStorage(task.matrixQuadrant)) {
        TaskQuadrant.NOW -> accents.urgent
        TaskQuadrant.SCHEDULE -> accents.schedule
        TaskQuadrant.DELEGATE -> accents.work
        TaskQuadrant.LET_GO -> accents.other
    }
    Surface(
        onClick = { onOpenTask(task) },
        modifier = Modifier.graphicsLayer { rotationZ = ((task.id % 5) - 2) * 0.55f },
        shape = RoundedCornerShape(6.dp, 22.dp, 8.dp, 18.dp),
        color = tone.container,
        contentColor = tone.onContainer,
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PushPin,
                    null,
                    Modifier.size(17.dp).graphicsLayer { rotationZ = -18f },
                    tint = tone.onContainer
                )
                Spacer(Modifier.weight(1f))
                task.completedAt?.let {
                    Text(
                        formatCompletedDate(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = tone.onContainer.copy(alpha = 0.78f)
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            if (task.imagePath != null) {
                LocalTaskImage(task.imagePath, Modifier.fillMaxWidth().height(76.dp))
                Spacer(Modifier.height(8.dp))
            }
            if (task.description.isNotBlank()) {
                Text(
                    task.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (task.definitionOfDone.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    task.definitionOfDone,
                    style = MaterialTheme.typography.bodySmall,
                    color = tone.onContainer.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val countedActivities = activities.filter(ActivityRecordEntity::countsTowardProgress)
            val spent = ActivityProgressCalculator.countedMillis(countedActivities) / 60_000L
            Text(
                listOfNotNull(
                    projectTitle,
                    "${spent / 60} ч ${spent % 60} мин",
                    "${countedActivities.count { it.source == "TIMER" }} фокусов"
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = tone.onContainer.copy(alpha = 0.78f)
            )
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.align(Alignment.End)) {
                IconButton(onClick = { onRestore(task) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Restore, stringResource(R.string.task_restore), Modifier.size(18.dp))
                }
                IconButton(onClick = { onDuplicate(task) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.ContentCopy, "Повторить", Modifier.size(18.dp))
                }
                IconButton(onClick = { onDelete(task) }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        stringResource(R.string.tasks_delete),
                        Modifier.size(18.dp),
                        tint = MaterialTheme.appAccents.urgent.color
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyBoard(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BoardCat(Modifier.size(110.dp))
            Text(
                stringResource(R.string.task_board_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.task_board_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
internal fun BoardCat(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.appAccents.focus.color
    val secondary = MaterialTheme.appAccents.calm.color
    val ink = MaterialTheme.appAccents.calm.onContainer
    Canvas(modifier) {
        val center = Offset(size.width * 0.5f, size.height * 0.55f)
        val r = size.minDimension * 0.30f
        val head = Path().apply {
            moveTo(center.x - r, center.y - r * 0.35f)
            lineTo(center.x - r * 0.82f, center.y - r * 1.25f)
            lineTo(center.x - r * 0.25f, center.y - r * 0.78f)
            lineTo(center.x + r * 0.25f, center.y - r * 0.78f)
            lineTo(center.x + r * 0.82f, center.y - r * 1.25f)
            lineTo(center.x + r, center.y - r * 0.35f)
            quadraticTo(center.x + r, center.y + r, center.x, center.y + r)
            quadraticTo(center.x - r, center.y + r, center.x - r, center.y - r * 0.35f)
            close()
        }
        drawPath(head, primary.copy(alpha = 0.82f))
        drawCircle(secondary.copy(alpha = 0.50f), r * 0.52f, Offset(center.x - r * 0.40f, center.y + r * 0.05f))
        val stroke = Stroke(width = size.minDimension * 0.035f, cap = StrokeCap.Round)
        drawLine(ink, Offset(center.x - r * 0.55f, center.y), Offset(center.x - r * 0.22f, center.y + r * 0.05f), stroke.width, StrokeCap.Round)
        drawLine(ink, Offset(center.x + r * 0.22f, center.y + r * 0.05f), Offset(center.x + r * 0.55f, center.y), stroke.width, StrokeCap.Round)
        drawCircle(ink.copy(alpha = 0.75f), r * 0.09f, Offset(center.x, center.y + r * 0.36f))
    }
}

private fun formatCompletedDate(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
