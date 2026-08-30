package com.personal.sleepalarm.ui.stats

import com.personal.sleepalarm.ui.theme.appAccents

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.ProjectEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.calculator.ActivityProgressCalculator
import com.personal.sleepalarm.domain.model.effectiveWorkBudgetMinutes
import com.personal.sleepalarm.domain.model.primaryLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.abs

data class ProductivityStatsState(
    val tasks: List<TaskEntity> = emptyList(),
    val projects: List<ProjectEntity> = emptyList(),
    val activities: List<ActivityRecordEntity> = emptyList()
)

class ProductivityStatsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application.applicationContext)

    val state: StateFlow<ProductivityStatsState> = combine(
        database.taskDao().observeAll(),
        database.projectDao().observeAll(),
        database.activityRecordDao().observeAll()
    ) { tasks, projects, activities ->
        ProductivityStatsState(tasks, projects, activities)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ProductivityStatsState()
    )
}

@Composable
fun ProductivityStatsContent(
    viewModel: ProductivityStatsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDone by remember { mutableStateOf(false) }
    val regularTasks = state.tasks.filterNot { it.isMorningRoutine }
    val shownTasks = regularTasks.filter { it.isDone == showDone }
        .sortedWith(compareBy<TaskEntity> { it.dueAtMillis ?: Long.MAX_VALUE }.thenBy { it.sortOrder })
    val now = System.currentTimeMillis()
    val completed = regularTasks.count(TaskEntity::isDone)
    val overdue = regularTasks.count { !it.isDone && (it.dueAtMillis ?: Long.MAX_VALUE) < now }
    val plannedMinutes = regularTasks.sumOf(::taskBudgetMinutes)
    val regularTaskIds = regularTasks.map(TaskEntity::id).toSet()
    val countedActivities = state.activities.filter(ActivityRecordEntity::countsTowardProgress)
    val taskActivities = countedActivities.filter { it.taskId in regularTaskIds }
    val actualMinutes = ActivityProgressCalculator.uniqueCountedMillis(taskActivities) / 60_000L
    val completedWithBudget = regularTasks.filter { it.isDone && taskBudgetMinutes(it) > 0 }
    val averageError = completedWithBudget.map { task ->
        val actual = ActivityProgressCalculator.countedMillis(
            countedActivities.filter { it.taskId == task.id }
        ) / 60_000.0
        abs(actual - taskBudgetMinutes(task)) / taskBudgetMinutes(task) * 100.0
    }.average().takeUnless(Double::isNaN)?.toInt() ?: 0

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.appAccents.work.container,
            contentColor = MaterialTheme.appAccents.work.onContainer
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Задачи и проекты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Выполнено $completed · просрочено $overdue")
                Text("План ${formatMinutes(plannedMinutes.toLong())} · факт ${formatMinutes(actualMinutes)}")
                Text("Средняя ошибка оценки завершённых задач: $averageError%", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (state.projects.isNotEmpty()) {
            Text("Проекты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            state.projects.filterNot(ProjectEntity::isArchived).forEach { project ->
                val projectTasks = regularTasks.filter { it.projectId == project.id }
                val budget = project.workBudgetMinutes.takeIf { it > 0 }
                    ?: projectTasks.sumOf(::taskBudgetMinutes)
                val projectTaskIds = projectTasks.map(TaskEntity::id).toSet()
                val spent = ActivityProgressCalculator.countedMillis(
                    countedActivities
                        .filter { it.projectId == project.id || it.taskId in projectTaskIds }
                        .distinctBy(ActivityRecordEntity::id)
                )
                StatCard(
                    title = project.title,
                    subtitle = "${projectTasks.count(TaskEntity::isDone)}/${projectTasks.size} задач · ${formatMinutes(spent / 60_000L)} из ${formatMinutes(budget.toLong())}",
                    progress = if (budget > 0) (spent / 60_000f / budget).coerceIn(0f, 1f) else 0f
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showDone, onClick = { showDone = false }, label = { Text("В работе") })
            FilterChip(selected = showDone, onClick = { showDone = true }, label = { Text("Завершённые") })
        }
        shownTasks.take(20).forEach { task ->
            val activities = state.activities.filter { it.taskId == task.id }
            val actual = ActivityProgressCalculator.countedMillis(activities)
            val budget = taskBudgetMinutes(task)
            val remaining = (budget * 60_000L - actual).coerceAtLeast(0L)
            val timers = activities.count { it.source == "TIMER" }
            val manual = activities.count { it.source == "MANUAL" }
            val title = task.primaryLabel()
            StatCard(
                title = title,
                subtitle = if (budget > 0) {
                    "План ${formatMinutes(budget.toLong())} · факт ${formatMinutes(actual / 60_000L)} · осталось ${formatMinutes(remaining / 60_000L)} · $timers фокусов · $manual вручную · квадрат ${task.matrixQuadrant}"
                } else {
                    stringResource(
                        R.string.daily_focus_stats_unlimited,
                        formatMinutes(actual / 60_000L),
                        timers,
                        manual,
                        task.matrixQuadrant
                    )
                },
                progress = if (budget > 0) (actual / 60_000f / budget).coerceIn(0f, 1f) else 0f
            )
        }
    }
}

@Composable
private fun StatCard(title: String, subtitle: String, progress: Float) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun taskBudgetMinutes(task: TaskEntity): Int =
    task.effectiveWorkBudgetMinutes()

private fun formatMinutes(minutes: Long): String {
    val safe = minutes.coerceAtLeast(0)
    return if (safe >= 60) "${safe / 60} ч ${safe % 60} мин" else "$safe мин"
}
