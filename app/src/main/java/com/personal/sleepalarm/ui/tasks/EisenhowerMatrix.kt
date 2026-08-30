package com.personal.sleepalarm.ui.tasks

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.remainingWorkMillisOrNull
import com.personal.sleepalarm.ui.theme.AppAccentTone
import com.personal.sleepalarm.ui.theme.appAccents
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val VISIBLE_TASKS_PER_QUADRANT = 4

private enum class MatrixDragPhase { DRAGGING, LANDING, COMMITTING }

internal data class TaskAreaGeometry(
    val topLeftInRoot: Offset,
    val size: Size
)

private data class DropCommitExpectation(
    val target: TaskDropTarget,
    val targetIndex: Int?,
    val noOp: Boolean
)

private data class MatrixBallDrag(
    val task: TaskEntity,
    val source: TaskQuadrant,
    val originCenterInRoot: Offset,
    val centerInRoot: Offset,
    val phase: MatrixDragPhase,
    val dropPositionInMatrix: Offset? = null,
    val landingCenterInRoot: Offset = centerInRoot,
    val expectation: DropCommitExpectation? = null
)

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
    var overlayOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var taskAreas by remember { mutableStateOf<Map<TaskQuadrant, TaskAreaGeometry>>(emptyMap()) }
    var activeDrag by remember { mutableStateOf<MatrixBallDrag?>(null) }
    val latestTasks by rememberUpdatedState(tasks)

    fun clearDrag(taskId: Int) {
        if (activeDrag?.task?.id == taskId) {
            activeDrag = null
            onDraggingChanged(false)
        }
    }

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
        val tasksByQuadrant = mapOf(
            TaskQuadrant.NOW to nowTasks,
            TaskQuadrant.SCHEDULE to scheduleTasks,
            TaskQuadrant.DELEGATE to delegateTasks,
            TaskQuadrant.LET_GO to letGoTasks
        )
        val ballSizePx = with(density) { TASK_BALL_SIZE.toPx() }

        val startDrag: (TaskEntity, TaskQuadrant, Offset) -> Unit = { task, source, centerInRoot ->
            activeDrag = MatrixBallDrag(
                task = task,
                source = source,
                originCenterInRoot = centerInRoot,
                centerInRoot = centerInRoot,
                phase = MatrixDragPhase.DRAGGING
            )
            onDraggingChanged(true)
        }
        val dragBy: (Int, Offset) -> Unit = { taskId, amount ->
            activeDrag?.takeIf { it.task.id == taskId && it.phase == MatrixDragPhase.DRAGGING }
                ?.let { activeDrag = it.copy(centerInRoot = it.centerInRoot + amount) }
        }
        val finishDrag: (Int, Offset) -> Unit = finish@{ taskId, centerInRoot ->
            val drag = activeDrag?.takeIf {
                it.task.id == taskId && it.phase == MatrixDragPhase.DRAGGING
            } ?: return@finish
            val dropPosition = centerInRoot - matrixOriginInRoot
            val expectation = buildDropCommitExpectation(
                task = drag.task,
                source = drag.source,
                tasksByQuadrant = tasksByQuadrant,
                dropPosition = dropPosition,
                quadrantWidthPx = quadrantWidthPx,
                quadrantHeightPx = quadrantHeightPx
            )
            val landingCenter = calculateMeasuredLandingCenter(
                drag = drag,
                expectation = expectation,
                taskAreas = taskAreas,
                targetTaskCount = expectation.target.quadrant
                    ?.let { tasksByQuadrant[it].orEmpty().size }
                    ?: 0,
                ballSizePx = ballSizePx,
                quadrantWidthPx = quadrantWidthPx,
                quadrantHeightPx = quadrantHeightPx,
                matrixOriginInRoot = matrixOriginInRoot
            )
            activeDrag = drag.copy(
                centerInRoot = centerInRoot,
                phase = MatrixDragPhase.LANDING,
                dropPositionInMatrix = dropPosition,
                landingCenterInRoot = landingCenter,
                expectation = expectation
            )
        }

        val overlayCenter by animateOffsetAsState(
            targetValue = activeDrag?.let { drag ->
                if (drag.phase == MatrixDragPhase.DRAGGING) drag.centerInRoot else drag.landingCenterInRoot
            } ?: Offset.Zero,
            animationSpec = if (activeDrag?.phase == MatrixDragPhase.DRAGGING) {
                snap()
            } else {
                spring(dampingRatio = 0.88f, stiffness = 340f)
            },
            finishedListener = finished@{
                val drag = activeDrag?.takeIf { it.phase == MatrixDragPhase.LANDING }
                    ?: return@finished
                val expectation = drag.expectation ?: return@finished
                val dropPosition = drag.dropPositionInMatrix ?: return@finished
                if (expectation.noOp) {
                    clearDrag(drag.task.id)
                    return@finished
                }
                applyDrop(
                    task = drag.task,
                    source = drag.source,
                    sourceTasks = latestTasks.inQuadrant(drag.source),
                    dropPosition = dropPosition,
                    quadrantWidthPx = quadrantWidthPx,
                    quadrantHeightPx = quadrantHeightPx,
                    onMoveTask = onMoveTask,
                    onReorderTask = onReorderTask,
                    onCompleteTask = onCompleteTask
                )
                activeDrag = drag.copy(phase = MatrixDragPhase.COMMITTING)
            },
            label = "matrix_drag_overlay"
        )

        val committingDrag = activeDrag?.takeIf { it.phase == MatrixDragPhase.COMMITTING }
        LaunchedEffect(committingDrag?.task?.id, committingDrag?.phase) {
            val drag = committingDrag ?: return@LaunchedEffect
            withTimeoutOrNull(1_800L) {
                snapshotFlow { isDropCommitVisible(latestTasks, drag) }.first { it }
            }
            // Let the destination node complete one layout pass underneath the
            // overlay, then reveal it at exactly the same measured slot.
            delay(34L)
            clearDrag(drag.task.id)
        }

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
                    onAdd = { onCreateInQuadrant(TaskQuadrant.NOW) },
                    onOpenQuadrant = { onOpenQuadrant(TaskQuadrant.NOW) },
                    draggedTaskId = activeDrag?.task?.id,
                    onTaskAreaPositioned = { geometry ->
                        if (taskAreas[TaskQuadrant.NOW] != geometry) {
                            taskAreas = taskAreas + (TaskQuadrant.NOW to geometry)
                        }
                    },
                    onBallDragStart = startDrag,
                    onBallDragBy = dragBy,
                    onBallDragFinished = finishDrag,
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
                    onAdd = { onCreateInQuadrant(TaskQuadrant.SCHEDULE) },
                    onOpenQuadrant = { onOpenQuadrant(TaskQuadrant.SCHEDULE) },
                    draggedTaskId = activeDrag?.task?.id,
                    onTaskAreaPositioned = { geometry ->
                        if (taskAreas[TaskQuadrant.SCHEDULE] != geometry) {
                            taskAreas = taskAreas + (TaskQuadrant.SCHEDULE to geometry)
                        }
                    },
                    onBallDragStart = startDrag,
                    onBallDragBy = dragBy,
                    onBallDragFinished = finishDrag,
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
                    onAdd = { onCreateInQuadrant(TaskQuadrant.DELEGATE) },
                    onOpenQuadrant = { onOpenQuadrant(TaskQuadrant.DELEGATE) },
                    draggedTaskId = activeDrag?.task?.id,
                    onTaskAreaPositioned = { geometry ->
                        if (taskAreas[TaskQuadrant.DELEGATE] != geometry) {
                            taskAreas = taskAreas + (TaskQuadrant.DELEGATE to geometry)
                        }
                    },
                    onBallDragStart = startDrag,
                    onBallDragBy = dragBy,
                    onBallDragFinished = finishDrag,
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
                    onAdd = { onCreateInQuadrant(TaskQuadrant.LET_GO) },
                    onOpenQuadrant = { onOpenQuadrant(TaskQuadrant.LET_GO) },
                    draggedTaskId = activeDrag?.task?.id,
                    onTaskAreaPositioned = { geometry ->
                        if (taskAreas[TaskQuadrant.LET_GO] != geometry) {
                            taskAreas = taskAreas + (TaskQuadrant.LET_GO to geometry)
                        }
                    },
                    onBallDragStart = startDrag,
                    onBallDragBy = dragBy,
                    onBallDragFinished = finishDrag,
                    modifier = Modifier.weight(1f).height(quadrantHeight)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { overlayOriginInRoot = it.positionInRoot() }
                .zIndex(20f)
        ) {
            activeDrag?.let { drag ->
                val centerInMatrix = overlayCenter - matrixOriginInRoot
                val hoverTarget = calculateDropTargetAtPosition(
                    centerInMatrix,
                    quadrantWidthPx,
                    quadrantHeightPx
                )
                val overlayColor = hoverTarget.quadrant
                    ?.let { quadrantColors(it).ball }
                    ?: MaterialTheme.appAccents.other.color
                val localCenter = overlayCenter - overlayOriginInRoot
                val overlayScale by animateFloatAsState(
                    targetValue = if (drag.phase == MatrixDragPhase.COMMITTING) 1f else 1.08f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
                    label = "matrix_drag_overlay_scale"
                )
                TaskBallSurface(
                    task = drag.task,
                    color = overlayColor,
                    contentColor = readableContentColor(overlayColor),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .size(TASK_BALL_SIZE)
                        .graphicsLayer {
                            translationX = localCenter.x - ballSizePx / 2f
                            translationY = localCenter.y - ballSizePx / 2f
                            scaleX = overlayScale
                            scaleY = overlayScale
                        }
                )
            }
        }
    }
}

