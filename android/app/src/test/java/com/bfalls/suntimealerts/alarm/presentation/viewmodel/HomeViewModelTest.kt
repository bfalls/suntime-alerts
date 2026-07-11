package com.bfalls.suntimealerts.alarm.presentation.viewmodel

import com.bfalls.suntimealerts.MainDispatcherRule
import com.bfalls.suntimealerts.alarm.data.LocationProvider
import com.bfalls.suntimealerts.alarm.data.SettingsRepository
import com.bfalls.suntimealerts.alarm.data.SunScheduler
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarmConfig
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.UserSettings
import com.bfalls.suntimealerts.alarm.services.AlarmReadiness
import com.bfalls.suntimealerts.alarm.services.AlarmReadinessProvider
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun rescheduleUsesFixedLocationWhenManualMode() = runTest {
        val fixedCoordinate = Coordinate(12.34, 56.78)
        val settings = UserSettings(
            locationMode = LocationMode.FIXED,
            fixedLocation = fixedCoordinate,
            sunriseConfig = SunAlarmConfig(enabled = true, eventType = SunEventType.SUNRISE, offsetMinutes = 0),
            sunsetConfig = SunAlarmConfig(enabled = false, eventType = SunEventType.SUNSET, offsetMinutes = 0),
            timeFormat24h = true,
            onboardingComplete = true,
            alarms = listOf(
                SunAlarm(
                    type = SunEventType.SUNRISE,
                    offsetMinutes = 0,
                    label = "Morning",
                    enabled = true
                )
            )
        )
        val settingsRepo = FakeSettingsRepository(settings, settings.alarms)
        val locationProvider = RecordingLocationProvider()
        val scheduler = RecordingScheduler()

        val viewModel = HomeViewModel(
            locationProvider,
            settingsRepo,
            scheduler,
            SunTimesCalculator(),
            FakeAlarmReadinessProvider()
        )

        advanceUntilIdle()
        viewModel.reschedule()
        advanceUntilIdle()

        assertEquals(listOf(fixedCoordinate, fixedCoordinate), scheduler.receivedCoordinates)
        assertEquals(0, locationProvider.requestCount)
    }

    @Test
    fun loadStateSkipsSchedulingWhenExactAlarmReadinessIsMissing() = runTest {
        val fixedCoordinate = Coordinate(12.34, 56.78)
        val settings = UserSettings(
            locationMode = LocationMode.FIXED,
            fixedLocation = fixedCoordinate,
            sunriseConfig = SunAlarmConfig(enabled = true, eventType = SunEventType.SUNRISE, offsetMinutes = 0),
            sunsetConfig = SunAlarmConfig(enabled = false, eventType = SunEventType.SUNSET, offsetMinutes = 0),
            timeFormat24h = true,
            onboardingComplete = true,
            alarms = listOf(
                SunAlarm(
                    type = SunEventType.SUNRISE,
                    offsetMinutes = 0,
                    label = "Morning",
                    enabled = true
                )
            )
        )
        val settingsRepo = FakeSettingsRepository(settings, settings.alarms)
        val scheduler = RecordingScheduler()

        HomeViewModel(
            RecordingLocationProvider(),
            settingsRepo,
            scheduler,
            SunTimesCalculator(),
            FakeAlarmReadinessProvider(exactAlarmReady = false)
        )

        advanceUntilIdle()

        assertTrue(scheduler.receivedCoordinates.isEmpty())
    }

    @Test
    fun exactAlarmSettingsResultReschedulesWhenReadinessBecomesGranted() = runTest {
        val fixedCoordinate = Coordinate(12.34, 56.78)
        val settings = UserSettings(
            locationMode = LocationMode.FIXED,
            fixedLocation = fixedCoordinate,
            sunriseConfig = SunAlarmConfig(enabled = true, eventType = SunEventType.SUNRISE, offsetMinutes = 0),
            sunsetConfig = SunAlarmConfig(enabled = false, eventType = SunEventType.SUNSET, offsetMinutes = 0),
            timeFormat24h = true,
            onboardingComplete = true,
            alarms = listOf(
                SunAlarm(
                    type = SunEventType.SUNRISE,
                    offsetMinutes = 0,
                    label = "Morning",
                    enabled = true
                )
            )
        )
        val settingsRepo = FakeSettingsRepository(settings, settings.alarms)
        val scheduler = RecordingScheduler()
        val readinessProvider = FakeAlarmReadinessProvider(exactAlarmReady = false)
        val viewModel = HomeViewModel(
            RecordingLocationProvider(),
            settingsRepo,
            scheduler,
            SunTimesCalculator(),
            readinessProvider
        )

        advanceUntilIdle()
        assertTrue(scheduler.receivedCoordinates.isEmpty())

        readinessProvider.current = readinessState(exactAlarmReady = true)
        viewModel.handleExactAlarmSettingsResult()
        advanceUntilIdle()

        assertEquals(listOf(fixedCoordinate), scheduler.receivedCoordinates)
    }

    @Test
    fun postOnboardingPermissionRevocationLeavesOnboardingDoneButReadinessDegraded() = runTest {
        val fixedCoordinate = Coordinate(12.34, 56.78)
        val settings = UserSettings(
            locationMode = LocationMode.FIXED,
            fixedLocation = fixedCoordinate,
            sunriseConfig = SunAlarmConfig(enabled = true, eventType = SunEventType.SUNRISE, offsetMinutes = 0),
            sunsetConfig = SunAlarmConfig(enabled = false, eventType = SunEventType.SUNSET, offsetMinutes = 0),
            timeFormat24h = true,
            onboardingComplete = true,
            alarms = listOf(
                SunAlarm(
                    type = SunEventType.SUNRISE,
                    offsetMinutes = 0,
                    label = "Morning",
                    enabled = true
                )
            )
        )
        val settingsRepo = FakeSettingsRepository(settings, settings.alarms)
        val readinessProvider = FakeAlarmReadinessProvider(exactAlarmReady = false)
        val viewModel = HomeViewModel(
            RecordingLocationProvider(),
            settingsRepo,
            RecordingScheduler(),
            SunTimesCalculator(),
            readinessProvider
        )

        advanceUntilIdle()

        assertFalse(viewModel.state.value.alarmReadiness?.canDeliverReliableAlerts ?: true)
        assertEquals(true, settingsRepo.load().onboardingComplete)
    }

    private class FakeSettingsRepository(
        private var settings: UserSettings,
        private var alarms: List<SunAlarm>
    ) : SettingsRepository {
        override suspend fun load(): UserSettings = settings.copy(alarms = alarms)
        override suspend fun save(settings: UserSettings) {
            this.settings = settings
        }

        override suspend fun loadAlarms(): List<SunAlarm> = alarms

        override suspend fun saveAlarms(alarms: List<SunAlarm>) {
            this.alarms = alarms
        }
    }

    private class RecordingLocationProvider(
        private val coordinate: Coordinate? = null
    ) : LocationProvider {
        var requestCount = 0
            private set

        override suspend fun currentCoordinate(): Coordinate? {
            requestCount++
            return coordinate
        }
    }

    private class RecordingScheduler : SunScheduler {
        val receivedCoordinates = mutableListOf<Coordinate>()
        override suspend fun schedule(coordinate: Coordinate, zoneId: ZoneId) {
            receivedCoordinates += coordinate
        }

        override suspend fun cancel(alarm: SunAlarm, zoneId: ZoneId) {
            // no-op for tests
        }
    }

    private fun readinessState(
        exactAlarmReady: Boolean = true
    ): AlarmReadiness = AlarmReadiness(
        locationReady = true,
        notificationsReady = true,
        notificationChannelReady = true,
        blockedNotificationChannelId = null,
        exactAlarmReady = exactAlarmReady,
        fullScreenIntentReady = true,
        bootRescheduleReady = true,
        canDeliverReliableAlerts = exactAlarmReady,
        missingCapabilities = if (exactAlarmReady) emptyList() else listOf(com.bfalls.suntimealerts.alarm.services.AlarmReadinessIssue.EXACT_ALARM),
        repairActions = if (exactAlarmReady) emptyList() else listOf(com.bfalls.suntimealerts.alarm.services.AlarmRepairAction.REQUEST_EXACT_ALARM_PERMISSION)
    )

    private class FakeAlarmReadinessProvider(
        exactAlarmReady: Boolean = true
    ) : AlarmReadinessProvider {
        var current: AlarmReadiness = AlarmReadiness(
            locationReady = true,
            notificationsReady = true,
            notificationChannelReady = true,
            blockedNotificationChannelId = null,
            exactAlarmReady = exactAlarmReady,
            fullScreenIntentReady = true,
            bootRescheduleReady = true,
            canDeliverReliableAlerts = exactAlarmReady,
            missingCapabilities = if (exactAlarmReady) {
                emptyList()
            } else {
                listOf(com.bfalls.suntimealerts.alarm.services.AlarmReadinessIssue.EXACT_ALARM)
            },
            repairActions = if (exactAlarmReady) {
                emptyList()
            } else {
                listOf(com.bfalls.suntimealerts.alarm.services.AlarmRepairAction.REQUEST_EXACT_ALARM_PERMISSION)
            }
        )

        override suspend fun readiness(): AlarmReadiness = current
    }
}
