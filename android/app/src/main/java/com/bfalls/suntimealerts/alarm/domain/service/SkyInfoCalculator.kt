package com.bfalls.suntimealerts.alarm.domain.service

import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class SkyInfoPeriod { DAWN, DAYLIGHT, EVENING_TWILIGHT, NIGHT }

data class SolarCrossing(val time: ZonedDateTime, val altitudeDeg: Double, val rising: Boolean)

data class SolarTransit(val time: ZonedDateTime, val altitudeDeg: Double)

enum class SkyLightPeriod { DAYLIGHT, CIVIL_TWILIGHT, NAUTICAL_TWILIGHT, ASTRONOMICAL_TWILIGHT, DARKNESS }

data class SkyLightSegment(val start: ZonedDateTime, val end: ZonedDateTime, val period: SkyLightPeriod)

data class TwilightWindow(val period: SkyLightPeriod, val start: ZonedDateTime, val end: ZonedDateTime, val isMorning: Boolean)

data class SolarDayInfo(
    val date: LocalDate,
    val sunrise: ZonedDateTime?,
    val sunset: ZonedDateTime?,
    val crossings: List<SolarCrossing>,
    val daylight: Duration?,
    val solarNoon: SolarTransit? = null,
    val solarMidnight: SolarTransit? = null,
    val sunriseAzimuthDeg: Double? = null,
    val sunsetAzimuthDeg: Double? = null,
    val lightSegments: List<SkyLightSegment> = emptyList(),
    val twilightWindows: List<TwilightWindow> = emptyList()
)

enum class PhotoLight { GOLDEN, BLUE }

data class PhotoLightWindow(val light: PhotoLight, val start: ZonedDateTime, val end: ZonedDateTime, val isMorning: Boolean = true)

/** Full snapshot for the banner and a future advanced-information screen. */
data class SkyInfoMetrics(
    val now: ZonedDateTime,
    val coordinate: Coordinate,
    val period: SkyInfoPeriod,
    val today: SolarDayInfo,
    val daylightChange: Duration?,
    val nextSunrise: ZonedDateTime?,
    val nextSunset: ZonedDateTime?,
    val previousSunset: ZonedDateTime?,
    val nextDawn: SolarCrossing?,
    val nextDusk: SolarCrossing?,
    val astronomicalDarkness: Duration?,
    val photoWindow: PhotoLightWindow?,
    val sunPosition: AltAz,
    val moonPhase: MoonPhase,
    val moonPosition: AltAz,
    val moonWindow: MoonArcWindow,
    val photoWindows: List<PhotoLightWindow> = emptyList(),
    val nextPhotoWindow: PhotoLightWindow? = null,
    val sunsetToSunrise: Duration? = null,
    val moonRiseAzimuthDeg: Double? = null,
    val moonSetAzimuthDeg: Double? = null
)

/**
 * Offline metrics, shared by every banner message. Daily scans are cached; only
 * live positions/phase and countdown inputs change on clock ticks.
 * Twilight thresholds: https://aa.usno.navy.mil/faq/RST_defs
 * Photo windows: golden -4°..+6°, blue -6°..-4° (PhotoPills convention).
 */
class SkyInfoCalculator(private val sunTimesCalculator: SunTimesCalculator = SunTimesCalculator()) {
    private data class DayKey(val date: LocalDate, val zone: ZoneId, val coordinate: Coordinate)
    private var dayKey: DayKey? = null
    private var days: List<SolarDayInfo> = emptyList()
    private var moonWindowAt: ZonedDateTime? = null
    private var cachedMoonCoordinate: Coordinate? = null
    private var cachedMoonWindow: MoonArcWindow? = null