private fun buildDropCommitExpectation(
    task: TaskEntity,
    source: TaskQuadrant,
    tasksByQuadrant: Map<TaskQuadrant, List<TaskEntity>>,
    dropPosition: Offset,
    quadrantWidthPx: Float,
    quadrantHeightPx: Float
): DropCommitExpectation {
    val target = calculateDropTargetAtPosition(dropPosition, quadrantWidthPx, quadrantHeightPx)
    if (target.completed || target.quadrant == null) {
        return DropCommitExpectation(target = target, targetIndex = null, noOp = false)
    }
    val targetQuadrant = target.quadrant
    if (targetQuadrant != source) {
        val appendIndex = tasksByQuadrant[targetQuadrant].orEmpty().size
            .coerceIn(0, VISIBLE_TASKS_PER_QUADRANT - 1)
        return DropCommitExpectation(target, appendIndex, noOp = false)
    }

    val ordered = tasksByQuadrant[source].orEmpty()
    val currentIndex = ordered.indexOfFirst { it.id == task.id }
    val targetIndex = calculateReorderTargetIndex(
        source = source,
        taskCount = ordered.size,
        dropPosition = dropPosition,
        quadrantWidthPx = quadrantWidthPx,
        quadrantHeightPx = quadrantHeightPx
    )
    return DropCommitExpectation(
        target = target,
        targetIndex = targetIndex,
        noOp = currentIndex < 0 || currentIndex == targetIndex
    )
}

