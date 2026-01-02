package com.bfalls.suntimealerts.alarm.presentation.ui

import com.bfalls.suntimealerts.alarm.domain.model.includesDay
import java.time.DayOfWeek

private val orderedDays = listOf(
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY
)

private val weekdaySet = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY
)

private val weekendSet = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

fun recurrenceSummary(recurrenceMask: Int?): String? {
    if (recurrenceMask == null) return null

    val days = orderedDays.filter { recurrenceMask.includesDay(it) }
    if (days.isEmpty()) return null

    val daySet = days.toSet()

    return when {
        days.size == orderedDays.size -> "every day"
        daySet == weekdaySet -> "every weekday"
        daySet == weekendSet -> "every weekend"
        else -> formatDayList(days)
    }
}

fun appendRecurrenceLabel(label: String, recurrenceSummary: String?): String {
    if (recurrenceSummary.isNullOrBlank()) return label
    if (label.isBlank()) return recurrenceSummary
    return "$label, $recurrenceSummary"
}

private fun formatDayList(days: List<DayOfWeek>): String {
    val names = days.map(::shortName)
    return when (names.size) {
        1 -> names.first()
        2 -> "${names[0]} and ${names[1]}"
        else -> names.dropLast(1).joinToString(separator = ", ") + ", and ${names.last()}"
    }
}

private fun shortName(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.SUNDAY -> "Sun"
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
}
