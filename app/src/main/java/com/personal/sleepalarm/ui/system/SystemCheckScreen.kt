package com.personal.sleepalarm.ui.system

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.util.PermissionChecker

/**
 * Экран проверки всех критичных разрешений с кнопками для настройки.
 */
@Composable
fun SystemCheckScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var refreshKey by remember { mutableStateOf(0) }
    val state = remember(refreshKey) { PermissionChecker.state(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                text = stringResource(R.string.system_check_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.system_check_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Уведомления.
        CheckRow(
            title = stringResource(R.string.system_check_notifications),
            description = stringResource(R.string.system_check_notifications_desc),
            ok = state.notificationsGranted,
            onFix = {
                state.notificationsIntent?.let { context.startActivity(it) }
            }
        )

        // 2. Точные будильники (Android 12+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            CheckRow(
                title = stringResource(R.string.system_check_exact_alarms),
                description = stringResource(R.string.system_check_exact_alarms_desc),
                ok = state.exactAlarmsAllowed,
                onFix = {
                    state.exactAlarmsIntent?.let {
                        runCatching { context.startActivity(it) }
                    }
                }
            )
        }

        // 3. Игнорирование экономии батареи.
        CheckRow(
            title = stringResource(R.string.system_check_battery),
            description = stringResource(R.string.system_check_battery_desc),
            ok = state.batteryOptimizationDisabled,
            onFix = {
                state.batteryOptimizationIntent?.let {
                    runCatching { context.startActivity(it) }
                }
            }
        )

        // 4. Полноэкранный показ будильника (Android 14+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            CheckRow(
                title = stringResource(R.string.system_check_fullscreen),
                description = stringResource(R.string.system_check_fullscreen_desc),
                ok = state.fullScreenIntentAllowed,
                onFix = {
                    state.fullScreenIntentSettings?.let {
                        runCatching { context.startActivity(it) }
                    }
                }
            )
        }

        // 5. Обход режима «Не беспокоить» для канала будильника.
        CheckRow(
            title = stringResource(R.string.system_check_dnd),
            description = stringResource(R.string.system_check_dnd_desc),
            ok = state.notificationPolicyAccessGranted,
            onFix = {
                state.notificationPolicyIntent?.let {
                    runCatching { context.startActivity(it) }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // HyperOS подсказки.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                .padding(12.dp)
        ) {
            Text(
                text = stringResource(R.string.system_check_hyperos_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.system_check_hyperos_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { refreshKey++ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.system_check_refresh))
        }
    }
}

@Composable
private fun CheckRow(
    title: String,
    description: String,
    ok: Boolean,
    onFix: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (ok) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (ok) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!ok) {
            OutlinedButton(onClick = onFix) {
                Text(stringResource(R.string.system_check_fix))
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
}
