package com.personal.sleepalarm.app

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import com.personal.sleepalarm.di.ServiceLocator
import com.personal.sleepalarm.launcher.LauncherIconCatalog
import com.personal.sleepalarm.launcher.LauncherIconManager
import com.personal.sleepalarm.service.EventNotificationBuilder
import com.personal.sleepalarm.service.ReminderNotificationBuilder
import com.personal.sleepalarm.service.SleepNotificationBuilder
import com.personal.sleepalarm.util.AppLanguageManager
import com.personal.sleepalarm.ui.theme.ThemeCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Application-класс.
 *
 * Выполняет инициализацию, которая нужна до любого экрана:
 * - создаёт каналы уведомлений;
 * - гарантирует существование профиля настроек;
 * - предоставляет ServiceLocator.
 */
class App : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(base))
    }

    val serviceLocator: ServiceLocator by lazy {
        ServiceLocator(this)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        deleteReplacedNotificationChannels()

        // Каналы уведомлений должны существовать до первого startForeground.
        SleepNotificationBuilder(this).createNotificationChannels()

        // Заменённые каналы должны появиться в системных настройках сразу,
        // а не только после первого срабатывания события или напоминания.
        EventNotificationBuilder(this)
        ReminderNotificationBuilder(this)

        // Создаём строку профиля по умолчанию,
        // чтобы главный экран сразу показывал валидные настройки.
        applicationScope.launch {
            serviceLocator.profileRepository.ensureProfileExists()
        }

        // Restores the selected alias after app updates/reboots and follows the
        // current theme only when the optional auto-match mode is enabled.
        applicationScope.launch {
            val iconPreference = serviceLocator.launcherIconPreference
            iconPreference.sanitizeSelection()
            combine(
                iconPreference.observeSelectedId(),
                iconPreference.observeAutoMatch(),
                serviceLocator.themePreference.observeThemeId()
            ) { selectedId, autoMatch, themeId ->
                if (autoMatch) {
                    LauncherIconCatalog.forTheme(
                        themeId = themeId,
                        isDark = ThemeCatalog.byId(themeId).isDark
                    ).id
                } else {
                    selectedId
                }
            }
                .distinctUntilChanged()
                .collect(LauncherIconManager(this@App)::activate)
        }
    }

    /** Убирает системные дубликаты каналов, заменённых в версии 1.2.1. */
    private fun deleteReplacedNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        REPLACED_NOTIFICATION_CHANNELS.forEach(notificationManager::deleteNotificationChannel)
    }

    private companion object {
        val REPLACED_NOTIFICATION_CHANNELS = listOf(
            "pomodoro_channel",
            "pomodoro_channel_app_volume_v2",
            "focus_protocol_channel",
            "focus_protocol_channel_app_volume_v2",
            "reminder_fire_channel",
            "reminder_fire_channel_app_volume_v2",
            "calendar_event_channel",
            "calendar_event_channel_app_volume_v2"
        )
    }
}
