package com.personal.sleepalarm.ui

import android.os.Build
import android.content.Context
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.sleepalarm.app.App
import com.personal.sleepalarm.ui.theme.ThemeCatalog
import androidx.compose.runtime.getValue
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.personal.sleepalarm.ui.alarm.AlarmEvent
import com.personal.sleepalarm.ui.alarm.AlarmScreen
import com.personal.sleepalarm.ui.alarm.AlarmViewModel
import com.personal.sleepalarm.ui.theme.SleepAlarmTheme
import com.personal.sleepalarm.util.IntentExtras
import com.personal.sleepalarm.util.AppLanguageManager
import kotlinx.coroutines.launch


/**
 * Экран будильника.
 *
 * Тема зафиксирована на DARK + dynamicColor = false,
 * чтобы не ослеплять ночью/в полутьме.
 */
class AlarmActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    private val viewModel: AlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        super.onCreate(savedInstanceState)

        // Убираем только дублирующее full-screen уведомление. Foreground
        // уведомление сервиса остаётся, пока пользователь не выключит сигнал.
        com.personal.sleepalarm.service.SleepNotificationBuilder
            .cancelAlarmNotification(this)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        handleIntent(intent)

        setContent {
            val app = application as App
            val themeId by app.serviceLocator.themePreference
                .observeThemeId()
                .collectAsStateWithLifecycle(initialValue = ThemeCatalog.DEFAULT_ID)

            SleepAlarmTheme(themeId = themeId) {
                AlarmScreen(viewModel = viewModel)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        AlarmEvent.Finish -> finish()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val sessionId = intent?.getIntExtra(IntentExtras.EXTRA_SESSION_ID, -1) ?: -1
        viewModel.startAlarm(sessionId)
    }
}
