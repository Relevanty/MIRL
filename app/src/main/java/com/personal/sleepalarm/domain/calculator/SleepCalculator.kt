package com.personal.sleepalarm.domain.calculator

import com.personal.sleepalarm.domain.model.SleepPlan
import com.personal.sleepalarm.domain.model.SleepPlanWarning
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Калькулятор планов сна.
 *
 * Основной метод — calculateFromNow (расчёт от текущего момента с обрезкой
 * по preferredWakeTime).
 *
 * Старые методы (calculateFromBedTime, alternativesFromDesiredWake) помечены
 * @Deprecated и оставлены для совместимости.
 */
object SleepCalculator {

    // =====================================================================
    // Константы (НЕ менять)
    // =====================================================================

    const val MIN_CYCLE_LENGTH_MINUTES = 75
    const val MAX_CYCLE_LENGTH_MINUTES = 120
    const val CYCLE_STEP_MINUTES = 5
    const val DEFAULT_CYCLE_LENGTH_MINUTES = 90

    const val MIN_CYCLES = 3
    const val MAX_CYCLES = 7
    const val DEFAULT_CYCLES = 5

    const val MIN_ONSET_LATENCY_MINUTES = 5
    const val MAX_ONSET_LATENCY_MINUTES = 45
    const val ONSET_STEP_MINUTES = 5
    const val DEFAULT_ONSET_LATENCY_MINUTES = 15

    val DEFAULT_CYCLE_OPTIONS: List<Int> = listOf(3, 4, 5, 6, 7)

    // =====================================================================
    // НОВЫЙ ОСНОВНОЙ МЕТОД
    // =====================================================================

    fun calculateFromNow(
        now: ZonedDateTime,
        onsetLatencyMinutes: Int,
        cycleLengthMinutes: Int,
        requestedCycles: Int,
        preferredWakeTime: LocalTime
    ): SleepPlan {
        val sleepStart = now.plusMinutes(onsetLatencyMinutes.toLong())

        var preferredWake = now.toLocalDate()
            .atTime(preferredWakeTime)
            .atZone(now.zone)
        if (!preferredWake.isAfter(now)) {
            preferredWake = preferredWake.plusDays(1)
        }

        val rawWake = sleepStart.plusMinutes(
            requestedCycles.toLong() * cycleLengthMinutes
        )

        if (!rawWake.isAfter(preferredWake)) {
            return SleepPlan(
                bedTime = now,
                estimatedSleepStart = sleepStart,
                estimatedWake = rawWake,
                cycles = requestedCycles,
                cycleLengthMinutes = cycleLengthMinutes,
                onsetLatencyMinutes = onsetLatencyMinutes,
                totalInBed = Duration.between(now, rawWake),
                totalSleep = Duration.ofMinutes(requestedCycles.toLong() * cycleLengthMinutes),
                crossesMidnight = rawWake.toLocalDate() > now.toLocalDate(),
                preferredWake = preferredWake,
                isCutByPreferredWake = false,
                cyclesDidNotFit = false
            )
        }

        val availableMinutes = Duration.between(sleepStart, preferredWake).toMinutes()
        val fittedCycles = if (cycleLengthMinutes > 0) {
            (availableMinutes / cycleLengthMinutes).toInt()
        } else {
            0
        }

        return if (fittedCycles >= 1) {
            val wake = sleepStart.plusMinutes(
                fittedCycles.toLong() * cycleLengthMinutes
            )
            SleepPlan(
                bedTime = now,
                estimatedSleepStart = sleepStart,
                estimatedWake = wake,
                cycles = fittedCycles,
                cycleLengthMinutes = cycleLengthMinutes,
                onsetLatencyMinutes = onsetLatencyMinutes,
                totalInBed = Duration.between(now, wake),
                totalSleep = Duration.ofMinutes(fittedCycles.toLong() * cycleLengthMinutes),
                crossesMidnight = wake.toLocalDate() > now.toLocalDate(),
                preferredWake = preferredWake,
                isCutByPreferredWake = true,
                cyclesDidNotFit = fittedCycles < requestedCycles
            )
        } else {
            SleepPlan(
                bedTime = now,
                estimatedSleepStart = sleepStart,
                estimatedWake = preferredWake,
                cycles = 0,
                cycleLengthMinutes = cycleLengthMinutes,
                onsetLatencyMinutes = onsetLatencyMinutes,
                totalInBed = Duration.between(now, preferredWake),
                totalSleep = Duration.ZERO,
                crossesMidnight = preferredWake.toLocalDate() > now.toLocalDate(),
                preferredWake = preferredWake,
                isCutByPreferredWake = true,
                cyclesDidNotFit = true
            )
        }
    }

    // =====================================================================
    // УСТАРЕВШИЕ МЕТОДЫ
    // =====================================================================

    @Deprecated(
        message = "Режим отхода ко сну больше не поддерживается. Используйте calculateFromNow.",
        level = DeprecationLevel.WARNING
    )
    fun resolveFutureBedTime(
        bedTime: LocalTime,
        referenceDate: LocalDate,
        zone: ZoneId,
        now: ZonedDateTime
    ): ZonedDateTime {
        val candidate = referenceDate.atTime(bedTime).atZone(zone)
        return if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
    }

