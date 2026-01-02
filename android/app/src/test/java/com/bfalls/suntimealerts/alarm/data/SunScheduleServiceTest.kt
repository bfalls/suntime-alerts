package com.bfalls.suntimealerts.alarm.data

import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarmConfig
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.UserSettings
import com.bfalls.suntimealerts.alarm.domain.model.toBitMask
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import com.bfalls.suntimealerts.alarm.services.AlarmScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
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

        assertTrue("Only one occurrence should be scheduled per enabled alarm", notificationScheduler.entries.size == 1)

        assertTrue(
            notificationScheduler.entries.any {
                it.eventType == SunEventType.SUNRISE &&
                    it.triggerAtMillis == expectedTrigger
            }
        )
    }

    @Test
    fun doesNotSchedulePastOccurrences() = runTest {
        val calculator = SunTimesCalculator()
        val fixedInstant = Instant.parse("2024-01-01T12:00:00Z")
        val zone = ZoneId.of("UTC")
        val clock = Clock.fixed(fixedInstant, zone)
        val notificationScheduler = RecordingNotificationScheduler()
        val repo = object : SettingsRepository {
            override suspend fun load(): UserSettings = TODO("Not used")
            override suspend fun save(settings: UserSettings) = Unit
            override suspend fun loadAlarms(): List<SunAlarm> = alarms
            override suspend fun saveAlarms(alarms: List<SunAlarm>) = Unit

            private val alarms = listOf(
                SunAlarm(
                    type = SunEventType.SUNRISE,
                    offsetMinutes = 0,
                    label = "Morning walk",
                    enabled = true
                )
            )
        }
        val service = SunScheduleService(calculator, repo, notificationScheduler, clock)
        val coordinate = Coordinate(0.0, 0.0)

        service.schedule(coordinate, zone)

        val today = LocalDate.now(clock)
        val tomorrow = today.plusDays(1)
        val sunTimesTomorrow = calculator.calculateSunTimes(tomorrow, coordinate, zone)
        val expectedTomorrowTrigger = requireNotNull(sunTimesTomorrow.sunrise).toInstant().toEpochMilli()
        assertTrue(
            "Should only schedule a single upcoming occurrence",
            notificationScheduler.entries.size == 1
        )
        assertFalse(
            "Should not schedule past occurrences for today",
            notificationScheduler.entries.any { entry ->
                Instant.ofEpochMilli(entry.triggerAtMillis).atZone(zone).toLocalDate() == today
            }
        )
        assertTrue(
            "Should schedule tomorrow's occurrence",
            notificationScheduler.entries.any { entry -> entry.triggerAtMillis == expectedTomorrowTrigger }
        )
    }

    @Test
    fun skipsUnselectedRecurrenceDays() = runTest {
        val calculator = SunTimesCalculator()
        val fixedInstant = Instant.parse("2024-01-02T10:00:00Z") // Tuesday
        val zone = ZoneId.of("UTC")
        val clock = Clock.fixed(fixedInstant, zone)
        val notificationScheduler = RecordingNotificationScheduler()
        val repo = object : SettingsRepository {
            override suspend fun load(): UserSettings = TODO("Not used")
            override suspend fun save(settings: UserSettings) = Unit
            override suspend fun loadAlarms(): List<SunAlarm> = alarms
            override suspend fun saveAlarms(alarms: List<SunAlarm>) = Unit

            private val alarms = listOf(
                SunAlarm(
                    type = SunEventType.SUNRISE,
                    offsetMinutes = 0,
                    label = "Weekday alarm",
                    enabled = true,
                    recurrenceDays = setOf(DayOfWeek.WEDNESDAY).toBitMask()
                )
            )
        }
        val service = SunScheduleService(calculator, repo, notificationScheduler, clock)
        val coordinate = Coordinate(0.0, 0.0)

        service.schedule(coordinate, zone)

        val today = LocalDate.now(clock)
        val tomorrow = today.plusDays(1)
        assertTrue(
            "Should only schedule one pending occurrence for the enabled alarm",
            notificationScheduler.entries.size == 1
        )
        assertFalse(
            "Should not schedule on an unselected day (today)",
            notificationScheduler.entries.any { entry ->
                Instant.ofEpochMilli(entry.triggerAtMillis).atZone(zone).toLocalDate() == today
            }
        )
        assertTrue(
            "Should schedule on the next selected day (tomorrow, Wednesday)",
            notificationScheduler.entries.any { entry ->
                Instant.ofEpochMilli(entry.triggerAtMillis).atZone(zone).toLocalDate() == tomorrow
            }
        )
    }

    @Test
    fun unschedulesWhenAlarmIsDisabled() = runTest {
        val calculator = SunTimesCalculator()
        val notificationScheduler = RecordingNotificationScheduler()
        val repo = object : SettingsRepository {
            override suspend fun load(): UserSettings = TODO("Not used")
            override suspend fun save(settings: UserSettings) = Unit
            override suspend fun loadAlarms(): List<SunAlarm> = alarms
            override suspend fun saveAlarms(alarms: List<SunAlarm>) = Unit

            var alarms: List<SunAlarm> = listOf(
                SunAlarm(
                    type = SunEventType.SUNRISE,
                    offsetMinutes = 0,
                    label = "One-shot",
                    enabled = true,
                    recurrenceDays = null
                )
            )
        }
        val service = SunScheduleService(calculator, repo, notificationScheduler)
        val coordinate = Coordinate(0.0, 0.0)
        val zone = ZoneId.of("UTC")

        service.schedule(coordinate, zone)
        assertTrue(notificationScheduler.entries.isNotEmpty())

        repo.alarms = repo.alarms.map { it.copy(enabled = false) }
        service.schedule(coordinate, zone)

        assertTrue(notificationScheduler.entries.isEmpty())
    }

    private class RecordingNotificationScheduler : AlarmScheduler {
        data class Entry(
            val alarmId: String,
            val eventType: SunEventType,
            val triggerAtMillis: Long
        )

        val entries = mutableListOf<Entry>()

        override fun schedule(
            alarmId: String,
            eventType: SunEventType,
            triggerAtMillis: Long,
            zoneId: ZoneId,
            label: String,
            offsetMinutes: Int,
            date: LocalDate,
            soundUri: String?,
            vibrate: Boolean,
            coordinate: Coordinate
        ) {
            entries += Entry(alarmId, eventType, triggerAtMillis)
        }

        override fun cancelAll() {
            entries.clear()
        }
    }
}
