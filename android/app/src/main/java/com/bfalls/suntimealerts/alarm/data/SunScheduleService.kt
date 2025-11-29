package com.bfalls.suntimealerts.alarm.data

import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarmConfig
import com.bfalls.suntimealerts.alarm.domain.model.SunEvent
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import com.bfalls.suntimealerts.alarm.services.NotificationScheduler
import java.time.LocalDate
import java.time.ZoneId

class SunScheduleService(
    private val calculator: SunTimesCalculator,
    private val settingsStore: SettingsStore,
    private val notificationScheduler: NotificationScheduler
) {
    suspend fun schedule(coordinate: Coordinate, zoneId: ZoneId) {
        val settings = settingsStore.load()
        val today = LocalDate.now(zoneId)
        val dates = listOf(today, today.plusDays(1))
        notificationScheduler.cancelAll()
        dates.forEach { date ->
            val times = calculator.calculateSunTimes(date, coordinate, zoneId)
            val events = buildList {
                times.sunrise?.let { add(
                    SunEvent(
                        date.toEpochDay(),
                        SunEventType.SUNRISE,
                        it.toInstant().toEpochMilli(),
                        coordinate
                    )
                ) }
                times.sunset?.let { add(
                    SunEvent(
                        date.toEpochDay(),
                        SunEventType.SUNSET,
                        it.toInstant().toEpochMilli(),
                        coordinate
                    )
                ) }
            }
            events.forEach { event ->
                val config: SunAlarmConfig = if (event.type == SunEventType.SUNRISE) settings.sunriseConfig else settings.sunsetConfig
                if (!config.enabled) return@forEach
                val trigger = event.dateTimeEpochMillis + config.offsetMinutes * 60 * 1000L
                notificationScheduler.schedule(event.type, trigger, zoneId)
            }
        }
    }
}