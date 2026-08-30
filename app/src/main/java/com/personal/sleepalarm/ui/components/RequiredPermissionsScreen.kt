package com.personal.sleepalarm.ui.components

import com.personal.sleepalarm.ui.theme.appAccents

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.util.PermissionState

private data class RequiredPermission(
    val title: String,
    val description: String,
    val granted: Boolean,
    val request: () -> Unit
)

/**
 * Обязательный экран допуска. Основной интерфейс отображается только после
 * выдачи всех доступов, от которых зависит полноценная работа будильника.
 */
@Composable
fun RequiredPermissionsScreen(
    state: PermissionState,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarms: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    onOpenFullScreenIntent: () -> Unit,
    onOpenNotificationPolicy: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val permissions = buildList {
        add(
            RequiredPermission(
                title = stringResource(R.string.system_check_notifications),
                description = stringResource(R.string.system_check_notifications_desc),
                granted = state.notificationsGranted,
                request = onRequestNotifications
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(
                RequiredPermission(
                    title = stringResource(R.string.system_check_exact_alarms),
                    description = stringResource(R.string.system_check_exact_alarms_desc),
                    granted = state.exactAlarmsAllowed,
                    request = onOpenExactAlarms
                )
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(
                RequiredPermission(
                    title = stringResource(R.string.system_check_fullscreen),
                    description = stringResource(
                        if (state.usesXiaomiAlarmPermissions) {
                            R.string.system_check_fullscreen_xiaomi_desc
                        } else {
                            R.string.system_check_fullscreen_desc
                        }
                    ),
                    granted = state.fullScreenIntentAllowed,
                    request = onOpenFullScreenIntent
                )
            )
        }
        add(
            RequiredPermission(
                title = stringResource(R.string.system_check_dnd),
                description = stringResource(R.string.system_check_dnd_desc),
                granted = state.notificationPolicyAccessGranted,
                request = onOpenNotificationPolicy
            )
        )
        add(
            RequiredPermission(
                title = stringResource(R.string.system_check_battery),
                description = stringResource(R.string.system_check_battery_desc),
                granted = state.batteryOptimizationDisabled,
                request = onOpenBatteryOptimization
            )
        )
    }

    val missing = permissions.filterNot { it.granted }
    val next = missing.firstOrNull()

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.required_permissions_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.required_permissions_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.required_permissions_progress,
                    permissions.size - missing.size,
                    permissions.size
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.appAccents.focus.color
            )

            Spacer(modifier = Modifier.height(4.dp))

            permissions.forEach { permission ->
                PermissionStatusRow(permission)
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (next != null) {
                Button(
                    onClick = next.request,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.required_permissions_grant, next.title))
                }
            }

            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.system_check_refresh))
            }

            Text(
                text = stringResource(R.string.required_permissions_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionStatusRow(permission: RequiredPermission) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (permission.granted) {
                MaterialTheme.appAccents.success.color
            } else {
                MaterialTheme.appAccents.urgent.color
            },
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                imageVector = if (permission.granted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (permission.granted) {
                    MaterialTheme.appAccents.success.onColor
                } else {
                    MaterialTheme.appAccents.urgent.onColor
                },
                modifier = Modifier.padding(6.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = permission.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = permission.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = stringResource(
                if (permission.granted) {
                    R.string.required_permissions_granted
                } else {
                    R.string.required_permissions_missing
                }
            ),
            style = MaterialTheme.typography.labelMedium,
            color = if (permission.granted) {
                MaterialTheme.appAccents.success.color
            } else {
                MaterialTheme.appAccents.urgent.color
            }
        )
    }
}
