package com.bfalls.suntimealerts.alarm.presentation.ui

import com.bfalls.suntimealerts.alarm.domain.service.PhotoLight
import com.bfalls.suntimealerts.alarm.domain.service.MoonPhase
import com.bfalls.suntimealerts.alarm.domain.service.SkyInfoMetrics
import com.bfalls.suntimealerts.alarm.domain.service.SkyInfoPeriod
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SkyInfoCategory(val colorArgb: Int) {
    SUNRISE(0xFFFFD166.toInt()), SUNSET(0xFFFFB68A.toInt()), DAYLIGHT(0xFFFFE29A.toInt()),
    DAYLIGHT_GAIN(0xFFAFE6BD.toInt()), DAYLIGHT_LOSS(0xFFAFE6BD.toInt()), TWILIGHT(0xFFBECFFF.toInt()),
    GOLDEN_HOUR(0xFFFFD166.toInt()), BLUE_HOUR(0xFFB5DFFF.toInt()), SUN_POSITION(0xFFFFE29A.toInt()),
    DARKNESS(0xFFBECFFF.toInt()), MOON_PHASE(0xFFDAD5FF.toInt()), MOON_EVENT(0xFFDAD5FF.toInt())
}

/** Platform-neutral styling/timing used by Compose and the desktop lab. */
object SkyInfoPresentation {
    const val ROTATION_MILLIS = 8_000L
    const val FADE_OUT_MILLIS = 250
    const val FADE_IN_DELAY_MILLIS = 200
    const val FADE_IN_MILLIS = 450
    val iconCircleArgb = 0xE6193025.toInt()
}

data class SkyInfoMessage(val id: String, val category: SkyInfoCategory, val text: String)

