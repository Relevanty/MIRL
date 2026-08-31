package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.entity.ContextSnapshotEntity
import com.personal.sleepalarm.data.db.entity.DailyCheckInEntity
import com.personal.sleepalarm.data.db.entity.EnergyObservationEntity
import com.personal.sleepalarm.data.db.entity.ExternalContextEntity
import com.personal.sleepalarm.data.db.entity.RecommendationDecisionEntity
import com.personal.sleepalarm.data.db.entity.TaskDemandProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveEnergyRepositoryNormalizationTest {
    @Test
    fun checkInAndEnergyValuesStayInsideLearningScales() {
        val checkIn = DailyCheckInEntity(
            localDate = " 2026-08-30 ",
            timestamp = 1L,
            zoneId = " Europe/Moscow ",
            energy = 20,
            mood = -4,
            stress = 11,
            source = " "
        ).normalizedForStorage(now = 99L)

        assertEquals("2026-08-30", checkIn.localDate)
        assertEquals("Europe/Moscow", checkIn.zoneId)
        assertEquals(10, checkIn.energy)
        assertEquals(1, checkIn.mood)
        assertEquals(4, checkIn.stress)
        assertEquals("AD_HOC", checkIn.source)
        assertEquals(99L, checkIn.createdAt)
        assertEquals(99L, checkIn.updatedAt)

        val observation = EnergyObservationEntity(
            timestamp = 2L,
            absoluteEnergy = 42,
            relativeDelta = -20,
            context = " BEFORE_TASK ",
            source = " ",
            quality = " ",
            confidence = 2f
        ).normalizedForStorage()

        assertEquals(10, observation.absoluteEnergy)
        assertEquals(-9, observation.relativeDelta)
        assertEquals("BEFORE_TASK", observation.context)
        assertEquals("USER", observation.source)
        assertEquals("EXACT", observation.quality)
        assertEquals(1f, observation.confidence, 0f)
    }

    @Test
    fun taskAndContextProfilesAreNormalizedWithoutLosingUnknownTextCodes() {
        val profile = TaskDemandProfileEntity(
            taskId = 7,
            domain = " custom-domain ",
            workMode = " ",
            difficulty = 9,
            concentrationDemand = -1,
            minimumBlockMinutes = 0,
            preferredBlockMinutes = 0,
            interruptibility = 7,
            confidence = -0.5f,
            userLockMask = -1L
        ).normalizedForStorage()

        assertEquals("custom-domain", profile.domain)
        assertEquals("OTHER", profile.workMode)
        assertEquals(4, profile.difficulty)
        assertEquals(0, profile.concentrationDemand)
        assertEquals(1, profile.minimumBlockMinutes)
        assertEquals(1, profile.preferredBlockMinutes)
        assertEquals(4, profile.interruptibility)
        assertEquals(0f, profile.confidence, 0f)
        assertEquals(0L, profile.userLockMask)

        val snapshot = ContextSnapshotEntity(
            timestamp = 3L,
            zoneId = " UTC ",
            localDate = " 2026-08-30 ",
            hoursAwake = 100f,
            sleepRegularity = 4f,
            dayOfWeek = 9,
            recentFocusMinutes = -1,
            recentBreakMinutes = -1
        ).normalizedForStorage()

        assertEquals(72f, snapshot.hoursAwake ?: 0f, 0f)
        assertEquals(1f, snapshot.sleepRegularity ?: 0f, 0f)
        assertEquals(7, snapshot.dayOfWeek)
        assertEquals(0, snapshot.recentFocusMinutes)
        assertEquals(0, snapshot.recentBreakMinutes)
    }

    @Test
    fun externalAndRecommendationConfidenceAreSafeToPersist() {
        val external = ExternalContextEntity(
            localDate = " 2026-08-30 ",
            regionKey = " coarse ",
            source = " provider ",
            daylightMinutes = 2_000,
            cloudCoverPercent = 120,
            precipitationProbability = -1,
            outdoorSuitability = 5f,
            fetchedAt = 100L,
            expiresAt = 50L,
            provenance = " cached "
        ).normalizedForStorage()

        assertEquals(1_440, external.daylightMinutes)
        assertEquals(100, external.cloudCoverPercent)
        assertEquals(0, external.precipitationProbability)
        assertEquals(1f, external.outdoorSuitability ?: 0f, 0f)
        assertEquals(100L, external.expiresAt)

        val decision = RecommendationDecisionEntity(
            generatedAt = 4L,
            modelVersion = " v1 ",
            strategy = " balanced ",
            stateSnapshotJson = " ",
            candidateTaskIds = " ",
            componentScores = " ",
            reasonCodes = " ",
            confidence = -2f
        ).normalizedForStorage()

        assertEquals("v1", decision.modelVersion)
        assertEquals("balanced", decision.strategy)
        assertEquals("{}", decision.stateSnapshotJson)
        assertEquals("[]", decision.candidateTaskIds)
        assertEquals("{}", decision.componentScores)
        assertEquals("[]", decision.reasonCodes)
        assertEquals(0f, decision.confidence, 0f)
    }
}
