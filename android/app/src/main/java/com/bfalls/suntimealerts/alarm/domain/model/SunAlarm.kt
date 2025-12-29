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

    val absoluteMinutes = abs(offsetMinutes)
    val hours = absoluteMinutes / 60
    val minutes = absoluteMinutes % 60
    val direction = if (offsetMinutes < 0) "before" else "after"

    return if (hours > 0) {
        "${hours}h ${minutes}m $direction"
    } else {
        "${minutes}m $direction"
    }
}
