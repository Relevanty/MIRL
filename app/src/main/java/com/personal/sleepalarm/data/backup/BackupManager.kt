package com.personal.sleepalarm.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.*
import com.personal.sleepalarm.util.ProfileJsonCodec
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Экспорт/импорт всех данных приложения в один JSON-файл.
 *
 * Структура:
 * {
 *   "version": 1,
 *   "exportedAt": "...",
 *   "alarmProfile": {...},
 *   "schedule": {...},
 *   "subjects": [...],
 *   "studySessions": [...],
 *   "calendarEvents": [...],
 *   "tasks": [...],
 *   "reminders": [...],
 *   "ddays": [...],
 *   "diary": [...],
 *   "moodEntries": [...],
 *   "sleepSessions": [...],
 *   "cueEvents": [...],
 *   "library": { "items": [...], "tags": [...], "refs": [...] }
 * }
 *
 * Импорт = полная замена всех таблиц.
 */
class BackupManager(private val context: Context) {

    companion object {
        private const val TAG = "BackupManager"
        private const val BACKUP_VERSION = 1
    }

    private val db: AppDatabase = AppDatabase.getInstance(context)

    // ========== ЭКСПОРТ ==========

    suspend fun exportToJson(): String {
        val root = JSONObject().apply {
            put("version", BACKUP_VERSION)
            put("exportedAt", System.currentTimeMillis())

            // Профиль (одна запись или пусто)
            db.alarmProfileDao().getProfile()?.let { put("alarmProfile", profileToJson(it)) }

            // Расписание
            db.scheduleDao().get()?.let {
                put("schedule", JSONObject().apply {
                    put("content", it.content)
                    put("updatedAt", it.updatedAt)
                })
            }

            // Все остальные таблицы
            put("subjects", JSONArray().apply {
                db.subjectDao().getAll().forEach { put(subjectToJson(it)) }
            })
            put("studySessions", JSONArray().apply {
                db.studySessionDao().getAll().forEach { put(studySessionToJson(it)) }
            })
            put("calendarEvents", JSONArray().apply {
                db.calendarEventDao().getAll().forEach { put(eventToJson(it)) }
            })
            put("tasks", JSONArray().apply {
                db.taskDao().getAll().forEach { put(taskToJson(it)) }
            })
            put("reminders", JSONArray().apply {
                db.reminderDao().getAll().forEach { put(reminderToJson(it)) }
            })
            put("ddays", JSONArray().apply {
                db.ddayDao().getAll().forEach { put(ddayToJson(it)) }
            })
            put("diary", JSONArray().apply {
                db.diaryDao().getAll().forEach { put(diaryToJson(it)) }
            })
            put("moodEntries", JSONArray().apply {
                db.moodEntryDao().getAll().forEach { put(moodToJson(it)) }
            })
            put("sleepSessions", JSONArray().apply {
                db.sleepSessionDao().getAllSessions().forEach { put(sleepSessionToJson(it)) }
            })
            put("cueEvents", JSONArray().apply {
                db.cueEventDao().getAll().forEach { put(cueEventToJson(it)) }
            })
            put("library", JSONObject().apply {
                put("items", JSONArray().apply {
                    db.libraryDao().getAllItems().forEach { put(libraryItemToJson(it)) }
                })
                put("tags", JSONArray().apply {
                    db.libraryDao().getAllTags().forEach { put(libraryTagToJson(it)) }
                })
                put("refs", JSONArray().apply {
                    db.libraryDao().getAllCrossRefs().forEach { put(crossRefToJson(it)) }
                })
            })
        }
        return root.toString(2)
    }

    suspend fun exportToUri(uri: Uri) {
        val json = exportToJson()
        context.contentResolver.openOutputStream(uri, "w")?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Cannot open URI for writing: $uri")
    }

    // ========== ИМПОРТ ==========

