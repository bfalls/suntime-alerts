package com.bfalls.suntimealerts.alarm.domain.service

import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class SkyInfoPeriod { DAWN, DAYLIGHT, EVENING_TWILIGHT, NIGHT }

data class SolarCrossing(val time: ZonedDateTime, val altitudeDeg: Double, val rising: Boolean)

data class SolarDayInfo(
    val date: LocalDate,
    val sunrise: ZonedDateTime?,
    val sunset: ZonedDateTime?,
    val crossings: List<SolarCrossing>,
    val daylight: Duration?
)

enum class PhotoLight { GOLDEN, BLUE }

data class PhotoLightWindow(val light: PhotoLight, val start: ZonedDateTime, val end: ZonedDateTime)

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
    val moonWindow: MoonArcWindow
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
            moonWindow = requireNotNull(cachedMoonWindow)
        )
    }

    private fun calculateDay(date: LocalDate, coordinate: Coordinate, zone: ZoneId): SolarDayInfo {
        val sunTimes = sunTimesCalculator.calculateSunTimes(date, coordinate, zone)
        val crossings = mutableListOf<SolarCrossing>()
        val thresholds = listOf(-18.0, -12.0, -6.0, -4.0, 6.0)
        var previousTime = date.atStartOfDay(zone)
        val end = date.plusDays(1).atStartOfDay(zone)
        var previousAlt = SunTimesCalculator.sunAltAz(previousTime, coordinate.latitude, coordinate.longitude).altitudeDeg
        while (previousTime.isBefore(end)) {
            val time = minOf(previousTime.plusMinutes(5), end)
            val altitude = SunTimesCalculator.sunAltAz(time, coordinate.latitude, coordinate.longitude).altitudeDeg
            for (threshold in thresholds) {
                val rising = previousAlt < threshold && altitude >= threshold
                val setting = previousAlt >= threshold && altitude < threshold
                if (rising || setting) {
                    crossings += SolarCrossing(findCrossing(previousTime, time, coordinate, threshold, rising), threshold, rising)
                }
            }
            previousTime = time
            previousAlt = altitude
        }
        val daylight = if (sunTimes.sunrise != null && sunTimes.sunset != null && sunTimes.sunset.isAfter(sunTimes.sunrise)) {
            Duration.between(sunTimes.sunrise, sunTimes.sunset)
        } else null
        return SolarDayInfo(date, sunTimes.sunrise, sunTimes.sunset, crossings.sortedBy { it.time.toInstant() }, daylight)
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
                windows += PhotoLightWindow(light, start.time, end.time)
            }
        }
        return windows.sortedBy { it.start.toInstant() }
    }
}
