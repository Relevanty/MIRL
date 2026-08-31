package com.personal.sleepalarm.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.domain.calculator.DailyTaskFocusProgress
import com.personal.sleepalarm.ui.theme.appAccents

@Composable
fun DailyFocusProgressCard(
    progress: DailyTaskFocusProgress,
    boutElapsedMillis: Long,
    boutMinutes: Int,
    modifier: Modifier = Modifier,
    requiredToday: Boolean = false,
    showBoutProgress: Boolean = true
) {
    val safeBoutMinutes = boutMinutes.coerceAtLeast(1)
    val elapsedBoutMinutes = (boutElapsedMillis.coerceAtLeast(0L) / 60_000L).toInt()
        .coerceAtMost(safeBoutMinutes)
    val remainingMinutes = ((progress.remainingMillis + 59_999L) / 60_000L).toInt()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.appAccents.progress.container,
        contentColor = MaterialTheme.appAccents.progress.onContainer
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.daily_focus_today_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(
                        R.string.daily_focus_today_value,
                        progress.spentMinutes,
                        progress.targetMinutes
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.appAccents.progress.onContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            LinearProgressIndicator(
                progress = { progress.progressFraction },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.appAccents.progress.color,
                trackColor = MaterialTheme.appAccents.progress.action
            )
            Text(
                if (remainingMinutes == 0) stringResource(R.string.daily_focus_today_done)
                else stringResource(R.string.daily_focus_today_remaining, remainingMinutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appAccents.progress.onContainer.copy(alpha = 0.78f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (showBoutProgress) {
                        stringResource(
                            R.string.daily_focus_bout_value,
                            elapsedBoutMinutes,
                            safeBoutMinutes
                        )
                    } else {
                        stringResource(R.string.daily_focus_bout_duration, safeBoutMinutes)
                    },
                    style = MaterialTheme.typography.labelLarge
                )
                if (requiredToday) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.appAccents.warning.action,
                        contentColor = MaterialTheme.appAccents.warning.onAction
                    ) {
                        Text(
                            stringResource(R.string.daily_focus_required_badge),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
