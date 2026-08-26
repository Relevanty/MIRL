package com.personal.sleepalarm.ui.tasks

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.TaskEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val VISIBLE_TASKS_PER_QUADRANT = 4

@Composable
internal fun EisenhowerMatrix(
    tasks: List<TaskEntity>,
    onOpenTask: (TaskEntity) -> Unit,
    onMoveTask: (TaskEntity, TaskQuadrant) -> Unit,
    onReorderTask: (TaskEntity, Int) -> Unit = { _, _ -> },
    onCompleteTask: (TaskEntity) -> Unit,
    onCreateInQuadrant: (TaskQuadrant) -> Unit,
    onOpenQuadrant: (TaskQuadrant) -> Unit = {},
    onDraggingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val animationsEnabled = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }

    var matrixOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { matrixOriginInRoot = it.positionInRoot() }
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(28.dp))
            .padding(4.dp)
    ) {
        val quadrantWidth = maxWidth / 2
        val quadrantHeight = maxHeight / 2
        val density = LocalDensity.current
        val quadrantWidthPx = with(density) { quadrantWidth.toPx() }
        val quadrantHeightPx = with(density) { quadrantHeight.toPx() }
        val nowTasks = tasks.inQuadrant(TaskQuadrant.NOW)
        val scheduleTasks = tasks.inQuadrant(TaskQuadrant.SCHEDULE)
        val delegateTasks = tasks.inQuadrant(TaskQuadrant.DELEGATE)
        val letGoTasks = tasks.inQuadrant(TaskQuadrant.LET_GO)

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f)) {
                MatrixQuadrant(
                    quadrant = TaskQuadrant.NOW,
                    tasks = nowTasks,
                    quadrantWidthPx = quadrantWidthPx,
                    quadrantHeightPx = quadrantHeightPx,
                    matrixOriginInRoot = matrixOriginInRoot,
                    animationsEnabled = animationsEnabled,
                    onOpenTask = onOpenTask,
                    onDrop = { task, offset ->
                        applyDrop(task, TaskQuadrant.NOW, nowTasks, offset, quadrantWidthPx, quadrantHeightPx, onMoveTask, onReorderTask, onCompleteTask)
                    },
                    onAdd = { onCreateInQuadrant(TaskQuadrant.NOW) },
                    onOpenQuadrant = { onOpenQuadrant(TaskQuadrant.NOW) },
                    onDraggingChanged = onDraggingChanged,
                    modifier = Modifier.weight(1f).height(quadrantHeight)
                )
                MatrixQuadrant(
                    quadrant = TaskQuadrant.SCHEDULE,
                    tasks = scheduleTasks,
                    quadrantWidthPx = quadrantWidthPx,
                    quadrantHeightPx = quadrantHeightPx,
                    matrixOriginInRoot = matrixOriginInRoot,
                    animationsEnabled = animationsEnabled,
                    onOpenTask = onOpenTask,
                    onDrop = { task, offset ->
                        applyDrop(task, TaskQuadrant.SCHEDULE, scheduleTasks, offset, quadrantWidthPx, quadrantHeightPx, onMoveTask, onReorderTask, onCompleteTask)
                    },
                    onAdd = { onCreateInQuadrant(TaskQuadrant.SCHEDULE) },
                    onOpenQuadrant = { onOpenQuadrant(TaskQuadrant.SCHEDULE) },
                    onDraggingChanged = onDraggingChanged,
                    modifier = Modifier.weight(1f).height(quadrantHeight)
                )
            }
            Row(Modifier.weight(1f)) {
                MatrixQuadrant(
                    quadrant = TaskQuadrant.DELEGATE,
                    tasks = delegateTasks,
                    quadrantWidthPx = quadrantWidthPx,
                    quadrantHeightPx = quadrantHeightPx,
                    matrixOriginInRoot = matrixOriginInRoot,
                    animationsEnabled = animationsEnabled,
                    onOpenTask = onOpenTask,
                    onDrop = { task, offset ->
                        applyDrop(task, TaskQuadrant.DELEGATE, delegateTasks, offset, quadrantWidthPx, quadrantHeightPx, onMoveTask, onReorderTask, onCompleteTask)
                    },
                    onAdd = { onCreateInQuadrant(TaskQuadrant.DELEGATE) },
                    onOpenQuadrant = { onOpenQuadrant(TaskQuadrant.DELEGATE) },
                    onDraggingChanged = onDraggingChanged,
                    modifier = Modifier.weight(1f).height(quadrantHeight)
                )
                MatrixQuadrant(
                    quadrant = TaskQuadrant.LET_GO,
                    tasks = letGoTasks,
                    quadrantWidthPx = quadrantWidthPx,
                    quadrantHeightPx = quadrantHeightPx,
                    matrixOriginInRoot = matrixOriginInRoot,
                    animationsEnabled = animationsEnabled,
                    onOpenTask = onOpenTask,
                    onDrop = { task, offset ->
                        applyDrop(task, TaskQuadrant.LET_GO, letGoTasks, offset, quadrantWidthPx, quadrantHeightPx, onMoveTask, onReorderTask, onCompleteTask)
                    },
                    onAdd = { onCreateInQuadrant(TaskQuadrant.LET_GO) },
                    onOpenQuadrant = { onOpenQuadrant(TaskQuadrant.LET_GO) },
                    onDraggingChanged = onDraggingChanged,
                    modifier = Modifier.weight(1f).height(quadrantHeight)
                )
            }
        }
    }
}

