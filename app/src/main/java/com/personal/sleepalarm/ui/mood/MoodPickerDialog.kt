package com.personal.sleepalarm.ui.mood

import com.personal.sleepalarm.ui.theme.ThemedAlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.ui.theme.appAccents

data class MorningCheckInInput(
    val energy: Int,
    val mood: Int,
    val clarity: Int?
)

/** A short, skippable morning observation. Mood and energy stay separate. */
@Composable
fun MorningCheckInDialog(
    onSubmit: (MorningCheckInInput) -> Unit,
    onSkip: () -> Unit
) {
    var energy by rememberSaveable { mutableStateOf<Int?>(null) }
    var mood by rememberSaveable { mutableStateOf<Int?>(null) }
    var clarity by rememberSaveable { mutableStateOf<Int?>(null) }
    val options = listOf(
        "😞" to 1,
        "😕" to 2,
        "😐" to 3,
        "🙂" to 4,
        "😄" to 5
    )
    val tones = listOf(
        MaterialTheme.appAccents.urgent,
        MaterialTheme.appAccents.warning,
        MaterialTheme.appAccents.other,
        MaterialTheme.appAccents.calm,
        MaterialTheme.appAccents.success
    )

    ThemedAlertDialog(
        onDismissRequest = onSkip,
        title = { Text(stringResource(R.string.morning_check_in_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.morning_check_in_energy),
                    style = MaterialTheme.typography.titleSmall
                )
                listOf(1..5, 6..10).forEach { range ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        range.forEach { value ->
                            FilterChip(
                                selected = energy == value,
                                onClick = { energy = value },
                                label = {
                                    Text(
                                        text = value.toString(),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.morning_check_in_mood),
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    options.forEachIndexed { index, (emoji, value) ->
                        IconButton(onClick = { mood = value }) {
                            Text(
                                text = emoji,
                                fontSize = if (mood == value) 34.sp else 28.sp,
                                color = tones[index].color
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.morning_check_in_clarity_optional),
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    (1..5).forEach { value ->
                        FilterChip(
                            selected = clarity == value,
                            onClick = { clarity = if (clarity == value) null else value },
                            label = {
                                Text(
                                    text = value.toString(),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.morning_check_in_privacy),
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val selectedEnergy = energy ?: return@Button
                    val selectedMood = mood ?: return@Button
                    onSubmit(MorningCheckInInput(selectedEnergy, selectedMood, clarity))
                },
                enabled = energy != null && mood != null
            ) {
                Text(stringResource(R.string.morning_check_in_build_plan))
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.morning_check_in_skip))
            }
        }
    )
}

/** Short recovery probe; no mood value is inferred from energy. */
@Composable
fun EnergyCheckInDialog(
    title: String,
    supportingText: String,
    onSubmit: (Int) -> Unit,
    onSkip: () -> Unit
) {
    var energy by rememberSaveable { mutableStateOf<Int?>(null) }
    ThemedAlertDialog(
        onDismissRequest = onSkip,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(supportingText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(1..5, 6..10).forEach { range ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        range.forEach { value ->
                            FilterChip(
                                selected = energy == value,
                                onClick = { energy = value },
                                label = {
                                    Text(
                                        value.toString(),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { energy?.let(onSubmit) },
                enabled = energy != null
            ) { Text(stringResource(R.string.energy_check_in_save)) }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.morning_check_in_skip))
            }
        }
    )
}