    @Deprecated(
        message = "Режим отхода ко сну больше не поддерживается. Используйте calculateFromNow.",
        level = DeprecationLevel.WARNING
    )
    fun calculateFromBedTime(
        bedTime: ZonedDateTime,
        onsetLatencyMinutes: Int,
        cycleLengthMinutes: Int,
        cycles: Int
    ): SleepPlan {
        val sleepStart = bedTime.plusMinutes(onsetLatencyMinutes.toLong())
        val wake = sleepStart.plusMinutes(cycles.toLong() * cycleLengthMinutes)

        return SleepPlan(
            bedTime = bedTime,
            estimatedSleepStart = sleepStart,
            estimatedWake = wake,
            cycles = cycles,
            cycleLengthMinutes = cycleLengthMinutes,
            onsetLatencyMinutes = onsetLatencyMinutes,
            totalInBed = Duration.between(bedTime, wake),
            totalSleep = Duration.ofMinutes(cycles.toLong() * cycleLengthMinutes),
            crossesMidnight = wake.toLocalDate() > bedTime.toLocalDate(),
            preferredWake = null,
            isCutByPreferredWake = false,
            cyclesDidNotFit = false
        )
    }

    fun resolveFutureWakeTime(
        wakeTime: LocalTime,
        referenceDate: LocalDate,
        zone: ZoneId,
        now: ZonedDateTime
    ): ZonedDateTime {
        val candidate = referenceDate.atTime(wakeTime).atZone(zone)
        return if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
    }

    fun calculateFromDesiredWake(
        desiredWake: ZonedDateTime,
        onsetLatencyMinutes: Int,
        cycleLengthMinutes: Int,
        cycles: Int
    ): SleepPlan {
        val totalCycleMinutes = cycles.toLong() * cycleLengthMinutes
        val sleepStart = desiredWake.minusMinutes(totalCycleMinutes)
        val bedTime = sleepStart.minusMinutes(onsetLatencyMinutes.toLong())

        return SleepPlan(
            bedTime = bedTime,
            estimatedSleepStart = sleepStart,
            estimatedWake = desiredWake,
            cycles = cycles,
            cycleLengthMinutes = cycleLengthMinutes,
            onsetLatencyMinutes = onsetLatencyMinutes,
            totalInBed = Duration.between(bedTime, desiredWake),
            totalSleep = Duration.ofMinutes(totalCycleMinutes),
            crossesMidnight = desiredWake.toLocalDate() > bedTime.toLocalDate(),
            preferredWake = desiredWake,
            isCutByPreferredWake = false,
            cyclesDidNotFit = false
        )
    }

    @Deprecated(
        message = "Альтернативы больше не используются. Используйте calculateFromNow.",
        level = DeprecationLevel.WARNING
    )
    fun alternativesFromDesiredWake(
        desiredWake: ZonedDateTime,
        onsetLatencyMinutes: Int,
        cycleLengthMinutes: Int
    ): List<SleepPlan> {
        return (MIN_CYCLES..MAX_CYCLES)
            .filter { it != DEFAULT_CYCLES }
            .map { cycles ->
                calculateFromDesiredWake(
                    desiredWake = desiredWake,
                    onsetLatencyMinutes = onsetLatencyMinutes,
                    cycleLengthMinutes = cycleLengthMinutes,
                    cycles = cycles
                )
            }
    }

    fun recommendCyclesForWake(
        desiredWake: ZonedDateTime,
        now: ZonedDateTime,
        onsetLatencyMinutes: Int,
        cycleLengthMinutes: Int
    ): Int? {
        if (cycleLengthMinutes <= 0) return null

        val availableMinutes = Duration.between(
            now.plusMinutes(onsetLatencyMinutes.toLong()),
            desiredWake
        ).toMinutes()

        if (availableMinutes <= 0) return null

        val maxPossible = (availableMinutes / cycleLengthMinutes).toInt()
        if (maxPossible < MIN_CYCLES) return null

        return maxPossible.coerceAtMost(MAX_CYCLES)
    }

    fun calculateRecommendedFromDesiredWake(
        desiredWake: ZonedDateTime,
        now: ZonedDateTime,
        onsetLatencyMinutes: Int,
        cycleLengthMinutes: Int
    ): SleepPlan? {
        val recommendedCycles = recommendCyclesForWake(
            desiredWake = desiredWake,
            now = now,
            onsetLatencyMinutes = onsetLatencyMinutes,
            cycleLengthMinutes = cycleLengthMinutes
        ) ?: return null

        return calculateFromDesiredWake(
            desiredWake = desiredWake,
            onsetLatencyMinutes = onsetLatencyMinutes,
            cycleLengthMinutes = cycleLengthMinutes,
            cycles = recommendedCycles
        )
    }

    // =====================================================================
    // Предупреждения
    // =====================================================================

    private val MIN_TOTAL_SLEEP: Duration = Duration.ofHours(3)
    private val MAX_TOTAL_SLEEP: Duration = Duration.ofHours(11)

    fun warningsFor(
        plan: SleepPlan,
        now: ZonedDateTime
    ): Set<SleepPlanWarning> {
        val warnings = mutableSetOf<SleepPlanWarning>()

        if (plan.cyclesDidNotFit) {
            warnings += SleepPlanWarning.TOTAL_SLEEP_TOO_SHORT
        }

        if (plan.cycles < MIN_CYCLES) {
            warnings += SleepPlanWarning.TOO_FEW_CYCLES
        }

        if (plan.cycles > MAX_CYCLES) {
            warnings += SleepPlanWarning.TOO_MANY_CYCLES
        }

        if (plan.bedTime.isBefore(now)) {
            warnings += SleepPlanWarning.BED_TIME_IN_PAST
        }

        if (plan.estimatedWake.isBefore(now)) {
            warnings += SleepPlanWarning.WAKE_TIME_IN_PAST
        }

        if (plan.totalSleep < MIN_TOTAL_SLEEP) {
            warnings += SleepPlanWarning.TOTAL_SLEEP_TOO_SHORT
        }

        if (plan.totalSleep > MAX_TOTAL_SLEEP) {
            warnings += SleepPlanWarning.TOTAL_SLEEP_TOO_LONG
        }

        return warnings
    }
}
