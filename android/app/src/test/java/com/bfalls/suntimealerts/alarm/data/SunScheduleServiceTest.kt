package com.bfalls.suntimealerts.alarm.data

import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarmConfig
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.UserSettings
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import com.bfalls.suntimealerts.alarm.services.AlarmScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class SunScheduleServiceTest {

    @Test
    fun schedulesWithOffsetApplied() = runTest {
        val calculator = SunTimesCalculator()
        val notificationScheduler = RecordingNotificationScheduler()
        val repo = object : SettingsRepository {
            override suspend fun load(): UserSettings = UserSettings(
                locationMode = LocationMode.DEVICE,
                sunriseConfig = SunAlarmConfig(true, SunEventType.SUNRISE, 0),
                sunsetConfig = SunAlarmConfig(true, SunEventType.SUNSET, 0),
                timeFormat24h = true,
                onboardingComplete = true,
                alarms = alarms
            )

            override suspend fun save(settings: UserSettings) = Unit
            override suspend fun loadAlarms(): List<SunAlarm> = alarms
            override suspend fun saveAlarms(alarms: List<SunAlarm>) = Unit

            private val alarms = listOf(
                SunAlarm(
                    type = SunEventType.SUNRISE,
                    offsetMinutes = 30,
                    label = "Morning walk",
                    enabled = true
                )
            )
        }
        val service = SunScheduleService(calculator, repo, notificationScheduler)
        val coordinate = Coordinate(0.0, 0.0)
        val zone = ZoneId.of("UTC")

        service.schedule(coordinate, zone)

        val today = LocalDate.now(zone)
        val sunTimes = calculator.calculateSunTimes(today, coordinate, zone)
        val expectedTrigger = requireNotNull(sunTimes.sunrise).toInstant().toEpochMilli() + 30 * 60 * 1000L

        assertTrue(
            notificationScheduler.entries.any {
                it.eventType == SunEventType.SUNRISE &&
                    it.triggerAtMillis == expectedTrigger
            }
        )
    }

    private class RecordingNotificationScheduler : AlarmScheduler {
        data class Entry(
            val alarmId: String,
            val eventType: SunEventType,
            val triggerAtMillis: Long
        )

        val entries = mutableListOf<Entry>()

        override fun schedule(alarmId: String, eventType: SunEventType, triggerAtMillis: Long, zoneId: ZoneId, label: String, offsetMinutes: Int, date: LocalDate) {
            entries += Entry(alarmId, eventType, triggerAtMillis)
        }

        override fun cancelAll() {
            entries.clear()
        }
    }
}
