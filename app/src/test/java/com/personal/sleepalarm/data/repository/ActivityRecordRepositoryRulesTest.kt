package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.FocusActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityRecordRepositoryRulesTest {

    @Test
    fun `linked task owns category project and relational ids`() {
        val input = manualInput(
            taskId = 7,
            projectId = 91,
            activityType = FocusActivityType.OTHER,
            subjectId = 3,
            otherActivityId = 4
        )
        val task = TaskEntity(
            id = 7,
            title = "Алгебра",
            category = "STUDY",
            projectId = 12
        )

        val canonical = canonicalizeManualActivityLinks(input, task)

        assertEquals(7, canonical.taskId)
        assertEquals(12, canonical.projectId)
        assertEquals(FocusActivityType.STUDY, canonical.activityType)
        assertEquals("STUDY", canonical.category)
        assertNull(canonical.subjectId)
        assertNull(canonical.otherActivityId)
    }

    @Test
    fun `unlinked activity keeps project and only category-compatible item id`() {
        val study = canonicalizeManualActivityLinks(
            manualInput(
                projectId = 5,
                activityType = FocusActivityType.STUDY,
                subjectId = 9,
                otherActivityId = 11
            ),
            linkedTask = null
        )
        val work = canonicalizeManualActivityLinks(
            manualInput(
                projectId = 6,
                activityType = FocusActivityType.WORK,
                subjectId = 9,
                otherActivityId = 11
            ),
            linkedTask = null
        )

        assertEquals(5, study.projectId)
        assertEquals(9, study.subjectId)
        assertNull(study.otherActivityId)
        assertNull(work.subjectId)
        assertNull(work.otherActivityId)
    }

    @Test
    fun `parallel overlap for same counted task does not reduce budget twice`() {
        val conflicts = listOf(activity(taskId = 7, countsTowardProgress = true))

        assertFalse(
            manualActivityCountsTowardProgress(
                taskId = 7,
                conflicts = conflicts,
                strategy = ActivityConflictStrategy.KEEP_PARALLEL
            )
        )
    }

    @Test
    fun `parallel overlap can count when no counted record belongs to same task`() {
        val conflicts = listOf(
            activity(taskId = 7, countsTowardProgress = false),
            activity(taskId = 8, countsTowardProgress = true)
        )

        assertTrue(
            manualActivityCountsTowardProgress(
                taskId = 7,
                conflicts = conflicts,
                strategy = ActivityConflictStrategy.KEEP_PARALLEL
            )
        )
        assertTrue(
            manualActivityCountsTowardProgress(
                taskId = 7,
                conflicts = listOf(activity(taskId = 7, countsTowardProgress = true)),
                strategy = ActivityConflictStrategy.REPLACE
            )
        )
    }

    private fun manualInput(
        taskId: Int? = null,
        projectId: Int? = null,
        activityType: FocusActivityType,
        subjectId: Int? = null,
        otherActivityId: Int? = null
    ) = ManualActivityInput(
        taskId = taskId,
        projectId = projectId,
        activityType = activityType,
        subjectId = subjectId,
        otherActivityId = otherActivityId,
        title = "Работа",
        startedAt = 1_000L,
        endedAt = 2_000L
    )

    private fun activity(
        taskId: Int?,
        countsTowardProgress: Boolean
    ) = ActivityRecordEntity(
        taskId = taskId,
        title = "История",
        startedAt = 1_000L,
        endedAt = 2_000L,
        durationMillis = 1_000L,
        source = "MANUAL",
        countsTowardProgress = countsTowardProgress
    )
}
