package com.personal.sleepalarm.ui.tasks

import com.personal.sleepalarm.ui.dday.DDayBadge
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.TaskEntity

/**
 * Экран задач.
 *
 * Две секции:
 * - «Утренняя рутина» — со стриком (🔥 N), визуальный сброс isDone каждый день.
 * - «Общие дела» — обычный toggle.
 *
 * У каждой задачи кнопка-колокольчик → запрос на создание напоминания
 * (через onAddReminder, который подключается из MainActivity).
 */
@Composable
fun TasksScreen(
    viewModel: TasksViewModel = viewModel(),
    /**
     * Callback: пользователь хочет создать напоминание для задачи.
     * MainActivity должен открыть ReminderEditScreen с привязкой к taskId,
     * а после создания — вызвать viewModel.linkReminder(taskId, newReminderId).
     */
    onAddReminder: (taskId: Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.tab_tasks),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        // ДОБАВЛЕНО (v5): бейдж ближайшего D-Day.
        DDayBadge()

        Spacer(modifier = Modifier.height(8.dp))

        Spacer(modifier = Modifier.height(12.dp))

        // === Поле ввода + переключатель секции ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.draftTitle,
                onValueChange = viewModel::setDraftTitle,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.tasks_new_placeholder)) },
                singleLine = true
            )
            IconButton(
                onClick = { viewModel.addDraftTask() },
                enabled = state.draftTitle.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tasks_add))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !state.draftIsMorning,
                onClick = { viewModel.setDraftIsMorning(false) },
                label = { Text(stringResource(R.string.tasks_section_general)) }
            )
            FilterChip(
                selected = state.draftIsMorning,
                onClick = { viewModel.setDraftIsMorning(true) },
                label = { Text(stringResource(R.string.tasks_section_morning)) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                TaskSection(
                    title = stringResource(R.string.tasks_section_morning),
                    subtitle = stringResource(R.string.tasks_morning_subtitle),
                    tasks = state.morningRoutine,
                    isDoneToday = viewModel::isDoneToday,
                    onToggle = viewModel::toggleDone,
                    onDelete = viewModel::deleteTask,
                    onAddReminder = { onAddReminder(it.id) }
                )
            }

            item {
                TaskSection(
                    title = stringResource(R.string.tasks_section_general),
                    subtitle = null,
                    tasks = state.generalTasks,
                    isDoneToday = viewModel::isDoneToday,
                    onToggle = viewModel::toggleDone,
                    onDelete = viewModel::deleteTask,
                    onAddReminder = { onAddReminder(it.id) }
                )
            }
        }
    }
}

// =====================================================================
// Секция задач
// =====================================================================

@Composable
private fun TaskSection(
    title: String,
    subtitle: String?,
    tasks: List<TaskEntity>,
    isDoneToday: (TaskEntity) -> Boolean,
    onToggle: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    onAddReminder: (TaskEntity) -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.tasks_empty_section),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            tasks.forEach { task ->
                TaskRow(
                    task = task,
                    isDoneToday = isDoneToday(task),
                    onToggle = { onToggle(task) },
                    onDelete = { onDelete(task) },
                    onAddReminder = { onAddReminder(task) }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

// =====================================================================
// Строка задачи
// =====================================================================

@Composable
private fun TaskRow(
    task: TaskEntity,
    isDoneToday: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onAddReminder: () -> Unit
) {
    val alpha = if (isDoneToday) 0.55f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isDoneToday,
            onCheckedChange = { onToggle() }
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textDecoration = if (isDoneToday) TextDecoration.LineThrough else TextDecoration.None
            )
            if (task.isMorningRoutine && task.streakCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🔥 ${task.streakCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (task.reminderId != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.tasks_has_reminder),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (task.reminderId != null) {
                Text(
                    text = stringResource(R.string.tasks_has_reminder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = onAddReminder) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = stringResource(R.string.tasks_add_reminder),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.tasks_delete),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}