private fun applyDrop(
    task: TaskEntity,
    source: TaskQuadrant,
    sourceTasks: List<TaskEntity>,
    dropPosition: Offset,
    quadrantWidthPx: Float,
    quadrantHeightPx: Float,
    onMoveTask: (TaskEntity, TaskQuadrant) -> Unit,
    onReorderTask: (TaskEntity, Int) -> Unit,
    onCompleteTask: (TaskEntity) -> Unit
) {
    val result = calculateDropTargetAtPosition(dropPosition, quadrantWidthPx, quadrantHeightPx)
    if (result.completed) onCompleteTask(task)
    else result.quadrant?.let { target ->
        if (target == source) {
            val ordered = sourceTasks.sortedWith(compareBy<TaskEntity> { it.sortOrder }.thenByDescending { it.createdAt })
            val current = ordered.indexOfFirst { it.id == task.id }
            if (current >= 0) {
                val sourceColumn = if (source == TaskQuadrant.SCHEDULE || source == TaskQuadrant.LET_GO) 1 else 0
                val sourceRow = if (source == TaskQuadrant.DELEGATE || source == TaskQuadrant.LET_GO) 1 else 0
                val localX = dropPosition.x - sourceColumn * quadrantWidthPx
                val localY = dropPosition.y - sourceRow * quadrantHeightPx
                val column = if (localX < quadrantWidthPx / 2f) 0 else 1
                val row = if (localY < quadrantHeightPx * 0.58f) 0 else 1
                val targetIndex = (row * 2 + column).coerceIn(0, ordered.lastIndex)
                onReorderTask(task, targetIndex)
            }
        } else {
            onMoveTask(task, target)
        }
    }
}

internal data class TaskDropTarget(val quadrant: TaskQuadrant?, val completed: Boolean)

/** Определяет сектор по реальному центру перетаскиваемого шарика в координатах матрицы. */
internal fun calculateDropTargetAtPosition(
    position: Offset,
    quadrantWidthPx: Float,
    quadrantHeightPx: Float
): TaskDropTarget {
    if (quadrantWidthPx <= 0f || quadrantHeightPx <= 0f) return TaskDropTarget(null, false)
    if (position.y >= quadrantHeightPx * 1.82f) return TaskDropTarget(null, true)
    val column = (position.x / quadrantWidthPx).toInt().coerceIn(0, 1)
    val row = (position.y / quadrantHeightPx).toInt().coerceIn(0, 1)
    return TaskDropTarget(
        quadrant = when (row to column) {
            0 to 0 -> TaskQuadrant.NOW
            0 to 1 -> TaskQuadrant.SCHEDULE
            1 to 0 -> TaskQuadrant.DELEGATE
            else -> TaskQuadrant.LET_GO
        },
        completed = false
    )
}

