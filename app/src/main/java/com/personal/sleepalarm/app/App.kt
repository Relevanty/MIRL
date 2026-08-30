package com.personal.sleepalarm.app

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import com.personal.sleepalarm.alarm.SleepAutomationScheduler
import com.personal.sleepalarm.alarm.DailyPlanNudgeScheduler
import com.personal.sleepalarm.alarm.EventAlarmScheduler
import com.personal.sleepalarm.alarm.TaskDeadlineScheduler
import com.personal.sleepalarm.di.ServiceLocator
import com.personal.sleepalarm.launcher.LauncherIconCatalog
import com.personal.sleepalarm.launcher.LauncherIconManager
import com.personal.sleepalarm.service.EventNotificationBuilder
import com.personal.sleepalarm.service.ReminderNotificationBuilder
import com.personal.sleepalarm.service.SleepNotificationBuilder
import com.personal.sleepalarm.service.DailyPlanNotificationBuilder
import com.personal.sleepalarm.data.repository.TaskEcosystemRepository
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.domain.model.focusItemTaskId
import com.personal.sleepalarm.util.AppLanguageManager
import com.personal.sleepalarm.ui.theme.ThemeCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview

/**
 * Application-класс.
 *
 * Выполняет инициализацию, которая нужна до любого экрана:
 * - создаёт каналы уведомлений;
 * - гарантирует существование профиля настроек;
 * - предоставляет ServiceLocator.
 */
@OptIn(FlowPreview::class)
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
        // Убираем подтверждения прошлой ночи, которые были созданы старой
        // версией без собственного срока жизни. Активную сессию не затрагиваем.
        SleepNotificationBuilder.cancelTransientNotifications(this)

        // Заменённые каналы должны появиться в системных настройках сразу,
        // а не только после первого срабатывания события или напоминания.
        val eventNotifications = EventNotificationBuilder(this)
        val reminderNotifications = ReminderNotificationBuilder(this)
        DailyPlanNotificationBuilder(this)

        // Создаём строку профиля по умолчанию,
        // чтобы главный экран сразу показывал валидные настройки.
        applicationScope.launch {
            serviceLocator.profileRepository.ensureProfileExists()
            val database = serviceLocator.database
            database.focusProtocolDao().getActive().forEach { session ->
                val linkedTaskId = focusItemTaskId(session.itemId)
                    ?: session.itemId.takeIf {
                        session.activityType == FocusActivityType.WORK && it > 0
                    }
                if (linkedTaskId != null && database.taskDao().getById(linkedTaskId) == null) {
                    serviceLocator.focusProtocolManager.cancel(session.id, "MISSING_TASK")
                }
            }
            TaskEcosystemRepository(database).repairIntegrity()

            // Reconcile durable rows with AlarmManager and the notification
            // shade. This closes process-death gaps after a Room transaction:
            // disabled/orphan reminders and terminal focus sessions must not
            // leave actionable UI behind.
            val schedulableReminders = database.reminderDao().getAll().mapNotNull { reminder ->
                serviceLocator.reminderRepository.reconcileForScheduling(reminder).also { reconciled ->
                    if (reconciled == null) {
                        serviceLocator.reminderScheduler.cancel(reminder.id)
                        reminderNotifications.cancelPre(reminder.id)
                        reminderNotifications.cancelFire(reminder.id)
                    }
                }
            }
            schedulableReminders.forEach(serviceLocator.reminderScheduler::schedule)
            serviceLocator.focusProtocolManager.reconcileActiveSessions()

            val now = System.currentTimeMillis()
            val events = database.calendarEventDao().getAll()
            val schedulableEvents = database.calendarEventDao().getSchedulableForAlarms()
            val schedulableEventIds = schedulableEvents.mapTo(hashSetOf()) { it.id }
            events.filter { event ->
                event.id !in schedulableEventIds || event.reminderMinutes == null ||
                    (event.repeatRule !in setOf("daily", "weekly") && event.endMillis < now)
            }.forEach { event -> eventNotifications.cancel(event.id) }
            EventAlarmScheduler(this@App).rescheduleAll(schedulableEvents)

            val explicitDeadlineTaskIds = schedulableReminders.asSequence()
                .filter { it.linkedType == "TASK" }
                .filter { it.triggerRule in setOf("BEFORE_DEADLINE", "BECOMES_URGENT") }
                .mapNotNull { it.linkedId }
                .toSet()
            val deadlineScheduler = TaskDeadlineScheduler(this@App)
            database.taskDao().getAll().forEach { task ->
                if (task.id in explicitDeadlineTaskIds) deadlineScheduler.cancel(task.id)
                else deadlineScheduler.schedule(task)
            }

            if (database.sleepSessionDao().getActiveSession() == null) {
                SleepNotificationBuilder.cancelSleepNotification(this@App)
                SleepNotificationBuilder.cancelAlarmNotification(this@App)
                SleepNotificationBuilder.cancelTransientNotifications(this@App)
            }
            SleepAutomationScheduler(this@App).scheduleNext()
        }

        // One debounced reconciliation stream keeps the daily-plan card in
        // sync with task edits, manual/Pomodoro history, focus and sleep. The
        // initial process-start pass is silent; later changes may alert only
        // when the persisted repeat policy says they are due.
        applicationScope.launch {
            val database = serviceLocator.database
            val scheduler = DailyPlanNudgeScheduler(this@App, database = database)
            var initialEmission = true
            merge(
                database.taskDao().observeAll().map { Unit },
                database.activityRecordDao().observeAll().map { Unit },
                database.focusProtocolDao().observeActive().map { Unit },
                database.sleepSessionDao().observeActiveSession().map { Unit },
                com.personal.sleepalarm.data.preferences.SleepAutomationPreference(this@App)
                    .observe().map { Unit },
                com.personal.sleepalarm.data.preferences.DailyPlanNudgePreferences(this@App)
                    .observe().map { Unit },
                serviceLocator.profileRepository.observeProfile().map { Unit }
            )
                .debounce(500L)
                .collect {
                    if (initialEmission) {
                        initialEmission = false
                        scheduler.reschedule()
                    } else {
                        scheduler.refreshNow(playSoundIfDue = true)
                    }
                }
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

    /** Убирает только устаревшие системные каналы, не затрагивая действующие. */
    private fun deleteReplacedNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        REPLACED_NOTIFICATION_CHANNELS.forEach(notificationManager::deleteNotificationChannel)
    }

    private companion object {
        val REPLACED_NOTIFICATION_CHANNELS = listOf(
            "pomodoro_channel",
            "pomodoro_channel_app_volume_v2",
            "pomodoro_channel_app_volume_v3",
            "focus_protocol_channel",
            "focus_protocol_channel_app_volume_v2",
            "reminder_fire_channel",
            "reminder_fire_channel_app_volume_v2",
            "calendar_event_channel"
        )
    }
}
