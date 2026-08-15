package com.personal.sleepalarm.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.util.PermissionState

/**
 * Баннеры разрешений.
 *
 * Красный баннер:
 * - точные будильники запрещены.
 *
 * Жёлтые баннеры:
 * - уведомления запрещены;
 * - battery optimization включена;
 * - full-screen intent ограничен.
 */
@Composable
fun PermissionBanners(
    state: PermissionState,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
    onOpenNotificationPolicySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!state.exactAlarmsAllowed) {
            WarningCard(
                text = stringResource(R.string.warning_exact_alarms_required),
                isError = true,
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = onOpenExactAlarmSettings
            )
        }

        if (!state.notificationsGranted) {
            WarningCard(
                text = stringResource(R.string.warning_notifications_disabled),
                isError = false,
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = onOpenNotificationSettings
            )
        }

        if (!state.batteryOptimizationDisabled) {
            WarningCard(
                text = stringResource(R.string.warning_battery_optimization_enabled),
                isError = false,
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = onOpenBatterySettings
            )
        }

        if (!state.fullScreenIntentAllowed) {
            WarningCard(
                text = stringResource(R.string.warning_full_screen_intent_disabled),
                isError = true,
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = onOpenFullScreenSettings
            )
        }

        if (!state.notificationPolicyAccessGranted) {
            WarningCard(
                text = stringResource(R.string.warning_dnd_access_disabled),
                isError = true,
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = onOpenNotificationPolicySettings
            )
        }
    }
}
