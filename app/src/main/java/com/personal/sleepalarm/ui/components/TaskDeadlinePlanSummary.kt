package com.personal.sleepalarm.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.domain.calculator.TaskDeadlinePlan
import com.personal.sleepalarm.ui.theme.appAccents

/** The same explanation in the task editor and Calendar: a hint, never a second daily target. */
@Composable
fun TaskDeadlinePlanSummary(plan: TaskDeadlinePlan, modifier: Modifier = Modifier) {
    val tone = when {
        plan.overdue || plan.cannotFitBeforeDeadline -> MaterialTheme.appAccents.urgent
        plan.estimateExhaustedButTaskOpen || plan.isManualDailyGoalSufficient == false -> MaterialTheme.appAccents.warning
        else -> MaterialTheme.appAccents.progress
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = tone.container,
        contentColor = tone.onContainer,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(stringResource(R.string.task_deadline_plan_title), style = MaterialTheme.typography.labelLarge, color = tone.onContainer)
            if (!plan.budgetConfigured) {
                Text(stringResource(R.string.task_deadline_no_estimate), style = MaterialTheme.typography.bodySmall, color = tone.onContainer)
            } else {
                Text(
                    stringResource(R.string.task_deadline_volume, plan.spentMinutes, plan.totalMinutes, plan.remainingMinutes),
                    style = MaterialTheme.typography.bodySmall, color = tone.onContainer
                )
                val status = when {
                    plan.estimateExhaustedButTaskOpen -> R.string.task_deadline_estimate_exhausted
                    plan.overdue -> R.string.task_deadline_overdue_plan
                    plan.cannotFitBeforeDeadline -> R.string.task_deadline_not_enough_time
                    plan.calendarDaysRemaining == null -> R.string.task_deadline_no_date_plan
                    else -> null
                }
                if (status != null) Text(stringResource(status), style = MaterialTheme.typography.bodySmall, color = tone.onContainer)
                if (!plan.cannotFitBeforeDeadline) {
                    plan.requiredMinutesPerDay?.let { pace ->
                        Text(
                            stringResource(R.string.task_deadline_required_pace, pace, plan.manualDailyGoalMinutes),
                            style = MaterialTheme.typography.bodyMedium, color = tone.onContainer
                        )
                        if (plan.isManualDailyGoalSufficient == false) {
                            Text(stringResource(R.string.task_deadline_goal_below_pace), style = MaterialTheme.typography.bodySmall, color = tone.onContainer)
                        }
                        Text(stringResource(R.string.task_deadline_pace_hint), style = MaterialTheme.typography.bodySmall, color = tone.onContainer)
                    }
                }
            }
        }
    }
}