private fun calculateMeasuredLandingCenter(
    drag: MatrixBallDrag,
    expectation: DropCommitExpectation,
    taskAreas: Map<TaskQuadrant, TaskAreaGeometry>,
    targetTaskCount: Int,
    ballSizePx: Float,
    quadrantWidthPx: Float,
    quadrantHeightPx: Float,
    matrixOriginInRoot: Offset
): Offset {
    if (expectation.noOp) return drag.originCenterInRoot
    if (expectation.target.completed) {
        return drag.centerInRoot + Offset(0f, quadrantHeightPx * 0.22f)
    }
    val targetQuadrant = expectation.target.quadrant ?: return drag.originCenterInRoot
    val targetIndex = expectation.targetIndex ?: return drag.originCenterInRoot
    val geometry = taskAreas[targetQuadrant]
    if (geometry != null) {
        val countAfterDrop = if (targetQuadrant == drag.source) {
            targetTaskCount
        } else {
            targetTaskCount + 1
        }.coerceIn(1, VISIBLE_TASKS_PER_QUADRANT)
        return calculateTaskSlotCenter(geometry, countAfterDrop, targetIndex, ballSizePx)
    }

    // The area is normally measured before any gesture. Keep a safe fallback
    // for the very first frame after screen creation.
    val column = if (
        targetQuadrant == TaskQuadrant.SCHEDULE || targetQuadrant == TaskQuadrant.LET_GO
    ) 1 else 0
    val row = if (
        targetQuadrant == TaskQuadrant.DELEGATE || targetQuadrant == TaskQuadrant.LET_GO
    ) 1 else 0
    return matrixOriginInRoot + Offset(
        (column + 0.5f) * quadrantWidthPx,
        (row + 0.54f) * quadrantHeightPx
    )
}