/** Чистая геометрия drop-зон; отдельно тестируется без Compose. */
internal fun calculateDropTarget(
    source: TaskQuadrant,
    offset: Offset,
    quadrantWidthPx: Float,
    quadrantHeightPx: Float
): TaskDropTarget {
    if (quadrantWidthPx <= 0f || quadrantHeightPx <= 0f) return TaskDropTarget(source, false)
    val sourceColumn = if (source == TaskQuadrant.SCHEDULE || source == TaskQuadrant.LET_GO) 1 else 0
    val sourceRow = if (source == TaskQuadrant.DELEGATE || source == TaskQuadrant.LET_GO) 1 else 0
    val globalCenterX = (sourceColumn + 0.5f) * quadrantWidthPx + offset.x
    val globalCenterY = (sourceRow + 0.5f) * quadrantHeightPx + offset.y

    if (globalCenterY >= quadrantHeightPx * 2f) return TaskDropTarget(null, true)

    val column = (globalCenterX / quadrantWidthPx).toInt().coerceIn(0, 1)
    val row = (globalCenterY / quadrantHeightPx).toInt().coerceIn(0, 1)
    val target = when (row to column) {
        0 to 0 -> TaskQuadrant.NOW
        0 to 1 -> TaskQuadrant.SCHEDULE
        1 to 0 -> TaskQuadrant.DELEGATE
        else -> TaskQuadrant.LET_GO
    }
    return TaskDropTarget(target, false)
}