/** Presentation only: every value comes from the shared astronomical snapshot. */
object SkyInfoMessages {
    fun forMetrics(metrics: SkyInfoMetrics): List<SkyInfoMessage> = with(metrics) {
        val sunrise = nextSunrise?.let { message(SkyInfoCategory.SUNRISE, "Sunrise ${until(now, it)}") }
            ?: message(SkyInfoCategory.SUNRISE, if (today.sunrise == null) "No sunrise today" else "No sunrise tomorrow")
        val sunset = nextSunset?.let { message(SkyInfoCategory.SUNSET, "Sunset ${until(now, it)}") }
            ?: message(SkyInfoCategory.SUNSET, if (today.sunset == null) "No sunset today" else "No sunset tomorrow")
        val daylight = today.daylight?.let { message(SkyInfoCategory.DAYLIGHT, "${duration(it)} daylight today") }
        val change = daylightChange?.let {
            val minutes = (it.seconds / 60.0).roundToInt()
            when {
                minutes > 0 -> message(SkyInfoCategory.DAYLIGHT_GAIN, "${minutes}m more daylight than yesterday")
                minutes < 0 -> message(SkyInfoCategory.DAYLIGHT_LOSS, "${abs(minutes)}m less daylight than yesterday")
                else -> message(SkyInfoCategory.DAYLIGHT, "Daylight nearly unchanged", "daylight_change")
            }
        }
        val photo = photoWindow?.let {
            val name = if (it.light == PhotoLight.GOLDEN) "Golden hour" else "Blue hour"
            val category = if (it.light == PhotoLight.GOLDEN) SkyInfoCategory.GOLDEN_HOUR else SkyInfoCategory.BLUE_HOUR
            val text = if (now.isBefore(it.start)) "$name ${until(now, it.start)}" else "$name ends ${until(now, it.end)}"
            message(category, text, "photo_light")
        }
        val dawn = nextDawn?.takeIf { nextSunrise == null || it.time.isBefore(nextSunrise) }?.let {
            message(SkyInfoCategory.TWILIGHT, "${twilightName(it.altitudeDeg)} dawn ${until(now, it.time)}")
        }
        val dusk = nextDusk?.let {
            message(SkyInfoCategory.TWILIGHT, "${twilightName(it.altitudeDeg)} dusk ${until(now, it.time)}")
        }
        val darkness = astronomicalDarkness?.let { message(SkyInfoCategory.DARKNESS, "${duration(it)} darkness tonight") }
        val position = message(SkyInfoCategory.SUN_POSITION,
            if (sunPosition.altitudeDeg >= 0) "Sun ${sunPosition.altitudeDeg.roundToInt()}° high · ${compass(sunPosition.azimuthDeg)}"
            else "Sun ${abs(sunPosition.altitudeDeg).roundToInt()}° below horizon")
        val phaseName = phaseName(moonPhase)
        val moon = message(SkyInfoCategory.MOON_PHASE, "$phaseName · ${(moonPhase.illumination01 * 100).roundToInt()}% lit")
        val moonEvent = if (moonPosition.altitudeDeg >= 0) {
            moonWindow.set?.takeIf { it.isAfter(now) }?.let { message(SkyInfoCategory.MOON_EVENT, "Moonset ${until(now, it)}") }
        } else {
            moonWindow.rise?.takeIf { it.isAfter(now) }?.let { message(SkyInfoCategory.MOON_EVENT, "Moonrise ${until(now, it)}") }
        }
        val sunsetElapsed = previousSunset?.let {
            val elapsed = Duration.between(it, now)
            message(SkyInfoCategory.SUNSET, if (elapsed < Duration.ofMinutes(1)) "Sunset just passed" else "Sunset was ${duration(elapsed)} ago")
        }
        val candidates = when (period) {
            SkyInfoPeriod.DAWN -> listOf(sunrise, photo, dawn, daylight, change, moon)
            SkyInfoPeriod.DAYLIGHT -> listOf(sunset, photo, daylight, change, position)
            SkyInfoPeriod.EVENING_TWILIGHT -> listOf(dusk, photo, sunsetElapsed, darkness, moon)
            SkyInfoPeriod.NIGHT -> listOf(sunrise, dawn, darkness, moon, moonEvent)
        }.filterNotNull().distinctBy { it.id }.take(5)
        // At polar locations some solar events do not exist. Supply real live
        // positions/phase instead of fabricated event times, retaining 2+ items.
        (candidates + listOf(moon, position)).distinctBy { it.id }.take(maxOf(2, candidates.size))
    }

    private fun message(category: SkyInfoCategory, text: String, id: String = category.name) = SkyInfoMessage(id, category, text)

    private fun until(now: ZonedDateTime, event: ZonedDateTime): String {
        val milliseconds = Duration.between(now, event).toMillis().coerceAtLeast(0)
        val minutes = ((milliseconds + 59_999) / 60_000).coerceAtLeast(1)
        return if (minutes < 60) "in $minutes ${if (minutes == 1L) "minute" else "minutes"}"
        else "in ${duration(Duration.ofMinutes(minutes))}"
    }

    private fun duration(value: Duration): String {
        val minutes = (value.seconds / 60.0).roundToInt().coerceAtLeast(0)
        return when {
            minutes < 60 -> "${minutes}m"
            minutes % 60 == 0 -> "${minutes / 60}h"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
    }

    private fun twilightName(altitude: Double) = when (altitude) {
        -6.0 -> "Civil"
        -12.0 -> "Nautical"
        else -> "Astronomical"
    }

    fun phaseName(phase: MoonPhase): String = when {
        phase.illumination01 <= 0.01 -> "New Moon"
        phase.illumination01 >= 0.99 -> "Full Moon"
        phase.illumination01 in 0.47..0.53 -> if (phase.isWaxing) "First quarter" else "Last quarter"
        phase.illumination01 < 0.5 -> if (phase.isWaxing) "Waxing crescent" else "Waning crescent"
        else -> if (phase.isWaxing) "Waxing gibbous" else "Waning gibbous"
    }

    fun compass(azimuth: Double): String {
        val names = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return names[((azimuth + 22.5) / 45.0).toInt() % 8]
    }
}
