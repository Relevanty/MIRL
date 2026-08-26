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
import com.personal.sleepalarm.data.db.entity.AlarmProfileEntity
import com.personal.sleepalarm.data.db.entity.CueEventEntity
import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.data.db.entity.EnergySampleEntity
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
        ActivityRecordEntity::class

    ],
    version = 21,
    exportSchema = false
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
                    MIGRATION_20_21
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
    }
}
