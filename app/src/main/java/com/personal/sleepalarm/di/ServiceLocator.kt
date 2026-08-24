package com.personal.sleepalarm.di

import android.content.Context
import com.personal.sleepalarm.alarm.AlarmScheduler
import com.personal.sleepalarm.alarm.ReminderScheduler
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.preferences.BriefingPreference
import com.personal.sleepalarm.data.preferences.ThemePreference
import com.personal.sleepalarm.data.repository.DDayRepository
import com.personal.sleepalarm.data.repository.FocusProtocolRepository
import com.personal.sleepalarm.data.repository.LibraryRepository
import com.personal.sleepalarm.data.repository.MoodRepository
import com.personal.sleepalarm.data.repository.PomodoroRepository
import com.personal.sleepalarm.data.repository.ReminderRepository
import com.personal.sleepalarm.data.repository.ScheduleRepository
import com.personal.sleepalarm.data.repository.SleepProfileRepository
import com.personal.sleepalarm.data.repository.SleepSessionRepository
import com.personal.sleepalarm.data.repository.TaskRepository
import com.personal.sleepalarm.service.audio.BriefingCoordinator
import com.personal.sleepalarm.service.focus.FocusProtocolManager

/**
 * Ручной DI-контейнер.
 *
 * ДОБАВЛЕНО (v5): taskRepository, reminderRepository, ddayRepository,
 * moodRepository, reminderScheduler, briefingPreference, briefingCoordinator.
 */
class ServiceLocator(
    context: Context
) {

    private val appContext: Context = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    // === Существующие (v1-v4) ===

    val profileRepository: SleepProfileRepository by lazy {
        SleepProfileRepository(profileDao = database.alarmProfileDao())
    }

    val sessionRepository: SleepSessionRepository by lazy {
        SleepSessionRepository(
            database = database,
            sessionDao = database.sleepSessionDao(),
            cueEventDao = database.cueEventDao()
        )
    }

    val themePreference: ThemePreference by lazy { ThemePreference(appContext) }

    val scheduleRepository: ScheduleRepository by lazy {
        ScheduleRepository(database.scheduleDao())
    }

    val pomodoroRepository: PomodoroRepository by lazy {
        PomodoroRepository(database.pomodoroDao())
    }

    val focusProtocolRepository: FocusProtocolRepository by lazy {
        FocusProtocolRepository(database.focusProtocolDao(), database.energySampleDao())
    }

    val focusProtocolManager: FocusProtocolManager by lazy {
        FocusProtocolManager(appContext)
    }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(database, database.libraryDao())
    }

    // === ДОБАВЛЕНО (v5) ===

    val taskRepository: TaskRepository by lazy {
        TaskRepository(database.taskDao())
    }

    val reminderRepository: ReminderRepository by lazy {
        ReminderRepository(database.reminderDao(), database.taskDao())
    }

    val ddayRepository: DDayRepository by lazy {
        DDayRepository(database.ddayDao())
    }

    val moodRepository: MoodRepository by lazy {
        MoodRepository(database.moodEntryDao())
    }

    val reminderScheduler: ReminderScheduler by lazy {
        ReminderScheduler(appContext)
    }

    val briefingPreference: BriefingPreference by lazy {
        BriefingPreference(appContext)
    }

    val briefingCoordinator: BriefingCoordinator by lazy {
        BriefingCoordinator(appContext, briefingPreference)
    }

    fun createAlarmScheduler(): AlarmScheduler {
        return AlarmScheduler.create(
            context = appContext,
            sessionRepository = sessionRepository
        )
    }
}
