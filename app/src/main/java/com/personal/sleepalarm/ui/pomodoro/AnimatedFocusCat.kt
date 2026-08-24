package com.personal.sleepalarm.ui.pomodoro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class FocusCatMood {
    IDLE,
    RESET,
    READY,
    FOCUS,
    PAUSED,
    REST,
    CELEBRATE
}

/**
 * Стабильный анимированный ASCII-кот из прежней визуальной системы Помодоро.
 * Каждый кадр помещается на одинаковый холст, поэтому моргание и движение не
 * меняют размер или центр персонажа.
 */
@Composable
fun AnimatedFocusCat(
    mood: FocusCatMood,
    modifier: Modifier = Modifier,
    onInteract: (() -> Unit)? = null
) {
    var frame by remember(mood) { mutableIntStateOf(0) }
    LaunchedEffect(mood) {
        while (true) {
            delay(
                when (mood) {
                    FocusCatMood.FOCUS -> 1_500L
                    FocusCatMood.REST -> 520L
                    FocusCatMood.CELEBRATE -> 650L
                    else -> 2_100L
                }
            )
            frame = (frame + 1) % 4
        }
    }

    val transition = rememberInfiniteTransition(label = "focusCat")
    val breath by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "catBreath"
    )
    val roam by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "catRoam"
    )
    val jumpProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_050, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "catJump"
    )

    val isSleeping = mood == FocusCatMood.IDLE || mood == FocusCatMood.RESET
    val sleepZProgress = remember { Animatable(0f) }
    var sleepZIsBig by remember { mutableStateOf(false) }
    var sleepZBaseSize by remember { mutableFloatStateOf(16f) }
    var sleepZAmplitude by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(isSleeping) {
        sleepZProgress.snapTo(0f)
        if (!isSleeping) return@LaunchedEffect

        while (true) {
            sleepZProgress.snapTo(0f)
            sleepZProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(3_200, easing = LinearEasing)
            )
            delay(kotlin.random.Random.nextLong(4_000L, 10_001L))
            sleepZIsBig = !sleepZIsBig
            sleepZBaseSize = if (sleepZBaseSize >= 30f) 16f else sleepZBaseSize + 3f
            sleepZAmplitude = 0.85f + kotlin.random.Random.nextFloat() * 0.3f
        }
    }

    val frames = remember(mood) { framesFor(mood) }
    val rendered = stableCatFrame(frames[frame % frames.size])
    val interactionModifier = if (onInteract != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onInteract
        )
    } else {
        Modifier
    }

    BoxWithConstraints(
        modifier = modifier.then(interactionModifier),
        contentAlignment = Alignment.Center
    ) {
        val restTravel = calculateCatHorizontalTravel(maxWidth.value, 124f)
        val x = if (mood == FocusCatMood.REST) roam * restTravel else 0f
        val y = when (mood) {
            FocusCatMood.REST -> calculateParabolicHopOffset(jumpProgress, 17f)
            FocusCatMood.CELEBRATE -> calculateParabolicHopOffset(jumpProgress, 9f)
            else -> 0f
        }
        val catScaleY = when (mood) {
            FocusCatMood.IDLE, FocusCatMood.RESET -> breath
            else -> 1f
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 13.dp)
                .size(width = 190.dp, height = 18.dp)
                .graphicsLayer(scaleX = 1f + (breath - 1f) * 0.25f)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    CircleShape
                )
        )

        Text(
            text = rendered,
            modifier = Modifier
                .offset(x = x.dp, y = y.dp)
                .graphicsLayer(
                    scaleX = 1f,
                    scaleY = catScaleY,
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.72f)
                ),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 27.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Start,
            softWrap = false
        )

        if (isSleeping) {
            val zProgress = sleepZProgress.value
            val smoothProgress = zProgress * zProgress * (3f - 2f * zProgress)
            val fadeIn = (zProgress / 0.12f).coerceIn(0f, 1f)
            val fadeOut = ((1f - zProgress) / 0.55f).coerceIn(0f, 1f)
            val zAlpha = minOf(fadeIn, fadeOut)
            val zScale = 0.78f + smoothProgress * 0.35f
            val zX = 58.dp + (38.dp * sleepZAmplitude) * smoothProgress
            val zY = (-15).dp - 110.dp * smoothProgress
            val zColor = if (zProgress < 0.5f) {
                lerp(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.tertiary,
                    zProgress * 2f
                )
            } else {
                lerp(
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.primary,
                    (zProgress - 0.5f) * 2f
                )
            }

            Text(
                text = if (sleepZIsBig) "Z" else "z",
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = zX, y = zY)
                    .graphicsLayer {
                        alpha = zAlpha
                        scaleX = zScale
                        scaleY = zScale
                    },
                color = zColor,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = sleepZBaseSize.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

private fun framesFor(mood: FocusCatMood): List<String> = when (mood) {
    FocusCatMood.IDLE, FocusCatMood.RESET -> listOf(
        " /\\_/\\\n( -.- )\n /|_|\\\n(_| |_)~",
        " /\\_/\\\n( -.- )\n /|_|\\\n(_| |_)~"
    )
    FocusCatMood.READY -> listOf(
        " /\\_/\\\n( o.o )\n /| |\\\n(_| |_)~",
        " /\\_/\\\n( -.- )\n /| |\\\n(_| |_)~",
        " /\\_/\\\n( o.o )\n /| |\\\n(_| |_)~"
    )
    FocusCatMood.FOCUS -> listOf(
        " /\\_/\\   ___\n( o.o )  /__/\n /| |\\_/ /\n(_| |_)__/",
        " /\\_/\\   ___\n( -.- )  /__/\n /| |\\_/ /\n(_| |_)__/",
        " /\\_/\\   ___\n( o.o )  /__/\n /| |\\>_/ /\n(_| |_)__/",
        " /\\_/\\   ___\n( o.o )  /__/\n /| |<_/ /\n(_| |_)__/"
    )
    FocusCatMood.PAUSED -> listOf(
        " /\\_/\\\n( ._. )\n /| |\\\n(_| |_)~",
        " /\\_/\\\n( -.- )\n /| |\\\n(_| |_)~"
    )
    FocusCatMood.REST -> listOf(
        " /\\_/\\     o\n( ^.^ )   /\n /| |\\__/\n(_| |_)~",
        " /\\_/\\  o\n( o.o ) /\n /| |\\/\n(_| |_)~",
        " /\\_/\\     o\n( ^.^ )  /\n /| |\\_/\n(_| |_)~"
    )
    FocusCatMood.CELEBRATE -> listOf(
        " /\\_/\\   *\n( ^.^ ) /\n /| |\\/\n(_| |_)~",
        "* /\\_/\\\n \\( ^.^ )\n  /| |\\\n (_| |_)~"
    )
}

internal fun stableCatFrame(frame: String, bodyAxis: Int = 4): String {
    val lines = frame.trim('\n', '\r').lines().map { it.trimEnd() }
    val topPadding = ((CAT_STAGE_LINES - lines.size) / 2).coerceAtLeast(0)
    val leadingPadding = (CAT_STAGE_COLUMNS / 2 - bodyAxis).coerceAtLeast(0)
    return List(CAT_STAGE_LINES) { index ->
        val sourceIndex = index - topPadding
        val line = if (sourceIndex in lines.indices) lines[sourceIndex] else ""
        (" ".repeat(leadingPadding) + line)
            .take(CAT_STAGE_COLUMNS)
            .padEnd(CAT_STAGE_COLUMNS, '\u00A0')
    }.joinToString("\n")
}

private const val CAT_STAGE_COLUMNS = 23
private const val CAT_STAGE_LINES = 6
