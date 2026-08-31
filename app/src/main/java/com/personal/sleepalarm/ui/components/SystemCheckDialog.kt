package com.personal.sleepalarm.ui.components

import com.personal.sleepalarm.ui.theme.appAccents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.personal.sleepalarm.R
import com.personal.sleepalarm.util.PermissionChecker
import com.personal.sleepalarm.util.PermissionState

/**
 * Диалог системного чек-листа.
 *
 * Показывает живой статус:
 * - точные будильники;
 * - уведомления;
 * - battery optimization;
 * - full-screen intent;
 *
 * и OEM-инструкции для популярных оболочек.
 *
 * Статус обновляется при возврате из настроек (ON_RESUME).
 */
@Composable
fun SystemCheckDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val infoTone = MaterialTheme.appAccents.info

    var permissionState by remember {
        mutableStateOf(PermissionChecker.state(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionState = PermissionChecker.state(context)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(infoTone.container)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.system_check_title),
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = infoTone.onContainer
                )
            )

            Text(
                text = stringResource(R.string.system_check_subtitle),
                style = TextStyle(
                    fontSize = 13.sp,
                    color = infoTone.onContainer
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            CheckRow(
                title = stringResource(R.string.system_check_exact_alarms),
                subtitle = stringResource(R.string.system_check_exact_alarms_desc),
                granted = permissionState.exactAlarmsAllowed,
                critical = true,
                onOpen = {
                    runCatching {
                        context.startActivity(
                            PermissionChecker.exactAlarmsIntent(context)
                        )
                    }
                }
            )

            CheckRow(
                title = stringResource(R.string.system_check_notifications),
                subtitle = stringResource(R.string.system_check_notifications_desc),
                granted = permissionState.notificationsGranted,
                critical = true,
                onOpen = {
                    runCatching {
                        context.startActivity(
                            PermissionChecker.notificationsIntent(context)
                        )
                    }
                }
            )

            CheckRow(
                title = stringResource(R.string.system_check_battery),
                subtitle = stringResource(R.string.system_check_battery_desc),
                granted = permissionState.batteryOptimizationDisabled,
                critical = false,
                onOpen = {
                    runCatching {
                        context.startActivity(
                            PermissionChecker.batteryOptimizationIntent(context)
                        )
                    }
                }
            )

            CheckRow(
                title = stringResource(R.string.system_check_fullscreen),
                subtitle = stringResource(R.string.system_check_fullscreen_desc),
                granted = permissionState.fullScreenIntentAllowed,
                critical = false,
                onOpen = {
                    runCatching {
                        context.startActivity(
                            PermissionChecker.fullScreenIntentSettings(context)
                        )
                    }
                }
            )

            CheckRow(
                title = stringResource(R.string.system_check_dnd),
                subtitle = stringResource(R.string.system_check_dnd_desc),
                granted = permissionState.notificationPolicyAccessGranted,
                critical = true,
                onOpen = {
                    runCatching {
                        context.startActivity(
                            PermissionChecker.notificationPolicyIntent(context)
                        )
                    }
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = infoTone.onContainer.copy(alpha = 0.22f)
            )

            Text(
                text = stringResource(R.string.system_check_oem_title),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = infoTone.onContainer
                )
            )

            Text(
                text = stringResource(R.string.system_check_oem_subtitle),
                style = TextStyle(
                    fontSize = 12.sp,
                    color = infoTone.onContainer
                )
            )

            OemCard(
                name = "Xiaomi / Redmi / Poco",
                steps = stringResource(R.string.oem_xiaomi_steps)
            )

            OemCard(
                name = "Samsung",
                steps = stringResource(R.string.oem_samsung_steps)
            )

            OemCard(
                name = "Huawei / Honor",
                steps = stringResource(R.string.oem_huawei_steps)
            )

            OemCard(
                name = "OnePlus / Oppo / Realme",
                steps = stringResource(R.string.oem_oneplus_steps)
            )

            OemCard(
                name = "Pixel / AOSP",
                steps = stringResource(R.string.oem_pixel_steps)
            )

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = infoTone.action,
                    contentColor = infoTone.onAction
                )
            ) {
                Text(text = stringResource(R.string.system_check_close))
            }
        }
    }
}

/**
 * Строка чек-листа с живым статусом.
 */
@Composable
private fun CheckRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    critical: Boolean,
    onOpen: () -> Unit
) {
    val infoTone = MaterialTheme.appAccents.info
    val statusTone = when {
        granted -> MaterialTheme.appAccents.success
        critical -> MaterialTheme.appAccents.urgent
        else -> MaterialTheme.appAccents.warning
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(infoTone.action)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(statusTone.color)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = infoTone.onAction
                )
            )

            Text(
                text = subtitle,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = infoTone.onAction
                )
            )
        }

        if (granted) {
            Text(
                text = stringResource(R.string.system_check_status_ok),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusTone.action)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusTone.onAction
                )
            )
        } else {
            TextButton(
                onClick = onOpen,
                colors = ButtonDefaults.textButtonColors(
                    containerColor = statusTone.action,
                    contentColor = statusTone.onAction
                )
            ) {
                Text(
                    text = stringResource(R.string.system_check_action_configure)
                )
            }
        }
    }
}

/**
 * Карточка с OEM-инструкцией.
 */
@Composable
private fun OemCard(
    name: String,
    steps: String
) {
    val infoTone = MaterialTheme.appAccents.info

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(infoTone.action)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = name,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = infoTone.onAction
            )
        )

        Text(
            text = steps,
            style = TextStyle(
                fontSize = 12.sp,
                color = infoTone.onAction,
                lineHeight = 17.sp
            )
        )
    }
}