/** Exact center produced by the matrix's two-column SpaceEvenly layout. */
internal fun calculateTaskSlotCenter(
    geometry: TaskAreaGeometry,
    taskCount: Int,
    targetIndex: Int,
    ballSizePx: Float
): Offset {
    val visibleCount = taskCount.coerceIn(1, VISIBLE_TASKS_PER_QUADRANT)
    val index = targetIndex.coerceIn(0, visibleCount - 1)
    val horizontalGap = ((geometry.size.width - ballSizePx * 2f) / 3f).coerceAtLeast(0f)
    val centerX = if (index % 2 == 0) {
        horizontalGap + ballSizePx / 2f
    } else {
        horizontalGap * 2f + ballSizePx * 1.5f
    }
    val rowCount = if (visibleCount <= 2) 1 else 2
    val row = index / 2
    val centerY = if (rowCount == 1) {
        geometry.size.height / 2f
    } else {
        val verticalGap = ((geometry.size.height - ballSizePx * 2f) / 3f).coerceAtLeast(0f)
        if (row == 0) {
            verticalGap + ballSizePx / 2f
        } else {
            verticalGap * 2f + ballSizePx * 1.5f
        }
    }
    return geometry.topLeftInRoot + Offset(centerX, centerY)
}

private fun isDropCommitVisible(tasks: List<TaskEntity>, drag: MatrixBallDrag): Boolean {
    val expectation = drag.expectation ?: return true
    if (expectation.target.completed) return tasks.none { it.id == drag.task.id && !it.isDone }
    val targetQuadrant = expectation.target.quadrant ?: return true
    val current = tasks.firstOrNull { it.id == drag.task.id } ?: return false
    if (current.matrixQuadrant != targetQuadrant.storageValue) return false
    if (targetQuadrant != drag.source) return true
    val ordered = tasks.inQuadrant(targetQuadrant)
    return ordered.indexOfFirst { it.id == drag.task.id } == expectation.targetIndex
}

