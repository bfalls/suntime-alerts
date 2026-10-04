package com.bfalls.suntimealerts.alarm.presentation.ui

import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Formatting only; the cached domain snapshot supplies all dashboard metrics. */
internal object SkyDashboardText {
    fun time(time: ZonedDateTime?, now: ZonedDateTime, use24h: Boolean): String {
        time ?: return "Does not occur today"
        val clock = time.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a"))
        return if (time.toLocalDate() == now.toLocalDate()) clock
        else "${time.format(DateTimeFormatter.ofPattern("MMM d"))}, $clock"
    }

    fun duration(duration: Duration?): String {
        duration ?: return "Unavailable"
        val minutes = (duration.seconds / 60.0).roundToInt().coerceAtLeast(0)
        return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
    }

    fun until(now: ZonedDateTime, event: ZonedDateTime): String {
        val minutes = ((Duration.between(now, event).toMillis().coerceAtLeast(0) + 59_999) / 60_000).coerceAtLeast(1)
        return if (minutes < 60) "in $minutes ${if (minutes == 1L) "minute" else "minutes"}"
        else "in ${duration(Duration.ofMinutes(minutes))}"
    }

    fun daylightChange(change: Duration?): String {
        change ?: return "Unavailable"
        val minutes = (change.seconds / 60.0).roundToInt()
        return when {
            minutes == 0 -> "Nearly unchanged"
            minutes > 0 -> "${minutes}m more daylight"
            else -> "${abs(minutes)}m less daylight"
        }
    }

    fun bearing(degrees: Double?): String = degrees?.let {
        "${SkyInfoMessages.compass(it)} · ${it.roundToInt() % 360}°"
    } ?: "Unavailable"

    fun altitude(degrees: Double): String =
        "${abs(degrees).roundToInt()}° ${if (degrees >= 0) "high" else "below horizon"}"

    fun coordinate(latitude: Double, longitude: Double): String =
        String.format(Locale.getDefault(), "%.5f°, %.5f°", latitude, longitude)
}
