package com.personal.sleepalarm.ui.components

import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

const val WHEEL_ITEM_HEIGHT = 52
const val WHEEL_VISIBLE_COUNT = 5
const val WHEEL_PAD_ITEMS = (WHEEL_VISIBLE_COUNT - 1) / 2

/**
 * Колесо-барабан в стиле iOS: 3 ряда, линии-разделители,
 * 3D-наклон и проявление из прозрачности к центру.
 */
@Composable
fun NumberWheel(
    items: List<String>,
    externalIndex: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val itemHeightPx = with(density) { WHEEL_ITEM_HEIGHT.dp.toPx() }
    val containerHeight = (WHEEL_ITEM_HEIGHT * WHEEL_VISIBLE_COUNT).dp
    val containerCenterPx = with(density) { containerHeight.toPx() } / 2f

    val listState = rememberLazyListState()
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(externalIndex) {
        val target = externalIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        if (!initialized) {
            listState.scrollToItem(target)
            initialized = true
        } else {
            listState.animateScrollToItem(target)
        }
    }

    val selectedIndex by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
                .minByOrNull { abs(it.offset + it.size / 2f - containerCenterPx) }
                ?.index
                ?.minus(WHEEL_PAD_ITEMS)
                ?.coerceIn(0, (items.size - 1).coerceAtLeast(0))
                ?: 0
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val target = selectedIndex
            try {
                listState.animateScrollToItem(target)
            } catch (_: CancellationException) {
            }
            onIndexChange(target)
        }
    }

    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    Box(
        modifier = modifier.height(containerHeight),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(WHEEL_ITEM_HEIGHT.dp)) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(1.dp)
                    .background(lineColor)
            )
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp)
                    .background(lineColor)
            )
        }

        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            modifier = Modifier.fillMaxSize()
        ) {
            items(WHEEL_PAD_ITEMS) {
                Spacer(Modifier.height(WHEEL_ITEM_HEIGHT.dp))
            }

            items(items.size) { index ->
                val itemInfo = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == index + WHEEL_PAD_ITEMS }
                val itemCenter = (itemInfo?.offset ?: 0) +
                        (itemInfo?.size ?: WHEEL_ITEM_HEIGHT) / 2f
                val fraction = ((itemCenter - containerCenterPx) / itemHeightPx)
                    .coerceIn(-2f, 2f)

                val scale = (1f - abs(fraction) * 0.15f).coerceIn(0.7f, 1f)
                val itemAlpha = (1f - abs(fraction) / 1.8f).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WHEEL_ITEM_HEIGHT.dp)
                        .graphicsLayer {
                            rotationX = fraction * 35f
                            alpha = itemAlpha
                        }
                        .scale(scale),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index],
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 26.sp,
                            fontWeight = if (abs(fraction) < 0.5f) FontWeight.SemiBold
                            else FontWeight.Normal
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                }
            }

            items(WHEEL_PAD_ITEMS) {
                Spacer(Modifier.height(WHEEL_ITEM_HEIGHT.dp))
            }
        }
    }
}