private fun calculateReorderTargetIndex(
    source: TaskQuadrant,
    taskCount: Int,
    dropPosition: Offset,
    quadrantWidthPx: Float,
    quadrantHeightPx: Float
): Int {
    if (taskCount <= 1) return 0
    val sourceColumn = if (source == TaskQuadrant.SCHEDULE || source == TaskQuadrant.LET_GO) 1 else 0
    val sourceRow = if (source == TaskQuadrant.DELEGATE || source == TaskQuadrant.LET_GO) 1 else 0
    val localX = dropPosition.x - sourceColumn * quadrantWidthPx
    val localY = dropPosition.y - sourceRow * quadrantHeightPx
    val column = if (localX < quadrantWidthPx / 2f) 0 else 1
    val row = if (localY < quadrantHeightPx * 0.58f) 0 else 1
    return (row * 2 + column).coerceIn(0, taskCount - 1)
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
                val targetIndex = calculateReorderTargetIndex(
                    source,
                    ordered.size,
                    dropPosition,
                    quadrantWidthPx,
                    quadrantHeightPx
                )
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

/** One landing vector keeps both axes in the same animation and completion callback. */
internal fun calculateDropLandingOffset(
    source: TaskQuadrant,
    target: TaskDropTarget,
    dragOffset: Offset,
    baseCenterInMatrix: Offset,
    quadrantWidthPx: Float,
    quadrantHeightPx: Float
): Offset = when {
    target.completed -> dragOffset + Offset(0f, quadrantHeightPx * 0.22f)
    target.quadrant == source -> dragOffset
    target.quadrant != null -> {
        val column = if (
            target.quadrant == TaskQuadrant.SCHEDULE || target.quadrant == TaskQuadrant.LET_GO
        ) 1 else 0
        val row = if (
            target.quadrant == TaskQuadrant.DELEGATE || target.quadrant == TaskQuadrant.LET_GO
        ) 1 else 0
        Offset(
            (column + 0.5f) * quadrantWidthPx,
            (row + 0.54f) * quadrantHeightPx
        ) - baseCenterInMatrix
    }
    else -> dragOffset
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
    onAdd: () -> Unit,
    onOpenQuadrant: () -> Unit,
    draggedTaskId: Int?,
    onTaskAreaPositioned: (TaskAreaGeometry) -> Unit,
    onBallDragStart: (TaskEntity, TaskQuadrant, Offset) -> Unit,
    onBallDragBy: (Int, Offset) -> Unit,
    onBallDragFinished: (Int, Offset) -> Unit,
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
                    .onGloballyPositioned { coordinates ->
                        onTaskAreaPositioned(
                            TaskAreaGeometry(
                                topLeftInRoot = coordinates.positionInRoot(),
                                size = Size(
                                    coordinates.size.width.toFloat(),
                                    coordinates.size.height.toFloat()
                                )
                            )
                        )
                    }
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
                    .onGloballyPositioned { coordinates ->
                        onTaskAreaPositioned(
                            TaskAreaGeometry(
                                topLeftInRoot = coordinates.positionInRoot(),
                                size = Size(
                                    coordinates.size.width.toFloat(),
                                    coordinates.size.height.toFloat()
                                )
                            )
                        )
                    }
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
                            key(task.id) {
                                FloatingTaskBall(
                                    task = task,
                                    source = quadrant,
                                    baseColor = colors.ball,
                                    quadrantWidthPx = quadrantWidthPx,
                                    quadrantHeightPx = quadrantHeightPx,
                                    matrixOriginInRoot = matrixOriginInRoot,
                                    animationsEnabled = animationsEnabled,
                                    isHidden = draggedTaskId == task.id,
                                    onClick = { onOpenTask(task) },
                                    onDragStart = { center -> onBallDragStart(task, quadrant, center) },
                                    onDragBy = { amount -> onBallDragBy(task.id, amount) },
                                    onDragFinished = { center -> onBallDragFinished(task.id, center) }
                                )
                            }
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
    isHidden: Boolean,
    onClick: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDragBy: (Offset) -> Unit,
    onDragFinished: (Offset) -> Unit
) {
    var dragOffset by remember(task.id) { mutableStateOf(Offset.Zero) }
    var ballCenterInRoot by remember(task.id) { mutableStateOf(Offset.Zero) }
    var dragging by remember(task.id) { mutableStateOf(false) }
    var previousHover by remember(task.id) { mutableStateOf(source) }
    val latestMatrixOrigin by rememberUpdatedState(matrixOriginInRoot)
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDragBy by rememberUpdatedState(onDragBy)
    val latestOnDragFinished by rememberUpdatedState(onDragFinished)
    val haptics = LocalHapticFeedback.current
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
    val ambientFactor by animateFloatAsState(
        targetValue = if (dragging || isHidden) 0f else 1f,
        animationSpec = tween(durationMillis = 170),
        label = "task_ambient_factor"
    )
    TaskBallSurface(
        task = task,
        color = baseColor,
        contentColor = readableContentColor(baseColor),
        shadowElevation = 3.dp,
        onClick = onClick,
        modifier = Modifier
            .size(TASK_BALL_SIZE)
            .onGloballyPositioned { coordinates ->
                if (!dragging && !isHidden) {
                    val topLeft = coordinates.positionInRoot()
                    ballCenterInRoot = topLeft + Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                }
            }
            .graphicsLayer {
                translationX = floatX * ambientFactor
                translationY = floatY * ambientFactor
                alpha = if (isHidden) 0f else 1f
            }
            .pointerInput(task.id, source) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragOffset = Offset.Zero
                        dragging = true
                        latestOnDragStart(ballCenterInRoot)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount
                        latestOnDragBy(amount)
                        val hover = calculateDropTargetAtPosition(
                            ballCenterInRoot - latestMatrixOrigin + dragOffset,
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
                        dragging = false
                        // A system pointer cancellation commits the section under
                        // the ball instead of exposing a confusing undo action.
                        latestOnDragFinished(ballCenterInRoot + dragOffset)
                    },
                    onDragEnd = {
                        previousHover = source
                        dragging = false
                        latestOnDragFinished(ballCenterInRoot + dragOffset)
                    }
                )
            }
    )
}

