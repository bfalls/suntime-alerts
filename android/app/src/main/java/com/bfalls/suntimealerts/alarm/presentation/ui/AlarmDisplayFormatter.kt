package com.bfalls.suntimealerts.alarm.presentation.ui

import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.abs

internal fun alarmPrimaryText(alarm: SunAlarm): String =
    alarmTimingText(alarm.type, alarm.offsetMinutes)

internal fun alarmSecondaryText(alarm: SunAlarm): String {
    val normalizedLabel = alarm.label.trim().takeUnless { it.isBlank() || it.equals(eventLabel(alarm.type), ignoreCase = true) }
    return appendRecurrenceLabel(
        label = normalizedLabel.orEmpty(),
        recurrenceSummary = recurrenceSummary(alarm.recurrenceDays)
    )
}

internal fun alarmTimingText(type: SunEventType, offsetMinutes: Int): String {
    if (offsetMinutes == 0) return "At ${eventLabel(type).lowercase()}"

    val absoluteMinutes = abs(offsetMinutes)
    val hours = absoluteMinutes / 60
    val minutes = absoluteMinutes % 60
    val direction = if (offsetMinutes < 0) "before" else "after"
    val duration = buildList {
        if (hours > 0) add("${hours}h")
        if (minutes > 0) add("${minutes}m")
    }.joinToString(" ")

    return "$duration $direction ${eventLabel(type).lowercase()}"
}

internal fun oneShotTimingMessage(
    type: SunEventType,
    offsetMinutes: Int,
    sunrise: ZonedDateTime?,
    sunset: ZonedDateTime?,
    now: ZonedDateTime
): String? {
    val eventTime = when (type) {
        SunEventType.SUNRISE -> sunrise
        SunEventType.SUNSET -> sunset
    } ?: return null
    val triggerTime = eventTime.plusMinutes(offsetMinutes.toLong())
    if (!triggerTime.isAfter(now)) return "Fires tomorrow."

    val minutesUntil = Duration.between(now, triggerTime).toMinutes().coerceAtLeast(0)
    return when {
        minutesUntil == 0L -> "Fires in under a minute."
        minutesUntil < 60 -> "Fires in about $minutesUntil ${minuteLabel(minutesUntil)}."
        minutesUntil < 90 -> "Fires in about an hour."
        else -> {
            val hoursUntil = ((minutesUntil + 30) / 60).coerceAtLeast(2)
            "Fires in about $hoursUntil hours."
        }
    }
}

private fun minuteLabel(minutes: Long): String = if (minutes == 1L) "minute" else "minutes"

private fun eventLabel(type: SunEventType): String = when (type) {
    SunEventType.SUNRISE -> "Sunrise"
    SunEventType.SUNSET -> "Sunset"
}
