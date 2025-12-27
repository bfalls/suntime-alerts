package com.bfalls.suntimealerts.alarm.data

import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import com.bfalls.suntimealerts.alarm.services.AlarmScheduler
import java.time.LocalDate
import java.time.ZoneId

interface SunScheduler {
    suspend fun schedule(coordinate: Coordinate, zoneId: ZoneId)
}

class SunScheduleService(
    private val calculator: SunTimesCalculator,
    private val settingsStore: SettingsRepository,
    private val notificationScheduler: AlarmScheduler
) : SunScheduler {
    override suspend fun schedule(coordinate: Coordinate, zoneId: ZoneId) {
        val alarms = settingsStore.loadAlarms()
        val today = LocalDate.now(zoneId)
        val dates = listOf(today, today.plusDays(1))
        notificationScheduler.cancelAll()
        dates.forEach { date ->
            val times = calculator.calculateSunTimes(date, coordinate, zoneId)
            alarms.filter { it.enabled }.forEach { alarm ->
                val baseTime = when (alarm.type) {
                    SunEventType.SUNRISE -> times.sunrise
                    SunEventType.SUNSET -> times.sunset
                } ?: return@forEach
                val trigger = baseTime.toInstant().toEpochMilli() + alarm.offsetMinutes * 60 * 1000L
                notificationScheduler.schedule(
                    alarmId = alarm.id,
                    eventType = alarm.type,
                    triggerAtMillis = trigger,
                    zoneId = zoneId,
                    label = alarm.label,
                    offsetMinutes = alarm.offsetMinutes,
                    date = date
                )
            }
        }
    }
}
