package com.bfalls.suntimealerts.alarm.domain.service

import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.max

data class MoonArcWindow(
    val rise: ZonedDateTime?,
    val set: ZonedDateTime?,
    val maxAltDeg: Double,
    val isUpNow: Boolean
)

object MoonTimesCalculator {

    private const val SCAN_HOURS = 36L
    private const val STEP_MINUTES = 10L
    private const val DETAIL_STEP_MINUTES = 5L

    fun computeWindow(now: ZonedDateTime, latDeg: Double, lonDeg: Double): MoonArcWindow {
        val altNow = MoonEphemeris.moonAltAz(now, latDeg, lonDeg).altitudeDeg
        val isUpNow = altNow >= 0

        val backwardEvents = findCrossings(now.minusHours(SCAN_HOURS), now, latDeg, lonDeg)
        val forwardEvents = findCrossings(now, now.plusHours(SCAN_HOURS), latDeg, lonDeg, initialAlt = altNow)

        val lastRise = backwardEvents.lastOrNull { it.second }?.first
        val nextRise = forwardEvents.firstOrNull { it.second }?.first
        val nextSet = forwardEvents.firstOrNull { !it.second }?.first

        val riseTime = if (isUpNow) lastRise else nextRise
        val setTime = if (isUpNow) {
            nextSet
        } else if (nextRise != null) {
            forwardEvents.dropWhile { it.first <= nextRise }.firstOrNull { !it.second }?.first
        } else {
            null
        }

        val orderedRise = riseTime
        val orderedSet = setTime?.takeIf { orderedRise?.isBefore(it) != false }
        val maxAltitude = calculateMaxAltitude(orderedRise, orderedSet, now, latDeg, lonDeg, altNow)

        return MoonArcWindow(
            rise = orderedRise,
            set = orderedSet,
            maxAltDeg = maxAltitude,
            isUpNow = isUpNow
        )
    }

    private fun findCrossings(
        start: ZonedDateTime,
        end: ZonedDateTime,
        latDeg: Double,
        lonDeg: Double,
        initialAlt: Double? = null
    ): List<Pair<ZonedDateTime, Boolean>> {
        if (start.isAfter(end)) return emptyList()
        val events = mutableListOf<Pair<ZonedDateTime, Boolean>>()
        var previousTime = start
        var previousAlt = initialAlt ?: MoonEphemeris.moonAltAz(start, latDeg, lonDeg).altitudeDeg
        var cursor = start.plusMinutes(STEP_MINUTES)
        while (!cursor.isAfter(end)) {
            val alt = MoonEphemeris.moonAltAz(cursor, latDeg, lonDeg).altitudeDeg
            if (previousAlt < 0 && alt >= 0) {
                events += interpolateCrossing(previousTime, cursor, previousAlt, alt) to true
            } else if (previousAlt >= 0 && alt < 0) {
                events += interpolateCrossing(previousTime, cursor, previousAlt, alt) to false
            }
            previousAlt = alt
            previousTime = cursor
            cursor = cursor.plusMinutes(STEP_MINUTES)
        }
        return events
    }

    private fun interpolateCrossing(
        previousTime: ZonedDateTime,
        currentTime: ZonedDateTime,
        previousAlt: Double,
        currentAlt: Double
    ): ZonedDateTime {
        val spanMillis = Duration.between(previousTime, currentTime).toMillis().coerceAtLeast(1)
        val ratio = if (currentAlt == previousAlt) {
            0.5
        } else {
            previousAlt / (previousAlt - currentAlt)
        }.coerceIn(0.0, 1.0)
        val offsetMillis = (ratio * spanMillis).toLong()
        return previousTime.plus(Duration.ofMillis(offsetMillis))
    }

    private fun calculateMaxAltitude(
        rise: ZonedDateTime?,
        set: ZonedDateTime?,
        now: ZonedDateTime,
        latDeg: Double,
        lonDeg: Double,
        altNow: Double
    ): Double {
        val riseTime = rise ?: return max(altNow, 0.0)
        val setTime = set ?: return max(altNow, 0.0)
        if (!riseTime.isBefore(setTime)) return max(altNow, 0.0)
        var cursor = riseTime
        var maxAlt = Double.NEGATIVE_INFINITY
        while (!cursor.isAfter(setTime)) {
            val alt = MoonEphemeris.moonAltAz(cursor, latDeg, lonDeg).altitudeDeg
            maxAlt = max(maxAlt, alt)
            cursor = cursor.plusMinutes(DETAIL_STEP_MINUTES)
        }
        return max(maxAlt, 0.0)
    }
}
