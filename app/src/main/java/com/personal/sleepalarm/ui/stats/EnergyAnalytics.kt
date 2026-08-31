package com.personal.sleepalarm.ui.stats

import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.DailyCheckInEntity
import com.personal.sleepalarm.data.db.entity.EnergyObservationEntity
import com.personal.sleepalarm.data.db.entity.FocusProtocolSessionEntity
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.data.db.entity.TaskDemandProfileEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.DismissType
import com.personal.sleepalarm.domain.model.FocusProtocolPhase
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

data class EnergyAverageBucket(
    val key: String,
    val average: Float,
    val sampleCount: Int
)

data class EnergyDeltaBucket(
    val key: String,
    val averageDelta: Float,
    val sampleCount: Int
)

data class EnergyAnalytics(
    val periodStartMillis: Long,
    val energySampleCount: Int,
    val episodeSampleCount: Int,
    val averageByTimeOfDay: List<EnergyAverageBucket>,
    val deltaByWorkMode: List<EnergyDeltaBucket>,
    val deltaByDomain: List<EnergyDeltaBucket>,
    val averageByHoursAwake: List<EnergyAverageBucket>
) {
    val hasAnyData: Boolean get() = energySampleCount > 0 || episodeSampleCount > 0
    val hasEnoughEnergyData: Boolean get() = energySampleCount >= MIN_ENERGY_SAMPLES
    val hasEnoughEpisodeData: Boolean get() = episodeSampleCount >= MIN_EPISODE_SAMPLES

    companion object {
        const val MIN_ENERGY_SAMPLES = 5
        const val MIN_EPISODE_SAMPLES = 3
        const val MIN_BUCKET_SAMPLES = 3

        fun empty(periodStartMillis: Long = 0L) = EnergyAnalytics(
            periodStartMillis = periodStartMillis,
            energySampleCount = 0,
            episodeSampleCount = 0,
            averageByTimeOfDay = emptyList(),
            deltaByWorkMode = emptyList(),
            deltaByDomain = emptyList(),
            averageByHoursAwake = emptyList()
        )
    }
}

private data class EnergyReading(
    val timestamp: Long,
    val energy: Int,
    val origin: String
)

private data class EnergyEpisode(
    val timestamp: Long,
    val delta: Int,
    val taskId: Int?,
    val activityType: String?,
    val focusSessionId: Int?
)

private data class ObservationLink(val type: String, val id: Int)

/**
 * Pure, deterministic aggregation for the energy statistics screen.
 *
 * It deliberately reports associations only. Linked observations are accepted only when their
 * focus session completed or their activity record counts toward progress. Excluded and malformed
 * subjective readings never enter either the charts or their sample counts.
 */