    fun calculate(now: ZonedDateTime, coordinate: Coordinate): SkyInfoMetrics {
        val key = DayKey(now.toLocalDate(), now.zone, coordinate)
        if (key != dayKey) {
            days = (-1L..1L).map { calculateDay(key.date.plusDays(it), coordinate, now.zone) }
            dayKey = key
        }
        val yesterday = days[0]
        val today = days[1]
        val tomorrow = days[2]
        val crossings = days.flatMap { it.crossings }.sortedBy { it.time.toInstant() }
        val sunPosition = SunTimesCalculator.sunAltAz(now, coordinate.latitude, coordinate.longitude)
        val rising = SunTimesCalculator.sunAltAz(now.plusMinutes(1), coordinate.latitude, coordinate.longitude)
            .altitudeDeg > sunPosition.altitudeDeg
        val isDaylight = if (today.sunrise != null && today.sunset != null) {
            !now.isBefore(today.sunrise) && now.isBefore(today.sunset)
        } else {
            sunPosition.altitudeDeg >= -0.833
        }
        val period = when {
            isDaylight -> SkyInfoPeriod.DAYLIGHT
            sunPosition.altitudeDeg < -18.0 -> SkyInfoPeriod.NIGHT
            rising -> SkyInfoPeriod.DAWN
            else -> SkyInfoPeriod.EVENING_TWILIGHT
        }
        // Use the night adjoining this viewing period, including yesterday's
        // dusk when viewing after midnight. Missing crossings remain unavailable.
        val duskDay = if (period == SkyInfoPeriod.DAWN ||
            (period == SkyInfoPeriod.NIGHT && now.isBefore(today.sunrise ?: today.date.atTime(12, 0).atZone(now.zone)))) {
            yesterday
        } else today
        val dawnDay = if (duskDay === yesterday) today else tomorrow
        val dusk = duskDay.crossings.firstOrNull { !it.rising && it.altitudeDeg == -18.0 }?.time
        val dawn = dawnDay.crossings.firstOrNull { it.rising && it.altitudeDeg == -18.0 }?.time
        val darkness = if (dusk != null && dawn != null && dawn.isAfter(dusk)) Duration.between(dusk, dawn) else null
        val night = duskDay.sunset?.let { sunset ->
            dawnDay.sunrise?.takeIf { it.isAfter(sunset) }?.let { Duration.between(sunset, it) }
        }

        val moonPosition = MoonEphemeris.moonAltAz(now, coordinate.latitude, coordinate.longitude)
        val oldWindow = cachedMoonWindow
        val windowAge = moonWindowAt?.let { Duration.between(it, now) }
        val recalculateMoon = oldWindow == null || coordinate != cachedMoonCoordinate ||
            moonWindowAt?.zone != now.zone || windowAge == null || windowAge.isNegative ||
            windowAge >= Duration.ofMinutes(15) || oldWindow.isUpNow != (moonPosition.altitudeDeg >= 0.0)
        if (recalculateMoon) {
            cachedMoonWindow = MoonTimesCalculator.computeWindow(now, coordinate.latitude, coordinate.longitude)
            cachedMoonCoordinate = coordinate
            moonWindowAt = now
        }

        val windows = photoWindows(crossings)
        val active = windows.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
        val upcoming = windows.firstOrNull { it.start.isAfter(now) && Duration.between(now, it.start) <= Duration.ofHours(2) }
        return SkyInfoMetrics(
            now = now,
            coordinate = coordinate,
            period = period,
            today = today,
            daylightChange = if (today.daylight != null && yesterday.daylight != null) today.daylight.minus(yesterday.daylight) else null,
            nextSunrise = days.mapNotNull { it.sunrise }.firstOrNull { it.isAfter(now) },
            nextSunset = days.mapNotNull { it.sunset }.firstOrNull { it.isAfter(now) },
            previousSunset = days.mapNotNull { it.sunset }.lastOrNull { !it.isAfter(now) },
            nextDawn = crossings.firstOrNull { it.rising && it.altitudeDeg in listOf(-18.0, -12.0, -6.0) && it.time.isAfter(now) },
            nextDusk = crossings.firstOrNull { !it.rising && it.altitudeDeg in listOf(-6.0, -12.0, -18.0) && it.time.isAfter(now) },
            astronomicalDarkness = darkness,
            photoWindow = active ?: upcoming,
            sunPosition = sunPosition,
            moonPhase = MoonEphemeris.moonPhase(now),
            moonPosition = moonPosition,
            moonWindow = requireNotNull(cachedMoonWindow),
            photoWindows = windows.filter {
                it.end.isAfter(today.date.atStartOfDay(now.zone)) && it.start.isBefore(today.date.plusDays(1).atStartOfDay(now.zone))
            },
            nextPhotoWindow = windows.firstOrNull { it.end.isAfter(now) },
            sunsetToSunrise = night,
            moonRiseAzimuthDeg = cachedMoonWindow?.rise?.let { MoonEphemeris.moonAltAz(it, coordinate.latitude, coordinate.longitude).azimuthDeg },
            moonSetAzimuthDeg = cachedMoonWindow?.set?.let { MoonEphemeris.moonAltAz(it, coordinate.latitude, coordinate.longitude).azimuthDeg }
        )
    }