@Composable
private fun TaskBallSurface(
    task: TaskEntity,
    color: Color,
    contentColor: Color,
    shadowElevation: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val content: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
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
                        tint = contentColor
                    )
                }
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
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = CircleShape,
            color = color,
            shadowElevation = shadowElevation,
            content = content
        )
    } else {
        Surface(
            modifier = modifier,
            shape = CircleShape,
            color = color,
            shadowElevation = shadowElevation,
            content = content
        )
    }
}

private fun List<TaskEntity>.inQuadrant(quadrant: TaskQuadrant): List<TaskEntity> =
    filter { it.matrixQuadrant == quadrant.storageValue }
        .sortedWith(compareBy<TaskEntity> { it.sortOrder }.thenByDescending { it.createdAt })

@Composable
private fun quadrantSummary(tasks: List<TaskEntity>): String {
    val remainingMinutes = tasks.mapNotNull { task ->
        task.remainingWorkMillisOrNull()?.let { (it + 59_999L) / 60_000L }
    }.sum()
    val overdue = tasks.count { (it.dueAtMillis ?: Long.MAX_VALUE) < System.currentTimeMillis() }
    return if (overdue > 0) {
        stringResource(R.string.task_quadrant_summary_overdue, tasks.size, remainingMinutes, overdue)
    } else {
        stringResource(R.string.task_quadrant_summary, tasks.size, remainingMinutes)
    }
}

private data class QuadrantColors(
    val container: Color,
    val ball: Color,
    val content: Color
)

@Composable
private fun quadrantColors(quadrant: TaskQuadrant): QuadrantColors {
    val accents = MaterialTheme.appAccents
    val tone = when (quadrant) {
        TaskQuadrant.NOW -> accents.urgent
        TaskQuadrant.SCHEDULE -> accents.study
        TaskQuadrant.DELEGATE -> accents.other
        TaskQuadrant.LET_GO -> accents.calm
    }
    return tone.toQuadrantColors()
}

@Composable
private fun readableContentColor(background: Color): Color {
    return MaterialTheme.appAccents.all.firstOrNull { it.color == background }?.onColor
        ?: MaterialTheme.appAccents.other.onColor
}

private fun AppAccentTone.toQuadrantColors() = QuadrantColors(
    container = container,
    ball = color,
    content = onContainer
)

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
