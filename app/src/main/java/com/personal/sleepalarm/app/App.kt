package com.personal.sleepalarm.app

import android.app.Application
import android.content.Context
import com.personal.sleepalarm.di.ServiceLocator
import com.personal.sleepalarm.service.SleepNotificationBuilder
import com.personal.sleepalarm.util.AppLanguageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

        // Каналы уведомлений должны существовать до первого startForeground.
        SleepNotificationBuilder(this).createNotificationChannels()

        // Создаём строку профиля по умолчанию,
        // чтобы главный экран сразу показывал валидные настройки.
        applicationScope.launch {
            serviceLocator.profileRepository.ensureProfileExists()
        }
    }
}
