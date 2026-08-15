package com.personal.sleepalarm.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.LibraryItemType

/**
 * Экран графа библиотеки.
 *
 * Узлы — элементы, рёбра — общие теги. Узлы можно таскать пальцем;
 * короткий тап по узлу открывает элемент.
 */
@Composable
fun LibraryGraphScreen(
    onBack: () -> Unit,
    onOpenItem: (Int) -> Unit,
    viewModel: LibraryGraphViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val textMeasurer = rememberTextMeasurer()

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var draggedId by remember { mutableStateOf<Int?>(null) }
    var moved by remember { mutableStateOf(0f) }

    val edgeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val labelColor = MaterialTheme.colorScheme.onBackground

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(state.nodes) {
                    detectDragGestures(
                        onDragStart = { touch ->
                            val center = Offset(
                                canvasSize.width / 2f,
                                canvasSize.height / 2f
                            )
                            val world = touch - center
                            draggedId = viewModel.nodeAt(world)
                            moved = 0f
                            draggedId?.let { viewModel.startDrag(it) }
                        },
                        onDrag = { change, drag ->
                            moved += drag.getDistance()
                            draggedId?.let { id ->
                                val center = Offset(
                                    canvasSize.width / 2f,
                                    canvasSize.height / 2f
                                )
                                viewModel.dragTo(id, change.position - center)
                            }
                        },
                        onDragEnd = {
                            draggedId?.let { id ->
                                // Короткий тап без движения — открыть элемент.
                                if (moved < 12f) onOpenItem(id)
                            }
                            viewModel.endDrag()
                            draggedId = null
                        },
                        onDragCancel = {
                            viewModel.endDrag()
                            draggedId = null
                        }
                    )
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val pos = state.positions

            // Рёбра.
            state.edges.forEach { edge ->
                val a = pos[edge.a] ?: return@forEach
                val b = pos[edge.b] ?: return@forEach
                drawLine(
                    color = edgeColor,
                    start = center + a,
                    end = center + b,
                    strokeWidth = 2f
                )
            }

            // Узлы.
            state.nodes.forEach { node ->
                val p = pos[node.id] ?: return@forEach
                val c = center + p
                val color = colorFor(node.type)

                drawCircle(
                    color = color,
                    radius = LibraryGraphViewModel.NODE_RADIUS,
                    center = c
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f),
                    radius = LibraryGraphViewModel.NODE_RADIUS - 6f,
                    center = c
                )

                // Подпись под узлом.
                val label = node.title.take(14)
                val layout = textMeasurer.measure(
                    text = label,
                    style = TextStyle(fontSize = 11.sp, color = labelColor)
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = c.x - layout.size.width / 2f,
                        y = c.y + LibraryGraphViewModel.NODE_RADIUS + 4f
                    )
                )
            }
        }

        // Кнопка назад + заголовок.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.action_back)
                )
            }
            Text(
                text = stringResource(R.string.library_graph),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (state.nodes.isEmpty()) {
            Text(
                text = stringResource(R.string.library_empty),
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun colorFor(type: LibraryItemType): Color = when (type) {
    LibraryItemType.BOOK -> Color(0xFF63D8C2)   // teal
    LibraryItemType.MOVIE -> Color(0xFFFFB86B) // amber
    LibraryItemType.MUSIC -> Color(0xFF7FB3FF) // blue
}