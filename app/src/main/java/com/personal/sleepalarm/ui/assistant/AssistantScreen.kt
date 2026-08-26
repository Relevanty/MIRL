package com.personal.sleepalarm.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.ui.activity.ManualActivitySheet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Экран ассистента: карточки предсказаний + чат с rule-based ответами.
 */
@Composable
fun AssistantScreen(
    onBack: () -> Unit,
    onStartTaskFocus: (Int) -> Unit = {},
    viewModel: AssistantViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val activityGap by viewModel.activityGap.collectAsStateWithLifecycle()
    val proposedAction by viewModel.proposedAction.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var showActivityForm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Заголовок.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                text = stringResource(R.string.assistant_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // === Карточки предсказаний ===
        InsightsRow(insights = insights)

        activityGap?.let { gap ->
            Spacer(modifier = Modifier.height(10.dp))
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val zone = ZoneId.systemDefault()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f))
                    .padding(12.dp)
            ) {
                Text(
                    "С ${Instant.ofEpochMilli(gap.startMillis).atZone(zone).format(formatter)} до " +
                        "${Instant.ofEpochMilli(gap.endMillis).atZone(zone).format(formatter)} нет активности. " +
                        "Вы работали без таймера?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.dismissActivityGap() }) { Text("Нет") }
                    TextButton(onClick = { showActivityForm = true }) { Text("Добавить время") }
                    TextButton(onClick = viewModel::dismissActivityGap) { Text("Позже") }
                }
            }
        }

        proposedAction?.let { action ->
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer).padding(12.dp)
            ) {
                Text("Начать «${action.title}» · ${action.focusMinutes} мин?", style = MaterialTheme.typography.titleSmall)
                Text("Запустится выбранная задача без повторной настройки.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::dismissProposedAction) { Text("Отмена") }
                    TextButton(onClick = {
                        onStartTaskFocus(action.taskId)
                        viewModel.dismissProposedAction()
                    }) { Text("Подтвердить") }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === Чат ===
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.assistant_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(messages) { msg ->
                ChatBubble(fromUser = msg.fromUser, text = msg.text)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Быстрые подсказки.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickChip(text = stringResource(R.string.assistant_quick_sleep)) {
                viewModel.ask(it)
            }
            QuickChip(text = stringResource(R.string.assistant_quick_morning)) {
                viewModel.ask(it)
            }
            QuickChip(text = stringResource(R.string.assistant_quick_tasks)) {
                viewModel.ask(it)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Поле ввода.
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.assistant_placeholder)) },
                singleLine = true
            )
            IconButton(
                onClick = {
                    viewModel.ask(input)
                    input = ""
                },
                enabled = input.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = stringResource(R.string.assistant_send))
            }
        }
    }

    if (showActivityForm) {
        ManualActivitySheet(
            initialStartMillis = activityGap?.startMillis,
            onDismiss = {
                showActivityForm = false
                viewModel.dismissActivityGap()
            }
        )
    }
}

// =====================================================================
// Карточки предсказаний
// =====================================================================

@Composable
private fun InsightsRow(insights: AssistantInsights) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!insights.isTrained) {
            Text(
                text = stringResource(R.string.assistant_not_trained),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.assistant_insight_morning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = if (insights.isHeavyMorning) {
                            stringResource(R.string.assistant_heavy)
                        } else {
                            stringResource(R.string.assistant_good)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    insights.predictedMood?.let {
                        Text(
                            text = stringResource(R.string.assistant_predicted, "%.1f".format(it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                insights.snoozeLimit?.let { limit ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.assistant_insight_snooze),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = limit.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.assistant_trained_on, insights.trainedOn),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =====================================================================
// Пузырь чата
// =====================================================================

@Composable
private fun ChatBubble(fromUser: Boolean, text: String) {
    val bg = if (fromUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (fromUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

// =====================================================================
// Быстрый чип
// =====================================================================

@Composable
private fun QuickChip(text: String, onAsk: (String) -> Unit) {
    androidx.compose.material3.AssistChip(
        onClick = { onAsk(text) },
        label = { Text(text, style = MaterialTheme.typography.bodySmall) }
    )
}