internal fun aggregateEnergyAnalytics(
    checkIns: List<DailyCheckInEntity>,
    observations: List<EnergyObservationEntity>,
    profiles: List<TaskDemandProfileEntity>,
    tasks: List<TaskEntity>,
    activities: List<ActivityRecordEntity>,
    focusSessions: List<FocusProtocolSessionEntity>,
    sleepSessions: List<SleepSessionEntity>,
    periodStartMillis: Long,
    snapshotTimeMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): EnergyAnalytics {
    val tasksById = tasks.associateBy(TaskEntity::id)
    val profilesByTaskId = profiles.associateBy(TaskDemandProfileEntity::taskId)
    val validActivities = activities
        .filter { it.countsTowardProgress && it.endedAt > it.startedAt }
        .associateBy(ActivityRecordEntity::id)
    val completedFocus = focusSessions
        .filter {
            it.phase == FocusProtocolPhase.COMPLETE &&
                it.completedAt != null &&
                it.cancelReason == null
        }
        .associateBy(FocusProtocolSessionEntity::id)

    val validObservations = observations.filter { observation ->
        observation.timestamp in periodStartMillis..snapshotTimeMillis &&
            !observation.excludedFromLearning &&
            observation.confidence > 0f &&
            !observation.quality.equals("INVALID", ignoreCase = true) &&
            (observation.absoluteEnergy != null || observation.relativeDelta != null) &&
            (observation.absoluteEnergy == null || observation.absoluteEnergy in 1..10) &&
            (observation.relativeDelta == null || observation.relativeDelta in -9..9) &&
            (observation.focusProtocolSessionId == null ||
                observation.focusProtocolSessionId in completedFocus) &&
            (observation.activityRecordId == null ||
                observation.activityRecordId in validActivities)
    }

    val readings = buildList {
        checkIns.asSequence()
            .filter { it.timestamp in periodStartMillis..snapshotTimeMillis }
            .filterNot { it.excludedFromLearning }
            .mapNotNull { checkIn ->
                checkIn.energy?.takeIf { it in 1..10 }
                    ?.let { EnergyReading(checkIn.timestamp, it, "CHECK_IN") }
            }
            .forEach(::add)
        validObservations.mapNotNullTo(this) { observation ->
            observation.absoluteEnergy?.let { EnergyReading(observation.timestamp, it, "OBSERVATION") }
        }
    }.sortedBy(EnergyReading::timestamp).deduplicateMirroredReadings()

    val observationEpisodes = buildObservationEpisodes(
        observations = validObservations,
        validActivities = validActivities,
        completedFocus = completedFocus,
        tasksById = tasksById
    )
    val pairedFocusIds = observationEpisodes
        .mapNotNull(EnergyEpisode::focusSessionId)
        .toSet()
    val fallbackFocusEpisodes = completedFocus.values.mapNotNull { session ->
        if (session.id in pairedFocusIds) return@mapNotNull null
        val completedAt = session.completedAt ?: return@mapNotNull null
        if (completedAt !in periodStartMillis..snapshotTimeMillis) return@mapNotNull null
        val after = session.energyAfter?.takeIf { it in 1..10 } ?: return@mapNotNull null
        if (session.energyBefore !in 1..10) return@mapNotNull null
        val taskId = resolveFocusTaskId(session, tasksById)
        EnergyEpisode(
            timestamp = session.focusStartedAt ?: session.createdAt,
            delta = after - session.energyBefore,
            taskId = taskId,
            activityType = session.activityType.name,
            focusSessionId = session.id
        )
    }
    val episodes = observationEpisodes + fallbackFocusEpisodes

    val timeBuckets = readings
        .groupBy { reading -> timeOfDayKey(Instant.ofEpochMilli(reading.timestamp).atZone(zoneId).hour) }
        .toAverageBuckets(TIME_OF_DAY_ORDER)
    val hoursAwakeBuckets = readings.mapNotNull { reading ->
        val wake = sleepSessions.asSequence()
            .filter { it.actualWakeTime != null && it.dismissType != DismissType.CANCELLED }
            .mapNotNull { it.actualWakeTime }
            .filter { it <= reading.timestamp }
            .maxOrNull()
            ?: return@mapNotNull null
        val hours = (reading.timestamp - wake) / HOUR_MILLIS
        if (hours !in 0..MAX_TRACKED_AWAKE_HOURS) return@mapNotNull null
        hoursAwakeKey(hours.toInt()) to reading
    }.groupBy({ it.first }, { it.second }).toAverageBuckets(HOURS_AWAKE_ORDER)

    fun episodeDimension(episode: EnergyEpisode, mode: Boolean): String {
        val profile = episode.taskId?.let(profilesByTaskId::get)
        val task = episode.taskId?.let(tasksById::get)
        return (if (mode) {
            profile?.workMode?.takeUnless { it.equals("OTHER", ignoreCase = true) }
                ?: episode.activityType
                ?: "OTHER"
        } else {
            profile?.domain?.takeUnless { it.equals("OTHER", ignoreCase = true) }
                ?: task?.category?.takeIf { it.isNotBlank() }
                ?: episode.activityType
                ?: "OTHER"
        }).uppercase(Locale.ROOT)
    }

    return EnergyAnalytics(
        periodStartMillis = periodStartMillis,
        energySampleCount = readings.size,
        episodeSampleCount = episodes.size,
        averageByTimeOfDay = timeBuckets,
        deltaByWorkMode = episodes.groupBy { episodeDimension(it, mode = true) }.toDeltaBuckets(),
        deltaByDomain = episodes.groupBy { episodeDimension(it, mode = false) }.toDeltaBuckets(),
        averageByHoursAwake = hoursAwakeBuckets
    )
}