@Composable
private fun MatrixQuadrant(
    quadrant: TaskQuadrant,
    tasks: List<TaskEntity>,
    quadrantWidthPx: Float,
    quadrantHeightPx: Float,
    matrixOriginInRoot: Offset,
    animationsEnabled: Boolean,
    onOpenTask: (TaskEntity) -> Unit,
    onDrop: (TaskEntity, Offset) -> Unit,
    onAdd: () -> Unit,
    onOpenQuadrant: () -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = quadrantColors(quadrant)
    Column(
        modifier = modifier
            .padding(2.dp)
            .background(colors.container.copy(alpha = 0.78f), RoundedCornerShape(24.dp))
            .padding(horizontal = 5.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Заголовок квадранта — полноценная точка входа в его список.
                .clickable(onClick = onOpenQuadrant),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(25.dp).background(colors.ball.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(quadrantIcon(quadrant), null, Modifier.size(15.dp), tint = colors.content)
            }
            Spacer(Modifier.width(5.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    quadrant.displayName(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.content,
                    maxLines = 1
                )
                Text(
                    quadrantSummary(tasks),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = colors.content.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onAdd, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Add, stringResource(R.string.tasks_add), Modifier.size(17.dp), tint = colors.content)
            }
        }

        Spacer(Modifier.height(3.dp))
        val visibleTasks = tasks.take(VISIBLE_TASKS_PER_QUADRANT)
        if (visibleTasks.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(onClick = onOpenQuadrant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.task_quadrant_empty),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.content.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // Открытие полного списка происходит по свободной области.
                    // Нажатия на сами шарики перехватывает FloatingTaskBall.
                    .clickable(onClick = onOpenQuadrant),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                visibleTasks.chunked(2).forEach { rowTasks ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowTasks.forEach { task ->
                            FloatingTaskBall(
                                task = task,
                                source = quadrant,
                                baseColor = colors.ball,
                                quadrantWidthPx = quadrantWidthPx,
                                quadrantHeightPx = quadrantHeightPx,
                                matrixOriginInRoot = matrixOriginInRoot,
                                animationsEnabled = animationsEnabled,
                                onClick = { onOpenTask(task) },
                                onDraggingChanged = onDraggingChanged,
                                onDrop = { onDrop(task, it) }
                            )
                        }
                        if (rowTasks.size == 1 && tasks.size <= VISIBLE_TASKS_PER_QUADRANT) {
                            Spacer(Modifier.size(TASK_BALL_SIZE))
                        }
                    }
                }
            }
            if (tasks.size > VISIBLE_TASKS_PER_QUADRANT) {
                Text(
                    "+${tasks.size - VISIBLE_TASKS_PER_QUADRANT}",
                    modifier = Modifier.align(Alignment.End).padding(end = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.content,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FloatingTaskBall(
    task: TaskEntity,
    source: TaskQuadrant,
    baseColor: Color,
    quadrantWidthPx: Float,
    quadrantHeightPx: Float,
    matrixOriginInRoot: Offset,
    animationsEnabled: Boolean,
    onClick: () -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
    onDrop: (Offset) -> Unit
) {
    var dragOffset by remember(task.id) { mutableStateOf(Offset.Zero) }
    var settling by remember(task.id) { mutableStateOf(false) }
    var landingTarget by remember(task.id) { mutableStateOf(Offset.Zero) }
    var pendingDropPosition by remember(task.id) { mutableStateOf<Offset?>(null) }
    var ballCenterInRoot by remember(task.id) { mutableStateOf(Offset.Zero) }
    var dragging by remember(task.id) { mutableStateOf(false) }
    var previousHover by remember(task.id) { mutableStateOf(source) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val transition = rememberInfiniteTransition(label = "task_${task.id}_float")
    val floatY by transition.animateFloat(
        initialValue = if (animationsEnabled) -3.5f else 0f,
        targetValue = if (animationsEnabled) 3.5f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400 + (task.id % 5) * 260),
            repeatMode = RepeatMode.Reverse
        ),
        label = "task_float_y"
    )
    val floatX by transition.animateFloat(
        initialValue = if (animationsEnabled) -2.4f else 0f,
        targetValue = if (animationsEnabled) 2.4f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3100 + (task.id % 4) * 290),
            repeatMode = RepeatMode.Reverse
        ),
        label = "task_float_x"
    )
    val settleX by animateFloatAsState(
        targetValue = if (settling) landingTarget.x else dragOffset.x,
        animationSpec = if (settling) tween(durationMillis = 380) else snap(),
        label = "task_settle_x"
    )
    val settleY by animateFloatAsState(
        targetValue = if (settling) landingTarget.y else dragOffset.y,
        animationSpec = if (settling) tween(durationMillis = 380) else snap(),
        finishedListener = {
            if (settling) {
                val droppedAt = pendingDropPosition
                dragOffset = landingTarget
                settling = false
                dragging = false
                pendingDropPosition = null
                droppedAt?.let(onDrop)
                onDraggingChanged(false)
                scope.launch {
                    delay(140)
                    dragOffset = Offset.Zero
                    landingTarget = Offset.Zero
                }
            }
        },
        label = "task_settle_y"
    )
    val offset = if (settling) Offset(settleX, settleY) else dragOffset
    val baseCenterInMatrix = ballCenterInRoot - matrixOriginInRoot
    val currentCenterInMatrix = baseCenterInMatrix + offset
    val hoverTarget = calculateDropTargetAtPosition(currentCenterInMatrix, quadrantWidthPx, quadrantHeightPx)
    val liveColor = hoverTarget.quadrant?.let { quadrantColors(it).ball } ?: MaterialTheme.colorScheme.secondary

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(TASK_BALL_SIZE)
            .onGloballyPositioned { coordinates ->
                if (!dragging && !settling) {
                    val topLeft = coordinates.positionInRoot()
                    ballCenterInRoot = topLeft + Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                }
            }
            .graphicsLayer {
                translationX = offset.x + floatX
                translationY = offset.y + floatY
                scaleX = if (offset == Offset.Zero) 1f else 1.07f
                scaleY = if (offset == Offset.Zero) 1f else 1.07f
            }
            .pointerInput(task.id, source) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        onDraggingChanged(true)
                        if (settling) {
                            dragOffset = offset
                            settling = false
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount
                        val hover = calculateDropTargetAtPosition(
                            baseCenterInMatrix + dragOffset,
                            quadrantWidthPx,
                            quadrantHeightPx
                        ).quadrant
                        if (hover != null && hover != previousHover) {
                            previousHover = hover
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragCancel = {
                        previousHover = source
                        landingTarget = Offset.Zero
                        pendingDropPosition = null
                        settling = true
                    },
                    onDragEnd = {
                        val droppedAt = baseCenterInMatrix + dragOffset
                        val target = calculateDropTargetAtPosition(droppedAt, quadrantWidthPx, quadrantHeightPx)
                        pendingDropPosition = droppedAt
                        landingTarget = when {
                            target.completed -> dragOffset + Offset(0f, quadrantHeightPx * 0.22f)
                            target.quadrant != null && target.quadrant != source -> {
                                val column = if (target.quadrant == TaskQuadrant.SCHEDULE || target.quadrant == TaskQuadrant.LET_GO) 1 else 0
                                val row = if (target.quadrant == TaskQuadrant.DELEGATE || target.quadrant == TaskQuadrant.LET_GO) 1 else 0
                                Offset(
                                    (column + 0.5f) * quadrantWidthPx,
                                    (row + 0.54f) * quadrantHeightPx
                                ) - baseCenterInMatrix
                            }
                            else -> Offset.Zero
                        }
                        previousHover = source
                        settling = true
                    }
                )
            },
        shape = CircleShape,
            color = if (offset == Offset.Zero) baseColor else liveColor,
            shadowElevation = if (offset == Offset.Zero) 3.dp else 10.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (task.imagePath != null) {
                LocalTaskImage(path = task.imagePath, modifier = Modifier.size(50.dp), circular = true)
            } else {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(25.dp),
                    tint = readableContentColor(if (offset == Offset.Zero) baseColor else liveColor)
                )
            }
        }
    }
}

