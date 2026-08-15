package com.personal.sleepalarm.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Готовые ASCII-коты для всего приложения. */
object CatArt {
    const val SLEEP  = " /\\_/\\\n( -.- ) zZ\n > ^ <"
    const val SIT    = " /\\_/\\\n( o.o )\n > ^ <"
    const val PLAY   = " /\\_/\\\n( =.= )\n > ^ < ~"
    const val PLUS   = " /\\_/\\\n( o.o )+\n > ^ <"
    const val CHECK  = " /\\_/\\\n( o.o )✓\n > ^ <"
    const val SCARED = " /\\_/\\\n( O_O )!\n > ^ <"
    const val FISH   = " /\\_/\\\n( o.o )\n > ^ <  ><> "
    const val CLOCK  = " /\\_/\\\n( o.o )\n > ^ < ~"
    const val PAW    = "ฅ"
    // === ТОНКИЕ КОТЫ (лёгкие штрихи) ===
    const val THIN_SLEEP = " ∧_∧\n( ·_· ) z\n ﹏﹏﹏"
    const val THIN_SIT   = " ∧_∧\n( ·.· )\n 乚 乚 ~"
    const val THIN_PLAY  = " ∧_∧\n( ·.· )\n / / o"
    const val THIN_FISH  = " ∧_∧\n( ·.· )\n 乚 乚 ><>"
    const val THIN_PAW   = "∧ ∧\n(·.·)"
}

@Composable
fun CatText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    fontSize: TextUnit = 12.sp
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        softWrap = false,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize,
            lineHeight = (fontSize.value + 2).sp,
            fontWeight = FontWeight.Bold
        ),
        textAlign = TextAlign.Start   // ВАЖНО: не Center, иначе строки разъедутся
    )
}
/** Тонкий кот (светлый вес, деликатные штрихи). */
@Composable
fun ThinCatText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary,
    fontSize: TextUnit = 12.sp
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        softWrap = false,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize,
            lineHeight = (fontSize.value + 2).sp,
            fontWeight = FontWeight.Light   // тонкие штрихи
        ),
        textAlign = TextAlign.Start
    )
}