    private fun calculateDay(date: LocalDate, coordinate: Coordinate, zone: ZoneId): SolarDayInfo {
        val sunTimes = sunTimesCalculator.calculateSunTimes(date, coordinate, zone)
        val crossings = mutableListOf<SolarCrossing>()
        val thresholds = listOf(-18.0, -12.0, -6.0, -4.0, 6.0)
        var previousTime = date.atStartOfDay(zone)
        val end = date.plusDays(1).atStartOfDay(zone)
        var previousAlt = SunTimesCalculator.sunAltAz(previousTime, coordinate.latitude, coordinate.longitude).altitudeDeg
        var previousHourAngle = SunTimesCalculator.sunHourAngleDeg(previousTime, coordinate.longitude)
        var solarNoon: SolarTransit? = null
        var solarMidnight: SolarTransit? = null
        while (previousTime.isBefore(end)) {
            val time = minOf(previousTime.plusMinutes(5), end)
            val altitude = SunTimesCalculator.sunAltAz(time, coordinate.latitude, coordinate.longitude).altitudeDeg
            val hourAngle = SunTimesCalculator.sunHourAngleDeg(time, coordinate.longitude)
            if (previousHourAngle <= 0.0 && hourAngle > 0.0 && hourAngle - previousHourAngle < 180.0 && solarNoon == null) {
                solarNoon = findTransit(previousTime, time, coordinate, 0.0).takeIf { it.time.isBefore(end) }
            }
            if (previousHourAngle > 0.0 && hourAngle < 0.0 && previousHourAngle - hourAngle > 180.0 && solarMidnight == null) {
                solarMidnight = findTransit(previousTime, time, coordinate, 180.0).takeIf { it.time.isBefore(end) }
            }
            for (threshold in thresholds) {
                val rising = previousAlt < threshold && altitude >= threshold
                val setting = previousAlt >= threshold && altitude < threshold
                if (rising || setting) {
                    crossings += SolarCrossing(findCrossing(previousTime, time, coordinate, threshold, rising), threshold, rising)
                }
            }
            previousTime = time
            previousAlt = altitude
            previousHourAngle = hourAngle
        }
        val daylight = if (sunTimes.sunrise != null && sunTimes.sunset != null && sunTimes.sunset.isAfter(sunTimes.sunrise)) {
            Duration.between(sunTimes.sunrise, sunTimes.sunset)
        } else null
        val orderedCrossings = crossings.sortedBy { it.time.toInstant() }
        val boundaries = (listOf(date.atStartOfDay(zone), end) + orderedCrossings.filter { it.altitudeDeg in listOf(-18.0, -12.0, -6.0) }.map { it.time } +
            listOfNotNull(sunTimes.sunrise, sunTimes.sunset)).distinct().sortedBy { it.toInstant() }
        val segments = boundaries.zipWithNext { start, finish ->
            val midpoint = start.plusNanos(Duration.between(start, finish).toNanos() / 2)
            val altitude = SunTimesCalculator.sunAltAz(midpoint, coordinate.latitude, coordinate.longitude).altitudeDeg
            val isDaylight = if (sunTimes.sunrise != null && sunTimes.sunset != null) {
                !midpoint.isBefore(sunTimes.sunrise) && midpoint.isBefore(sunTimes.sunset)
            } else altitude >= -0.833
            SkyLightSegment(start, finish, when {
                isDaylight -> SkyLightPeriod.DAYLIGHT
                altitude >= -6.0 -> SkyLightPeriod.CIVIL_TWILIGHT
                altitude >= -12.0 -> SkyLightPeriod.NAUTICAL_TWILIGHT
                altitude >= -18.0 -> SkyLightPeriod.ASTRONOMICAL_TWILIGHT
                else -> SkyLightPeriod.DARKNESS
            })
        }
        val twilightWindows = buildList {
            for (morning in listOf(true, false)) {
                fun boundary(altitude: Double) = orderedCrossings.firstOrNull { it.rising == morning && it.altitudeDeg == altitude }?.time
                val edges = if (morning) listOf(boundary(-18.0), boundary(-12.0), boundary(-6.0), sunTimes.sunrise)
                else listOf(sunTimes.sunset, boundary(-6.0), boundary(-12.0), boundary(-18.0))
                val periods = if (morning) listOf(SkyLightPeriod.ASTRONOMICAL_TWILIGHT, SkyLightPeriod.NAUTICAL_TWILIGHT, SkyLightPeriod.CIVIL_TWILIGHT)
                else listOf(SkyLightPeriod.CIVIL_TWILIGHT, SkyLightPeriod.NAUTICAL_TWILIGHT, SkyLightPeriod.ASTRONOMICAL_TWILIGHT)
                edges.zipWithNext().forEachIndexed { index, (start, finish) ->
                    if (start != null && finish != null && finish.isAfter(start)) add(TwilightWindow(periods[index], start, finish, morning))
                }
            }
        }
        return SolarDayInfo(date, sunTimes.sunrise, sunTimes.sunset, orderedCrossings, daylight,
            solarNoon, solarMidnight,
            sunTimes.sunrise?.let { SunTimesCalculator.sunAltAz(it, coordinate.latitude, coordinate.longitude).azimuthDeg },
            sunTimes.sunset?.let { SunTimesCalculator.sunAltAz(it, coordinate.latitude, coordinate.longitude).azimuthDeg },
            segments, twilightWindows)
    }

