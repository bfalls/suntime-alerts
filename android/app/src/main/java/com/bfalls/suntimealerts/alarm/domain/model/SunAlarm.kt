package com.bfalls.suntimealerts.alarm.domain.model

import java.time.DayOfWeek
import java.util.UUID
import kotlin.math.abs

data class SunAlarm(
    val id: String = UUID.randomUUID().toString(),
    val type: SunEventType,
    val offsetMinutes: Int,
    val label: String,
    val enabled: Boolean,
    val recurrenceDays: Int? = null,
    val soundUri: String? = null,
    val vibrate: Boolean? = null
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

const val ALL_DAYS_MASK = 0b1111111

private fun dayToBit(dayOfWeek: DayOfWeek): Int = when (dayOfWeek) {
    DayOfWeek.SUNDAY -> 1 shl 0
    DayOfWeek.MONDAY -> 1 shl 1
    DayOfWeek.TUESDAY -> 1 shl 2
    DayOfWeek.WEDNESDAY -> 1 shl 3
    DayOfWeek.THURSDAY -> 1 shl 4
    DayOfWeek.FRIDAY -> 1 shl 5
    DayOfWeek.SATURDAY -> 1 shl 6
}

fun Int.includesDay(dayOfWeek: DayOfWeek): Boolean = this and dayToBit(dayOfWeek) != 0

fun Set<DayOfWeek>.toBitMask(): Int = fold(0) { acc, day -> acc or dayToBit(day) }

fun Int.toDayOfWeekSet(): Set<DayOfWeek> = DayOfWeek.entries.filter { includesDay(it) }.toSet()

fun Int.prettyPrintDays(): String {
    val days = this.toDayOfWeekSet()
    if (days.size == DayOfWeek.entries.size) return "Every day"
    return days.joinToString(", ") { it.name.lowercase().replaceFirstChar(Char::uppercase) }
}

fun SunAlarm.withDefaults(): SunAlarm = copy(
    soundUri = soundUri,
    vibrate = vibrate ?: true
)