private fun buildObservationEpisodes(
    observations: List<EnergyObservationEntity>,
    validActivities: Map<Int, ActivityRecordEntity>,
    completedFocus: Map<Int, FocusProtocolSessionEntity>,
    tasksById: Map<Int, TaskEntity>
): List<EnergyEpisode> {
    val result = mutableListOf<EnergyEpisode>()
    observations.mapNotNull { observation ->
        observation.link()?.let { it to observation }
    }.groupBy({ it.first }, { it.second }).forEach { (_, linked) ->
        val waitingBefore = mutableListOf<EnergyObservationEntity>()
        linked.sortedBy(EnergyObservationEntity::timestamp).forEach rowLoop@ { current ->
            when (current.context.uppercase(Locale.ROOT)) {
                "BEFORE_TASK" -> if (current.absoluteEnergy != null) waitingBefore += current
                "AFTER_TASK" -> {
                    val beforeIndex = waitingBefore.indexOfLast {
                        current.timestamp >= it.timestamp &&
                            current.timestamp - it.timestamp <= MAX_EPISODE_MILLIS
                    }
                    val before = if (beforeIndex >= 0) waitingBefore.removeAt(beforeIndex)
                        else return@rowLoop
                    val delta = when {
                        before.absoluteEnergy != null && current.absoluteEnergy != null ->
                            current.absoluteEnergy - before.absoluteEnergy
                        current.relativeDelta != null -> current.relativeDelta
                        else -> null
                    } ?: return@rowLoop
                    val focus = current.focusProtocolSessionId?.let(completedFocus::get)
                        ?: before.focusProtocolSessionId?.let(completedFocus::get)
                    val activity = current.activityRecordId?.let(validActivities::get)
                        ?: before.activityRecordId?.let(validActivities::get)
                    val taskId = current.taskId ?: before.taskId ?: activity?.taskId
                        ?: focus?.let { resolveFocusTaskId(it, tasksById) }
                    result += EnergyEpisode(
                        timestamp = before.timestamp,
                        delta = delta,
                        taskId = taskId,
                        activityType = activity?.activityType?.name ?: focus?.activityType?.name,
                        focusSessionId = focus?.id
                    )
                }
            }
        }
    }
    return result
}

private fun EnergyObservationEntity.link(): ObservationLink? = when {
    focusProtocolSessionId != null -> ObservationLink("FOCUS", focusProtocolSessionId)
    activityRecordId != null -> ObservationLink("ACTIVITY", activityRecordId)
    taskId != null -> ObservationLink("TASK", taskId)
    else -> null
}

private fun resolveFocusTaskId(
    session: FocusProtocolSessionEntity,
    tasksById: Map<Int, TaskEntity>
): Int? {
    val canonical = if (session.itemId < 0) -session.itemId else session.itemId
    return canonical.takeIf(tasksById::containsKey)
}

private fun List<EnergyReading>.deduplicateMirroredReadings(): List<EnergyReading> {
    val result = mutableListOf<EnergyReading>()
    forEach { reading ->
        val mirrored = result.lastOrNull()?.let { previous ->
            previous.energy == reading.energy &&
                previous.origin != reading.origin &&
                reading.timestamp - previous.timestamp <= MIRRORED_READING_WINDOW_MILLIS
        } == true
        if (!mirrored) result += reading
    }
    return result
}

private fun Map<String, List<EnergyReading>>.toAverageBuckets(order: List<String>): List<EnergyAverageBucket> =
    order.mapNotNull { key ->
        this[key]?.takeIf { it.isNotEmpty() }?.let { rows ->
            EnergyAverageBucket(key, rows.map(EnergyReading::energy).average().toFloat(), rows.size)
        }
    }

private fun Map<String, List<EnergyEpisode>>.toDeltaBuckets(): List<EnergyDeltaBucket> =
    map { (key, rows) ->
        EnergyDeltaBucket(key, rows.map(EnergyEpisode::delta).average().toFloat(), rows.size)
    }.sortedWith(compareByDescending<EnergyDeltaBucket> { it.sampleCount }.thenBy { it.key })

private fun timeOfDayKey(hour: Int): String = when (hour) {
    in 5..10 -> "MORNING"
    in 11..16 -> "DAY"
    in 17..22 -> "EVENING"
    else -> "NIGHT"
}

private fun hoursAwakeKey(hours: Int): String = when (hours) {
    in 0..2 -> "H0_3"
    in 3..5 -> "H3_6"
    in 6..8 -> "H6_9"
    in 9..11 -> "H9_12"
    in 12..15 -> "H12_16"
    else -> "H16_PLUS"
}

private val TIME_OF_DAY_ORDER = listOf("MORNING", "DAY", "EVENING", "NIGHT")
private val HOURS_AWAKE_ORDER = listOf("H0_3", "H3_6", "H6_9", "H9_12", "H12_16", "H16_PLUS")
private const val HOUR_MILLIS = 60L * 60L * 1000L
private const val MAX_TRACKED_AWAKE_HOURS = 30L
private const val MAX_EPISODE_MILLIS = 12L * HOUR_MILLIS
private const val MIRRORED_READING_WINDOW_MILLIS = 90L * 1000L
