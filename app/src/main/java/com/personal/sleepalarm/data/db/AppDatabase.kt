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
import com.personal.sleepalarm.data.db.dao.LibraryDao
import com.personal.sleepalarm.data.db.dao.MoodEntryDao
import com.personal.sleepalarm.data.db.dao.PomodoroDao
import com.personal.sleepalarm.data.db.dao.OtherActivityDao
import com.personal.sleepalarm.data.db.dao.ReminderDao
import com.personal.sleepalarm.data.db.dao.ScheduleDao
import com.personal.sleepalarm.data.db.dao.SleepSessionDao
import com.personal.sleepalarm.data.db.dao.TaskDao
import com.personal.sleepalarm.data.db.entity.AlarmProfileEntity
import com.personal.sleepalarm.data.db.entity.CueEventEntity
import com.personal.sleepalarm.data.db.entity.DDayEntity
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

/**
 * Главная база приложения.
 *
 * Версия 5 (ДОБАВЛЕНО):
 *  - tasks: задачи и утренняя рутина со стриками
 *  - reminders: напоминания с повторами (ONCE/DAILY/WEEKLY/INTERVAL)
 *  - mood_entries: настроение (одна запись в день)
 *  - dday_events: обратные отсчёты до событий
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
        OtherActivityEntity::class

    ],
    version = 8,
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
    abstract fun libraryDao(): LibraryDao

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
                    MIGRATION_7_8
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
    }
}
