package com.personal.sleepalarm.ui.tasks

import com.personal.sleepalarm.ui.theme.appAccents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.ProjectEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.domain.model.effectiveWorkBudgetMinutes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectsScreen(
    projects: List<ProjectEntity>,
    tasks: List<TaskEntity>,
    onBack: () -> Unit,
    onSave: (ProjectEntity) -> Unit,
    onArchive: (ProjectEntity) -> Unit,
    onOpenTask: (TaskEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<ProjectEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.projects_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, stringResource(R.string.projects_add)) }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp, 8.dp, 14.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val ordered = projects.sortedBy(ProjectEntity::isArchived)
            if (ordered.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.projects_empty),
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(ordered, key = ProjectEntity::id) { project ->
                val projectTasks = tasks.filter { it.projectId == project.id }
                val linked = projectTasks.filterNot(TaskEntity::isDone)
                val budgetMinutes = project.workBudgetMinutes.takeIf { it > 0 }
                    ?: projectTasks.sumOf(TaskEntity::effectiveWorkBudgetMinutes)
                val budgetMillis = budgetMinutes * 60_000L
                val progress = if (budgetMillis > 0L) (project.spentMillis.toFloat() / budgetMillis).coerceIn(0f, 1f) else 0f
                Card(
                    onClick = { editing = project },
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (project.isArchived) MaterialTheme.colorScheme.surfaceContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(project.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (project.goal.isNotBlank()) {
                                    Text(project.goal, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { onArchive(project) }) {
                                Icon(if (project.isArchived) Icons.Default.Unarchive else Icons.Default.Archive, null)
                            }
                        }
                        if (budgetMillis > 0L) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            Text(
                                stringResource(
                                    R.string.projects_progress,
                                    project.spentMillis / 60_000L,
                                    budgetMinutes,
                                    ((budgetMillis - project.spentMillis).coerceAtLeast(0L) / 60_000L)
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        project.dueAtMillis?.let {
                            Text(
                                stringResource(R.string.projects_deadline, formatProjectDate(it)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.appAccents.work.color
                            )
                        }
                        Text(stringResource(R.string.projects_open_tasks, linked.size), style = MaterialTheme.typography.labelMedium)
                        linked.take(4).forEach { task ->
                            TextButton(onClick = { onOpenTask(task) }) {
                                Text("• " + task.primaryLabel().take(70), maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        ProjectEditorDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = {
                onSave(it)
                creating = false
                editing = null
            }
        )
    }
}

@Composable
private fun ProjectEditorDialog(
    initial: ProjectEntity?,
    onDismiss: () -> Unit,
    onSave: (ProjectEntity) -> Unit
) {
    val base = initial ?: ProjectEntity(title = "")
    var title by remember(base.id) { mutableStateOf(base.title) }
    var goal by remember(base.id) { mutableStateOf(base.goal) }
    var description by remember(base.id) { mutableStateOf(base.description) }
    var budgetHours by remember(base.id) { mutableStateOf((base.workBudgetMinutes / 60f).let { if (it == 0f) "" else "%.1f".format(it) }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.projects_add else R.string.projects_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.projects_name)) }, singleLine = true)
                OutlinedTextField(goal, { goal = it }, label = { Text(stringResource(R.string.projects_goal)) }, minLines = 2)
                OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.task_field_description)) }, minLines = 2)
                OutlinedTextField(
                    budgetHours,
                    { budgetHours = it.filter { char -> char.isDigit() || char == '.' || char == ',' } },
                    label = { Text(stringResource(R.string.projects_budget_hours)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val hours = budgetHours.replace(',', '.').toFloatOrNull()?.coerceIn(0f, 10_000f) ?: 0f
                onSave(base.copy(title = title, goal = goal, description = description, workBudgetMinutes = (hours * 60).toInt()))
            }, enabled = title.isNotBlank()) { Text(stringResource(R.string.task_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

private fun formatProjectDate(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
