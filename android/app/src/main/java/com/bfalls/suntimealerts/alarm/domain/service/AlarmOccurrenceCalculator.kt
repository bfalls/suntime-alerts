package com.bfalls.suntimealerts.alarm.domain.service

import com.bfalls.suntimealerts.alarm.domain.model.ALL_DAYS_MASK
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.includesDay
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AlarmOccurrenceCalculator(
    private val calculator: SunTimesCalculator,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    data class Occurrence(val date: LocalDate, val triggerAtMillis: Long)

    fun nextOccurrence(
        alarm: SunAlarm,
        coordinate: Coordinate,
        zoneId: ZoneId
    ): Occurrence? {
        val zonedClock = clock.withZone(zoneId)
        val nowMillis = Instant.now(zonedClock).toEpochMilli()
        val startDate = LocalDate.now(zonedClock)
        val recurrenceMask = alarm.recurrenceDays ?: ALL_DAYS_MASK

        for (daysFromNow in 0..6) {
            val date = startDate.plusDays(daysFromNow.toLong())
            if (!recurrenceMask.includesDay(date.dayOfWeek)) continue

            val sunTimes = calculator.calculateSunTimes(date, coordinate, zoneId)
            val baseTime = when (alarm.type) {
                SunEventType.SUNRISE -> sunTimes.sunrise
                SunEventType.SUNSET -> sunTimes.sunset
            } ?: continue

            val triggerAtMillis = baseTime.toInstant().toEpochMilli() + alarm.offsetMinutes * 60 * 1000L
            if (triggerAtMillis > nowMillis) {
                return Occurrence(date, triggerAtMillis)
            }
        }

        return null
    }
}
