package com.personal.sleepalarm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.personal.sleepalarm.R
import com.personal.sleepalarm.domain.calculator.RemCueScheduleCalculator

private val DialogSurface = Color(0xFF161E38)
private val RowSurface = Color(0xFF1D2645)
private val BorderColor = Color(0xFF2A3558)
private val TextPrimary = Color(0xFFE9EDF9)
private val TextMuted = Color(0xFF8A93B2)
private val Accent = Color(0xFFFFB86B)

/**
 * Справка по lucid-подсказкам, привязанным к REM-фазам (F7).
 *
 * Содержит:
 * 1. объяснение модели цикла сна;
 * 2. таблицу пропорций REM по циклам (из REM_FRACTION_BY_CYCLE);
 * 3. рекомендации по таймингу (WBTB/MILD, cueing в REM);
 * 4. список источников;
 * 5. дисклеймер.
 *
 * Таблица пропорций строится динамически из доменной константы,
 * чтобы справка никогда не расходилась с реальным калькулятором.
 */
@Composable
fun HelpCuesDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(DialogSurface)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.help_cues_title),
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
            )

            // 1. Модель цикла сна.
            HelpBlock(
                title = stringResource(R.string.help_cues_model_title),
                body = stringResource(R.string.help_cues_model)
            )

            // 2. Таблица пропорций REM.
            Text(
                text = stringResource(R.string.help_cues_table_header),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Accent
                )
            )

            RemFractionTable()

            Text(
                text = stringResource(R.string.help_cues_table_note),
                style = TextStyle(fontSize = 12.sp, color = TextMuted)
            )

            // 3. Рекомендации по таймингу.
            HelpBlock(
                title = stringResource(R.string.help_cues_timing_title),
                body = stringResource(R.string.help_cues_timing)
            )

            // 4. Источники.
            Text(
                text = stringResource(R.string.help_cues_sources_title),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Accent
                )
            )

            SourceLine(stringResource(R.string.help_cues_source_1))
            SourceLine(stringResource(R.string.help_cues_source_2))
            SourceLine(stringResource(R.string.help_cues_source_3))
            SourceLine(stringResource(R.string.help_cues_source_4))

            HorizontalDivider(color = BorderColor)

            // 5. Дисклеймер.
            Text(
                text = stringResource(R.string.help_cues_disclaimer),
                style = TextStyle(
                    fontSize = 12.sp,
                    color = TextMuted,
                    lineHeight = 17.sp
                )
            )

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(text = stringResource(R.string.help_cues_close))
            }
        }
    }
}

/**
 * Блок «заголовок + текст».
 */
@Composable
private fun HelpBlock(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Accent
            )
        )
        Text(
            text = body,
            style = TextStyle(
                fontSize = 13.sp,
                color = TextPrimary,
                lineHeight = 19.sp
            )
        )
    }
}

/**
 * Таблица «Цикл N ≈ X% цикла — REM».
 *
 * Значения берутся из RemCueScheduleCalculator.REM_FRACTION_BY_CYCLE,
 * чтобы справка и калькулятор всегда совпадали.
 */
@Composable
private fun RemFractionTable() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RowSurface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RemCueScheduleCalculator.REM_FRACTION_BY_CYCLE.forEachIndexed { index, fraction ->
            val cycleNumber = index + 1
            val percent = (fraction * 100).toInt()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.help_cues_cycle_format, cycleNumber),
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = stringResource(R.string.percent_format, percent),
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Accent
                    )
                )
            }
        }
    }
}

/**
 * Строка источника литературы.
 */
@Composable
private fun SourceLine(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "• ",
            style = TextStyle(fontSize = 12.sp, color = Accent)
        )
        Text(
            text = text,
            style = TextStyle(
                fontSize = 12.sp,
                color = TextMuted,
                lineHeight = 17.sp
            ),
            modifier = Modifier.weight(1f)
        )
    }
}