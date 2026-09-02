package com.personal.sleepalarm.data.db


import com.personal.sleepalarm.data.db.dao.SubjectDao
import com.personal.sleepalarm.data.db.dao.StudySessionDao
import com.personal.sleepalarm.data.db.dao.DiaryDao
import com.personal.sleepalarm.data.db.entity.DiaryEntryEntity
import com.personal.sleepalarm.data.db.dao.CalendarEventDao
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personal.sleepalarm.data.db.dao.AlarmProfileDao
import com.personal.sleepalarm.data.db.dao.CueEventDao
import com.personal.sleepalarm.data.db.dao.DDayDao
import com.personal.sleepalarm.data.db.dao.EnergySampleDao
import com.personal.sleepalarm.data.db.dao.EnglishStudyDao
import com.personal.sleepalarm.data.db.dao.FocusProtocolDao
import com.personal.sleepalarm.data.db.dao.LibraryDao
import com.personal.sleepalarm.data.db.dao.MoodEntryDao
import com.personal.sleepalarm.data.db.dao.PomodoroDao
import com.personal.sleepalarm.data.db.dao.OtherActivityDao
import com.personal.sleepalarm.data.db.dao.ReminderDao
import com.personal.sleepalarm.data.db.dao.ScheduleDao
import com.personal.sleepalarm.data.db.dao.SleepSessionDao
import com.personal.sleepalarm.data.db.dao.TaskDao
import com.personal.sleepalarm.data.db.dao.ActivityRecordDao
import com.personal.sleepalarm.data.db.dao.ProjectDao
import com.personal.sleepalarm.data.db.dao.TaskAttachmentDao
import com.personal.sleepalarm.data.db.dao.TaskLibraryLinkDao
import com.personal.sleepalarm.data.db.dao.TaskSubtaskDao
import com.personal.sleepalarm.data.db.dao.ContextSnapshotDao
import com.personal.sleepalarm.data.db.dao.DailyCheckInDao
import com.personal.sleepalarm.data.db.dao.EnergyObservationDao
import com.personal.sleepalarm.data.db.dao.ExternalContextDao
import com.personal.sleepalarm.data.db.dao.RecommendationDecisionDao
import com.personal.sleepalarm.data.db.dao.TaskDemandProfileDao
import com.personal.sleepalarm.data.db.dao.TaskDependencyDao
import com.personal.sleepalarm.data.db.dao.WorkEpisodeAssessmentDao
import com.personal.sleepalarm.data.db.entity.AlarmProfileEntity
import com.personal.sleepalarm.data.db.entity.CueEventEntity
import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.data.db.entity.EnergySampleEntity
import com.personal.sleepalarm.data.db.entity.EnglishWordEntity
import com.personal.sleepalarm.data.db.entity.EnglishWordProgressEntity
import com.personal.sleepalarm.data.db.entity.EnglishDictionaryMetadataEntity
import com.personal.sleepalarm.data.db.entity.EnglishCardProgressEntity
import com.personal.sleepalarm.data.db.entity.EnglishStudyCardEntity
import com.personal.sleepalarm.data.db.entity.EnglishStudySetEntity
import com.personal.sleepalarm.data.db.entity.EnglishWordDirectionalProgressEntity
import com.personal.sleepalarm.data.db.entity.EnglishWordSenseEntity
import com.personal.sleepalarm.data.db.entity.FocusProtocolSessionEntity
import com.personal.sleepalarm.data.db.entity.LibraryItemEntity
import com.personal.sleepalarm.data.db.entity.LibraryItemTagCrossRef
import com.personal.sleepalarm.data.db.entity.LibraryTagEntity
import com.personal.sleepalarm.data.db.entity.MoodEntryEntity
import com.personal.sleepalarm.data.db.entity.PomodoroSessionEntity
import com.personal.sleepalarm.data.db.entity.OtherActivityEntity
import com.personal.sleepalarm.data.db.entity.ReminderEntity
import com.personal.sleepalarm.data.db.entity.ScheduleEntity
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.data.db.entity.CalendarEventEntity
import com.personal.sleepalarm.data.db.entity.StudySessionEntity
import com.personal.sleepalarm.data.db.entity.SubjectEntity
import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.ProjectEntity
import com.personal.sleepalarm.data.db.entity.TaskAttachmentEntity
import com.personal.sleepalarm.data.db.entity.TaskLibraryLinkEntity
import com.personal.sleepalarm.data.db.entity.TaskSubtaskEntity
import com.personal.sleepalarm.data.db.entity.ContextSnapshotEntity
import com.personal.sleepalarm.data.db.entity.DailyCheckInEntity
import com.personal.sleepalarm.data.db.entity.EnergyObservationEntity
import com.personal.sleepalarm.data.db.entity.ExternalContextEntity
import com.personal.sleepalarm.data.db.entity.RecommendationDecisionEntity
import com.personal.sleepalarm.data.db.entity.TaskDemandProfileEntity
import com.personal.sleepalarm.data.db.entity.TaskDependencyEntity
import com.personal.sleepalarm.data.db.entity.WorkEpisodeAssessmentEntity

internal const val APP_DATABASE_VERSION = 29

