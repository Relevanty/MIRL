package com.personal.sleepalarm.domain.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortHorizonPlannerTest {
    @Test
    fun planNeverOverlapsFixedCalendarWindowsAndKeepsBuffers() {
        val context = context(
            horizonMinutes = 240,
            fixed = listOf(TimeWindow(minutes(40), minutes(80))),
            bufferMinutes = 10,
            maxTasks = 3
        )
        val ranking = adaptiveRanking(
            ranked("a", duration = 30, score = 0.9),
            ranked("b", duration = 30, score = 0.8),
            ranked("c", duration = 30, score = 0.7)
        )

        val plan = ShortHorizonPlanner.plan(ranking, context)

        assertEquals(3, plan.tasks.size)
        plan.tasks.forEach { planned ->
            val window = TimeWindow(planned.startMillis, planned.endMillis)
            assertFalse(context.fixedCalendarWindows.any(window::overlaps))
            assertTrue(plan.freeWindows.any {
                planned.startMillis >= it.startMillis && planned.endMillis <= it.endMillis
            })
        }
        plan.tasks.zipWithNext().forEach { (left, right) ->
            assertTrue(right.startMillis - left.endMillis >= minutes(10))
        }
    }

    @Test
    fun deadlineCoverageWinsOverAHighAdaptiveScore() {
        val context = context(horizonMinutes = 60, bufferMinutes = 0, maxTasks = 2)
        val ranking = adaptiveRanking(
            ranked("urgent", duration = 30, score = -2.0, tier = 2, due = minutes(40)),
            ranked("attractive", duration = 60, score = 10.0, tier = 4)
        )

        val plan = ShortHorizonPlanner.plan(ranking, context)

        assertEquals(listOf("urgent"), plan.tasks.map { it.rankedTask.demand.id })
        assertTrue(plan.tasks.single().endMillis <= minutes(40))
    }

    @Test
    fun strictDeadlineIsNeverViolatedWhileSoftOverdueTaskRemainsActionable() {
        val context = context(horizonMinutes = 120, bufferMinutes = 0, maxTasks = 2)
        val impossibleStrict = ranked(
            id = "strict",
            duration = 60,
            score = 2.0,
            tier = 1,
            due = minutes(30),
            hardDeadline = true
        )
        val softOverdue = ranked(
            id = "soft-overdue",
            duration = 30,
            score = 1.0,
            tier = 0,
            due = minutes(-10),
            hardDeadline = false
        )

        val plan = ShortHorizonPlanner.plan(
            adaptiveRanking(impossibleStrict, softOverdue),
            context
        )

        assertEquals(listOf("soft-overdue"), plan.tasks.map { it.rankedTask.demand.id })
    }

    @Test
    fun fixedStartAndReleaseTimeAreHonouredExactly() {
        val context = context(horizonMinutes = 240, bufferMinutes = 0, maxTasks = 2)
        val ranking = adaptiveRanking(
            ranked(
                id = "released",
                duration = 20,
                score = 1.0,
                earliest = minutes(60)
            ),
            ranked(
                id = "fixed",
                duration = 30,
                score = 1.0,
                tier = 2,
                fixed = minutes(120)
            )
        )

        val plan = ShortHorizonPlanner.plan(ranking, context)
        val byId = plan.tasks.associateBy { it.rankedTask.demand.id }

        assertTrue(byId.getValue("released").startMillis >= minutes(60))
        assertEquals(minutes(120), byId.getValue("fixed").startMillis)
    }

    @Test
    fun fallbackPlanningPreservesCanonicalRelativeOrder() {
        val context = context(horizonMinutes = 240, bufferMinutes = 0, maxTasks = 2)
        val canonical = listOf(
            ranked("fixed-first", 30, 0.0, fixed = minutes(120)),
            ranked("second", 30, -1.0)
        )
        val fallback = AdaptiveRanking(
            tasks = canonical,
            mode = RankingMode.FALLBACK_LOW_CONFIDENCE,
            fallbackReason = "test"
        )

        val plan = ShortHorizonPlanner.plan(fallback, context)

        assertEquals(
            listOf("fixed-first", "second"),
            plan.tasks.map { it.rankedTask.demand.id }
        )
        assertEquals(minutes(120), plan.tasks.first().startMillis)
        assertTrue(plan.tasks[1].startMillis >= plan.tasks[0].endMillis)
    }

    @Test
    fun lookaheadIsDeterministicAndScoreIsAuditable() {
        val context = context(horizonMinutes = 180, bufferMinutes = 5, maxTasks = 4)
        val ranking = adaptiveRanking(
            ranked("a", 35, 0.52),
            ranked("b", 25, 0.66),
            ranked("c", 45, 0.71),
            ranked("d", 15, 0.32)
        )

        val first = ShortHorizonPlanner.plan(ranking, context)
        val second = ShortHorizonPlanner.plan(ranking, context)

        assertEquals(first, second)
        assertEquals(
            first.tasks.sumOf { it.rankedTask.score },
            first.totalAdaptiveScore,
            1e-9
        )
    }

    private fun adaptiveRanking(vararg tasks: RankedTask<String>) = AdaptiveRanking(
        tasks = tasks.toList(),
        mode = RankingMode.ADAPTIVE
    )

    private fun ranked(
        id: String,
        duration: Int,
        score: Double,
        tier: Int = 4,
        due: Long? = null,
        hardDeadline: Boolean = false,
        earliest: Long? = null,
        fixed: Long? = null
    ) = RankedTask(
        demand = TaskDemand(
            id = id,
            durationMinutes = duration,
            dueAtMillis = due,
            deadlineIsHard = hardDeadline,
            earliestStartMillis = earliest,
            fixedStartMillis = fixed
        ),
        score = score,
        hardConstraintTier = tier,
        fallbackIndex = id.hashCode().and(Int.MAX_VALUE),
        explanations = listOf(
            ScoreExplanation(ScoreFactor.ENERGY_FIT, score, "test score")
        )
    )

    private fun context(
        horizonMinutes: Int,
        fixed: List<TimeWindow> = emptyList(),
        bufferMinutes: Int,
        maxTasks: Int
    ) = PlanningContext(
        nowMillis = minutes(0),
        horizonEndMillis = minutes(horizonMinutes),
        personalState = null,
        fixedCalendarWindows = fixed,
        bufferMinutes = bufferMinutes,
        maxSequenceTasks = maxTasks,
        candidateLimit = maxOf(maxTasks, 8)
    )

    private fun minutes(value: Int): Long = value * 60_000L
}
