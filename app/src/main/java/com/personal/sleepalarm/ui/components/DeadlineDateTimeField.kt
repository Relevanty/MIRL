package com.personal.sleepalarm.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.ui.dday.DeadlineToneTheme
import com.personal.sleepalarm.ui.theme.AppAccentTone
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One exact local deadline for both task editing and calendar deadline editing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlineDateTimeField(
    value: Long?,
    onValueChange: (Long?) -> Unit,
    tone: AppAccentTone,
    allowClear: Boolean = true,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val locale = LocalConfiguration.current.locales[0]
    val local = value?.let { Instant.ofEpochMilli(it).atZone(zone) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = { showDate = true },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            color = tone.action,
            contentColor = tone.onAction
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.deadline_exact_date), style = MaterialTheme.typography.labelSmall, color = tone.onAction)
                Text(
                    local?.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale)) ?: stringResource(R.string.deadline_choose_date),
                    style = MaterialTheme.typography.bodyLarge,
                    color = tone.onAction
                )
            }
        }
        Surface(
            onClick = { showTime = true },
            enabled = enabled && value != null,
            modifier = Modifier.widthIn(min = 84.dp),
            shape = RoundedCornerShape(10.dp),
            color = tone.action,
            contentColor = tone.onAction
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.deadline_exact_time), style = MaterialTheme.typography.labelSmall, color = tone.onAction)
                Text(local?.format(DateTimeFormatter.ofPattern("HH:mm", locale)) ?: "—", style = MaterialTheme.typography.bodyLarge, color = tone.onAction)
            }
        }
        if (allowClear && value != null) {
            IconButton(onClick = { onValueChange(null) }, enabled = enabled) {
                Icon(Icons.Default.Close, stringResource(R.string.deadline_clear_due), tint = tone.onContainer)
            }
        }
    }
    if (showDate) {
        DeadlineToneTheme(tone) {
            val pickerDate = local?.toLocalDate() ?: LocalDate.now()
            val picker = rememberDatePickerState(
                initialSelectedDateMillis = pickerDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                yearRange = minOf(1900, pickerDate.year)..maxOf(2100, pickerDate.year)
            )
            DatePickerDialog(
                onDismissRequest = { showDate = false },
                confirmButton = {
                    TextButton(
                        enabled = enabled && picker.selectedDateMillis != null,
                        onClick = {
                            picker.selectedDateMillis?.let { millis ->
                                // Material's picker encodes a calendar date at UTC midnight, not local midnight.
                                val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                                val time = local?.toLocalTime() ?: LocalTime.of(23, 59)
                                onValueChange(date.atTime(time).atZone(zone).toInstant().toEpochMilli())
                            }
                            showDate = false
                        }
                    ) { Text(stringResource(R.string.deadline_apply_date)) }
                },
                dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.action_cancel)) } }
            ) { DatePicker(state = picker) }
        }
    }
    if (showTime && local != null) {
        DeadlineToneTheme(tone) {
            val picker = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute, is24Hour = true)
            AlertDialog(
                onDismissRequest = { showTime = false },
                title = { Text(stringResource(R.string.deadline_choose_time)) },
                text = { TimeInput(state = picker) },
                confirmButton = {
                    TextButton(
                        enabled = enabled,
                        onClick = {
                            onValueChange(local.toLocalDate().atTime(picker.hour, picker.minute).atZone(zone).toInstant().toEpochMilli())
                            showTime = false
                        }
                    ) { Text(stringResource(R.string.deadline_apply_date)) }
                },
                dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.action_cancel)) } }
            )
        }
    }
}
