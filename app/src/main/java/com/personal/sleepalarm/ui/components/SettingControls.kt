package com.personal.sleepalarm.ui.components

import com.personal.sleepalarm.ui.theme.appAccents
import com.personal.sleepalarm.ui.theme.AppAccentTone

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import com.personal.sleepalarm.R
import kotlin.math.roundToInt
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults

private val LocalSectionTone = staticCompositionLocalOf<AppAccentTone?> { null }

/**
 * Карточка секции настроек.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    tone: AppAccentTone = MaterialTheme.appAccents.focus,
    content: @Composable ColumnScope.() -> Unit
) {
    val outerScheme = MaterialTheme.colorScheme
    val sectionTypography = MaterialTheme.typography
    val sectionShapes = MaterialTheme.shapes
    val sectionScheme = outerScheme.copy(
        primary = tone.color,
        onPrimary = tone.onColor,
        primaryContainer = tone.container,
        onPrimaryContainer = tone.onContainer,
        secondary = tone.fill,
        onSecondary = tone.onFill,
        secondaryContainer = tone.action,
        onSecondaryContainer = tone.onAction,
        surface = tone.container,
        onSurface = tone.onContainer,
        surfaceVariant = tone.action,
        onSurfaceVariant = tone.onAction,
        surfaceContainerLowest = tone.container,
        surfaceContainerLow = tone.container,
        surfaceContainer = tone.container,
        surfaceContainerHigh = tone.action,
        surfaceContainerHighest = tone.action,
        outline = tone.color.copy(alpha = 0.48f),
        outlineVariant = tone.color.copy(alpha = 0.24f)
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = tone.container,
            contentColor = tone.onContainer
        )
    ) {
        MaterialTheme(
            colorScheme = sectionScheme,
            typography = sectionTypography,
            shapes = sectionShapes
        ) {
            CompositionLocalProvider(LocalSectionTone provides tone) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = tone.onContainer
                    )

                    Spacer(modifier = Modifier.padding(top = 12.dp))

                    content()
                }
            }
        }
    }
}

/**
 * Слайдер с подписью и текущим значением.
 */
@Composable
fun LabeledSlider(
    label: String,
    value: Int,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tone = LocalSectionTone.current ?: MaterialTheme.appAccents.focus
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyLarge,
                color = tone.onContainer
            )
        }

        Slider(
            value = value.toFloat(),
            onValueChange = { newValue ->
                onValueChange(newValue.roundToInt())
            },
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = tone.color,
                activeTrackColor = tone.color,
                inactiveTrackColor = tone.action
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Горизонтальный ряд chip'ов выбора.
 */
@Composable
fun <T> ChoiceChips(
    label: String,
    options: List<T>,
    selected: T,
    optionText: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val tone = LocalSectionTone.current ?: MaterialTheme.appAccents.focus
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.padding(top = 8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = {
                        Text(text = optionText(option))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = tone.action.copy(alpha = 0.62f),
                        labelColor = tone.onAction,
                        selectedContainerColor = tone.color,
                        selectedLabelColor = tone.onColor
                    )
                )
            }
        }
    }
}

/**
 * Переключатель.
 */
@Composable
fun SwitchSetting(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val tone = LocalSectionTone.current ?: MaterialTheme.appAccents.focus
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = tone.onColor,
                checkedTrackColor = tone.color,
                uncheckedThumbColor = tone.onAction,
                uncheckedTrackColor = tone.action
            )
        )
    }
}

/**
 * Степпер времени.
 *
 * Показывает часы и минуты, позволяет менять их кнопками +/-.
 */
@Composable
fun TimeStepper(
    label: String,
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tone = LocalSectionTone.current ?: MaterialTheme.appAccents.sleep
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.padding(top = 8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Часы
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        onHourChange((hour - 1 + 24) % 24)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = stringResource(R.string.content_description_previous_hour),
                        tint = tone.color
                    )
                }

                Text(
                    text = String.format(Locale.ROOT, "%02d", hour),
                    style = MaterialTheme.typography.headlineSmall,
                    color = tone.onContainer
                )

                IconButton(
                    onClick = {
                        onHourChange((hour + 1) % 24)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.content_description_next_hour),
                        tint = tone.color
                    )
                }

                Text(
                    text = stringResource(R.string.time_unit_hours),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Минуты
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        onMinuteChange((minute - 5 + 60) % 60)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = stringResource(R.string.content_description_previous_minute),
                        tint = tone.color
                    )
                }

                Text(
                    text = String.format(Locale.ROOT, "%02d", minute),
                    style = MaterialTheme.typography.headlineSmall,
                    color = tone.onContainer
                )

                IconButton(
                    onClick = {
                        onMinuteChange((minute + 5) % 60)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.content_description_next_minute),
                        tint = tone.color
                    )
                }

                Text(
                    text = stringResource(R.string.time_unit_minutes),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
