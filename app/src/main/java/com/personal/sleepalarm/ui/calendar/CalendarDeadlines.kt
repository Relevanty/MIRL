package com.personal.sleepalarm.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.ui.dday.DDayPlanInfo
import com.personal.sleepalarm.ui.dday.DeadlineCard
import com.personal.sleepalarm.ui.theme.appAccents
import java.time.LocalDate

/** Deadline management belongs to Calendar; no extra entry in Today/Sections. */
@Composable
internal fun CalendarDeadlines(
    events: List<DDayEntity>,
    plans: Map<Int, DDayPlanInfo>,
    completedTaskIds: Set<Int>,
    taskDueDates: Map<Int, Long>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (DDayEntity) -> Unit,
    onOpenTask: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tone = MaterialTheme.appAccents.schedule
    var showPast by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now().toString()
    val upcoming = events.filter { it.targetDate >= today && it.taskId !in completedTaskIds }
        .sortedWith(compareBy<DDayEntity> { it.targetDate }.thenBy { it.id })
    val past = events.filter { it.targetDate < today || it.taskId in completedTaskIds }
        .sortedWith(compareByDescending<DDayEntity> { it.targetDate }.thenBy { it.id })
    // Past deadlines remain visible by default, including uncompleted overdue work.
    val displayed = if (showPast) past else upcoming + past

    Column(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back), tint = tone.color)
            }
            Text(
                stringResource(R.string.calendar_deadlines_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = tone.color
            )
            Button(
                onClick = onCreate,
                colors = ButtonDefaults.buttonColors(containerColor = tone.action, contentColor = tone.onAction)
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.calendar_create_deadline))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false, true).forEach { pastSelected ->
                FilterChip(
                    selected = showPast == pastSelected,
                    onClick = { showPast = pastSelected },
                    label = {
                        Text(stringResource(
                            if (pastSelected) R.string.calendar_deadlines_past else R.string.calendar_deadlines_upcoming,
                            if (pastSelected) past.size else events.size
                        ))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = tone.container,
                        labelColor = tone.onContainer,
                        selectedContainerColor = tone.action,
                        selectedLabelColor = tone.onAction
                    ),
                    border = null
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (displayed.isEmpty()) {
                item {
                    Text(
                        stringResource(if (showPast) R.string.calendar_deadlines_empty_past else R.string.calendar_deadlines_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = tone.color,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 24.dp)
                    )
                }
            }
            items(displayed, key = { it.id }) { event ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DeadlineCard(
                        event = event,
                        plan = plans[event.id],
                        dueAtMillis = taskDueDates[event.taskId],
                        isCompleted = event.taskId in completedTaskIds,
                        onClick = { onEdit(event) },
                        onOpenTask = onOpenTask
                    )
                }
            }
        }
    }
}
