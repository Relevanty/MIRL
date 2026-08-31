package com.personal.sleepalarm.domain.externalcontext

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * NOAA-style sunrise/sunset approximation using the standard civil sunrise zenith (90.833°).
 * It is intended for daily context, not navigation or safety decisions.
 */
object DaylightCalculator {
    private const val CIVIL_SUNRISE_ZENITH_DEGREES = 90.833
    private const val MINUTES_PER_DAY = 1_440

    fun calculate(
        date: LocalDate,
        location: CoarseLocation,
        zoneId: ZoneId = ZoneId.of(location.zoneId)
    ): DaylightContext {
        val safeLocation = location.normalizedOrNull()
            ?: throw IllegalArgumentException("Invalid coarse location")
        val daysInYear = if (date.isLeapYear) 366.0 else 365.0
        val fractionalYear = 2.0 * PI / daysInYear * (date.dayOfYear - 1)
        val equationOfTimeMinutes = 229.18 * (
            0.000075 +
                0.001868 * cos(fractionalYear) -
                0.032077 * sin(fractionalYear) -
                0.014615 * cos(2.0 * fractionalYear) -
                0.040849 * sin(2.0 * fractionalYear)
            )
        val solarDeclination =
            0.006918 -
                0.399912 * cos(fractionalYear) +
                0.070257 * sin(fractionalYear) -
                0.006758 * cos(2.0 * fractionalYear) +
                0.000907 * sin(2.0 * fractionalYear) -
                0.002697 * cos(3.0 * fractionalYear) +
                0.00148 * sin(3.0 * fractionalYear)

        val latitudeRadians = Math.toRadians(safeLocation.latitude)
        val zenithRadians = Math.toRadians(CIVIL_SUNRISE_ZENITH_DEGREES)
        val hourAngleCosine = (
            cos(zenithRadians) / (cos(latitudeRadians) * cos(solarDeclination)) -
                tan(latitudeRadians) * tan(solarDeclination)
            )

        if (hourAngleCosine < -1.0) {
            return DaylightContext(
                date = date,
                zoneId = zoneId.id,
                sunrise = null,
                sunset = null,
                daylightMinutes = MINUTES_PER_DAY,
                state = PolarDaylightState.POLAR_DAY
            )
        }
        if (hourAngleCosine > 1.0) {
            return DaylightContext(
                date = date,
                zoneId = zoneId.id,
                sunrise = null,
                sunset = null,
                daylightMinutes = 0,
                state = PolarDaylightState.POLAR_NIGHT
            )
        }

        val hourAngleDegrees = Math.toDegrees(acos(hourAngleCosine.coerceIn(-1.0, 1.0)))
        val localNoon = date.atTime(12, 0).atZone(zoneId)
        val offsetMinutes = localNoon.offset.totalSeconds / 60.0
        val solarNoonMinutes =
            720.0 - 4.0 * safeLocation.longitude - equationOfTimeMinutes + offsetMinutes
        val sunriseMinutes = solarNoonMinutes - 4.0 * hourAngleDegrees
        val sunsetMinutes = solarNoonMinutes + 4.0 * hourAngleDegrees
        val dayStart = date.atStartOfDay(zoneId)

        return DaylightContext(
            date = date,
            zoneId = zoneId.id,
            sunrise = dayStart.plusMinutes(sunriseMinutes.roundToInt().toLong()).toInstant(),
            sunset = dayStart.plusMinutes(sunsetMinutes.roundToInt().toLong()).toInstant(),
            daylightMinutes = (8.0 * hourAngleDegrees).roundToInt().coerceIn(0, MINUTES_PER_DAY),
            state = PolarDaylightState.NORMAL
        )
    }
}

internal fun Instant.localDateAt(location: CoarseLocation): LocalDate =
    atZone(ZoneId.of(location.zoneId)).toLocalDate()
