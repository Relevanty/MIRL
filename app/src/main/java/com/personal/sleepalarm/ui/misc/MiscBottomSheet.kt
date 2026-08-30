package com.personal.sleepalarm.ui.misc

import com.personal.sleepalarm.ui.theme.appAccents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.ui.theme.ThemedModalBottomSheet
import androidx.compose.ui.text.style.TextAlign
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiscBottomSheet(
    onDismiss: () -> Unit,
    onSelect: (MiscScreen) -> Unit
) {
    ThemedModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // === Кот сверху шторки ===
            Text(
                text = "=^..^=",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.appAccents.other.color
            )

            // ... дальше существующие пункты
            MiscItem(Icons.Default.MenuBook, stringResource(R.string.misc_library)) {
                onSelect(MiscScreen.Library)
            }
            MiscItem(Icons.Default.Notifications, stringResource(R.string.misc_reminders)) {
                onSelect(MiscScreen.Reminders)
            }
            MiscItem(Icons.Default.Flag, stringResource(R.string.misc_dday)) {
                onSelect(MiscScreen.DDay)
            }
            MiscItem(Icons.Default.SmartToy, stringResource(R.string.misc_assistant)) {
                onSelect(MiscScreen.Assistant)
            }
            MiscItem(Icons.Default.RecordVoiceOver, stringResource(R.string.misc_briefing)) {
                onSelect(MiscScreen.Briefing)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun MiscItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.appAccents.other.color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
