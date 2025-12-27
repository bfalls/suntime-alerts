package com.bfalls.suntimealerts.alarm.domain.model

import java.util.UUID
import kotlin.math.abs

data class SunAlarm(
    val id: String = UUID.randomUUID().toString(),
    val type: SunEventType,
    val offsetMinutes: Int,
    val label: String,
    val enabled: Boolean
)

fun formatOffset(offsetMinutes: Int): String {
    if (offsetMinutes == 0) return "0m"

    val sign = when {
        offsetMinutes > 0 -> "+"
        offsetMinutes < 0 -> "-"
        else -> ""
    }
    val absoluteMinutes = abs(offsetMinutes)
    val hours = absoluteMinutes / 60
    val minutes = absoluteMinutes % 60

    return if (hours > 0) {
        "$sign${hours}h ${minutes}m"
    } else {
        "$sign${minutes}m"
    }
}
