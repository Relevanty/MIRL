package com.personal.sleepalarm.ui.dday

import com.personal.sleepalarm.ui.theme.appAccents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R

/**
 * Бейдж ближайшего D-Day: «До X — N дней» (или «X — сегодня!»).
 *
 * Переиспользуется на главном экране, в Помодоро и в Задачах.
 * Показывается только если 0 <= days <= 30.
 */
@Composable
fun DDayBadge(
    viewModel: DDayViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val nearest by viewModel.nearest.collectAsStateWithLifecycle()

    if (!viewModel.isBadgeVisible(nearest) || nearest == null) return

    val event = nearest!!.event
    val days = nearest!!.days
    val tone = if (days == 0) {
        MaterialTheme.appAccents.urgent
    } else {
        MaterialTheme.appAccents.warning
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tone.container)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Flag,
            contentDescription = null,
            tint = tone.onContainer,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (days == 0) {
                stringResource(R.string.dday_badge_today, event.title)
            } else {
                stringResource(R.string.dday_badge, event.title, days)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = tone.onContainer,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