    suspend fun importFromUri(uri: Uri) {
        val json = context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        } ?: throw IllegalStateException("Cannot read URI: $uri")
        importFromJson(json)
    }

    suspend fun importFromJson(json: String) {
        val root = JSONObject(json)

        val version = root.optInt("version", 0)
        if (version > BACKUP_VERSION) {
            Log.w(
                TAG,
                "Импорт версии $version, текущая $BACKUP_VERSION. Часть данных может не импортироваться."
            )
        }

        // Всё в одной транзакции через стандартный withTransaction от Room
        db.withTransaction {
            // 1. Очищаем все таблицы (сначала связи, потом основные)
            db.cueEventDao().deleteAll()
            db.libraryDao().deleteAllCrossRefs()
            db.libraryDao().deleteAllItems()
            db.libraryDao().deleteAllTags()
            db.reminderDao().deleteAll()
            db.studySessionDao().deleteAll()
            db.subjectDao().deleteAll()
            db.calendarEventDao().deleteAll()
            db.taskDao().deleteAll()
            db.ddayDao().deleteAll()
            db.diaryDao().deleteAll()
            db.moodEntryDao().deleteAll()
            db.sleepSessionDao().deleteAll()
            db.scheduleDao().deleteAll()
            db.alarmProfileDao().deleteAll()

            // 2. Загружаем данные
            root.optJSONObject("alarmProfile")?.let {
                profileFromJson(it)?.let { p ->
                    db.alarmProfileDao().upsert(p)
                }
            }

            root.optJSONObject("schedule")?.let {
                db.scheduleDao().upsert(
                    ScheduleEntity(
                        id = 1,
                        content = it.optString("content", ""),
                        updatedAt = it.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            root.optJSONArray("subjects")?.let { arr ->
                val list =
                    (0 until arr.length()).mapNotNull { subjectFromJson(arr.getJSONObject(it)) }
                if (list.isNotEmpty()) db.subjectDao().insertAll(list)
            }

            root.optJSONArray("studySessions")?.let { arr ->
                val list =
                    (0 until arr.length()).mapNotNull { studySessionFromJson(arr.getJSONObject(it)) }
                if (list.isNotEmpty()) db.studySessionDao().insertAll(list)
            }

            root.optJSONArray("calendarEvents")?.let { arr ->
                val list =
                    (0 until arr.length()).mapNotNull { eventFromJson(arr.getJSONObject(it)) }
                if (list.isNotEmpty()) db.calendarEventDao().insertAll(list)
            }

            root.optJSONArray("tasks")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull { taskFromJson(arr.getJSONObject(it)) }
                if (list.isNotEmpty()) db.taskDao().insertAll(list)
            }

            root.optJSONArray("reminders")?.let { arr ->
                val list =
                    (0 until arr.length()).mapNotNull { reminderFromJson(arr.getJSONObject(it)) }
                if (list.isNotEmpty()) db.reminderDao().insertAll(list)
            }

            root.optJSONArray("ddays")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull { ddayFromJson(arr.getJSONObject(it)) }
                if (list.isNotEmpty()) db.ddayDao().insertAll(list)
            }

            root.optJSONArray("diary")?.let { arr ->
                val list =
                    (0 until arr.length()).mapNotNull { diaryFromJson(arr.getJSONObject(it)) }
                if (list.isNotEmpty()) db.diaryDao().insertAll(list)
            }

            root.optJSONArray("moodEntries")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull { moodFromJson(arr.getJSONObject(it)) }
                if (list.isNotEmpty()) db.moodEntryDao().insertAll(list)
            }

            root.optJSONArray("sleepSessions")?.let { arr ->
                val list =
                    (0 until arr.length()).mapNotNull { sleepSessionFromJson(arr.getJSONObject(it)) }
                if (list.isNotEmpty()) db.sleepSessionDao().insertAll(list)
            }

            root.optJSONArray("cueEvents")?.let { arr ->
                val list =
                    (0 until arr.length()).mapNotNull { cueEventFromJson(arr.getJSONObject(it)) }
                if (list.isNotEmpty()) db.cueEventDao().insertAll(list)
            }

            root.optJSONObject("library")?.let { lib ->
                lib.optJSONArray("tags")?.let { arr ->
                    val list =
                        (0 until arr.length()).mapNotNull { libraryTagFromJson(arr.getJSONObject(it)) }
                    if (list.isNotEmpty()) db.libraryDao().insertAllTags(list)
                }
                lib.optJSONArray("items")?.let { arr ->
                    val list =
                        (0 until arr.length()).mapNotNull { libraryItemFromJson(arr.getJSONObject(it)) }
                    if (list.isNotEmpty()) db.libraryDao().insertAllItems(list)
                }
                lib.optJSONArray("refs")?.let { arr ->
                    val list =
                        (0 until arr.length()).mapNotNull { crossRefFromJson(arr.getJSONObject(it)) }
                    if (list.isNotEmpty()) db.libraryDao().insertAllCrossRefs(list)
                }
            }
        }

        Log.d(TAG, "Импорт завершён успешно")
    }

    // ========== СЕРИАЛИЗАЦИЯ (Entity → JSON) ==========

    private fun profileToJson(p: AlarmProfileEntity) = JSONObject().apply {
        put("id", p.id)
        put("cycleLengthMinutes", p.cycleLengthMinutes)
        put("cycles", p.cycles)
        put("onsetLatencyMinutes", p.onsetLatencyMinutes)
        put("calculationMode", p.calculationMode.name)
        put("preferredBedTimeHour", p.preferredBedTimeHour)
        put("preferredBedTimeMinute", p.preferredBedTimeMinute)
        put("preferredWakeHour", p.preferredWakeHour)
        put("preferredWakeMinute", p.preferredWakeMinute)
        put("cuesEnabled", p.cuesEnabled)
        put("cueType", p.cueType.name)
        put("firstCueDelayMinutes", p.firstCueDelayMinutes)
        put("cueIntervalMinutes", p.cueIntervalMinutes)
        put("cueVolumePercent", p.cueVolumePercent)
        put("mathDifficulty", p.mathDifficulty.name)
        put("quietAlarmEnabled", p.quietAlarmEnabled)
        put("vibrationEnabled", p.vibrationEnabled)
        put("alarmRingtoneUri", p.alarmRingtoneUri ?: JSONObject.NULL)
        put("cueScheduleMode", p.cueScheduleMode.name)
        put("remCueOffsetPercent", p.remCueOffsetPercent)
        put("autoDetectOnsetEnabled", p.autoDetectOnsetEnabled)
        put("autoCorrectWakeEnabled", p.autoCorrectWakeEnabled)
        put("smartRepeatEnabled", p.smartRepeatEnabled)
        put("smartRepeatFirstDelayMinutes", p.smartRepeatFirstDelayMinutes)
        put("smartRepeatIntervalMinutes", p.smartRepeatIntervalMinutes)
        put("smartRepeatMaxCount", p.smartRepeatMaxCount)
        put("mirrorToSystemClock", p.mirrorToSystemClock)
        put("cueRingtoneUri", p.cueRingtoneUri ?: JSONObject.NULL)
        put("updatedAt", p.updatedAt)
    }

    private fun subjectToJson(s: SubjectEntity) = JSONObject().apply {
        put("id", s.id); put("name", s.name); put("color", s.color); put("createdAt", s.createdAt)
    }

    private fun studySessionToJson(s: StudySessionEntity) = JSONObject().apply {
        put("id", s.id); put("subjectId", s.subjectId); put("startMillis", s.startMillis)
        put("endMillis", s.endMillis); put("durationMillis", s.durationMillis)
        put("dateKey", s.dateKey); put("createdAt", s.createdAt)
    }

    private fun eventToJson(e: CalendarEventEntity) = JSONObject().apply {
        put("id", e.id); put("title", e.title); put("startMillis", e.startMillis)
        put("endMillis", e.endMillis); put("allDay", e.allDay); put("repeatRule", e.repeatRule)
        put("reminderMinutes", e.reminderMinutes ?: JSONObject.NULL)
        put("createdAt", e.createdAt)
    }

    private fun taskToJson(t: TaskEntity) = JSONObject().apply {
        put("id", t.id); put("title", t.title); put("isDone", t.isDone)
        put("isMorningRoutine", t.isMorningRoutine); put("createdAt", t.createdAt)
        put("completedAt", t.completedAt ?: JSONObject.NULL)
        put("doneDate", t.doneDate ?: JSONObject.NULL)
        put("streakCount", t.streakCount)
        put("reminderId", t.reminderId ?: JSONObject.NULL)
    }

    private fun reminderToJson(r: ReminderEntity) = JSONObject().apply {
        put("id", r.id); put("title", r.title); put("timeHour", r.timeHour)
        put("timeMinute", r.timeMinute); put("repeatMode", r.repeatMode.name)
        put("daysOfWeek", r.daysOfWeek); put("intervalDays", r.intervalDays)
        put("nextTriggerTime", r.nextTriggerTime); put("isEnabled", r.isEnabled)
        put("createdAt", r.createdAt)
    }

    private fun ddayToJson(d: DDayEntity) = JSONObject().apply {
        put("id", d.id); put("title", d.title); put("targetDate", d.targetDate)
        put("createdAt", d.createdAt)
    }

    private fun diaryToJson(d: DiaryEntryEntity) = JSONObject().apply {
        put("id", d.id); put("dateKey", d.dateKey); put("text", d.text)
        put("createdAt", d.createdAt); put("updatedAt", d.updatedAt)
    }

    private fun moodToJson(m: MoodEntryEntity) = JSONObject().apply {
        put("id", m.id); put("date", m.date); put("mood", m.mood); put("createdAt", m.createdAt)
    }

    private fun sleepSessionToJson(s: SleepSessionEntity) = JSONObject().apply {
        put("id", s.id)
        put("bedTimePlanned", s.bedTimePlanned)
        put("sleepOnsetLatencyMinutes", s.sleepOnsetLatencyMinutes)
        put("estimatedSleepStartTime", s.estimatedSleepStartTime)
        put("cycleLengthMinutes", s.cycleLengthMinutes)
        put("cyclesPlanned", s.cyclesPlanned)
        put("estimatedWakeTime", s.estimatedWakeTime)
        put("actualWakeTime", s.actualWakeTime ?: JSONObject.NULL)
        put("dismissType", s.dismissType?.name ?: JSONObject.NULL)
        put("cuesEnabled", s.cuesEnabled)
        put("cueType", s.cueType.name)
        put("cueVolumePercent", s.cueVolumePercent)
        put("cuesScheduledCount", s.cuesScheduledCount)
        put("cuesPlayedCount", s.cuesPlayedCount)
        put("cuesSkippedCount", s.cuesSkippedCount)
        put("isActive", s.isActive)
        put("isSnoozeSession", s.isSnoozeSession)
        put("parentSessionId", s.parentSessionId ?: JSONObject.NULL)
        put("zoneId", s.zoneId)
        put("createdAt", s.createdAt)
        put("updatedAt", s.updatedAt)
        put("cueRingtoneUri", s.cueRingtoneUri ?: JSONObject.NULL)
        put("detectedSleepOnsetTime", s.detectedSleepOnsetTime ?: JSONObject.NULL)
        put("detectedOnsetLatencyMinutes", s.detectedOnsetLatencyMinutes ?: JSONObject.NULL)
    }

    private fun cueEventToJson(c: CueEventEntity) = JSONObject().apply {
        put("id", c.id); put("sessionId", c.sessionId); put("cueIndex", c.cueIndex)
        put("scheduledTime", c.scheduledTime); put("state", c.state.name)
        put("playedAt", c.playedAt ?: JSONObject.NULL)
        put("playedBy", c.playedBy ?: JSONObject.NULL)
        put("createdAt", c.createdAt); put("updatedAt", c.updatedAt)
    }

    private fun libraryItemToJson(i: LibraryItemEntity) = JSONObject().apply {
        put("id", i.id); put("type", i.type.name); put("title", i.title)
        put("author", i.author); put("coverUri", i.coverUri ?: JSONObject.NULL)
        put("shortDescription", i.shortDescription); put("impression", i.impression)
        put("thoughts", i.thoughts); put("rating", i.rating)
        put("createdAt", i.createdAt); put("updatedAt", i.updatedAt)
    }

    private fun libraryTagToJson(t: LibraryTagEntity) = JSONObject().apply {
        put("id", t.id); put("name", t.name)
    }

    private fun crossRefToJson(r: LibraryItemTagCrossRef) = JSONObject().apply {
        put("itemId", r.itemId); put("tagId", r.tagId)
    }

    // ========== ДЕСЕРИАЛИЗАЦИЯ (JSON → Entity) ==========

    private fun profileFromJson(o: JSONObject): AlarmProfileEntity? =
        ProfileJsonCodec.decode(o.toString())

    private fun subjectFromJson(o: JSONObject): SubjectEntity? = try {
        SubjectEntity(
            id = o.optInt("id", 0),
            name = o.optString("name", ""),
            color = o.optInt("color", 0),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга предмета", e); null
    }

    private fun studySessionFromJson(o: JSONObject): StudySessionEntity? = try {
        StudySessionEntity(
            id = o.optInt("id", 0),
            subjectId = o.optInt("subjectId", 0),
            startMillis = o.optLong("startMillis"),
            endMillis = o.optLong("endMillis"),
            durationMillis = o.optLong("durationMillis"),
            dateKey = o.optString("dateKey"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга study-сессии", e); null
    }

    private fun eventFromJson(o: JSONObject): CalendarEventEntity? = try {
        CalendarEventEntity(
            id = o.optInt("id", 0),
            title = o.optString("title"),
            startMillis = o.optLong("startMillis"),
            endMillis = o.optLong("endMillis"),
            allDay = o.optBoolean("allDay", false),
            repeatRule = o.optString("repeatRule", "none"),
            reminderMinutes = if (o.isNull("reminderMinutes")) null else o.optInt("reminderMinutes"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга события", e); null
    }

    private fun taskFromJson(o: JSONObject): TaskEntity? = try {
        TaskEntity(
            id = o.optInt("id", 0),
            title = o.optString("title"),
            isDone = o.optBoolean("isDone", false),
            isMorningRoutine = o.optBoolean("isMorningRoutine", false),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            completedAt = if (o.isNull("completedAt")) null else o.optLong("completedAt"),
            doneDate = o.optStringOrNull("doneDate"),
            streakCount = o.optInt("streakCount", 0),
            reminderId = if (o.isNull("reminderId")) null else o.optInt("reminderId")
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга задачи", e); null
    }

    private fun reminderFromJson(o: JSONObject): ReminderEntity? = try {
        ReminderEntity(
            id = o.optInt("id", 0),
            title = o.optString("title"),
            timeHour = o.optInt("timeHour"),
            timeMinute = o.optInt("timeMinute"),
            repeatMode = enumValueOrDefault(o.optString("repeatMode"), RepeatMode.ONCE),
            daysOfWeek = o.optInt("daysOfWeek", 0),
            intervalDays = o.optInt("intervalDays", 1),
            nextTriggerTime = o.optLong("nextTriggerTime"),
            isEnabled = o.optBoolean("isEnabled", true),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга напоминания", e); null
    }

    private fun ddayFromJson(o: JSONObject): DDayEntity? = try {
        DDayEntity(
            id = o.optInt("id", 0),
            title = o.optString("title"),
            targetDate = o.optString("targetDate"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга D-Day", e); null
    }

    private fun diaryFromJson(o: JSONObject): DiaryEntryEntity? = try {
        DiaryEntryEntity(
            id = o.optInt("id", 0),
            dateKey = o.optString("dateKey"),
            text = o.optString("text", ""),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга дневника", e); null
    }

    private fun moodFromJson(o: JSONObject): MoodEntryEntity? = try {
        MoodEntryEntity(
            id = o.optInt("id", 0),
            date = o.optString("date"),
            mood = o.optInt("mood", 3),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга настроения", e); null
    }

    private fun sleepSessionFromJson(o: JSONObject): SleepSessionEntity? = try {
        SleepSessionEntity(
            id = o.optInt("id", 0),
            bedTimePlanned = o.optLong("bedTimePlanned"),
            sleepOnsetLatencyMinutes = o.optInt("sleepOnsetLatencyMinutes", 15),
            estimatedSleepStartTime = o.optLong("estimatedSleepStartTime"),
            cycleLengthMinutes = o.optInt("cycleLengthMinutes", 90),
            cyclesPlanned = o.optInt("cyclesPlanned"),
            estimatedWakeTime = o.optLong("estimatedWakeTime"),
            actualWakeTime = if (o.isNull("actualWakeTime")) null else o.optLong("actualWakeTime"),
            dismissType = o.optStringOrNull("dismissType")
                ?.let { enumValueOrNull<com.personal.sleepalarm.domain.model.DismissType>(it) },
            cuesEnabled = o.optBoolean("cuesEnabled", true),
            cueType = enumValueOrDefault(
                o.optString("cueType"),
                com.personal.sleepalarm.domain.model.CueType.BEEP
            ),
            cueVolumePercent = o.optInt("cueVolumePercent", 10),
            cuesScheduledCount = o.optInt("cuesScheduledCount", 0),
            cuesPlayedCount = o.optInt("cuesPlayedCount", 0),
            cuesSkippedCount = o.optInt("cuesSkippedCount", 0),
            isActive = o.optBoolean("isActive", false),
            isSnoozeSession = o.optBoolean("isSnoozeSession", false),
            parentSessionId = if (o.isNull("parentSessionId")) null else o.optInt("parentSessionId"),
            zoneId = o.optString("zoneId", java.time.ZoneId.systemDefault().id),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            cueRingtoneUri = o.optStringOrNull("cueRingtoneUri"),
            detectedSleepOnsetTime = if (o.isNull("detectedSleepOnsetTime")) null else o.optLong("detectedSleepOnsetTime"),
            detectedOnsetLatencyMinutes = if (o.isNull("detectedOnsetLatencyMinutes")) null else o.optInt(
                "detectedOnsetLatencyMinutes"
            )
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга sleep-сессии", e); null
    }

    private fun cueEventFromJson(o: JSONObject): CueEventEntity? = try {
        CueEventEntity(
            id = o.optInt("id", 0),
            sessionId = o.optInt("sessionId"),
            cueIndex = o.optInt("cueIndex"),
            scheduledTime = o.optLong("scheduledTime"),
            state = enumValueOrDefault(
                o.optString("state"),
                com.personal.sleepalarm.domain.model.CueEventState.SCHEDULED
            ),
            playedAt = if (o.isNull("playedAt")) null else o.optLong("playedAt"),
            playedBy = o.optStringOrNull("playedBy"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга cue-события", e); null
    }

    private fun libraryItemFromJson(o: JSONObject): LibraryItemEntity? = try {
        LibraryItemEntity(
            id = o.optInt("id", 0),
            type = enumValueOrDefault(o.optString("type"), LibraryItemType.BOOK),
            title = o.optString("title"),
            author = o.optString("author", ""),
            coverUri = o.optStringOrNull("coverUri"),
            shortDescription = o.optString("shortDescription", ""),
            impression = o.optString("impression", ""),
            thoughts = o.optString("thoughts", ""),
            rating = o.optInt("rating", 0),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга элемента библиотеки", e); null
    }

    private fun libraryTagFromJson(o: JSONObject): LibraryTagEntity? = try {
        LibraryTagEntity(id = o.optInt("id", 0), name = o.optString("name"))
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга тега библиотеки", e); null
    }

    private fun crossRefFromJson(o: JSONObject): LibraryItemTagCrossRef? = try {
        LibraryItemTagCrossRef(itemId = o.optInt("itemId"), tagId = o.optInt("tagId"))
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга cross-ref", e); null
    }

    // ========== УТИЛИТЫ ==========

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        enumValueOrNull<T>(value) ?: default

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
        try {
            enumValueOf<T>(value)
        } catch (_: IllegalArgumentException) {
            null
        }

}