/**
 * Главная база приложения.
 *
 * Версия 16:
 *  - tasks: задачи и утренняя рутина со стриками
 *  - reminders: напоминания с повторами (ONCE/DAILY/WEEKLY/INTERVAL)
 *  - mood_entries: настроение (одна запись в день)
 *  - dday_events: обратные отсчёты до событий
 *  - focus_protocol_sessions и energy_samples: устойчивый протокол фокуса
 *
 * Старые таблицы (v1-v4) не изменяются — данные пользователя в безопасности.
 */
@Database(
    entities = [
        // Существующие (v1-v4):
        AlarmProfileEntity::class,
        SleepSessionEntity::class,
        CueEventEntity::class,
        ScheduleEntity::class,
        PomodoroSessionEntity::class,
        LibraryItemEntity::class,
        LibraryTagEntity::class,
        LibraryItemTagCrossRef::class,

        // ДОБАВЛЕНО (v5):
        TaskEntity::class,
        ReminderEntity::class,
        MoodEntryEntity::class,
        DDayEntity::class,

        // ДОБАВЛЕНО (v6):
        SubjectEntity::class,
        StudySessionEntity::class,
        CalendarEventEntity::class,

        DiaryEntryEntity::class,

        // ДОБАВЛЕНО (v8): дела для категории «Другое» в помодоро.
        OtherActivityEntity::class,

        // ДОБАВЛЕНО (v9): устойчивый протокол фокуса и замеры энергии.
        FocusProtocolSessionEntity::class,
        EnergySampleEntity::class,

        // Unified offline productivity model (v17).
        ProjectEntity::class,
        TaskSubtaskEntity::class,
        TaskAttachmentEntity::class,
        TaskLibraryLinkEntity::class,
        ActivityRecordEntity::class,

        // Offline English vocabulary and spaced-repetition progress (v25).
        EnglishWordEntity::class,
        EnglishWordProgressEntity::class,
        EnglishDictionaryMetadataEntity::class,

        // User study sets, structured articles and per-direction progress (v26).
        EnglishWordSenseEntity::class,
        EnglishWordDirectionalProgressEntity::class,
        EnglishStudySetEntity::class,
        EnglishStudyCardEntity::class,
        EnglishCardProgressEntity::class,

        // Adaptive energy and planning history (v28).
        DailyCheckInEntity::class,
        EnergyObservationEntity::class,
        TaskDemandProfileEntity::class,
        TaskDependencyEntity::class,
        WorkEpisodeAssessmentEntity::class,
        ExternalContextEntity::class,
        ContextSnapshotEntity::class,
        RecommendationDecisionEntity::class

    ],
    version = APP_DATABASE_VERSION,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    // Существующие DAO (v1-v4):
    abstract fun alarmProfileDao(): AlarmProfileDao
    abstract fun sleepSessionDao(): SleepSessionDao
    abstract fun cueEventDao(): CueEventDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun pomodoroDao(): PomodoroDao
    abstract fun otherActivityDao(): OtherActivityDao
    abstract fun focusProtocolDao(): FocusProtocolDao
    abstract fun energySampleDao(): EnergySampleDao
    abstract fun libraryDao(): LibraryDao
    abstract fun activityRecordDao(): ActivityRecordDao
    abstract fun projectDao(): ProjectDao
    abstract fun taskSubtaskDao(): TaskSubtaskDao
    abstract fun taskAttachmentDao(): TaskAttachmentDao
    abstract fun taskLibraryLinkDao(): TaskLibraryLinkDao

    // ДОБАВЛЕНО (v5):
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun moodEntryDao(): MoodEntryDao
    abstract fun ddayDao(): DDayDao

    // ДОБАВЛЕНО (v6):

    abstract fun subjectDao(): SubjectDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun calendarEventDao(): CalendarEventDao

    abstract fun diaryDao(): DiaryDao
    abstract fun englishStudyDao(): EnglishStudyDao

    // Adaptive energy and planning (v28).
    abstract fun dailyCheckInDao(): DailyCheckInDao
    abstract fun energyObservationDao(): EnergyObservationDao
    abstract fun taskDemandProfileDao(): TaskDemandProfileDao
    abstract fun taskDependencyDao(): TaskDependencyDao
    abstract fun workEpisodeAssessmentDao(): WorkEpisodeAssessmentDao
    abstract fun externalContextDao(): ExternalContextDao
    abstract fun contextSnapshotDao(): ContextSnapshotDao
    abstract fun recommendationDecisionDao(): RecommendationDecisionDao

    companion object {
        private const val DATABASE_NAME = "sleep-alarm.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also {
                    instance = it
                }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                    MIGRATION_21_22,
                    MIGRATION_22_23,
                    MIGRATION_23_24,
                    MIGRATION_24_25,
                    MIGRATION_25_26,
                    MIGRATION_26_27,
                    MIGRATION_27_28,
                    MIGRATION_28_29
                )
                .build()
        }

        // === Существующие миграции (НЕ менять) ===

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarm_profiles ADD COLUMN vibrationEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE alarm_profiles ADD COLUMN alarmRingtoneUri TEXT")
                db.execSQL("ALTER TABLE alarm_profiles ADD COLUMN cueScheduleMode TEXT NOT NULL DEFAULT 'REM_TARGETED'")
                db.execSQL("ALTER TABLE alarm_profiles ADD COLUMN remCueOffsetPercent INTEGER NOT NULL DEFAULT 40")
                db.execSQL("ALTER TABLE alarm_profiles ADD COLUMN autoDetectOnsetEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alarm_profiles ADD COLUMN autoCorrectWakeEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alarm_profiles ADD COLUMN smartRepeatEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE alarm_profiles ADD COLUMN smartRepeatFirstDelayMinutes INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE alarm_profiles ADD COLUMN smartRepeatIntervalMinutes INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE alarm_profiles ADD COLUMN smartRepeatMaxCount INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE alarm_profiles ADD COLUMN mirrorToSystemClock INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sleep_sessions ADD COLUMN detectedSleepOnsetTime INTEGER")
                db.execSQL("ALTER TABLE sleep_sessions ADD COLUMN detectedOnsetLatencyMinutes INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarm_profiles ADD COLUMN cueRingtoneUri TEXT")
                db.execSQL("ALTER TABLE sleep_sessions ADD COLUMN cueRingtoneUri TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule` (
                        `id` INTEGER NOT NULL,
                        `content` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pomodoro_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `durationMinutes` INTEGER NOT NULL,
                        `completedAt` INTEGER,
                        `isCompleted` INTEGER NOT NULL,
                        `isBreak` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pomodoro_sessions_startedAt` ON `pomodoro_sessions` (`startedAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `library_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `author` TEXT NOT NULL,
                        `coverUri` TEXT,
                        `shortDescription` TEXT NOT NULL,
                        `impression` TEXT NOT NULL,
                        `thoughts` TEXT NOT NULL,
                        `rating` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_items_type` ON `library_items` (`type`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `library_tags` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_library_tags_name` ON `library_tags` (`name`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `library_item_tags` (
                        `itemId` INTEGER NOT NULL,
                        `tagId` INTEGER NOT NULL,
                        PRIMARY KEY(`itemId`, `tagId`),
                        FOREIGN KEY(`itemId`) REFERENCES `library_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`tagId`) REFERENCES `library_tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_item_tags_tagId` ON `library_item_tags` (`tagId`)")
            }
        }

        // === ДОБАВЛЕНО: миграция 4 → 5 ===
        // Создаёт ТОЛЬКО 4 новые таблицы. Старые не трогаем.

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // === tasks ===
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tasks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `isDone` INTEGER NOT NULL,
                        `isMorningRoutine` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `completedAt` INTEGER,
                        `doneDate` TEXT,
                        `streakCount` INTEGER NOT NULL,
                        `reminderId` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_isMorningRoutine` ON `tasks` (`isMorningRoutine`)")

                // === reminders ===
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `timeHour` INTEGER NOT NULL,
                        `timeMinute` INTEGER NOT NULL,
                        `repeatMode` TEXT NOT NULL,
                        `daysOfWeek` INTEGER NOT NULL,
                        `intervalDays` INTEGER NOT NULL,
                        `nextTriggerTime` INTEGER NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_nextTriggerTime` ON `reminders` (`nextTriggerTime`)")

                // === mood_entries (уникальна дата) ===
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `mood_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` TEXT NOT NULL,
                        `mood` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_mood_entries_date` ON `mood_entries` (`date`)")

                // === dday_events ===
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `dday_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `targetDate` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dday_events_targetDate` ON `dday_events` (`targetDate`)")
            }
        }


        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS subjects (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "name TEXT NOT NULL, color INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS study_sessions (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "subjectId INTEGER NOT NULL, startMillis INTEGER NOT NULL, " +
                            "endMillis INTEGER NOT NULL, durationMillis INTEGER NOT NULL, " +
                            "dateKey TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS events (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "title TEXT NOT NULL, startMillis INTEGER NOT NULL, " +
                            "endMillis INTEGER NOT NULL, allDay INTEGER NOT NULL, " +
                            "repeatRule TEXT NOT NULL, reminderMinutes INTEGER, createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_events_startMillis ON events (startMillis)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS diary_entries (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "dateKey TEXT NOT NULL, text TEXT NOT NULL, " +
                            "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_diary_entries_dateKey ON diary_entries (dateKey)")
            }

        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pomodoro_sessions ADD COLUMN activityType TEXT NOT NULL DEFAULT 'STUDY'")
                db.execSQL("ALTER TABLE pomodoro_sessions ADD COLUMN subjectId INTEGER")
                db.execSQL("ALTER TABLE pomodoro_sessions ADD COLUMN taskId INTEGER")
                db.execSQL("ALTER TABLE pomodoro_sessions ADD COLUMN otherActivityId INTEGER")
                db.execSQL("ALTER TABLE pomodoro_sessions ADD COLUMN itemName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE pomodoro_sessions ADD COLUMN actualDurationMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE pomodoro_sessions " +
                        "SET actualDurationMillis = durationMinutes * 60000 " +
                        "WHERE isCompleted = 1 AND actualDurationMillis = 0"
                )
                // Переносим прежнюю учебную историю в единый журнал фокуса.
                db.execSQL(
                    "INSERT INTO pomodoro_sessions (" +
                        "startedAt, durationMinutes, completedAt, isCompleted, isBreak, " +
                        "activityType, subjectId, taskId, otherActivityId, itemName, actualDurationMillis) " +
                        "SELECT s.startMillis, CAST((s.durationMillis + 59999) / 60000 AS INTEGER), " +
                        "s.endMillis, 1, 0, 'STUDY', s.subjectId, NULL, NULL, " +
                        "COALESCE((SELECT name FROM subjects WHERE id = s.subjectId), ''), s.durationMillis " +
                        "FROM study_sessions s WHERE NOT EXISTS (" +
                        "SELECT 1 FROM pomodoro_sessions p WHERE p.startedAt = s.startMillis " +
                        "AND p.completedAt = s.endMillis AND p.activityType = 'STUDY')"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS other_activities (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, color INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `focus_protocol_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `activityType` TEXT NOT NULL,
                        `itemId` INTEGER NOT NULL,
                        `itemName` TEXT NOT NULL,
                        `outcome` TEXT NOT NULL,
                        `phase` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `phaseStartedAt` INTEGER NOT NULL,
                        `phaseEndsAt` INTEGER,
                        `resetDurationMinutes` INTEGER NOT NULL,
                        `focusDurationMinutes` INTEGER NOT NULL,
                        `recoveryDurationMinutes` INTEGER NOT NULL,
                        `energyBefore` INTEGER NOT NULL,
                        `energyAfter` INTEGER,
                        `distractionCount` INTEGER NOT NULL,
                        `focusStartedAt` INTEGER,
                        `focusElapsedMillis` INTEGER NOT NULL,
                        `pausedRemainingMillis` INTEGER NOT NULL,
                        `completedAt` INTEGER,
                        `cancelReason` TEXT,
                        `pomodoroRecorded` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_focus_protocol_sessions_phase` " +
                        "ON `focus_protocol_sessions` (`phase`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_focus_protocol_sessions_createdAt` " +
                        "ON `focus_protocol_sessions` (`createdAt`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `energy_samples` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `energy` INTEGER NOT NULL,
                        `context` TEXT NOT NULL,
                        `protocolSessionId` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_energy_samples_timestamp` " +
                        "ON `energy_samples` (`timestamp`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_energy_samples_protocolSessionId` " +
                        "ON `energy_samples` (`protocolSessionId`)"
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE focus_protocol_sessions " +
                        "ADD COLUMN completedCycles INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE focus_protocol_sessions " +
                        "ADD COLUMN totalFocusMillis INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE alarm_profiles " +
                        "ADD COLUMN notificationVolumePercent INTEGER NOT NULL DEFAULT 50"
                )
            }
        }

        /** Расширяет обычные задачи до карточек матрицы Эйзенхауэра. */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN matrixQuadrant INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE tasks ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN whyImportant TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN definitionOfDone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN nextAction TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN imagePath TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN dueAtMillis INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN estimatedMinutes INTEGER NOT NULL DEFAULT 25")
                db.execSQL("ALTER TABLE tasks ADD COLUMN energyLevel TEXT NOT NULL DEFAULT 'MEDIUM'")
                db.execSQL("ALTER TABLE tasks ADD COLUMN contextTag TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN dependencies TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN obstacle TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN ifThenPlan TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN checklist TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN projectTag TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN assignee TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE tasks SET updatedAt = createdAt WHERE updatedAt = 0")
            }
        }

        /** Добавляет фактическое время работы и стабильный порядок задач. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN spentMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Хранит качество и источник оценки времени засыпания. */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sleep_sessions ADD COLUMN detectedOnsetConfidencePercent INTEGER")
                db.execSQL("ALTER TABLE sleep_sessions ADD COLUMN detectedOnsetSource TEXT")
            }
        }

        /** Отличает фактический таймер от ручного прошедшего события. */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pomodoro_sessions ADD COLUMN recordSource TEXT NOT NULL DEFAULT 'TIMER'")
            }
        }

        /** Заполняет стабильный порядок шариков для уже существующих задач. */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE tasks
                    SET sortOrder = (
                        SELECT COUNT(*)
                        FROM tasks AS previous
                        WHERE previous.matrixQuadrant = tasks.matrixQuadrant
                          AND (
                              previous.createdAt < tasks.createdAt
                              OR (previous.createdAt = tasks.createdAt AND previous.id < tasks.id)
                          )
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Introduces the canonical actual-work journal and relational productivity
         * objects. Existing Pomodoro history is copied, then task totals are rebuilt.
         */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN workBudgetMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN projectId INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN category TEXT NOT NULL DEFAULT 'WORK'")
                db.execSQL("ALTER TABLE tasks ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN materials TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN expectedResult TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN startAtMillis INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN repeatRule TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN plannedFocusMinutes INTEGER NOT NULL DEFAULT 25")

                db.execSQL("ALTER TABLE events ADD COLUMN eventKind TEXT NOT NULL DEFAULT 'PLANNED'")
                db.execSQL("ALTER TABLE events ADD COLUMN taskId INTEGER")
                db.execSQL("ALTER TABLE events ADD COLUMN projectId INTEGER")
                db.execSQL("ALTER TABLE reminders ADD COLUMN linkedType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reminders ADD COLUMN linkedId INTEGER")
                db.execSQL("ALTER TABLE dday_events ADD COLUMN projectId INTEGER")
                db.execSQL("ALTER TABLE dday_events ADD COLUMN taskId INTEGER")
                db.execSQL("ALTER TABLE dday_events ADD COLUMN notes TEXT NOT NULL DEFAULT ''")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `projects` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `goal` TEXT NOT NULL,
                        `color` INTEGER NOT NULL,
                        `workBudgetMinutes` INTEGER NOT NULL,
                        `spentMillis` INTEGER NOT NULL,
                        `dueAtMillis` INTEGER,
                        `isArchived` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_projects_isArchived` ON `projects` (`isArchived`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_projects_dueAtMillis` ON `projects` (`dueAtMillis`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `task_subtasks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `taskId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `isDone` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `completedAt` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_subtasks_taskId` ON `task_subtasks` (`taskId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `task_attachments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `taskId` INTEGER NOT NULL,
                        `localPath` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `caption` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_attachments_taskId` ON `task_attachments` (`taskId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `task_library_links` (
                        `taskId` INTEGER NOT NULL,
                        `libraryItemId` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`taskId`, `libraryItemId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_library_links_libraryItemId` ON `task_library_links` (`libraryItemId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `activity_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `taskId` INTEGER,
                        `projectId` INTEGER,
                        `activityType` TEXT NOT NULL,
                        `subjectId` INTEGER,
                        `otherActivityId` INTEGER,
                        `title` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `endedAt` INTEGER NOT NULL,
                        `durationMillis` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `result` TEXT NOT NULL,
                        `material` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `pomodoroSessionId` INTEGER,
                        `countsTowardProgress` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_records_startedAt` ON `activity_records` (`startedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_records_taskId` ON `activity_records` (`taskId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_records_projectId` ON `activity_records` (`projectId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_activity_records_pomodoroSessionId` ON `activity_records` (`pomodoroSessionId`)")

                val now = System.currentTimeMillis()
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO activity_records (
                        taskId, projectId, activityType, subjectId, otherActivityId,
                        title, category, startedAt, endedAt, durationMillis, source,
                        result, material, note, pomodoroSessionId, countsTowardProgress,
                        createdAt, updatedAt
                    )
                    SELECT p.taskId, t.projectId, p.activityType, p.subjectId, p.otherActivityId,
                        p.itemName, p.activityType, p.startedAt,
                        COALESCE(p.completedAt, p.startedAt + p.actualDurationMillis),
                        p.actualDurationMillis, p.recordSource, '', '', '', p.id, 1, $now, $now
                    FROM pomodoro_sessions p
                    LEFT JOIN tasks t ON t.id = p.taskId
                    WHERE p.isBreak = 0 AND p.actualDurationMillis > 0
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE tasks SET spentMillis = COALESCE((
                        SELECT SUM(a.durationMillis) FROM activity_records a
                        WHERE a.taskId = tasks.id AND a.countsTowardProgress = 1
                    ), 0)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sleep_sessions ADD COLUMN detectedOnsetUncertaintyMinutes INTEGER")
                db.execSQL("ALTER TABLE sleep_sessions ADD COLUMN onsetReviewState TEXT NOT NULL DEFAULT 'PENDING'")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_items ADD COLUMN resourceKind TEXT NOT NULL DEFAULT 'NOTE'")
                db.execSQL("ALTER TABLE library_items ADD COLUMN localFilePath TEXT")
                db.execSQL("ALTER TABLE library_items ADD COLUMN originalFileName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE library_items ADD COLUMN referenceUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN triggerRule TEXT NOT NULL DEFAULT 'AT_TIME'")
                db.execSQL("ALTER TABLE reminders ADD COLUMN offsetMinutes INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE reminders ADD COLUMN inactivityHours INTEGER NOT NULL DEFAULT 24")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarm_profiles ADD COLUMN autoCorrectMinConfidencePercent INTEGER NOT NULL DEFAULT 75")
                db.execSQL("ALTER TABLE alarm_profiles ADD COLUMN autoCorrectMaxShiftMinutes INTEGER NOT NULL DEFAULT 30")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sleep_sessions ADD COLUMN automationSafetyWakeTime INTEGER")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarm_profiles ADD COLUMN mathChallengeCount INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN isDailyRequired INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `english_words` (
                        `id` INTEGER NOT NULL,
                        `word` TEXT NOT NULL,
                        `translation` TEXT NOT NULL,
                        `hint` TEXT NOT NULL,
                        `pronunciation` TEXT NOT NULL,
                        `partOfSpeech` TEXT NOT NULL,
                        `level` TEXT NOT NULL,
                        `frequencyRank` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_english_words_word` ON `english_words` (`word`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_english_words_frequencyRank` ON `english_words` (`frequencyRank`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_english_words_level` ON `english_words` (`level`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `english_word_progress` (
                        `wordId` INTEGER NOT NULL,
                        `dueAtMillis` INTEGER NOT NULL,
                        `intervalMinutes` INTEGER NOT NULL,
                        `easePermille` INTEGER NOT NULL,
                        `repetitions` INTEGER NOT NULL,
                        `lapses` INTEGER NOT NULL,
                        `reviewCount` INTEGER NOT NULL,
                        `correctCount` INTEGER NOT NULL,
                        `cardReviews` INTEGER NOT NULL,
                        `writingReviews` INTEGER NOT NULL,
                        `pronunciationReviews` INTEGER NOT NULL,
                        `listeningReviews` INTEGER NOT NULL,
                        `lastGrade` TEXT NOT NULL,
                        `lastMode` TEXT NOT NULL,
                        `lastReviewedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`wordId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_english_word_progress_dueAtMillis` ON `english_word_progress` (`dueAtMillis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_english_word_progress_lastReviewedAtMillis` ON `english_word_progress` (`lastReviewedAtMillis`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `english_dictionary_metadata` (
                        `id` INTEGER NOT NULL,
                        `datasetVersion` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `english_word_senses` (
                        `wordId` INTEGER NOT NULL,
                        `senseOrder` INTEGER NOT NULL,
                        `definition` TEXT NOT NULL,
                        `translations` TEXT NOT NULL,
                        `example` TEXT NOT NULL,
                        `exampleTranslation` TEXT NOT NULL,
                        `synonyms` TEXT NOT NULL,
                        `usageLabels` TEXT NOT NULL,
                        PRIMARY KEY(`wordId`, `senseOrder`),
                        FOREIGN KEY(`wordId`) REFERENCES `english_words`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_english_word_senses_wordId` ON `english_word_senses` (`wordId`)")
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `english_word_senses` (
                        wordId, senseOrder, definition, translations, example,
                        exampleTranslation, synonyms, usageLabels
                    )
                    SELECT id, 0, hint, translation, '', '', '', partOfSpeech
                    FROM english_words
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `english_word_directional_progress` (
                        `wordId` INTEGER NOT NULL,
                        `direction` TEXT NOT NULL,
                        `dueAtMillis` INTEGER NOT NULL,
                        `intervalMinutes` INTEGER NOT NULL,
                        `easePermille` INTEGER NOT NULL,
                        `repetitions` INTEGER NOT NULL,
                        `lapses` INTEGER NOT NULL,
                        `reviewCount` INTEGER NOT NULL,
                        `correctCount` INTEGER NOT NULL,
                        `cardReviews` INTEGER NOT NULL,
                        `writingReviews` INTEGER NOT NULL,
                        `pronunciationReviews` INTEGER NOT NULL,
                        `listeningReviews` INTEGER NOT NULL,
                        `lastGrade` TEXT NOT NULL,
                        `lastMode` TEXT NOT NULL,
                        `lastReviewedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`wordId`, `direction`),
                        FOREIGN KEY(`wordId`) REFERENCES `english_words`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_english_word_directional_progress_dueAtMillis` ON `english_word_directional_progress` (`dueAtMillis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_english_word_directional_progress_lastReviewedAtMillis` ON `english_word_directional_progress` (`lastReviewedAtMillis`)")
                listOf("EN_TO_RU", "RU_TO_EN").forEach { direction ->
                    db.execSQL(
                        """
                        INSERT OR REPLACE INTO `english_word_directional_progress` (
                            wordId, direction, dueAtMillis, intervalMinutes, easePermille,
                            repetitions, lapses, reviewCount, correctCount, cardReviews,
                            writingReviews, pronunciationReviews, listeningReviews,
                            lastGrade, lastMode, lastReviewedAtMillis
                        )
                        SELECT wordId, '$direction', dueAtMillis, intervalMinutes, easePermille,
                            repetitions, lapses, reviewCount, correctCount, cardReviews,
                            writingReviews, pronunciationReviews, listeningReviews,
                            lastGrade, lastMode, lastReviewedAtMillis
                        FROM english_word_progress
                        """.trimIndent()
                    )
                }

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `english_study_sets` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `colorSeed` INTEGER NOT NULL,
                        `defaultDirection` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_english_study_sets_updatedAtMillis` ON `english_study_sets` (`updatedAtMillis`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `english_study_cards` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `setId` INTEGER NOT NULL,
                        `dictionaryWordId` INTEGER,
                        `term` TEXT NOT NULL,
                        `translation` TEXT NOT NULL,
                        `definition` TEXT NOT NULL,
                        `example` TEXT NOT NULL,
                        `exampleTranslation` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        FOREIGN KEY(`setId`) REFERENCES `english_study_sets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_english_study_cards_setId` ON `english_study_cards` (`setId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_english_study_cards_dictionaryWordId` ON `english_study_cards` (`dictionaryWordId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_english_study_cards_setId_position` ON `english_study_cards` (`setId`, `position`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `english_card_progress` (
                        `cardId` INTEGER NOT NULL,
                        `direction` TEXT NOT NULL,
                        `dueAtMillis` INTEGER NOT NULL,
                        `intervalMinutes` INTEGER NOT NULL,
                        `easePermille` INTEGER NOT NULL,
                        `repetitions` INTEGER NOT NULL,
                        `lapses` INTEGER NOT NULL,
                        `reviewCount` INTEGER NOT NULL,
                        `correctCount` INTEGER NOT NULL,
                        `cardReviews` INTEGER NOT NULL,
                        `writingReviews` INTEGER NOT NULL,
                        `pronunciationReviews` INTEGER NOT NULL,
                        `listeningReviews` INTEGER NOT NULL,
                        `lastGrade` TEXT NOT NULL,
                        `lastMode` TEXT NOT NULL,
                        `lastReviewedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`cardId`, `direction`),
                        FOREIGN KEY(`cardId`) REFERENCES `english_study_cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_english_card_progress_dueAtMillis` ON `english_card_progress` (`dueAtMillis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_english_card_progress_lastReviewedAtMillis` ON `english_card_progress` (`lastReviewedAtMillis`)")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE focus_protocol_sessions ADD COLUMN soundscapeId TEXT NOT NULL DEFAULT 'silence'")
                db.execSQL("ALTER TABLE focus_protocol_sessions ADD COLUMN soundscapeCustomUri TEXT")
                db.execSQL("ALTER TABLE focus_protocol_sessions ADD COLUMN soundscapeCustomName TEXT")
                db.execSQL("ALTER TABLE focus_protocol_sessions ADD COLUMN soundscapeVolume INTEGER NOT NULL DEFAULT 35")
                db.execSQL("ALTER TABLE focus_protocol_sessions ADD COLUMN soundscapeSecondaryId TEXT")
                db.execSQL("ALTER TABLE focus_protocol_sessions ADD COLUMN soundscapeSecondaryVolume INTEGER NOT NULL DEFAULT 20")
                db.execSQL("ALTER TABLE focus_protocol_sessions ADD COLUMN soundscapePlayDuringRecovery INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dday_events ADD COLUMN linksJson TEXT NOT NULL DEFAULT '[]'")
                migrateCanonicalTaskDeadlines(db)
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_check_ins` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `localDate` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `zoneId` TEXT NOT NULL,
                        `energy` INTEGER,
                        `mood` INTEGER,
                        `clarity` INTEGER,
                        `focus` INTEGER,
                        `social` INTEGER,
                        `physical` INTEGER,
                        `stress` INTEGER,
                        `source` TEXT NOT NULL,
                        `unusualDayFlags` TEXT NOT NULL,
                        `unusualDayNote` TEXT NOT NULL,
                        `excludedFromLearning` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_check_ins_localDate` ON `daily_check_ins` (`localDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_check_ins_timestamp` ON `daily_check_ins` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_check_ins_localDate_timestamp` ON `daily_check_ins` (`localDate`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_check_ins_source` ON `daily_check_ins` (`source`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `energy_observations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `absoluteEnergy` INTEGER,
                        `relativeDelta` INTEGER,
                        `context` TEXT NOT NULL,
                        `taskId` INTEGER,
                        `activityRecordId` INTEGER,
                        `focusProtocolSessionId` INTEGER,
                        `source` TEXT NOT NULL,
                        `quality` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `excludedFromLearning` INTEGER NOT NULL,
                        `legacyEnergySampleId` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`activityRecordId`) REFERENCES `activity_records`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`focusProtocolSessionId`) REFERENCES `focus_protocol_sessions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_energy_observations_timestamp` ON `energy_observations` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_energy_observations_taskId` ON `energy_observations` (`taskId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_energy_observations_activityRecordId` ON `energy_observations` (`activityRecordId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_energy_observations_focusProtocolSessionId` ON `energy_observations` (`focusProtocolSessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_energy_observations_context` ON `energy_observations` (`context`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_energy_observations_legacyEnergySampleId` ON `energy_observations` (`legacyEnergySampleId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `task_demand_profiles` (
                        `taskId` INTEGER NOT NULL,
                        `domain` TEXT NOT NULL,
                        `workMode` TEXT NOT NULL,
                        `difficulty` INTEGER NOT NULL,
                        `concentrationDemand` INTEGER NOT NULL,
                        `executiveDemand` INTEGER NOT NULL,
                        `memoryDemand` INTEGER NOT NULL,
                        `creativeDemand` INTEGER NOT NULL,
                        `socialDemand` INTEGER NOT NULL,
                        `physicalDemand` INTEGER NOT NULL,
                        `emotionalDemand` INTEGER NOT NULL,
                        `startFriction` INTEGER NOT NULL,
                        `minimumBlockMinutes` INTEGER NOT NULL,
                        `preferredBlockMinutes` INTEGER NOT NULL,
                        `interruptibility` INTEGER NOT NULL,
                        `placeContext` TEXT NOT NULL,
                        `toolContext` TEXT NOT NULL,
                        `internetRequirement` TEXT NOT NULL,
                        `peopleContext` TEXT NOT NULL,
                        `canDoPartially` INTEGER NOT NULL,
                        `fixedTime` INTEGER NOT NULL,
                        `provenance` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `userLockMask` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`taskId`),
                        FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_demand_profiles_domain` ON `task_demand_profiles` (`domain`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_demand_profiles_workMode` ON `task_demand_profiles` (`workMode`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `task_dependencies` (
                        `taskId` INTEGER NOT NULL,
                        `dependsOnTaskId` INTEGER NOT NULL,
                        `dependencyType` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`taskId`, `dependsOnTaskId`),
                        FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`dependsOnTaskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_dependencies_taskId` ON `task_dependencies` (`taskId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_dependencies_dependsOnTaskId` ON `task_dependencies` (`dependsOnTaskId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `work_episode_assessments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `activityRecordId` INTEGER NOT NULL,
                        `beforeObservationId` INTEGER,
                        `afterObservationId` INTEGER,
                        `recoveryObservationId` INTEGER,
                        `goalOutcome` TEXT NOT NULL,
                        `perceivedDifficulty` INTEGER,
                        `interruptionReason` TEXT,
                        `profileMismatchFlags` TEXT NOT NULL,
                        `modelEligible` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`activityRecordId`) REFERENCES `activity_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`beforeObservationId`) REFERENCES `energy_observations`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`afterObservationId`) REFERENCES `energy_observations`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`recoveryObservationId`) REFERENCES `energy_observations`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_work_episode_assessments_activityRecordId` ON `work_episode_assessments` (`activityRecordId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_episode_assessments_beforeObservationId` ON `work_episode_assessments` (`beforeObservationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_episode_assessments_afterObservationId` ON `work_episode_assessments` (`afterObservationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_episode_assessments_recoveryObservationId` ON `work_episode_assessments` (`recoveryObservationId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `external_contexts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `localDate` TEXT NOT NULL,
                        `regionKey` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `daylightMinutes` INTEGER,
                        `daylightChangeMinutes` INTEGER,
                        `weatherCode` TEXT,
                        `temperatureCelsius` REAL,
                        `cloudCoverPercent` INTEGER,
                        `precipitationProbability` INTEGER,
                        `outdoorSuitability` REAL,
                        `publicBackgroundSummary` TEXT,
                        `fetchedAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER NOT NULL,
                        `provenance` TEXT NOT NULL,
                        `rawPayloadHash` TEXT,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_external_contexts_localDate` ON `external_contexts` (`localDate`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_external_contexts_localDate_regionKey_source` ON `external_contexts` (`localDate`, `regionKey`, `source`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_external_contexts_expiresAt` ON `external_contexts` (`expiresAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `context_snapshots` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `zoneId` TEXT NOT NULL,
                        `localDate` TEXT NOT NULL,
                        `minutesSinceWake` INTEGER,
                        `hoursAwake` REAL,
                        `sleepDurationMinutes` INTEGER,
                        `sleepDeviationMinutes` INTEGER,
                        `sleepDebtMinutes` INTEGER,
                        `sleepRegularity` REAL,
                        `dayOfWeek` INTEGER NOT NULL,
                        `isFreeDay` INTEGER NOT NULL,
                        `calendarWindowMinutes` INTEGER,
                        `recentFocusMinutes` INTEGER NOT NULL,
                        `recentWorkModes` TEXT NOT NULL,
                        `recentBreakMinutes` INTEGER NOT NULL,
                        `dailyCheckInId` INTEGER,
                        `lastObservationAgeMinutes` INTEGER,
                        `personalPeriodFlags` TEXT NOT NULL,
                        `externalContextId` INTEGER,
                        `version` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`dailyCheckInId`) REFERENCES `daily_check_ins`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`externalContextId`) REFERENCES `external_contexts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_context_snapshots_timestamp` ON `context_snapshots` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_context_snapshots_localDate` ON `context_snapshots` (`localDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_context_snapshots_dailyCheckInId` ON `context_snapshots` (`dailyCheckInId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_context_snapshots_externalContextId` ON `context_snapshots` (`externalContextId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recommendation_decisions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `generatedAt` INTEGER NOT NULL,
                        `modelVersion` TEXT NOT NULL,
                        `strategy` TEXT NOT NULL,
                        `contextSnapshotId` INTEGER,
                        `stateSnapshotJson` TEXT NOT NULL,
                        `selectedTaskId` INTEGER,
                        `candidateTaskIds` TEXT NOT NULL,
                        `componentScores` TEXT NOT NULL,
                        `reasonCodes` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `accepted` INTEGER NOT NULL,
                        `dismissed` INTEGER NOT NULL,
                        `reordered` INTEGER NOT NULL,
                        `feedbackReason` TEXT,
                        `resultingActivityRecordId` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`contextSnapshotId`) REFERENCES `context_snapshots`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`selectedTaskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`resultingActivityRecordId`) REFERENCES `activity_records`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recommendation_decisions_generatedAt` ON `recommendation_decisions` (`generatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recommendation_decisions_contextSnapshotId` ON `recommendation_decisions` (`contextSnapshotId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recommendation_decisions_selectedTaskId` ON `recommendation_decisions` (`selectedTaskId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recommendation_decisions_resultingActivityRecordId` ON `recommendation_decisions` (`resultingActivityRecordId`)")

                // Preserve every historical focus reading while keeping the old table intact for
                // existing UI code. Invalid legacy session ids are deliberately detached.
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `energy_observations` (
                        `id`, `timestamp`, `absoluteEnergy`, `relativeDelta`, `context`,
                        `taskId`, `activityRecordId`, `focusProtocolSessionId`, `source`,
                        `quality`, `confidence`, `excludedFromLearning`, `legacyEnergySampleId`,
                        `createdAt`
                    )
                    SELECT
                        e.`id`, e.`timestamp`,
                        CASE WHEN e.`energy` < 1 THEN 1 WHEN e.`energy` > 10 THEN 10 ELSE e.`energy` END,
                        NULL,
                        CASE
                            WHEN e.`context` = 'BEFORE_FOCUS' THEN 'BEFORE_TASK'
                            WHEN e.`context` = 'AFTER_FOCUS' THEN 'AFTER_TASK'
                            ELSE e.`context`
                        END,
                        NULL, NULL,
                        CASE WHEN EXISTS (
                            SELECT 1 FROM `focus_protocol_sessions` f
                            WHERE f.`id` = e.`protocolSessionId`
                        ) THEN e.`protocolSessionId` ELSE NULL END,
                        'LEGACY_ENERGY_SAMPLE', 'EXACT', 1.0, 0, e.`id`, e.`timestamp`
                    FROM `energy_samples` e
                    """.trimIndent()
                )
            }
        }
    }
}
