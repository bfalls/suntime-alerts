package com.bfalls.suntimealerts.alarm.domain.service

import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

class SunTimesCalculator {
    data class SunTimes(val sunrise: ZonedDateTime?, val sunset: ZonedDateTime?)

    fun calculateSunTimes(date: LocalDate, coordinate: Coordinate, zoneId: ZoneId): SunTimes {
        val tzOffsetMinutes = zoneId.rules.getOffset(date.atStartOfDay(zoneId).toInstant()).totalSeconds / 60.0
        val julianDay = julianDay(date)
        val jc = (julianDay - 2451545.0) / 36525.0

        val (declination, eqTime) = solarCoordinates(jc)
        val haSunrise = hourAngleSunrise(coordinate.latitude, declination)
        if (haSunrise.isNaN()) return SunTimes(null, null)

        val solarNoon = (720.0 - eqTime - 4 * coordinate.longitude + tzOffsetMinutes) / 1440.0
        val sunrise = solarNoon - haSunrise * 4.0 / 1440.0
        val sunset = solarNoon + haSunrise * 4.0 / 1440.0

        return SunTimes(
            fractionalDayToDate(date, sunrise, zoneId),
            fractionalDayToDate(date, sunset, zoneId)
        )
    }

    private fun julianDay(date: LocalDate): Double {
        val y = date.year
        val m = date.monthValue
        val d = date.dayOfMonth
        val a = (14 - m) / 12
        val yPrime = y + 4800 - a
        val mPrime = m + 12 * a - 3
        return d + ((153 * mPrime + 2) / 5) + 365 * yPrime + (yPrime / 4) - (yPrime / 100) + (yPrime / 400) - 32045.0
    }

    private fun solarCoordinates(jc: Double): Pair<Double, Double> {
        val geomMeanLongSun = normalizeAngle(280.46646 + jc * (36000.76983 + 0.0003032 * jc))
        val geomMeanAnomalySun = 357.52911 + jc * (35999.05029 - 0.0001537 * jc)
        val eccentEarthOrbit = 0.016708634 - jc * (0.000042037 + 0.0000001267 * jc)
        val sunEqOfCenter = sin(Math.toRadians(geomMeanAnomalySun)) * (1.914602 - jc * (0.004817 + 0.000014 * jc)) + sin(
            Math.toRadians(2 * geomMeanAnomalySun)
        ) * (0.019993 - 0.000101 * jc) + sin(Math.toRadians(3 * geomMeanAnomalySun)) * 0.000289
        val sunTrueLong = geomMeanLongSun + sunEqOfCenter
        val sunAppLong = sunTrueLong - 0.00569 - 0.00478 * sin(Math.toRadians(125.04 - 1934.136 * jc))
        val meanObliqEcliptic = 23.0 + (26.0 + ((21.448 - jc * (46.815 + jc * (0.00059 - jc * 0.001813))) / 60.0)) / 60.0
        val obliqCorr = meanObliqEcliptic + 0.00256 * cos(Math.toRadians(125.04 - 1934.136 * jc))
        val declination = Math.toDegrees(
            asin(
                sin(Math.toRadians(obliqCorr)) * sin(
                    Math.toRadians(
                        sunAppLong
                    )
                )
            )
        )
        val y = tan(Math.toRadians(obliqCorr / 2)) * tan(Math.toRadians(obliqCorr / 2))
        val eqTime = 4.0 * Math.toDegrees(
            y * sin(2 * Math.toRadians(geomMeanLongSun)) - 2 * eccentEarthOrbit * sin(
                Math.toRadians(
                    geomMeanAnomalySun
                )
            ) + 4 * eccentEarthOrbit * y * sin(Math.toRadians(geomMeanAnomalySun)) * cos(
                2 * Math.toRadians(
                    geomMeanLongSun
                )
            ) - 0.5 * y * y * sin(4 * Math.toRadians(geomMeanLongSun)) - 1.25 * eccentEarthOrbit * eccentEarthOrbit * sin(
                2 * Math.toRadians(geomMeanAnomalySun)
            )
        )
        return declination to eqTime
    }

    private fun hourAngleSunrise(latitude: Double, declination: Double): Double {
        val latRad = Math.toRadians(latitude)
        val declRad = Math.toRadians(declination)
        val solarZenith = Math.toRadians(90.833)
        val cosH = (cos(solarZenith) - sin(latRad) * sin(declRad)) / (cos(latRad) * cos(declRad))
        return acos(cosH)
    }

    private fun fractionalDayToDate(date: LocalDate, fraction: Double, zoneId: ZoneId): ZonedDateTime? {
        val seconds = (fraction * 86400).roundToInt()
        if (seconds < 0 || seconds > 86400) return null
        return date.atStartOfDay(zoneId).plusSeconds(seconds.toLong())
    }

    private fun normalizeAngle(angle: Double): Double {
        var result = angle % 360.0
        if (result < 0) result += 360.0
        return result
    }
}