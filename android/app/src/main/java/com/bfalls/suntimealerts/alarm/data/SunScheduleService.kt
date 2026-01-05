package com.bfalls.suntimealerts.alarm.data

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.service.AlarmOccurrenceCalculator
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import com.bfalls.suntimealerts.alarm.services.AlarmScheduler
import java.time.LocalDate
import java.time.Clock
import java.time.ZoneId

interface SunScheduler {
    suspend fun schedule(coordinate: Coordinate, zoneId: ZoneId)
    suspend fun cancel(alarm: com.bfalls.suntimealerts.alarm.domain.model.SunAlarm, zoneId: ZoneId)
}

class SunScheduleService(
    private val calculator: SunTimesCalculator,
    private val settingsStore: SettingsRepository,
    private val notificationScheduler: AlarmScheduler,
    private val clock: Clock = Clock.systemDefaultZone()
    ) : SunScheduler {
    private val occurrenceCalculator = AlarmOccurrenceCalculator(calculator, clock)
    private val cancelSweepDays = 14

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun schedule(coordinate: Coordinate, zoneId: ZoneId) {
        val alarms = settingsStore.loadAlarms()
        alarms.forEach { alarm ->
            val occurrence = occurrenceCalculator.nextOccurrence(
                alarm = alarm,
                coordinate = coordinate,
                zoneId = zoneId
            ) ?: return@forEach
            if (alarm.enabled) {
                cancelStaleScheduledOccurrences(alarm, occurrence.date)
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
            } else {
                cancelAllScheduledOccurrences(alarm.id, occurrence.date)
            }
        }
    }

    private fun cancelStaleScheduledOccurrences(
        alarm: com.bfalls.suntimealerts.alarm.domain.model.SunAlarm,
        keepDate: LocalDate
    ) {
        // Sweep a small window around the target date to clear stale scheduled occurrences for this alarm.
        // This handles edits (offset/recurrence) that move the next firing to a different date.
        val datesToCheck = mutableSetOf(keepDate)
        (1..cancelSweepDays).forEach { offset ->
            datesToCheck += keepDate.plusDays(offset.toLong())
            datesToCheck += keepDate.minusDays(offset.toLong())
        }

        datesToCheck.forEach { date ->
            SunEventType.values().forEach { eventType ->
                val isCurrent = eventType == alarm.type && date == keepDate
                if (isCurrent) return@forEach

                if (notificationScheduler.hasScheduledOccurrence(alarm.id, eventType, date)) {
                    notificationScheduler.cancelOccurrence(alarm.id, eventType, date)
                }
            }
        }
    }

    private fun cancelAllScheduledOccurrences(alarmId: String, centerDate: LocalDate) {
        val datesToCheck = mutableSetOf(centerDate)
        (1..cancelSweepDays).forEach { offset ->
            datesToCheck += centerDate.plusDays(offset.toLong())
            datesToCheck += centerDate.minusDays(offset.toLong())
        }

        datesToCheck.forEach { date ->
            SunEventType.values().forEach { eventType ->
                if (notificationScheduler.hasScheduledOccurrence(alarmId, eventType, date)) {
                    notificationScheduler.cancelOccurrence(alarmId, eventType, date)
                }
            }
        }
    }

    override suspend fun cancel(alarm: com.bfalls.suntimealerts.alarm.domain.model.SunAlarm, zoneId: ZoneId) {
        val coordinate = settingsStore.load().fixedLocation ?: Coordinate(0.0, 0.0)
        val occurrence = occurrenceCalculator.nextOccurrence(
            alarm = alarm,
            coordinate = coordinate,
            zoneId = zoneId
        ) ?: return
        notificationScheduler.cancelOccurrence(alarm.id, alarm.type, occurrence.date)
    }
}
