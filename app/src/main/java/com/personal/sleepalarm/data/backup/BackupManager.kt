package com.personal.sleepalarm.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.dao.EnglishProgressWithWordProjection
import com.personal.sleepalarm.data.db.dao.EnglishDirectionalProgressWithWordProjection
import com.personal.sleepalarm.data.db.dao.EnglishStudyCardBackupProjection
import com.personal.sleepalarm.data.db.entity.*
import com.personal.sleepalarm.data.english.EnglishDictionaryAssetSource
import com.personal.sleepalarm.data.english.toDirectionalProgressTracks
import com.personal.sleepalarm.domain.english.EnglishStudyDirection
import com.personal.sleepalarm.util.ProfileJsonCodec
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.domain.model.FocusProtocolPhase
import com.personal.sleepalarm.service.focus.FocusProtocolManager
import com.personal.sleepalarm.alarm.SleepAutomationScheduler
import com.personal.sleepalarm.data.preferences.SleepAutomationPreference
import com.personal.sleepalarm.data.preferences.SleepAutomationSettings
import com.personal.sleepalarm.data.preferences.DailyPlanNudgePreferences
import com.personal.sleepalarm.data.preferences.DailyPlanNudgeSettings
import com.personal.sleepalarm.alarm.DailyPlanNudgeScheduler
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Экспорт/импорт всех данных приложения в один JSON-файл.
 *
 * Структура:
 * {
 *   "version": 14,
 *   "exportedAt": "...",
 *   "alarmProfile": {...},
 *   "schedule": {...},
 *   "subjects": [...],
 *   "studySessions": [...],
 *   "calendarEvents": [...],
 *   "tasks": [...],
 *   "dailyCheckIns": [...],
 *   "energyObservations": [...],
 *   "taskDemandProfiles": [...],
 *   "taskDependencies": [...],
 *   "workEpisodeAssessments": [...],
 *   "externalContexts": [...],
 *   "contextSnapshots": [...],
 *   "recommendationDecisions": [...],
 *   "englishProgress": [...],
 *   "englishDirectionalProgress": [...],
 *   "englishStudySets": [...],
 *   "englishStudyCards": [...],
 *   "englishCardProgress": [...],
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
        private const val BACKUP_VERSION = 14
    }

    private data class EnglishBackupSnapshot(
        val legacyProgress: List<EnglishProgressWithWordProjection>,
        val directionalProgress: List<EnglishDirectionalProgressWithWordProjection>,
        val studySets: List<EnglishStudySetEntity>,
        val studyCards: List<EnglishStudyCardBackupProjection>,
        val cardProgress: List<EnglishCardProgressEntity>
    )

    private val db: AppDatabase = AppDatabase.getInstance(context)

    // ========== ЭКСПОРТ ==========

    suspend fun exportToJson(): String {
        val sleepAutomation = SleepAutomationPreference(context).get()
        val dailyPlanNudges = DailyPlanNudgePreferences(context).get()
        // Every Room-backed section belongs to one snapshot. Background focus writes,
        // task edits and recommendation updates must not produce an orphaned backup graph.
        val root = db.withTransaction {
            val englishBackup = EnglishBackupSnapshot(
                legacyProgress = db.englishStudyDao().getAllProgressWithWords(),
                directionalProgress = db.englishStudyDao().getAllDirectionalProgressWithWords(),
                studySets = db.englishStudyDao().getAllStudySets(),
                studyCards = db.englishStudyDao().getAllStudyCardsForBackup(),
                cardProgress = db.englishStudyDao().getAllCardProgress()
            )
            JSONObject().apply {
                put("version", BACKUP_VERSION)
                put("exportedAt", System.currentTimeMillis())

            // Профиль (одна запись или пусто)
            db.alarmProfileDao().getProfile()?.let { put("alarmProfile", profileToJson(it)) }
            put("sleepAutomation", JSONObject().apply {
                put("enabled", sleepAutomation.enabled)
                put("windowStartMinutes", sleepAutomation.windowStartMinutes)
                put("windowEndMinutes", sleepAutomation.windowEndMinutes)
            })
            put("dailyPlanNudges", JSONObject().apply {
                put("enabled", dailyPlanNudges.enabled)
                put("bufferMinutes", dailyPlanNudges.bufferMinutes)
                put("repeatEnabled", dailyPlanNudges.repeatEnabled)
                put("repeatIntervalMinutes", dailyPlanNudges.repeatIntervalMinutes)
                put("morningReminderEnabled", dailyPlanNudges.morningReminderEnabled)
                put("cutoffMinutesOfDay", dailyPlanNudges.cutoffMinutesOfDay)
            })

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
            put("pomodoroSessions", JSONArray().apply {
                db.pomodoroDao().getAll().forEach { put(pomodoroSessionToJson(it)) }
            })
            put("focusProtocolSessions", JSONArray().apply {
                db.focusProtocolDao().getAll().forEach { put(focusProtocolToJson(it)) }
            })
            put("energySamples", JSONArray().apply {
                db.energySampleDao().getAll().forEach { put(energySampleToJson(it)) }
            })
            put("otherActivities", JSONArray().apply {
                db.otherActivityDao().getAll().forEach { put(otherActivityToJson(it)) }
            })
            put("calendarEvents", JSONArray().apply {
                db.calendarEventDao().getAll().forEach { put(eventToJson(it)) }
            })
            put("tasks", JSONArray().apply {
                db.taskDao().getAll().forEach { put(taskToJson(it)) }
            })
            put("projects", JSONArray().apply {
                db.projectDao().getAll().forEach { put(projectToJson(it)) }
            })
            put("taskSubtasks", JSONArray().apply {
                db.taskSubtaskDao().getAll().forEach { put(subtaskToJson(it)) }
            })
            put("taskAttachments", JSONArray().apply {
                db.taskAttachmentDao().getAll().forEach { put(attachmentToJson(it)) }
            })
            put("taskLibraryLinks", JSONArray().apply {
                db.taskLibraryLinkDao().getAll().forEach { put(taskLibraryLinkToJson(it)) }
            })
            put("activityRecords", JSONArray().apply {
                db.activityRecordDao().getAll().forEach { put(activityRecordToJson(it)) }
            })
            put("dailyCheckIns", JSONArray().apply {
                db.dailyCheckInDao().getAll().forEach { put(dailyCheckInToJson(it)) }
            })
            put("energyObservations", JSONArray().apply {
                db.energyObservationDao().getAll().forEach { put(energyObservationToJson(it)) }
            })
            put("taskDemandProfiles", JSONArray().apply {
                db.taskDemandProfileDao().getAll().forEach { put(taskDemandProfileToJson(it)) }
            })
            put("taskDependencies", JSONArray().apply {
                db.taskDependencyDao().getAll().forEach { put(taskDependencyToJson(it)) }
            })
            put("workEpisodeAssessments", JSONArray().apply {
                db.workEpisodeAssessmentDao().getAll().forEach { put(workEpisodeAssessmentToJson(it)) }
            })
            put("externalContexts", JSONArray().apply {
                db.externalContextDao().getAll().forEach { put(externalContextToJson(it)) }
            })
            put("contextSnapshots", JSONArray().apply {
                db.contextSnapshotDao().getAll().forEach { put(contextSnapshotToJson(it)) }
            })
            put("recommendationDecisions", JSONArray().apply {
                db.recommendationDecisionDao().getAll().forEach { put(recommendationDecisionToJson(it)) }
            })
            // The 10,000-word lexicon is bundled with the APK and is re-seeded locally.
            // Only personal spaced-repetition history belongs in a backup.
            put("englishProgress", JSONArray().apply {
                englishBackup.legacyProgress.forEach {
                    put(englishProgressToJson(it))
                }
            })
            put("englishDirectionalProgress", JSONArray().apply {
                englishBackup.directionalProgress.forEach {
                    put(englishDirectionalProgressToJson(it))
                }
            })
            put("englishStudySets", JSONArray().apply {
                englishBackup.studySets.forEach { put(englishStudySetToJson(it)) }
            })
            put("englishStudyCards", JSONArray().apply {
                englishBackup.studyCards.forEach {
                    put(englishStudyCardToJson(it))
                }
            })
            put("englishCardProgress", JSONArray().apply {
                englishBackup.cardProgress.forEach {
                    put(englishCardProgressToJson(it))
                }
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
            // Never perform a destructive downgrade import. A newer backup may contain
            // required tables, fields or enum values this build cannot preserve.
            throw IllegalArgumentException(
                "Версия резервной копии $version новее поддерживаемой версии $BACKUP_VERSION"
            )
        }
        validateBackupStructureBeforeRestore(root, version)

        // Resolve saved progress against the current bundled headwords before
        // touching user data. If the asset is damaged, import remains atomic.
        val englishProgressJson = root.optJSONArray("englishProgress")
        val englishDirectionalProgressJson = root.optJSONArray("englishDirectionalProgress")
        val englishStudyCardsJson = root.optJSONArray("englishStudyCards")
        val needsEnglishDictionary = listOf(
            englishProgressJson,
            englishDirectionalProgressJson,
            englishStudyCardsJson
        ).any { (it?.length() ?: 0) > 0 }
        val currentEnglishWords = if (needsEnglishDictionary) {
            withContext(Dispatchers.IO) { EnglishDictionaryAssetSource(context).load() }
        } else {
            emptyList()
        }
        val currentEnglishIdsByWord = currentEnglishWords.associate { it.word to it.id }

        // Всё в одной транзакции через стандартный withTransaction от Room
        db.withTransaction {
            // 1. Очищаем все таблицы (сначала связи, потом основные)
            db.cueEventDao().deleteAll()
            db.recommendationDecisionDao().deleteAll()
            db.workEpisodeAssessmentDao().deleteAll()
            db.contextSnapshotDao().deleteAll()
            db.taskDependencyDao().deleteAll()
            db.taskDemandProfileDao().deleteAll()
            db.energyObservationDao().deleteAll()
            db.externalContextDao().deleteAll()
            db.dailyCheckInDao().deleteAll()
            db.taskLibraryLinkDao().deleteAll()
            db.taskAttachmentDao().deleteAll()
            db.taskSubtaskDao().deleteAll()
            db.activityRecordDao().deleteAll()
            db.englishStudyDao().deleteAllCardProgress()
            db.englishStudyDao().deleteAllStudyCards()
            db.englishStudyDao().deleteAllStudySets()
            db.englishStudyDao().deleteAllDirectionalProgress()
            db.englishStudyDao().deleteAllProgress()
            db.projectDao().deleteAll()
            db.libraryDao().deleteAllCrossRefs()
            db.libraryDao().deleteAllItems()
            db.libraryDao().deleteAllTags()
            db.reminderDao().deleteAll()
            db.studySessionDao().deleteAll()
            db.subjectDao().deleteAll()
            db.energySampleDao().deleteAll()
            db.focusProtocolDao().deleteAll()
            db.pomodoroDao().deleteAll()
            db.otherActivityDao().deleteAll()
            db.calendarEventDao().deleteAll()
            db.taskDao().deleteAll()
            db.ddayDao().deleteAll()
            db.diaryDao().deleteAll()
            db.moodEntryDao().deleteAll()
            db.sleepSessionDao().deleteAll()
            db.scheduleDao().deleteAll()
            db.alarmProfileDao().deleteAll()

            if (currentEnglishWords.isNotEmpty()) {
                db.englishStudyDao().replaceDictionary(
                    words = currentEnglishWords,
                    datasetVersion = EnglishDictionaryAssetSource.DATASET_VERSION
                )
            }

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

            root.optJSONArray("pomodoroSessions")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull {
                    pomodoroSessionFromJson(arr.getJSONObject(it))
                }
                if (list.isNotEmpty()) db.pomodoroDao().insertAll(list)
            }

            root.optJSONArray("focusProtocolSessions")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull {
                    focusProtocolFromJson(arr.getJSONObject(it))
                }
                if (list.isNotEmpty()) db.focusProtocolDao().insertAll(list)
            }

            root.optJSONArray("energySamples")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull {
                    energySampleFromJson(arr.getJSONObject(it))
                }
                if (list.isNotEmpty()) db.energySampleDao().insertAll(list)
            }

            root.optJSONArray("otherActivities")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull {
                    otherActivityFromJson(arr.getJSONObject(it))
                }
                if (list.isNotEmpty()) db.otherActivityDao().insertAll(list)
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

            root.optJSONArray("projects")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull { projectFromJson(arr.getJSONObject(it)) }
                if (list.isNotEmpty()) db.projectDao().insertAll(list)
            }
            root.optJSONArray("taskSubtasks")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull { subtaskFromJson(arr.getJSONObject(it)) }
                if (list.isNotEmpty()) db.taskSubtaskDao().insertAll(list)
            }
            root.optJSONArray("taskAttachments")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull { attachmentFromJson(arr.getJSONObject(it)) }
                if (list.isNotEmpty()) db.taskAttachmentDao().insertAll(list)
            }
            root.optJSONArray("taskLibraryLinks")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull { taskLibraryLinkFromJson(arr.getJSONObject(it)) }
                if (list.isNotEmpty()) db.taskLibraryLinkDao().insertAll(list)
            }
            root.optJSONArray("activityRecords")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull { activityRecordFromJson(arr.getJSONObject(it)) }
                if (list.isNotEmpty()) db.activityRecordDao().insertAll(list)
            }

            root.optJSONArray("dailyCheckIns")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull {
                    dailyCheckInFromJson(arr.getJSONObject(it))
                }
                if (list.isNotEmpty()) db.dailyCheckInDao().insertAll(list)
            }
            root.optJSONArray("externalContexts")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull {
                    externalContextFromJson(arr.getJSONObject(it))
                }
                if (list.isNotEmpty()) db.externalContextDao().insertAll(list)
            }

            if (root.has("energyObservations")) {
                root.optJSONArray("energyObservations")?.let { arr ->
                    val list = (0 until arr.length()).mapNotNull {
                        energyObservationFromJson(arr.getJSONObject(it))
                    }
                    if (list.isNotEmpty()) db.energyObservationDao().insertAll(list)
                }
            } else {
                // Backups through v13 only contain energy_samples. Recreate the v28
                // compatibility projection after its optional session parents exist.
                val validSessionIds = db.focusProtocolDao().getAll().mapTo(hashSetOf()) { it.id }
                val observations = db.energySampleDao().getAll().map { sample ->
                    EnergyObservationEntity(
                        id = sample.id,
                        timestamp = sample.timestamp,
                        absoluteEnergy = sample.energy.coerceIn(1, 10),
                        context = when (sample.context) {
                            "BEFORE_FOCUS" -> "BEFORE_TASK"
                            "AFTER_FOCUS" -> "AFTER_TASK"
                            else -> sample.context
                        },
                        focusProtocolSessionId = sample.protocolSessionId?.takeIf(validSessionIds::contains),
                        source = "LEGACY_ENERGY_SAMPLE",
                        quality = "EXACT",
                        confidence = 1f,
                        legacyEnergySampleId = sample.id,
                        createdAt = sample.timestamp
                    )
                }
                if (observations.isNotEmpty()) db.energyObservationDao().insertAll(observations)
            }

            root.optJSONArray("taskDemandProfiles")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull {
                    taskDemandProfileFromJson(arr.getJSONObject(it))
                }
                if (list.isNotEmpty()) db.taskDemandProfileDao().insertAll(list)
            }
            root.optJSONArray("taskDependencies")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull {
                    taskDependencyFromJson(arr.getJSONObject(it))
                }
                if (list.isNotEmpty()) db.taskDependencyDao().insertAll(list)
            }
            root.optJSONArray("workEpisodeAssessments")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull {
                    workEpisodeAssessmentFromJson(arr.getJSONObject(it))
                }
                if (list.isNotEmpty()) db.workEpisodeAssessmentDao().insertAll(list)
            }
            root.optJSONArray("contextSnapshots")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull {
                    contextSnapshotFromJson(arr.getJSONObject(it))
                }
                if (list.isNotEmpty()) db.contextSnapshotDao().insertAll(list)
            }
            root.optJSONArray("recommendationDecisions")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull {
                    recommendationDecisionFromJson(arr.getJSONObject(it))
                }
                if (list.isNotEmpty()) db.recommendationDecisionDao().insertAll(list)
            }

            val importedLegacyEnglishProgress = englishProgressJson?.let { arr ->
                (0 until arr.length()).mapNotNull {
                    englishProgressFromJson(
                        o = arr.getJSONObject(it),
                        currentIdsByWord = currentEnglishIdsByWord
                    )
                }
            }.orEmpty()
            if (importedLegacyEnglishProgress.isNotEmpty()) {
                db.englishStudyDao().insertAllProgress(importedLegacyEnglishProgress)
            }

            val importedDirectionalProgress = englishDirectionalProgressJson?.let { arr ->
                (0 until arr.length()).mapNotNull {
                    englishDirectionalProgressFromJson(
                        o = arr.getJSONObject(it),
                        currentIdsByWord = currentEnglishIdsByWord
                    )
                }
            }.orEmpty()
            if (importedDirectionalProgress.isNotEmpty()) {
                db.englishStudyDao().insertAllDirectionalProgress(importedDirectionalProgress)
            } else if (importedLegacyEnglishProgress.isNotEmpty()) {
                // Backup v12 and earlier had one aggregate schedule per word. Preserve that
                // history in both tracks, matching the database 25 -> 26 migration semantics.
                db.englishStudyDao().insertAllDirectionalProgress(
                    importedLegacyEnglishProgress.flatMap { it.toDirectionalProgressTracks() }
                )
            }

            val importedSets = root.optJSONArray("englishStudySets")?.let { arr ->
                (0 until arr.length()).mapNotNull {
                    englishStudySetFromJson(arr.getJSONObject(it))
                }
            }.orEmpty()
            if (importedSets.isNotEmpty()) db.englishStudyDao().insertAllStudySets(importedSets)

            val validSetIds = importedSets.mapTo(mutableSetOf()) { it.id }
            val importedCards = englishStudyCardsJson?.let { arr ->
                (0 until arr.length()).mapNotNull {
                    englishStudyCardFromJson(
                        o = arr.getJSONObject(it),
                        currentIdsByWord = currentEnglishIdsByWord
                    )
                }.filter { it.setId in validSetIds }
            }.orEmpty()
            if (importedCards.isNotEmpty()) db.englishStudyDao().insertAllStudyCards(importedCards)

            val validCardIds = importedCards.mapTo(mutableSetOf()) { it.id }
            root.optJSONArray("englishCardProgress")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull {
                    englishCardProgressFromJson(arr.getJSONObject(it))
                }.filter { it.cardId in validCardIds }
                if (list.isNotEmpty()) db.englishStudyDao().insertAllCardProgress(list)
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

        val automationJson = root.optJSONObject("sleepAutomation")
        SleepAutomationPreference(context).replace(
            if (automationJson == null) {
                SleepAutomationSettings()
            } else {
                SleepAutomationSettings(
                    enabled = automationJson.optBoolean("enabled", false),
                    windowStartMinutes = automationJson.optInt("windowStartMinutes", 22 * 60),
                    windowEndMinutes = automationJson.optInt("windowEndMinutes", 2 * 60)
                )
            }
        )
        SleepAutomationScheduler(context).scheduleNext()

        val dailyPlanJson = root.optJSONObject("dailyPlanNudges")
        DailyPlanNudgePreferences(context).replaceControls(
            if (dailyPlanJson == null) {
                DailyPlanNudgeSettings()
            } else {
                DailyPlanNudgeSettings(
                    enabled = dailyPlanJson.optBoolean("enabled", true),
                    bufferMinutes = dailyPlanJson.optInt("bufferMinutes", 60),
                    repeatEnabled = dailyPlanJson.optBoolean("repeatEnabled", true),
                    repeatIntervalMinutes = dailyPlanJson.optInt("repeatIntervalMinutes", 15),
                    morningReminderEnabled = dailyPlanJson.optBoolean("morningReminderEnabled", true),
                    cutoffMinutesOfDay = dailyPlanJson.optInt("cutoffMinutesOfDay", 0)
                )
            }
        )
        DailyPlanNudgeScheduler(context, database = db).reschedule()

        FocusProtocolManager(context).reconcileActiveSessions()
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
        put("notificationVolumePercent", p.notificationVolumePercent)
        put("mathDifficulty", p.mathDifficulty.name)
        put("mathChallengeCount", p.mathChallengeCount)
        put("quietAlarmEnabled", p.quietAlarmEnabled)
        put("vibrationEnabled", p.vibrationEnabled)
        put("alarmRingtoneUri", p.alarmRingtoneUri ?: JSONObject.NULL)
        put("cueScheduleMode", p.cueScheduleMode.name)
        put("remCueOffsetPercent", p.remCueOffsetPercent)
        put("autoDetectOnsetEnabled", p.autoDetectOnsetEnabled)
        put("autoCorrectWakeEnabled", p.autoCorrectWakeEnabled)
        put("autoCorrectMinConfidencePercent", p.autoCorrectMinConfidencePercent)
        put("autoCorrectMaxShiftMinutes", p.autoCorrectMaxShiftMinutes)
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

    private fun pomodoroSessionToJson(s: PomodoroSessionEntity) = JSONObject().apply {
        put("id", s.id); put("startedAt", s.startedAt); put("durationMinutes", s.durationMinutes)
        put("completedAt", s.completedAt ?: JSONObject.NULL); put("isCompleted", s.isCompleted)
        put("isBreak", s.isBreak); put("activityType", s.activityType.name)
        put("subjectId", s.subjectId ?: JSONObject.NULL); put("taskId", s.taskId ?: JSONObject.NULL)
        put("otherActivityId", s.otherActivityId ?: JSONObject.NULL); put("itemName", s.itemName)
        put("actualDurationMillis", s.actualDurationMillis)
        put("recordSource", s.recordSource)
    }

    private fun focusProtocolToJson(s: FocusProtocolSessionEntity) = JSONObject().apply {
        put("id", s.id); put("activityType", s.activityType.name); put("itemId", s.itemId)
        put("itemName", s.itemName); put("outcome", s.outcome); put("phase", s.phase.name)
        put("createdAt", s.createdAt); put("phaseStartedAt", s.phaseStartedAt)
        put("phaseEndsAt", s.phaseEndsAt ?: JSONObject.NULL)
        put("resetDurationMinutes", s.resetDurationMinutes)
        put("focusDurationMinutes", s.focusDurationMinutes)
        put("recoveryDurationMinutes", s.recoveryDurationMinutes)
        put("energyBefore", s.energyBefore); put("energyAfter", s.energyAfter ?: JSONObject.NULL)
        put("distractionCount", s.distractionCount)
        put("focusStartedAt", s.focusStartedAt ?: JSONObject.NULL)
        put("focusElapsedMillis", s.focusElapsedMillis)
        put("pausedRemainingMillis", s.pausedRemainingMillis)
        put("completedAt", s.completedAt ?: JSONObject.NULL)
        put("cancelReason", s.cancelReason ?: JSONObject.NULL)
        put("pomodoroRecorded", s.pomodoroRecorded)
        put("completedCycles", s.completedCycles)
        put("totalFocusMillis", s.totalFocusMillis)
        put("soundscapeId", s.soundscapeId)
        put("soundscapeCustomUri", s.soundscapeCustomUri ?: JSONObject.NULL)
        put("soundscapeCustomName", s.soundscapeCustomName ?: JSONObject.NULL)
        put("soundscapeVolume", s.soundscapeVolume)
        put("soundscapeSecondaryId", s.soundscapeSecondaryId ?: JSONObject.NULL)
        put("soundscapeSecondaryVolume", s.soundscapeSecondaryVolume)
        put("soundscapePlayDuringRecovery", s.soundscapePlayDuringRecovery)
    }

    private fun energySampleToJson(s: EnergySampleEntity) = JSONObject().apply {
        put("id", s.id); put("timestamp", s.timestamp); put("energy", s.energy)
        put("context", s.context)
        put("protocolSessionId", s.protocolSessionId ?: JSONObject.NULL)
    }

    private fun otherActivityToJson(a: OtherActivityEntity) = JSONObject().apply {
        put("id", a.id); put("name", a.name); put("color", a.color); put("createdAt", a.createdAt)
    }

    private fun eventToJson(e: CalendarEventEntity) = JSONObject().apply {
        put("id", e.id); put("title", e.title); put("startMillis", e.startMillis)
        put("endMillis", e.endMillis); put("allDay", e.allDay); put("repeatRule", e.repeatRule)
        put("reminderMinutes", e.reminderMinutes ?: JSONObject.NULL)
        put("eventKind", e.eventKind)
        put("taskId", e.taskId ?: JSONObject.NULL)
        put("projectId", e.projectId ?: JSONObject.NULL)
        put("createdAt", e.createdAt)
    }

    private fun taskToJson(t: TaskEntity) = JSONObject().apply {
        put("id", t.id); put("title", t.title); put("isDone", t.isDone)
        put("isMorningRoutine", t.isMorningRoutine); put("createdAt", t.createdAt)
        put("completedAt", t.completedAt ?: JSONObject.NULL)
        put("doneDate", t.doneDate ?: JSONObject.NULL)
        put("streakCount", t.streakCount)
        put("reminderId", t.reminderId ?: JSONObject.NULL)
        put("matrixQuadrant", t.matrixQuadrant)
        put("description", t.description)
        put("whyImportant", t.whyImportant)
        put("definitionOfDone", t.definitionOfDone)
        put("nextAction", t.nextAction)
        put("imagePath", t.imagePath ?: JSONObject.NULL)
        put("dueAtMillis", t.dueAtMillis ?: JSONObject.NULL)
        put("estimatedMinutes", t.estimatedMinutes)
        put("spentMillis", t.spentMillis)
        put("sortOrder", t.sortOrder)
        put("energyLevel", t.energyLevel)
        put("contextTag", t.contextTag)
        put("dependencies", t.dependencies)
        put("obstacle", t.obstacle)
        put("ifThenPlan", t.ifThenPlan)
        put("checklist", t.checklist)
        put("projectTag", t.projectTag)
        put("assignee", t.assignee)
        put("workBudgetMinutes", t.workBudgetMinutes)
        put("projectId", t.projectId ?: JSONObject.NULL)
        put("category", t.category)
        put("tags", t.tags)
        put("materials", t.materials)
        put("expectedResult", t.expectedResult)
        put("startAtMillis", t.startAtMillis ?: JSONObject.NULL)
        put("repeatRule", t.repeatRule)
        put("plannedFocusMinutes", t.plannedFocusMinutes)
        put("isDailyRequired", t.isDailyRequired)
        put("updatedAt", t.updatedAt)
    }

    private fun reminderToJson(r: ReminderEntity) = JSONObject().apply {
        put("id", r.id); put("title", r.title); put("timeHour", r.timeHour)
        put("timeMinute", r.timeMinute); put("repeatMode", r.repeatMode.name)
        put("daysOfWeek", r.daysOfWeek); put("intervalDays", r.intervalDays)
        put("nextTriggerTime", r.nextTriggerTime); put("isEnabled", r.isEnabled)
        put("linkedType", r.linkedType); put("linkedId", r.linkedId ?: JSONObject.NULL)
        put("triggerRule", r.triggerRule); put("offsetMinutes", r.offsetMinutes)
        put("inactivityHours", r.inactivityHours)
        put("createdAt", r.createdAt)
    }

    private fun ddayToJson(d: DDayEntity) = JSONObject().apply {
        put("id", d.id); put("title", d.title); put("targetDate", d.targetDate)
        put("projectId", d.projectId ?: JSONObject.NULL); put("taskId", d.taskId ?: JSONObject.NULL)
        put("notes", d.notes)
        put("createdAt", d.createdAt)
    }

    private fun projectToJson(p: ProjectEntity) = JSONObject().apply {
        put("id", p.id); put("title", p.title); put("description", p.description); put("goal", p.goal)
        put("color", p.color); put("workBudgetMinutes", p.workBudgetMinutes); put("spentMillis", p.spentMillis)
        put("dueAtMillis", p.dueAtMillis ?: JSONObject.NULL); put("isArchived", p.isArchived)
        put("createdAt", p.createdAt); put("updatedAt", p.updatedAt)
    }

    private fun subtaskToJson(s: TaskSubtaskEntity) = JSONObject().apply {
        put("id", s.id); put("taskId", s.taskId); put("title", s.title); put("isDone", s.isDone)
        put("sortOrder", s.sortOrder); put("createdAt", s.createdAt); put("completedAt", s.completedAt ?: JSONObject.NULL)
    }

    private fun attachmentToJson(a: TaskAttachmentEntity) = JSONObject().apply {
        put("id", a.id); put("taskId", a.taskId); put("localPath", a.localPath); put("mimeType", a.mimeType)
        put("caption", a.caption); put("createdAt", a.createdAt)
    }

    private fun taskLibraryLinkToJson(link: TaskLibraryLinkEntity) = JSONObject().apply {
        put("taskId", link.taskId); put("libraryItemId", link.libraryItemId); put("note", link.note); put("createdAt", link.createdAt)
    }

    private fun activityRecordToJson(a: ActivityRecordEntity) = JSONObject().apply {
        put("id", a.id); put("taskId", a.taskId ?: JSONObject.NULL); put("projectId", a.projectId ?: JSONObject.NULL)
        put("activityType", a.activityType.name); put("subjectId", a.subjectId ?: JSONObject.NULL)
        put("otherActivityId", a.otherActivityId ?: JSONObject.NULL); put("title", a.title); put("category", a.category)
        put("startedAt", a.startedAt); put("endedAt", a.endedAt); put("durationMillis", a.durationMillis)
        put("source", a.source); put("result", a.result); put("material", a.material); put("note", a.note)
        put("pomodoroSessionId", a.pomodoroSessionId ?: JSONObject.NULL)
        put("countsTowardProgress", a.countsTowardProgress); put("createdAt", a.createdAt); put("updatedAt", a.updatedAt)
    }

    private fun dailyCheckInToJson(c: DailyCheckInEntity) = JSONObject().apply {
        put("id", c.id)
        put("localDate", c.localDate)
        put("timestamp", c.timestamp)
        put("zoneId", c.zoneId)
        put("energy", c.energy ?: JSONObject.NULL)
        put("mood", c.mood ?: JSONObject.NULL)
        put("clarity", c.clarity ?: JSONObject.NULL)
        put("focus", c.focus ?: JSONObject.NULL)
        put("social", c.social ?: JSONObject.NULL)
        put("physical", c.physical ?: JSONObject.NULL)
        put("stress", c.stress ?: JSONObject.NULL)
        put("source", c.source)
        put("unusualDayFlags", c.unusualDayFlags)
        put("unusualDayNote", c.unusualDayNote)
        put("excludedFromLearning", c.excludedFromLearning)
        put("createdAt", c.createdAt)
        put("updatedAt", c.updatedAt)
    }

    private fun energyObservationToJson(e: EnergyObservationEntity) = JSONObject().apply {
        put("id", e.id)
        put("timestamp", e.timestamp)
        put("absoluteEnergy", e.absoluteEnergy ?: JSONObject.NULL)
        put("relativeDelta", e.relativeDelta ?: JSONObject.NULL)
        put("context", e.context)
        put("taskId", e.taskId ?: JSONObject.NULL)
        put("activityRecordId", e.activityRecordId ?: JSONObject.NULL)
        put("focusProtocolSessionId", e.focusProtocolSessionId ?: JSONObject.NULL)
        put("source", e.source)
        put("quality", e.quality)
        put("confidence", e.confidence.toDouble())
        put("excludedFromLearning", e.excludedFromLearning)
        put("legacyEnergySampleId", e.legacyEnergySampleId ?: JSONObject.NULL)
        put("createdAt", e.createdAt)
    }

    private fun taskDemandProfileToJson(p: TaskDemandProfileEntity) = JSONObject().apply {
        put("taskId", p.taskId)
        put("domain", p.domain)
        put("workMode", p.workMode)
        put("difficulty", p.difficulty)
        put("concentrationDemand", p.concentrationDemand)
        put("executiveDemand", p.executiveDemand)
        put("memoryDemand", p.memoryDemand)
        put("creativeDemand", p.creativeDemand)
        put("socialDemand", p.socialDemand)
        put("physicalDemand", p.physicalDemand)
        put("emotionalDemand", p.emotionalDemand)
        put("startFriction", p.startFriction)
        put("minimumBlockMinutes", p.minimumBlockMinutes)
        put("preferredBlockMinutes", p.preferredBlockMinutes)
        put("interruptibility", p.interruptibility)
        put("placeContext", p.placeContext)
        put("toolContext", p.toolContext)
        put("internetRequirement", p.internetRequirement)
        put("peopleContext", p.peopleContext)
        put("canDoPartially", p.canDoPartially)
        put("fixedTime", p.fixedTime)
        put("provenance", p.provenance)
        put("confidence", p.confidence.toDouble())
        put("userLockMask", p.userLockMask)
        put("updatedAt", p.updatedAt)
    }

    private fun taskDependencyToJson(d: TaskDependencyEntity) = JSONObject().apply {
        put("taskId", d.taskId)
        put("dependsOnTaskId", d.dependsOnTaskId)
        put("dependencyType", d.dependencyType)
        put("createdAt", d.createdAt)
    }

    private fun workEpisodeAssessmentToJson(a: WorkEpisodeAssessmentEntity) = JSONObject().apply {
        put("id", a.id)
        put("activityRecordId", a.activityRecordId)
        put("beforeObservationId", a.beforeObservationId ?: JSONObject.NULL)
        put("afterObservationId", a.afterObservationId ?: JSONObject.NULL)
        put("recoveryObservationId", a.recoveryObservationId ?: JSONObject.NULL)
        put("goalOutcome", a.goalOutcome)
        put("perceivedDifficulty", a.perceivedDifficulty ?: JSONObject.NULL)
        put("interruptionReason", a.interruptionReason ?: JSONObject.NULL)
        put("profileMismatchFlags", a.profileMismatchFlags)
        put("modelEligible", a.modelEligible)
        put("createdAt", a.createdAt)
        put("updatedAt", a.updatedAt)
    }

    private fun externalContextToJson(c: ExternalContextEntity) = JSONObject().apply {
        put("id", c.id)
        put("localDate", c.localDate)
        put("regionKey", c.regionKey)
        put("source", c.source)
        put("daylightMinutes", c.daylightMinutes ?: JSONObject.NULL)
        put("daylightChangeMinutes", c.daylightChangeMinutes ?: JSONObject.NULL)
        put("weatherCode", c.weatherCode ?: JSONObject.NULL)
        put("temperatureCelsius", c.temperatureCelsius?.toDouble() ?: JSONObject.NULL)
        put("cloudCoverPercent", c.cloudCoverPercent ?: JSONObject.NULL)
        put("precipitationProbability", c.precipitationProbability ?: JSONObject.NULL)
        put("outdoorSuitability", c.outdoorSuitability?.toDouble() ?: JSONObject.NULL)
        put("publicBackgroundSummary", c.publicBackgroundSummary ?: JSONObject.NULL)
        put("fetchedAt", c.fetchedAt)
        put("expiresAt", c.expiresAt)
        put("provenance", c.provenance)
        put("rawPayloadHash", c.rawPayloadHash ?: JSONObject.NULL)
        put("createdAt", c.createdAt)
    }

    private fun contextSnapshotToJson(s: ContextSnapshotEntity) = JSONObject().apply {
        put("id", s.id)
        put("timestamp", s.timestamp)
        put("zoneId", s.zoneId)
        put("localDate", s.localDate)
        put("minutesSinceWake", s.minutesSinceWake ?: JSONObject.NULL)
        put("hoursAwake", s.hoursAwake?.toDouble() ?: JSONObject.NULL)
        put("sleepDurationMinutes", s.sleepDurationMinutes ?: JSONObject.NULL)
        put("sleepDeviationMinutes", s.sleepDeviationMinutes ?: JSONObject.NULL)
        put("sleepDebtMinutes", s.sleepDebtMinutes ?: JSONObject.NULL)
        put("sleepRegularity", s.sleepRegularity?.toDouble() ?: JSONObject.NULL)
        put("dayOfWeek", s.dayOfWeek)
        put("isFreeDay", s.isFreeDay)
        put("calendarWindowMinutes", s.calendarWindowMinutes ?: JSONObject.NULL)
        put("recentFocusMinutes", s.recentFocusMinutes)
        put("recentWorkModes", s.recentWorkModes)
        put("recentBreakMinutes", s.recentBreakMinutes)
        put("dailyCheckInId", s.dailyCheckInId ?: JSONObject.NULL)
        put("lastObservationAgeMinutes", s.lastObservationAgeMinutes ?: JSONObject.NULL)
        put("personalPeriodFlags", s.personalPeriodFlags)
        put("externalContextId", s.externalContextId ?: JSONObject.NULL)
        put("version", s.version)
        put("createdAt", s.createdAt)
    }

    private fun recommendationDecisionToJson(d: RecommendationDecisionEntity) = JSONObject().apply {
        put("id", d.id)
        put("generatedAt", d.generatedAt)
        put("modelVersion", d.modelVersion)
        put("strategy", d.strategy)
        put("contextSnapshotId", d.contextSnapshotId ?: JSONObject.NULL)
        put("stateSnapshotJson", d.stateSnapshotJson)
        put("selectedTaskId", d.selectedTaskId ?: JSONObject.NULL)
        put("candidateTaskIds", d.candidateTaskIds)
        put("componentScores", d.componentScores)
        put("reasonCodes", d.reasonCodes)
        put("confidence", d.confidence.toDouble())
        put("accepted", d.accepted)
        put("dismissed", d.dismissed)
        put("reordered", d.reordered)
        put("feedbackReason", d.feedbackReason ?: JSONObject.NULL)
        put("resultingActivityRecordId", d.resultingActivityRecordId ?: JSONObject.NULL)
        put("createdAt", d.createdAt)
        put("updatedAt", d.updatedAt)
    }

    private fun englishProgressToJson(saved: EnglishProgressWithWordProjection) = JSONObject().apply {
        val p = saved.progress
        put("word", saved.word)
        put("wordId", p.wordId)
        put("dueAtMillis", p.dueAtMillis)
        put("intervalMinutes", p.intervalMinutes)
        put("easePermille", p.easePermille)
        put("repetitions", p.repetitions)
        put("lapses", p.lapses)
        put("reviewCount", p.reviewCount)
        put("correctCount", p.correctCount)
        put("cardReviews", p.cardReviews)
        put("writingReviews", p.writingReviews)
        put("pronunciationReviews", p.pronunciationReviews)
        put("listeningReviews", p.listeningReviews)
        put("lastGrade", p.lastGrade)
        put("lastMode", p.lastMode)
        put("lastReviewedAtMillis", p.lastReviewedAtMillis)
    }

    private fun englishDirectionalProgressToJson(
        saved: EnglishDirectionalProgressWithWordProjection
    ) = JSONObject().apply {
        val p = saved.progress
        put("word", saved.word)
        put("wordId", p.wordId)
        put("direction", p.direction)
        put("dueAtMillis", p.dueAtMillis)
        put("intervalMinutes", p.intervalMinutes)
        put("easePermille", p.easePermille)
        put("repetitions", p.repetitions)
        put("lapses", p.lapses)
        put("reviewCount", p.reviewCount)
        put("correctCount", p.correctCount)
        put("cardReviews", p.cardReviews)
        put("writingReviews", p.writingReviews)
        put("pronunciationReviews", p.pronunciationReviews)
        put("listeningReviews", p.listeningReviews)
        put("lastGrade", p.lastGrade)
        put("lastMode", p.lastMode)
        put("lastReviewedAtMillis", p.lastReviewedAtMillis)
    }

    private fun englishStudySetToJson(studySet: EnglishStudySetEntity) = JSONObject().apply {
        put("id", studySet.id)
        put("title", studySet.title)
        put("description", studySet.description)
        put("colorSeed", studySet.colorSeed)
        put("defaultDirection", studySet.defaultDirection)
        put("createdAtMillis", studySet.createdAtMillis)
        put("updatedAtMillis", studySet.updatedAtMillis)
    }

    private fun englishStudyCardToJson(saved: EnglishStudyCardBackupProjection) = JSONObject().apply {
        val card = saved.card
        put("id", card.id)
        put("setId", card.setId)
        put("dictionaryWordId", card.dictionaryWordId ?: JSONObject.NULL)
        put("dictionaryHeadword", saved.dictionaryHeadword ?: JSONObject.NULL)
        put("term", card.term)
        put("translation", card.translation)
        put("definition", card.definition)
        put("example", card.example)
        put("exampleTranslation", card.exampleTranslation)
        put("notes", card.notes)
        put("position", card.position)
        put("createdAtMillis", card.createdAtMillis)
        put("updatedAtMillis", card.updatedAtMillis)
    }

    private fun englishCardProgressToJson(progress: EnglishCardProgressEntity) = JSONObject().apply {
        put("cardId", progress.cardId)
        put("direction", progress.direction)
        put("dueAtMillis", progress.dueAtMillis)
        put("intervalMinutes", progress.intervalMinutes)
        put("easePermille", progress.easePermille)
        put("repetitions", progress.repetitions)
        put("lapses", progress.lapses)
        put("reviewCount", progress.reviewCount)
        put("correctCount", progress.correctCount)
        put("cardReviews", progress.cardReviews)
        put("writingReviews", progress.writingReviews)
        put("pronunciationReviews", progress.pronunciationReviews)
        put("listeningReviews", progress.listeningReviews)
        put("lastGrade", progress.lastGrade)
        put("lastMode", progress.lastMode)
        put("lastReviewedAtMillis", progress.lastReviewedAtMillis)
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
        put("automationSafetyWakeTime", s.automationSafetyWakeTime ?: JSONObject.NULL)
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
        put("detectedOnsetConfidencePercent", s.detectedOnsetConfidencePercent ?: JSONObject.NULL)
        put("detectedOnsetSource", s.detectedOnsetSource ?: JSONObject.NULL)
        put("detectedOnsetUncertaintyMinutes", s.detectedOnsetUncertaintyMinutes ?: JSONObject.NULL)
        put("onsetReviewState", s.onsetReviewState)
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
        put("resourceKind", i.resourceKind.name)
        put("localFilePath", i.localFilePath ?: JSONObject.NULL)
        put("originalFileName", i.originalFileName)
        put("referenceUrl", i.referenceUrl)
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

    private fun pomodoroSessionFromJson(o: JSONObject): PomodoroSessionEntity? = try {
        PomodoroSessionEntity(
            id = o.optInt("id", 0),
            startedAt = o.optLong("startedAt"),
            durationMinutes = o.optInt("durationMinutes"),
            completedAt = if (o.isNull("completedAt")) null else o.optLong("completedAt"),
            isCompleted = o.optBoolean("isCompleted", false),
            isBreak = o.optBoolean("isBreak", false),
            activityType = enumValueOrDefault(
                o.optString("activityType"),
                FocusActivityType.STUDY
            ),
            subjectId = if (o.isNull("subjectId")) null else o.optInt("subjectId"),
            taskId = if (o.isNull("taskId")) null else o.optInt("taskId"),
            otherActivityId = if (o.isNull("otherActivityId")) null else o.optInt("otherActivityId"),
            itemName = o.optString("itemName", ""),
            actualDurationMillis = o.optLong(
                "actualDurationMillis",
                o.optInt("durationMinutes", 0) * 60_000L
            ),
            recordSource = o.optString("recordSource", "TIMER")
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга pomodoro-сессии", e); null
    }

    private fun focusProtocolFromJson(o: JSONObject): FocusProtocolSessionEntity? = try {
        FocusProtocolSessionEntity(
            id = o.optInt("id", 0),
            activityType = enumValueOrDefault(
                o.optString("activityType"),
                FocusActivityType.OTHER
            ),
            itemId = o.optInt("itemId", 0),
            itemName = o.optString("itemName", ""),
            outcome = o.optString("outcome", ""),
            phase = enumValueOrDefault(
                o.optString("phase"),
                FocusProtocolPhase.CANCELLED
            ),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            phaseStartedAt = o.optLong("phaseStartedAt", System.currentTimeMillis()),
            phaseEndsAt = if (o.isNull("phaseEndsAt")) null else o.optLong("phaseEndsAt"),
            resetDurationMinutes = o.optInt("resetDurationMinutes", 10),
            focusDurationMinutes = o.optInt("focusDurationMinutes", 25),
            recoveryDurationMinutes = o.optInt("recoveryDurationMinutes", 5),
            energyBefore = o.optInt("energyBefore", 5).coerceIn(1, 10),
            energyAfter = if (o.isNull("energyAfter")) null else o.optInt("energyAfter").coerceIn(1, 10),
            distractionCount = o.optInt("distractionCount", 0),
            focusStartedAt = if (o.isNull("focusStartedAt")) null else o.optLong("focusStartedAt"),
            focusElapsedMillis = o.optLong("focusElapsedMillis", 0L),
            pausedRemainingMillis = o.optLong("pausedRemainingMillis", 0L),
            completedAt = if (o.isNull("completedAt")) null else o.optLong("completedAt"),
            cancelReason = if (o.isNull("cancelReason")) null else o.optString("cancelReason"),
            pomodoroRecorded = o.optBoolean("pomodoroRecorded", false),
            completedCycles = o.optInt("completedCycles", 0),
            totalFocusMillis = o.optLong("totalFocusMillis", 0L),
            soundscapeId = o.optString("soundscapeId", "silence"),
            soundscapeCustomUri = if (o.isNull("soundscapeCustomUri")) {
                null
            } else {
                o.optString("soundscapeCustomUri").takeIf { it.isNotBlank() }
            },
            soundscapeCustomName = if (o.isNull("soundscapeCustomName")) {
                null
            } else {
                o.optString("soundscapeCustomName").takeIf { it.isNotBlank() }
            },
            soundscapeVolume = o.optInt("soundscapeVolume", 35).coerceIn(0, 100),
            soundscapeSecondaryId = if (o.isNull("soundscapeSecondaryId")) {
                null
            } else {
                o.optString("soundscapeSecondaryId").takeIf { it.isNotBlank() }
            },
            soundscapeSecondaryVolume = o.optInt("soundscapeSecondaryVolume", 20)
                .coerceIn(0, 100),
            soundscapePlayDuringRecovery = o.optBoolean(
                "soundscapePlayDuringRecovery",
                false
            )
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга протокола фокуса", e); null
    }

    private fun energySampleFromJson(o: JSONObject): EnergySampleEntity? = try {
        EnergySampleEntity(
            id = o.optInt("id", 0),
            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
            energy = o.optInt("energy", 5).coerceIn(1, 10),
            context = o.optString("context", "BEFORE_FOCUS"),
            protocolSessionId = if (o.isNull("protocolSessionId")) {
                null
            } else {
                o.optInt("protocolSessionId")
            }
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга энергии", e); null
    }

    private fun otherActivityFromJson(o: JSONObject): OtherActivityEntity? = try {
        OtherActivityEntity(
            id = o.optInt("id", 0),
            name = o.optString("name", ""),
            color = o.optInt("color", 0xFF9E9E9E.toInt()),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга дела", e); null
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
            eventKind = o.optString("eventKind", "PLANNED"),
            taskId = if (o.isNull("taskId")) null else o.optInt("taskId"),
            projectId = if (o.isNull("projectId")) null else o.optInt("projectId"),
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
            reminderId = if (o.isNull("reminderId")) null else o.optInt("reminderId"),
            matrixQuadrant = o.optInt("matrixQuadrant", 2).coerceIn(1, 4),
            description = o.optString("description", ""),
            whyImportant = o.optString("whyImportant", ""),
            definitionOfDone = o.optString("definitionOfDone", ""),
            nextAction = o.optString("nextAction", ""),
            imagePath = o.optStringOrNull("imagePath"),
            dueAtMillis = if (o.isNull("dueAtMillis")) null else o.optLong("dueAtMillis"),
            estimatedMinutes = o.optInt("estimatedMinutes", 25).coerceIn(5, 480),
            spentMillis = o.optLong("spentMillis", 0L).coerceAtLeast(0L),
            sortOrder = o.optInt("sortOrder", 0),
            energyLevel = o.optString("energyLevel", "MEDIUM"),
            contextTag = o.optString("contextTag", ""),
            dependencies = o.optString("dependencies", ""),
            obstacle = o.optString("obstacle", ""),
            ifThenPlan = o.optString("ifThenPlan", ""),
            checklist = o.optString("checklist", ""),
            projectTag = o.optString("projectTag", ""),
            assignee = o.optString("assignee", ""),
            workBudgetMinutes = o.optInt("workBudgetMinutes", 0).coerceAtLeast(0),
            projectId = if (o.isNull("projectId")) null else o.optInt("projectId"),
            category = o.optString("category", "WORK"),
            tags = o.optString("tags", ""),
            materials = o.optString("materials", ""),
            expectedResult = o.optString("expectedResult", ""),
            startAtMillis = if (o.isNull("startAtMillis")) null else o.optLong("startAtMillis"),
            repeatRule = o.optString("repeatRule", ""),
            plannedFocusMinutes = o.optInt("plannedFocusMinutes", o.optInt("estimatedMinutes", 25)).coerceIn(5, 480),
            isDailyRequired = o.optBoolean("isDailyRequired", false),
            updatedAt = o.optLong("updatedAt", o.optLong("createdAt", System.currentTimeMillis()))
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
            linkedType = o.optString("linkedType", ""),
            linkedId = if (o.isNull("linkedId")) null else o.optInt("linkedId"),
            triggerRule = o.optString("triggerRule", "AT_TIME"),
            offsetMinutes = o.optInt("offsetMinutes", 5),
            inactivityHours = o.optInt("inactivityHours", 24),
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
            projectId = if (o.isNull("projectId")) null else o.optInt("projectId"),
            taskId = if (o.isNull("taskId")) null else o.optInt("taskId"),
            notes = o.optString("notes", ""),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга D-Day", e); null
    }

    private fun projectFromJson(o: JSONObject): ProjectEntity? = try {
        ProjectEntity(
            id = o.optInt("id", 0), title = o.optString("title"), description = o.optString("description", ""),
            goal = o.optString("goal", ""), color = o.optLong("color", 0xFF6574CD),
            workBudgetMinutes = o.optInt("workBudgetMinutes", 0), spentMillis = o.optLong("spentMillis", 0L),
            dueAtMillis = if (o.isNull("dueAtMillis")) null else o.optLong("dueAtMillis"),
            isArchived = o.optBoolean("isArchived", false), createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        )
    } catch (e: Exception) { Log.e(TAG, "Ошибка парсинга проекта", e); null }

    private fun subtaskFromJson(o: JSONObject): TaskSubtaskEntity? = try {
        TaskSubtaskEntity(
            id = o.optInt("id", 0), taskId = o.optInt("taskId"), title = o.optString("title"),
            isDone = o.optBoolean("isDone", false), sortOrder = o.optInt("sortOrder", 0),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            completedAt = if (o.isNull("completedAt")) null else o.optLong("completedAt")
        )
    } catch (e: Exception) { Log.e(TAG, "Ошибка парсинга подзадачи", e); null }

    private fun attachmentFromJson(o: JSONObject): TaskAttachmentEntity? = try {
        TaskAttachmentEntity(
            id = o.optInt("id", 0), taskId = o.optInt("taskId"), localPath = o.optString("localPath"),
            mimeType = o.optString("mimeType", "application/octet-stream"), caption = o.optString("caption", ""),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    } catch (e: Exception) { Log.e(TAG, "Ошибка парсинга вложения", e); null }

    private fun taskLibraryLinkFromJson(o: JSONObject): TaskLibraryLinkEntity? = try {
        TaskLibraryLinkEntity(
            taskId = o.optInt("taskId"), libraryItemId = o.optInt("libraryItemId"), note = o.optString("note", ""),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    } catch (e: Exception) { Log.e(TAG, "Ошибка парсинга связи библиотеки", e); null }

    private fun activityRecordFromJson(o: JSONObject): ActivityRecordEntity? = try {
        ActivityRecordEntity(
            id = o.optInt("id", 0), taskId = if (o.isNull("taskId")) null else o.optInt("taskId"),
            projectId = if (o.isNull("projectId")) null else o.optInt("projectId"),
            activityType = enumValueOrDefault(o.optString("activityType"), FocusActivityType.WORK),
            subjectId = if (o.isNull("subjectId")) null else o.optInt("subjectId"),
            otherActivityId = if (o.isNull("otherActivityId")) null else o.optInt("otherActivityId"),
            title = o.optString("title"), category = o.optString("category", "WORK"),
            startedAt = o.optLong("startedAt"), endedAt = o.optLong("endedAt"), durationMillis = o.optLong("durationMillis"),
            source = o.optString("source", "MANUAL"), result = o.optString("result", ""),
            material = o.optString("material", ""), note = o.optString("note", ""),
            pomodoroSessionId = if (o.isNull("pomodoroSessionId")) null else o.optInt("pomodoroSessionId"),
            countsTowardProgress = o.optBoolean("countsTowardProgress", true),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()), updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        )
    } catch (e: Exception) { Log.e(TAG, "Ошибка парсинга активности", e); null }

    private fun dailyCheckInFromJson(o: JSONObject): DailyCheckInEntity? = try {
        DailyCheckInEntity(
            id = o.optInt("id", 0),
            localDate = o.optString("localDate", ""),
            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
            zoneId = o.optString("zoneId", "UTC"),
            energy = o.optIntOrNull("energy")?.coerceIn(1, 10),
            mood = o.optIntOrNull("mood")?.coerceIn(1, 5),
            clarity = o.optIntOrNull("clarity")?.coerceIn(0, 4),
            focus = o.optIntOrNull("focus")?.coerceIn(0, 4),
            social = o.optIntOrNull("social")?.coerceIn(0, 4),
            physical = o.optIntOrNull("physical")?.coerceIn(0, 4),
            stress = o.optIntOrNull("stress")?.coerceIn(0, 4),
            source = o.optString("source", "AD_HOC"),
            unusualDayFlags = o.optString("unusualDayFlags", ""),
            unusualDayNote = o.optString("unusualDayNote", ""),
            excludedFromLearning = o.optBoolean("excludedFromLearning", false),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", o.optLong("createdAt", System.currentTimeMillis()))
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга дневной самооценки", e); null
    }

    private fun energyObservationFromJson(o: JSONObject): EnergyObservationEntity? = try {
        EnergyObservationEntity(
            id = o.optInt("id", 0),
            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
            absoluteEnergy = o.optIntOrNull("absoluteEnergy")?.coerceIn(1, 10),
            relativeDelta = o.optIntOrNull("relativeDelta")?.coerceIn(-9, 9),
            context = o.optString("context", "AD_HOC"),
            taskId = o.optIntOrNull("taskId"),
            activityRecordId = o.optIntOrNull("activityRecordId"),
            focusProtocolSessionId = o.optIntOrNull("focusProtocolSessionId"),
            source = o.optString("source", "USER"),
            quality = o.optString("quality", "EXACT"),
            confidence = o.optDouble("confidence", 1.0).toFloat().coerceIn(0f, 1f),
            excludedFromLearning = o.optBoolean("excludedFromLearning", false),
            legacyEnergySampleId = o.optIntOrNull("legacyEnergySampleId"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга наблюдения энергии", e); null
    }

    private fun taskDemandProfileFromJson(o: JSONObject): TaskDemandProfileEntity? = try {
        val minimum = o.optInt("minimumBlockMinutes", 5).coerceIn(1, 24 * 60)
        TaskDemandProfileEntity(
            taskId = o.optInt("taskId"),
            domain = o.optString("domain", "OTHER"),
            workMode = o.optString("workMode", "OTHER"),
            difficulty = o.optInt("difficulty", 0).coerceIn(0, 4),
            concentrationDemand = o.optInt("concentrationDemand", 0).coerceIn(0, 4),
            executiveDemand = o.optInt("executiveDemand", 0).coerceIn(0, 4),
            memoryDemand = o.optInt("memoryDemand", 0).coerceIn(0, 4),
            creativeDemand = o.optInt("creativeDemand", 0).coerceIn(0, 4),
            socialDemand = o.optInt("socialDemand", 0).coerceIn(0, 4),
            physicalDemand = o.optInt("physicalDemand", 0).coerceIn(0, 4),
            emotionalDemand = o.optInt("emotionalDemand", 0).coerceIn(0, 4),
            startFriction = o.optInt("startFriction", 0).coerceIn(0, 4),
            minimumBlockMinutes = minimum,
            preferredBlockMinutes = o.optInt("preferredBlockMinutes", 25)
                .coerceIn(minimum, 24 * 60),
            interruptibility = o.optInt("interruptibility", 2).coerceIn(0, 4),
            placeContext = o.optString("placeContext", "ANY"),
            toolContext = o.optString("toolContext", ""),
            internetRequirement = o.optString("internetRequirement", "ANY"),
            peopleContext = o.optString("peopleContext", "ANY"),
            canDoPartially = o.optBoolean("canDoPartially", true),
            fixedTime = o.optBoolean("fixedTime", false),
            provenance = o.optString("provenance", "USER"),
            confidence = o.optDouble("confidence", 1.0).toFloat().coerceIn(0f, 1f),
            userLockMask = o.optLong("userLockMask", 0L).coerceAtLeast(0L),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        ).takeIf { it.taskId > 0 }
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга профиля нагрузки задачи", e); null
    }

    private fun taskDependencyFromJson(o: JSONObject): TaskDependencyEntity? = try {
        TaskDependencyEntity(
            taskId = o.optInt("taskId"),
            dependsOnTaskId = o.optInt("dependsOnTaskId"),
            dependencyType = o.optString("dependencyType", "FINISH_TO_START"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        ).takeIf { it.taskId > 0 && it.dependsOnTaskId > 0 && it.taskId != it.dependsOnTaskId }
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга зависимости задач", e); null
    }

    private fun workEpisodeAssessmentFromJson(o: JSONObject): WorkEpisodeAssessmentEntity? = try {
        WorkEpisodeAssessmentEntity(
            id = o.optInt("id", 0),
            activityRecordId = o.optInt("activityRecordId"),
            beforeObservationId = o.optIntOrNull("beforeObservationId"),
            afterObservationId = o.optIntOrNull("afterObservationId"),
            recoveryObservationId = o.optIntOrNull("recoveryObservationId"),
            goalOutcome = o.optString("goalOutcome", "UNKNOWN"),
            perceivedDifficulty = o.optIntOrNull("perceivedDifficulty")?.coerceIn(1, 10),
            interruptionReason = o.optStringOrNull("interruptionReason"),
            profileMismatchFlags = o.optString("profileMismatchFlags", ""),
            modelEligible = o.optBoolean("modelEligible", true),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", o.optLong("createdAt", System.currentTimeMillis()))
        ).takeIf { it.activityRecordId > 0 }
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга оценки рабочего эпизода", e); null
    }

    private fun externalContextFromJson(o: JSONObject): ExternalContextEntity? = try {
        val fetchedAt = o.optLong("fetchedAt", System.currentTimeMillis())
        ExternalContextEntity(
            id = o.optInt("id", 0),
            localDate = o.optString("localDate", ""),
            regionKey = o.optString("regionKey", ""),
            source = o.optString("source", ""),
            daylightMinutes = o.optIntOrNull("daylightMinutes")?.coerceIn(0, 24 * 60),
            daylightChangeMinutes = o.optIntOrNull("daylightChangeMinutes"),
            weatherCode = o.optStringOrNull("weatherCode"),
            temperatureCelsius = o.optFloatOrNull("temperatureCelsius"),
            cloudCoverPercent = o.optIntOrNull("cloudCoverPercent")?.coerceIn(0, 100),
            precipitationProbability = o.optIntOrNull("precipitationProbability")
                ?.coerceIn(0, 100),
            outdoorSuitability = o.optFloatOrNull("outdoorSuitability")?.coerceIn(0f, 1f),
            publicBackgroundSummary = o.optStringOrNull("publicBackgroundSummary"),
            fetchedAt = fetchedAt,
            expiresAt = o.optLong("expiresAt", fetchedAt).coerceAtLeast(fetchedAt),
            provenance = o.optString("provenance", ""),
            rawPayloadHash = o.optStringOrNull("rawPayloadHash"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        ).takeIf { it.localDate.isNotBlank() && it.regionKey.isNotBlank() && it.source.isNotBlank() }
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга внешнего контекста", e); null
    }

    private fun contextSnapshotFromJson(o: JSONObject): ContextSnapshotEntity? = try {
        ContextSnapshotEntity(
            id = o.optInt("id", 0),
            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
            zoneId = o.optString("zoneId", "UTC"),
            localDate = o.optString("localDate", ""),
            minutesSinceWake = o.optIntOrNull("minutesSinceWake")?.coerceAtLeast(0),
            hoursAwake = o.optFloatOrNull("hoursAwake")?.coerceIn(0f, 72f),
            sleepDurationMinutes = o.optIntOrNull("sleepDurationMinutes")?.coerceAtLeast(0),
            sleepDeviationMinutes = o.optIntOrNull("sleepDeviationMinutes"),
            sleepDebtMinutes = o.optIntOrNull("sleepDebtMinutes")?.coerceAtLeast(0),
            sleepRegularity = o.optFloatOrNull("sleepRegularity")?.coerceIn(0f, 1f),
            dayOfWeek = o.optInt("dayOfWeek", 1).coerceIn(1, 7),
            isFreeDay = o.optBoolean("isFreeDay", false),
            calendarWindowMinutes = o.optIntOrNull("calendarWindowMinutes")?.coerceAtLeast(0),
            recentFocusMinutes = o.optInt("recentFocusMinutes", 0).coerceAtLeast(0),
            recentWorkModes = o.optString("recentWorkModes", ""),
            recentBreakMinutes = o.optInt("recentBreakMinutes", 0).coerceAtLeast(0),
            dailyCheckInId = o.optIntOrNull("dailyCheckInId"),
            lastObservationAgeMinutes = o.optIntOrNull("lastObservationAgeMinutes")
                ?.coerceAtLeast(0),
            personalPeriodFlags = o.optString("personalPeriodFlags", ""),
            externalContextId = o.optIntOrNull("externalContextId"),
            version = o.optInt("version", 1).coerceAtLeast(1),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        ).takeIf { it.localDate.isNotBlank() }
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга снимка контекста", e); null
    }

    private fun recommendationDecisionFromJson(o: JSONObject): RecommendationDecisionEntity? = try {
        RecommendationDecisionEntity(
            id = o.optInt("id", 0),
            generatedAt = o.optLong("generatedAt", System.currentTimeMillis()),
            modelVersion = o.optString("modelVersion", "unknown"),
            strategy = o.optString("strategy", "UNKNOWN"),
            contextSnapshotId = o.optIntOrNull("contextSnapshotId"),
            stateSnapshotJson = o.optString("stateSnapshotJson", "{}"),
            selectedTaskId = o.optIntOrNull("selectedTaskId"),
            candidateTaskIds = o.optString("candidateTaskIds", "[]"),
            componentScores = o.optString("componentScores", "{}"),
            reasonCodes = o.optString("reasonCodes", "[]"),
            confidence = o.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f),
            accepted = o.optBoolean("accepted", false),
            dismissed = o.optBoolean("dismissed", false),
            reordered = o.optBoolean("reordered", false),
            feedbackReason = o.optStringOrNull("feedbackReason"),
            resultingActivityRecordId = o.optIntOrNull("resultingActivityRecordId"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", o.optLong("createdAt", System.currentTimeMillis()))
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга решения рекомендателя", e); null
    }

    private fun englishProgressFromJson(
        o: JSONObject,
        currentIdsByWord: Map<String, Int>
    ): EnglishWordProgressEntity? = try {
        val savedWord = o.optStringOrNull("word")
        val wordId = resolveEnglishProgressWordId(
            savedWord = savedWord,
            legacyWordId = o.optInt("wordId", 0),
            currentIdsByWord = currentIdsByWord
        )
        if (wordId == null) null else EnglishWordProgressEntity(
            wordId = wordId,
            dueAtMillis = o.optLong("dueAtMillis", 0L).coerceAtLeast(0L),
            intervalMinutes = o.optLong("intervalMinutes", 0L).coerceAtLeast(0L),
            easePermille = o.optInt("easePermille", 2_500).coerceIn(1_300, 3_500),
            repetitions = o.optInt("repetitions", 0).coerceAtLeast(0),
            lapses = o.optInt("lapses", 0).coerceAtLeast(0),
            reviewCount = o.optInt("reviewCount", 0).coerceAtLeast(0),
            correctCount = o.optInt("correctCount", 0).coerceAtLeast(0),
            cardReviews = o.optInt("cardReviews", 0).coerceAtLeast(0),
            writingReviews = o.optInt("writingReviews", 0).coerceAtLeast(0),
            pronunciationReviews = o.optInt("pronunciationReviews", 0).coerceAtLeast(0),
            listeningReviews = o.optInt("listeningReviews", 0).coerceAtLeast(0),
            lastGrade = o.optString("lastGrade", ""),
            lastMode = o.optString("lastMode", ""),
            lastReviewedAtMillis = o.optLong("lastReviewedAtMillis", 0L).coerceAtLeast(0L)
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга прогресса английского", e)
        null
    }

    private fun englishDirectionalProgressFromJson(
        o: JSONObject,
        currentIdsByWord: Map<String, Int>
    ): EnglishWordDirectionalProgressEntity? = try {
        val wordId = resolveEnglishProgressWordId(
            savedWord = o.optStringOrNull("word"),
            legacyWordId = o.optInt("wordId", 0),
            currentIdsByWord = currentIdsByWord
        )
        val direction = EnglishStudyDirection.fromStorage(o.optString("direction", ""))
        if (wordId == null || !direction.isConcrete) null else EnglishWordDirectionalProgressEntity(
            wordId = wordId,
            direction = direction.name,
            dueAtMillis = o.optLong("dueAtMillis", 0L).coerceAtLeast(0L),
            intervalMinutes = o.optLong("intervalMinutes", 0L).coerceAtLeast(0L),
            easePermille = o.optInt("easePermille", 2_500).coerceIn(1_300, 3_500),
            repetitions = o.optInt("repetitions", 0).coerceAtLeast(0),
            lapses = o.optInt("lapses", 0).coerceAtLeast(0),
            reviewCount = o.optInt("reviewCount", 0).coerceAtLeast(0),
            correctCount = o.optInt("correctCount", 0).coerceAtLeast(0),
            cardReviews = o.optInt("cardReviews", 0).coerceAtLeast(0),
            writingReviews = o.optInt("writingReviews", 0).coerceAtLeast(0),
            pronunciationReviews = o.optInt("pronunciationReviews", 0).coerceAtLeast(0),
            listeningReviews = o.optInt("listeningReviews", 0).coerceAtLeast(0),
            lastGrade = o.optString("lastGrade", ""),
            lastMode = o.optString("lastMode", ""),
            lastReviewedAtMillis = o.optLong("lastReviewedAtMillis", 0L).coerceAtLeast(0L)
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга направленного прогресса английского", e)
        null
    }

    private fun englishStudySetFromJson(o: JSONObject): EnglishStudySetEntity? = try {
        val id = o.optLong("id", 0L)
        val title = o.optString("title", "").trim()
        val direction = EnglishStudyDirection.fromStorage(o.optString("defaultDirection", "MIXED"))
        if (id <= 0L || title.isEmpty() || title.length > 80) null else EnglishStudySetEntity(
            id = id,
            title = title,
            // Sets created by older builds had no description length limit. Keep the
            // complete user-authored text so an export/import round trip is lossless.
            description = o.optString("description", ""),
            colorSeed = o.optInt("colorSeed", 0),
            defaultDirection = direction.name,
            createdAtMillis = o.optLong("createdAtMillis", 0L).coerceAtLeast(0L),
            updatedAtMillis = o.optLong("updatedAtMillis", 0L).coerceAtLeast(0L)
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга набора английского", e)
        null
    }

    private fun englishStudyCardFromJson(
        o: JSONObject,
        currentIdsByWord: Map<String, Int>
    ): EnglishStudyCardEntity? = try {
        val id = o.optLong("id", 0L)
        val setId = o.optLong("setId", 0L)
        val term = o.optString("term", "").trim()
        val translation = o.optString("translation", "").trim()
        val savedHeadword = o.optStringOrNull("dictionaryHeadword")
        // Numeric dictionary IDs are generated from frequency order and can change
        // between assets. v13 stores the stable headword; if it is absent, keep the
        // card's text snapshot but deliberately detach the unsafe numeric link.
        val dictionaryWordId = savedHeadword?.let(currentIdsByWord::get)
        if (
            id <= 0L || setId <= 0L || term.isEmpty() || translation.isEmpty() ||
            term.length > 120 || translation.length > 240
        ) null else EnglishStudyCardEntity(
            id = id,
            setId = setId,
            dictionaryWordId = dictionaryWordId,
            term = term,
            translation = translation,
            definition = o.optString("definition", "").take(2_000),
            example = o.optString("example", "").take(1_000),
            exampleTranslation = o.optString("exampleTranslation", "").take(1_000),
            notes = o.optString("notes", "").take(4_000),
            position = o.optInt("position", 0).coerceAtLeast(0),
            createdAtMillis = o.optLong("createdAtMillis", 0L).coerceAtLeast(0L),
            updatedAtMillis = o.optLong("updatedAtMillis", 0L).coerceAtLeast(0L)
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга карточки английского", e)
        null
    }

    private fun englishCardProgressFromJson(o: JSONObject): EnglishCardProgressEntity? = try {
        val cardId = o.optLong("cardId", 0L)
        val direction = EnglishStudyDirection.fromStorage(o.optString("direction", ""))
        if (cardId <= 0L || !direction.isConcrete) null else EnglishCardProgressEntity(
            cardId = cardId,
            direction = direction.name,
            dueAtMillis = o.optLong("dueAtMillis", 0L).coerceAtLeast(0L),
            intervalMinutes = o.optLong("intervalMinutes", 0L).coerceAtLeast(0L),
            easePermille = o.optInt("easePermille", 2_500).coerceIn(1_300, 3_500),
            repetitions = o.optInt("repetitions", 0).coerceAtLeast(0),
            lapses = o.optInt("lapses", 0).coerceAtLeast(0),
            reviewCount = o.optInt("reviewCount", 0).coerceAtLeast(0),
            correctCount = o.optInt("correctCount", 0).coerceAtLeast(0),
            cardReviews = o.optInt("cardReviews", 0).coerceAtLeast(0),
            writingReviews = o.optInt("writingReviews", 0).coerceAtLeast(0),
            pronunciationReviews = o.optInt("pronunciationReviews", 0).coerceAtLeast(0),
            listeningReviews = o.optInt("listeningReviews", 0).coerceAtLeast(0),
            lastGrade = o.optString("lastGrade", ""),
            lastMode = o.optString("lastMode", ""),
            lastReviewedAtMillis = o.optLong("lastReviewedAtMillis", 0L).coerceAtLeast(0L)
        )
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка парсинга прогресса карточки английского", e)
        null
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
            automationSafetyWakeTime = if (o.isNull("automationSafetyWakeTime")) null
                else o.optLong("automationSafetyWakeTime"),
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
            ),
            detectedOnsetConfidencePercent = if (o.isNull("detectedOnsetConfidencePercent")) null else o.optInt(
                "detectedOnsetConfidencePercent"
            ),
            detectedOnsetSource = o.optStringOrNull("detectedOnsetSource"),
            detectedOnsetUncertaintyMinutes = if (o.isNull("detectedOnsetUncertaintyMinutes")) null
                else o.optInt("detectedOnsetUncertaintyMinutes"),
            onsetReviewState = o.optString("onsetReviewState", "PENDING")
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
            resourceKind = enumValueOrDefault(
                o.optString("resourceKind"),
                com.personal.sleepalarm.data.db.entity.LibraryResourceKind.NOTE
            ),
            localFilePath = o.optStringOrNull("localFilePath"),
            originalFileName = o.optString("originalFileName", ""),
            referenceUrl = o.optString("referenceUrl", ""),
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

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key)

    private fun JSONObject.optFloatOrNull(key: String): Float? =
        if (isNull(key) || !has(key)) null else optDouble(key).toFloat()

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        enumValueOrNull<T>(value) ?: default

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
        try {
            enumValueOf<T>(value)
        } catch (_: IllegalArgumentException) {
            null
        }

}

private const val STRICT_BACKUP_STRUCTURE_VERSION = 14

private val V14_REQUIRED_ARRAY_SECTIONS = listOf(
    "subjects",
    "studySessions",
    "pomodoroSessions",
    "focusProtocolSessions",
    "energySamples",
    "otherActivities",
    "calendarEvents",
    "tasks",
    "projects",
    "taskSubtasks",
    "taskAttachments",
    "taskLibraryLinks",
    "activityRecords",
    "dailyCheckIns",
    "energyObservations",
    "taskDemandProfiles",
    "taskDependencies",
    "workEpisodeAssessments",
    "externalContexts",
    "contextSnapshots",
    "recommendationDecisions",
    "englishProgress",
    "englishDirectionalProgress",
    "englishStudySets",
    "englishStudyCards",
    "englishCardProgress",
    "reminders",
    "ddays",
    "diary",
    "moodEntries",
    "sleepSessions",
    "cueEvents"
)

private val V14_REQUIRED_OBJECT_SECTIONS = listOf(
    "sleepAutomation",
    "dailyPlanNudges",
    "library"
)

private val V14_OPTIONAL_OBJECT_SECTIONS = listOf("alarmProfile", "schedule")
private val V14_LIBRARY_ARRAY_SECTIONS = listOf("items", "tags", "refs")

/**
 * v14 is the first strict, fully relational backup shape. Reject an incomplete or
 * mistyped document before import can clear any user table. Older backup versions
 * deliberately retain their permissive compatibility path.
 */
internal fun validateBackupStructureBeforeRestore(root: JSONObject, version: Int) {
    if (version != STRICT_BACKUP_STRUCTURE_VERSION) return

    val problems = mutableListOf<String>()
    if (root.opt("version") !is Number) problems += "version (ожидалось число)"
    if (root.opt("exportedAt") !is Number) problems += "exportedAt (ожидалось число)"

    V14_REQUIRED_ARRAY_SECTIONS.forEach { key ->
        val array = root.opt(key) as? JSONArray
        if (array == null) {
            problems += "$key (ожидался массив)"
        } else {
            val invalidIndex = (0 until array.length()).firstOrNull { array.opt(it) !is JSONObject }
            if (invalidIndex != null) problems += "$key[$invalidIndex] (ожидался объект)"
        }
    }

    V14_REQUIRED_OBJECT_SECTIONS.forEach { key ->
        if (root.opt(key) !is JSONObject) problems += "$key (ожидался объект)"
    }
    V14_OPTIONAL_OBJECT_SECTIONS.forEach { key ->
        if (root.has(key) && !root.isNull(key) && root.opt(key) !is JSONObject) {
            problems += "$key (ожидался объект)"
        }
    }

    (root.opt("library") as? JSONObject)?.let { library ->
        V14_LIBRARY_ARRAY_SECTIONS.forEach { key ->
            val array = library.opt(key) as? JSONArray
            if (array == null) {
                problems += "library.$key (ожидался массив)"
            } else {
                val invalidIndex = (0 until array.length())
                    .firstOrNull { array.opt(it) !is JSONObject }
                if (invalidIndex != null) {
                    problems += "library.$key[$invalidIndex] (ожидался объект)"
                }
            }
        }
    }

    if (problems.isNotEmpty()) {
        throw IllegalArgumentException(
            "Резервная копия v14 повреждена или неполна. " +
                "Импорт отменён до изменения данных. Проверьте разделы: " +
                problems.joinToString()
        )
    }
}

internal fun resolveEnglishProgressWordId(
    savedWord: String?,
    legacyWordId: Int,
    currentIdsByWord: Map<String, Int>
): Int? = if (savedWord == null) {
    // Compatibility with the short-lived v11 shape and older local files.
    legacyWordId.takeIf { it in 1..EnglishDictionaryAssetSource.EXPECTED_WORD_COUNT }
} else {
    currentIdsByWord[savedWord]
}
