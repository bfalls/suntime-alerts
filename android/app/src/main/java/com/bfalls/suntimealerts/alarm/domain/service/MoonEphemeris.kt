package com.bfalls.suntimealerts.alarm.domain.service

import java.time.ZonedDateTime
import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class AltAz(val altitudeDeg: Double, val azimuthDeg: Double)

data class MoonPhase(val illumination01: Double, val isWaxing: Boolean)

object MoonEphemeris {

    fun moonAltAz(time: ZonedDateTime, latitudeDeg: Double, longitudeDeg: Double): AltAz {
        val (lon, lat) = moonEclipticCoordinates(time)
        val eqCoords = toEquatorial(lon, lat, time)
        val lst = localSiderealTime(time, longitudeDeg)
        val hourAngle = Math.toRadians(normalizeDegrees(lst - eqCoords.first))
        val latRad = Math.toRadians(latitudeDeg)
        val decRad = Math.toRadians(eqCoords.second)

        val altitude = asin(
            sin(decRad) * sin(latRad) + cos(decRad) * cos(latRad) * cos(hourAngle)
        )
        val azimuth = atan2(
            -sin(hourAngle),
            tan(decRad) * cos(latRad) - sin(latRad) * cos(hourAngle)
        )

        return AltAz(
            altitudeDeg = Math.toDegrees(altitude),
            azimuthDeg = normalizeDegrees(Math.toDegrees(azimuth))
        )
    }

    fun moonPhase(time: ZonedDateTime): MoonPhase {
        val (moonLon, _) = moonEclipticCoordinates(time)
        val sunLon = SunTimesCalculator.sunApparentEclipticLongitude(time)
        val elongation = Math.toRadians(normalizeDegrees(moonLon - sunLon))
        val illumination = ((1 - cos(elongation)) / 2.0).coerceIn(0.0, 1.0)

        val futureTime = time.plusHours(6)
        val (futureMoonLon, _) = moonEclipticCoordinates(futureTime)
        val futureElongation = Math.toRadians(normalizeDegrees(futureMoonLon - SunTimesCalculator.sunApparentEclipticLongitude(futureTime)))
        val futureIllumination = ((1 - cos(futureElongation)) / 2.0).coerceIn(0.0, 1.0)

        val isWaxing = futureIllumination >= illumination
        return MoonPhase(illumination01 = illumination, isWaxing = isWaxing)
    }

    private fun moonEclipticCoordinates(time: ZonedDateTime): Pair<Double, Double> {
        val d = julianDay(time) - 2451545.0
        val L = normalizeDegrees(218.3164477 + 13.17639648 * d)
        val D = normalizeDegrees(297.8501921 + 12.19074912 * d)
        val M = normalizeDegrees(134.9633964 + 13.06499295 * d)
        val Ms = normalizeDegrees(357.5291092 + 0.98560028 * d)
        val F = normalizeDegrees(93.2720950 + 13.22935024 * d)

        var longitude = L +
            6.289 * sin(Math.toRadians(M)) +
            1.274 * sin(Math.toRadians(2 * D - M)) +
            0.658 * sin(Math.toRadians(2 * D)) +
            0.214 * sin(Math.toRadians(2 * M)) +
            0.11 * sin(Math.toRadians(D)) -
            0.186 * sin(Math.toRadians(Ms))

        var latitude = 5.128 * sin(Math.toRadians(F)) +
            0.28 * sin(Math.toRadians(M + F)) +
            0.277 * sin(Math.toRadians(M - F)) +
            0.173 * sin(Math.toRadians(2 * D - F))

        longitude = normalizeDegrees(longitude)
        return longitude to latitude
    }

    private fun toEquatorial(lonDeg: Double, latDeg: Double, time: ZonedDateTime): Pair<Double, Double> {
        val lon = Math.toRadians(lonDeg)
        val lat = Math.toRadians(latDeg)
        val d = julianDay(time) - 2451545.0
        val obliquity = Math.toRadians(23.439291 - 0.00000036 * d)

        val sinDec = sin(lat) * cos(obliquity) + cos(lat) * sin(obliquity) * sin(lon)
        val dec = asin(sinDec)
        val ra = atan2(
            sin(lon) * cos(obliquity) - tan(lat) * sin(obliquity),
            cos(lon)
        )

        return normalizeDegrees(Math.toDegrees(ra)) to Math.toDegrees(dec)
    }

    private fun localSiderealTime(time: ZonedDateTime, longitudeDeg: Double): Double {
        val jd = julianDay(time)
        val T = (jd - 2451545.0) / 36525.0
        val theta = 280.46061837 +
            360.98564736629 * (jd - 2451545.0) +
            0.000387933 * T * T -
            T * T * T / 38710000.0
        return normalizeDegrees(theta + longitudeDeg)
    }

    private fun julianDay(time: ZonedDateTime): Double {
        return time.toInstant().toEpochMilli() / 86400000.0 + 2440587.5
    }

    private fun normalizeDegrees(angle: Double): Double {
        var result = angle % 360.0
        if (result < 0) result += 360.0
        return result
    }
}