    private fun findTransit(start: ZonedDateTime, end: ZonedDateTime, coordinate: Coordinate, target: Double): SolarTransit {
        var low = start
        var high = end
        while (Duration.between(low, high).toMillis() > 1000) {
            val midpoint = low.plusNanos(Duration.between(low, high).toNanos() / 2)
            val hourAngle = SunTimesCalculator.sunHourAngleDeg(midpoint, coordinate.longitude)
            val relative = ((hourAngle - target + 540.0) % 360.0) - 180.0
            if (relative >= 0.0) high = midpoint else low = midpoint
        }
        val time = high.withNano(0)
        return SolarTransit(time, SunTimesCalculator.sunAltAz(time, coordinate.latitude, coordinate.longitude).altitudeDeg)
    }

    private fun findCrossing(start: ZonedDateTime, end: ZonedDateTime, coordinate: Coordinate, threshold: Double, rising: Boolean): ZonedDateTime {
        var low = start
        var high = end
        while (Duration.between(low, high).toMillis() > 1000) {
            val midpoint = low.plusNanos(Duration.between(low, high).toNanos() / 2)
            val above = SunTimesCalculator.sunAltAz(midpoint, coordinate.latitude, coordinate.longitude).altitudeDeg >= threshold
            if (above == rising) high = midpoint else low = midpoint
        }
        return high.withNano(0)
    }

    private fun photoWindows(crossings: List<SolarCrossing>): List<PhotoLightWindow> {
        val windows = mutableListOf<PhotoLightWindow>()
        for (start in crossings) {
            val light = when {
                start.rising && start.altitudeDeg == -6.0 -> PhotoLight.BLUE
                start.rising && start.altitudeDeg == -4.0 -> PhotoLight.GOLDEN
                !start.rising && start.altitudeDeg == 6.0 -> PhotoLight.GOLDEN
                !start.rising && start.altitudeDeg == -4.0 -> PhotoLight.BLUE
                else -> continue
            }
            val endAltitude = when {
                light == PhotoLight.BLUE && start.rising -> -4.0
                light == PhotoLight.BLUE -> -6.0
                start.rising -> 6.0
                else -> -4.0
            }
            // The next matching crossing must belong to the same rising/setting
            // pass. Do not manufacture a multi-day window at high latitudes.
            val end = crossings.firstOrNull { it.time.isAfter(start.time) && it.rising == start.rising && it.altitudeDeg == endAltitude }
            if (end != null && Duration.between(start.time, end.time) < Duration.ofHours(12)) {
                windows += PhotoLightWindow(light, start.time, end.time, start.rising)
            }
        }
        return windows.sortedBy { it.start.toInstant() }
    }
}