private fun List<TaskEntity>.inQuadrant(quadrant: TaskQuadrant): List<TaskEntity> =
    filter { it.matrixQuadrant == quadrant.storageValue }
        .sortedWith(compareBy<TaskEntity> { it.sortOrder }.thenByDescending { it.createdAt })

@Composable
private fun quadrantSummary(tasks: List<TaskEntity>): String {
    val remainingMinutes = tasks.sumOf { task ->
        val budget = task.workBudgetMinutes.takeIf { it > 0 } ?: task.estimatedMinutes
        (budget - task.spentMillis / 60_000L).coerceAtLeast(0L)
    }
    val overdue = tasks.count { (it.dueAtMillis ?: Long.MAX_VALUE) < System.currentTimeMillis() }
    return if (overdue > 0) {
        stringResource(R.string.task_quadrant_summary_overdue, tasks.size, remainingMinutes, overdue)
    } else {
        stringResource(R.string.task_quadrant_summary, tasks.size, remainingMinutes)
    }
}

private data class QuadrantColors(val container: Color, val ball: Color, val content: Color)

@Composable
private fun quadrantColors(quadrant: TaskQuadrant): QuadrantColors {
    val scheme = MaterialTheme.colorScheme
    return when (quadrant) {
        TaskQuadrant.NOW -> QuadrantColors(scheme.errorContainer, scheme.error, scheme.onErrorContainer)
        TaskQuadrant.SCHEDULE -> QuadrantColors(scheme.primaryContainer, scheme.primary, scheme.onPrimaryContainer)
        TaskQuadrant.DELEGATE -> QuadrantColors(scheme.tertiaryContainer, scheme.tertiary, scheme.onTertiaryContainer)
        TaskQuadrant.LET_GO -> QuadrantColors(scheme.surfaceContainerHighest, scheme.secondary, scheme.onSurfaceVariant)
    }
}

@Composable
private fun readableContentColor(background: Color): Color {
    val scheme = MaterialTheme.colorScheme
    return if (background == scheme.error) scheme.onError
    else if (background == scheme.primary) scheme.onPrimary
    else if (background == scheme.tertiary) scheme.onTertiary
    else scheme.onSecondary
}

private fun quadrantIcon(quadrant: TaskQuadrant) = when (quadrant) {
    TaskQuadrant.NOW -> Icons.Default.Bolt
    TaskQuadrant.SCHEDULE -> Icons.Default.Event
    TaskQuadrant.DELEGATE -> Icons.Default.Group
    TaskQuadrant.LET_GO -> Icons.Default.Spa
}

@Composable
private fun quadrantSubtitle(quadrant: TaskQuadrant): String = when (quadrant) {
    TaskQuadrant.NOW -> stringResource(R.string.task_quadrant_now_subtitle)
    TaskQuadrant.SCHEDULE -> stringResource(R.string.task_quadrant_schedule_subtitle)
    TaskQuadrant.DELEGATE -> stringResource(R.string.task_quadrant_delegate_subtitle)
    TaskQuadrant.LET_GO -> stringResource(R.string.task_quadrant_let_go_subtitle)
}

private val TASK_BALL_SIZE: Dp = 66.dp
