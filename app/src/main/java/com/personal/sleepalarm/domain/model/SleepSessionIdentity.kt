package com.personal.sleepalarm.domain.model

import com.personal.sleepalarm.data.db.entity.SleepSessionEntity

/** Canonical sleep onset used by statistics, briefing and the assistant. */
fun SleepSessionEntity.effectiveSleepStartMillis(): Long =
    detectedSleepOnsetTime ?: estimatedSleepStartTime
