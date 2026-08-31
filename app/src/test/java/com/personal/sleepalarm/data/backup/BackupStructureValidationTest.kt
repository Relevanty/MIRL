package com.personal.sleepalarm.data.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupStructureValidationTest {
    @Test
    fun completeV14ShapeIsAccepted() {
        validateBackupStructureBeforeRestore(completeV14Backup(), 14)
    }

    @Test
    fun missingOrMistypedV14SectionIsRejectedBeforeRestore() {
        val missing = completeV14Backup().apply { remove("energyObservations") }
        val missingError = expectInvalid(missing)
        assertTrue(missingError.message.orEmpty().contains("energyObservations"))
        assertTrue(missingError.message.orEmpty().contains("Импорт отменён до изменения данных"))

        val mistyped = completeV14Backup().apply { put("tasks", JSONObject()) }
        val mistypedError = expectInvalid(mistyped)
        assertTrue(mistypedError.message.orEmpty().contains("tasks (ожидался массив)"))
    }

    @Test
    fun malformedNestedLibraryAndArrayRowsAreRejected() {
        val badLibrary = completeV14Backup().apply {
            getJSONObject("library").put("refs", "not-an-array")
        }
        assertTrue(expectInvalid(badLibrary).message.orEmpty().contains("library.refs"))

        val badRow = completeV14Backup().apply {
            getJSONArray("dailyCheckIns").put("not-an-object")
        }
        assertTrue(expectInvalid(badRow).message.orEmpty().contains("dailyCheckIns[0]"))
    }

    @Test
    fun requiredAndPresentOptionalObjectsMustHaveTheRightType() {
        val missingRequired = completeV14Backup().apply { remove("dailyPlanNudges") }
        assertTrue(
            expectInvalid(missingRequired).message.orEmpty()
                .contains("dailyPlanNudges (ожидался объект)")
        )

        val mistypedOptional = completeV14Backup().apply { put("schedule", JSONArray()) }
        assertTrue(
            expectInvalid(mistypedOptional).message.orEmpty()
                .contains("schedule (ожидался объект)")
        )
    }

    @Test
    fun olderBackupShapesRemainPermissive() {
        validateBackupStructureBeforeRestore(JSONObject().put("version", 13), 13)
    }

    private fun expectInvalid(root: JSONObject): IllegalArgumentException = try {
        validateBackupStructureBeforeRestore(root, 14)
        fail("Expected an incomplete v14 backup to be rejected")
        error("unreachable")
    } catch (error: IllegalArgumentException) {
        error
    }

    private fun completeV14Backup(): JSONObject = JSONObject().apply {
        put("version", 14)
        put("exportedAt", 1L)
        put("sleepAutomation", JSONObject())
        put("dailyPlanNudges", JSONObject())
        REQUIRED_ARRAYS.forEach { put(it, JSONArray()) }
        put("library", JSONObject().apply {
            put("items", JSONArray())
            put("tags", JSONArray())
            put("refs", JSONArray())
        })
    }

    private companion object {
        val REQUIRED_ARRAYS = listOf(
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
    }
}
