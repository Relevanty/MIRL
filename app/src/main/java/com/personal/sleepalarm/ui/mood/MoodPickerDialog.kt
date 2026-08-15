package com.personal.sleepalarm.ui.mood

import com.personal.sleepalarm.ui.theme.ThemedAlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.personal.sleepalarm.R

/**
 * Диалог «как вы себя чувствуете?» — один тап из 5.
 * Нельзя закрыть без выбора (onDismissRequest пустой).
 */
@Composable
fun MoodPickerDialog(
    onSelect: (Int) -> Unit
) {
    val options = listOf(
        "😞" to 1,
        "😕" to 2,
        "😐" to 3,
        "🙂" to 4,
        "😄" to 5
    )

    ThemedAlertDialog(
        onDismissRequest = { /* обязательный выбор */ },
        title = { Text(stringResource(R.string.mood_picker_title)) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                options.forEach { (emoji, value) ->
                    IconButton(onClick = { onSelect(value) }) {
                        Text(text = emoji, fontSize = 28.sp)
                    }
                }
            }
        },
        confirmButton = { /* выбор только эмодзи */ }
    )
}