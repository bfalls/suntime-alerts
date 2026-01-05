package com.bfalls.suntimealerts.alarm.data

import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarmConfig
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.UserSettings
import com.bfalls.suntimealerts.alarm.domain.model.toBitMask
import com.bfalls.suntimealerts.alarm.domain.service.AlarmOccurrenceCalculator
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
        val fixedInstant = Instant.parse("2024-01-01T00:00:00Z")
        val zone = ZoneId.of("UTC")
        val clock = Clock.fixed(fixedInstant, zone)
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

            val alarms = listOf(
                SunAlarm(
                    type = SunEventType.SUNRISE,
                    offsetMinutes = 30,
                    label = "Morning walk",
                    enabled = true
                )
            )
        }
        val service = SunScheduleService(calculator, repo, notificationScheduler, clock)
        val coordinate = Coordinate(0.0, 0.0)

        service.schedule(coordinate, zone)

        assertTrue("Should schedule exactly one upcoming occurrence", notificationScheduler.entries.size == 1)

        val expectedDate = LocalDate.now(clock)
        val expectedTrigger = requireNotNull(
            calculator.calculateSunTimes(expectedDate, coordinate, zone).sunrise
        ).toInstant().toEpochMilli() + 30 * 60 * 1000L

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
        assertTrue(
            "Should schedule exactly one upcoming occurrence",
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
            notificationScheduler.entries.single().date == today.plusDays(1)
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
        assertTrue(
            "Should only schedule one pending occurrence on the next selected day",
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
            notificationScheduler.entries.single().date == today.plusDays(1)
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
        val fixedInstant = Instant.parse("2024-01-01T00:00:00Z")
        val zone = ZoneId.of("UTC")
        val clock = Clock.fixed(fixedInstant, zone)
        val service = SunScheduleService(calculator, repo, notificationScheduler, clock)
        val coordinate = Coordinate(0.0, 0.0)

        service.schedule(coordinate, zone)
        assertTrue(notificationScheduler.entries.isNotEmpty())

        // Simulate a duplicate pending occurrence on the same date that should be removed.
        val scheduledDate = notificationScheduler.entries.first().date
        notificationScheduler.entries += RecordingNotificationScheduler.Entry(
            alarmId = repo.alarms.first().id,
            eventType = repo.alarms.first().type,
            triggerAtMillis = Instant.now(clock).plusSeconds(24 * 3600).toEpochMilli(),
            date = scheduledDate
        )

        repo.alarms = repo.alarms.map { it.copy(enabled = false) }
        service.schedule(coordinate, zone)

        assertTrue(notificationScheduler.entries.isEmpty())
    }

    @Test
    fun reschedulesWhenEditMovesNextDate() = runTest {
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
                    label = "Weekday edit",
                    enabled = true,
                    recurrenceDays = setOf(DayOfWeek.MONDAY).toBitMask()
                )
            )
        }
        val fixedInstant = Instant.parse("2024-01-01T00:00:00Z") // Monday
        val zone = ZoneId.of("UTC")
        val clock = Clock.fixed(fixedInstant, zone)
        val service = SunScheduleService(calculator, repo, notificationScheduler, clock)
        val coordinate = Coordinate(0.0, 0.0)

        service.schedule(coordinate, zone)
        assertTrue(notificationScheduler.entries.size == 1)
        val originalDate = notificationScheduler.entries.single().date

        // Change recurrence to Friday, which should move the next occurrence and cancel the original.
        repo.alarms = repo.alarms.map {
            it.copy(recurrenceDays = setOf(DayOfWeek.FRIDAY).toBitMask())
        }
        service.schedule(coordinate, zone)

        assertTrue(
            "Old occurrence should be canceled after edit",
            notificationScheduler.entries.none { it.date == originalDate }
        )
        assertTrue(
            "New occurrence should be scheduled on the edited recurrence day",
            notificationScheduler.entries.single().date.dayOfWeek == DayOfWeek.FRIDAY
        )
    }

    private class RecordingNotificationScheduler : AlarmScheduler {
        data class Entry(
            val alarmId: String,
            val eventType: SunEventType,
            val triggerAtMillis: Long,
            val date: LocalDate
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
            entries += Entry(alarmId, eventType, triggerAtMillis, date)
        }

        override fun hasScheduledOccurrence(alarmId: String, eventType: SunEventType, date: LocalDate): Boolean {
            return entries.any { entry ->
                entry.alarmId == alarmId &&
                    entry.eventType == eventType &&
                    entry.date == date
            }
        }

        override fun cancelOccurrence(alarmId: String, eventType: SunEventType, date: LocalDate) {
            entries.removeAll { entry ->
                entry.alarmId == alarmId &&
                    entry.eventType == eventType &&
                    entry.date == date
            }
        }
    }
}
