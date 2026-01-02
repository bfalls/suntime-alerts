package com.bfalls.suntimealerts.alarm.data

import android.os.Build
import androidx.annotation.RequiresApi
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.ALL_DAYS_MASK
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.includesDay
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import com.bfalls.suntimealerts.alarm.services.AlarmScheduler
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

interface SunScheduler {
    suspend fun schedule(coordinate: Coordinate, zoneId: ZoneId)
}

class SunScheduleService(
    private val calculator: SunTimesCalculator,
    private val settingsStore: SettingsRepository,
    private val notificationScheduler: AlarmScheduler,
    private val clock: Clock = Clock.systemDefaultZone()
) : SunScheduler {
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun schedule(coordinate: Coordinate, zoneId: ZoneId) {
        val alarms = settingsStore.loadAlarms()
        val zonedClock = clock.withZone(zoneId)
        val today = LocalDate.now(zonedClock)
        val dates = listOf(today, today.plusDays(1))
        val nowMillis = Instant.now(clock).toEpochMilli()
        notificationScheduler.cancelAll()
        dates.forEach { date ->
            val times = calculator.calculateSunTimes(date, coordinate, zoneId)
            alarms.filter { it.enabled }.forEach { alarm ->
                val recurrenceMask = alarm.recurrenceDays ?: ALL_DAYS_MASK
                if (!recurrenceMask.includesDay(date.dayOfWeek)) return@forEach
                val baseTime = when (alarm.type) {
                    SunEventType.SUNRISE -> times.sunrise
                    SunEventType.SUNSET -> times.sunset
                } ?: return@forEach
                val trigger = baseTime.toInstant().toEpochMilli() + alarm.offsetMinutes * 60 * 1000L
                if (trigger <= nowMillis) return@forEach
                notificationScheduler.schedule(
                    alarmId = alarm.id,
                    eventType = alarm.type,
                    triggerAtMillis = trigger,
                    zoneId = zoneId,
                    label = alarm.label,
                    offsetMinutes = alarm.offsetMinutes,
                    date = date,
                    soundUri = alarm.soundUri,
                    vibrate = alarm.vibrate ?: true
                )
            }
        }
    }
}
