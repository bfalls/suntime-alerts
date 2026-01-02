package com.bfalls.suntimealerts.alarm.data

import android.os.Build
import androidx.annotation.RequiresApi
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.service.AlarmOccurrenceCalculator
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import com.bfalls.suntimealerts.alarm.services.AlarmScheduler
import java.time.Clock
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
    private val occurrenceCalculator = AlarmOccurrenceCalculator(calculator, clock)

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun schedule(coordinate: Coordinate, zoneId: ZoneId) {
        val alarms = settingsStore.loadAlarms()
        notificationScheduler.cancelAll()
        alarms.filter { it.enabled }.forEach { alarm ->
            val occurrence = occurrenceCalculator.nextOccurrence(alarm, coordinate, zoneId) ?: return@forEach
            notificationScheduler.schedule(
                alarmId = alarm.id,
                eventType = alarm.type,
                triggerAtMillis = occurrence.triggerAtMillis,
                zoneId = zoneId,
                label = alarm.label,
                offsetMinutes = alarm.offsetMinutes,
                date = occurrence.date,
                soundUri = alarm.soundUri,
                vibrate = alarm.vibrate ?: true,
                coordinate = coordinate
            )
        }
    }
}
