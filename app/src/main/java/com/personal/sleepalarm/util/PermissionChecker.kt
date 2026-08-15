package com.personal.sleepalarm.util

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Состояние критичных разрешений + Intent'ы для их настройки.
 *
 * ВАЖНО: SYSTEM_ALERT_WINDOW («поверх других окон») НЕ нужен для будильника —
 * он показывается через USE_FULL_SCREEN_INTENT + showWhenLocked.
 */
data class PermissionState(
    val notificationsGranted: Boolean = false,
    val exactAlarmsAllowed: Boolean = false,
    val batteryOptimizationDisabled: Boolean = false,
    val fullScreenIntentAllowed: Boolean = false,
    val usesXiaomiAlarmPermissions: Boolean = false,
    val notificationPolicyAccessGranted: Boolean = false,
    val notificationsIntent: Intent? = null,
    val exactAlarmsIntent: Intent? = null,
    val batteryOptimizationIntent: Intent? = null,
    val fullScreenIntentSettings: Intent? = null,
    val notificationPolicyIntent: Intent? = null
) {
    val allRequiredGranted: Boolean
        get() = notificationsGranted &&
                exactAlarmsAllowed &&
                batteryOptimizationDisabled &&
                fullScreenIntentAllowed &&
                notificationPolicyAccessGranted

    val allGranted: Boolean
        get() = allRequiredGranted
}

object PermissionChecker {

    fun state(context: Context): PermissionState {
        val appContext = context.applicationContext
        val packageName = appContext.packageName

        val notificationsOk = checkNotifications(appContext)
        val exactAlarmsOk = checkExactAlarms(appContext)
        val batteryOk = checkBatteryOptimization(appContext)
        val systemFullScreenOk = canUseSystemFullScreenIntent(appContext)
        val usesXiaomiAlarmPermissions = isXiaomiFamilyDevice() && !systemFullScreenOk
        val fullScreenOk = systemFullScreenOk ||
                (usesXiaomiAlarmPermissions && hasXiaomiAlarmWindowPermissions(appContext))
        val notificationPolicyOk = checkNotificationPolicyAccess(appContext)

        val notificationsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val exactAlarmsIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else null

        val batteryIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // В HyperOS 1.0 на части Xiaomi/POCO экран Android 14 для
        // USE_FULL_SCREEN_INTENT падает внутри Settings.apk. В этом случае
        // открываем штатный редактор разрешений MIUI/HyperOS, где пользователь
        // включает «Экран блокировки» и «Открытие новых окон в фоне».
        val fullScreenIntent = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> null
            isXiaomiFamilyDevice() -> Intent(MIUI_APP_PERMISSION_EDITOR).apply {
                setClassName(MIUI_SECURITY_CENTER_PACKAGE, MIUI_PERMISSION_EDITOR_ACTIVITY)
                addCategory(Intent.CATEGORY_DEFAULT)
                putExtra(MIUI_PACKAGE_EXTRA, packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            else -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        val notificationPolicyIntent = Intent(
            Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return PermissionState(
            notificationsGranted = notificationsOk,
            exactAlarmsAllowed = exactAlarmsOk,
            batteryOptimizationDisabled = batteryOk,
            fullScreenIntentAllowed = fullScreenOk,
            usesXiaomiAlarmPermissions = usesXiaomiAlarmPermissions,
            notificationPolicyAccessGranted = notificationPolicyOk,
            notificationsIntent = notificationsIntent,
            exactAlarmsIntent = exactAlarmsIntent,
            batteryOptimizationIntent = batteryIntent,
            fullScreenIntentSettings = fullScreenIntent,
            notificationPolicyIntent = notificationPolicyIntent
        )
    }

    private fun checkNotifications(context: Context): Boolean {
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

        return runtimeGranted &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun checkExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    private fun checkBatteryOptimization(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun canUseSystemFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.canUseFullScreenIntent()
    }

    fun hasXiaomiAlarmWindowPermissions(context: Context): Boolean {
        if (!isXiaomiFamilyDevice()) return false
        return checkXiaomiAppOp(context, MIUI_OP_SHOW_ON_LOCK_SCREEN) &&
                checkXiaomiAppOp(context, MIUI_OP_BACKGROUND_START_ACTIVITY)
    }

    fun shouldLaunchAlarmDirectly(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                !canUseSystemFullScreenIntent(context) &&
                hasXiaomiAlarmWindowPermissions(context)
    }

    private fun isXiaomiFamilyDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        return manufacturer.equals("xiaomi", ignoreCase = true) ||
                brand.equals("xiaomi", ignoreCase = true) ||
                brand.equals("redmi", ignoreCase = true) ||
                brand.equals("poco", ignoreCase = true)
    }

    /**
     * HyperOS хранит эти два разрешения как OEM AppOps. Числовые операции
     * принадлежат Xiaomi и не входят в публичные константы Android SDK.
     */
    private fun checkXiaomiAppOp(context: Context, operation: Int): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return runCatching {
            val method = AppOpsManager::class.java.getDeclaredMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            method.isAccessible = true
            method.invoke(
                appOps,
                operation,
                context.applicationInfo.uid,
                context.packageName
            ) as Int
        }.getOrNull() == AppOpsManager.MODE_ALLOWED
    }

    private fun checkNotificationPolicyAccess(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    // === Совместимость со старым кодом ===
    fun exactAlarmsIntent(context: Context): Intent? = state(context).exactAlarmsIntent
    fun notificationsIntent(context: Context): Intent? = state(context).notificationsIntent
    fun batteryOptimizationIntent(context: Context): Intent? = state(context).batteryOptimizationIntent
    fun fullScreenIntentSettings(context: Context): Intent? = state(context).fullScreenIntentSettings
    fun notificationPolicyIntent(context: Context): Intent? = state(context).notificationPolicyIntent

    private const val MIUI_APP_PERMISSION_EDITOR = "miui.intent.action.APP_PERM_EDITOR"
    private const val MIUI_SECURITY_CENTER_PACKAGE = "com.miui.securitycenter"
    private const val MIUI_PERMISSION_EDITOR_ACTIVITY =
        "com.miui.permcenter.permissions.PermissionsEditorActivity"
    private const val MIUI_PACKAGE_EXTRA = "extra_pkgname"
    private const val MIUI_OP_SHOW_ON_LOCK_SCREEN = 10020
    private const val MIUI_OP_BACKGROUND_START_ACTIVITY = 10021
}
