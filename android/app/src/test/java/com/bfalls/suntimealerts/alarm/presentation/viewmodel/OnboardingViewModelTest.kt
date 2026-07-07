package com.bfalls.suntimealerts.alarm.presentation.viewmodel

import com.bfalls.suntimealerts.MainDispatcherRule
import com.bfalls.suntimealerts.alarm.data.LocationProvider
import com.bfalls.suntimealerts.alarm.data.SettingsRepository
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarmConfig
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.UserSettings
import com.bfalls.suntimealerts.alarm.services.AlarmReadiness
import com.bfalls.suntimealerts.alarm.services.AlarmReadinessIssue
import com.bfalls.suntimealerts.alarm.services.AlarmReadinessProvider
import com.bfalls.suntimealerts.alarm.services.AlarmRepairAction
import com.bfalls.suntimealerts.cities.data.City
import com.bfalls.suntimealerts.cities.data.CityLookup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun notificationPermissionResultDoesNotAdvanceWhenReadinessIsStillMissing() = runTest {
        val readinessProvider = FakeAlarmReadinessProvider(
            readiness(notificationsReady = false)
        )
        val viewModel = createViewModel(readinessProvider)
        advanceUntilIdle()
        viewModel.nextStep()
        viewModel.nextStep()

        viewModel.handleNotificationPermissionResult()
        advanceUntilIdle()

        assertEquals(OnboardingStep.NOTIFICATIONS, viewModel.state.value.step)
    }

    @Test
    fun notificationPermissionResultAdvancesWhenReadinessIsGranted() = runTest {
        val readinessProvider = FakeAlarmReadinessProvider(
            readiness(notificationsReady = false)
        )
        val viewModel = createViewModel(readinessProvider)
        advanceUntilIdle()
        viewModel.nextStep()
        viewModel.nextStep()

        readinessProvider.current = readiness(notificationsReady = true)
        viewModel.handleNotificationPermissionResult()
        advanceUntilIdle()

        assertEquals(OnboardingStep.EXACT_ALARMS, viewModel.state.value.step)
    }

    @Test
    fun exactAlarmSettingsResultDoesNotAdvanceWhenReadinessIsStillMissing() = runTest {
        val readinessProvider = FakeAlarmReadinessProvider(
            readiness(exactAlarmReady = false)
        )
        val viewModel = createViewModel(readinessProvider)
        advanceUntilIdle()
        viewModel.nextStep()
        viewModel.nextStep()
        viewModel.nextStep()

        viewModel.handleExactAlarmSettingsResult()
        advanceUntilIdle()

        assertEquals(OnboardingStep.EXACT_ALARMS, viewModel.state.value.step)
    }

    @Test
    fun exactAlarmSettingsResultAdvancesWhenReadinessIsGranted() = runTest {
        val readinessProvider = FakeAlarmReadinessProvider(
            readiness(exactAlarmReady = false)
        )
        val viewModel = createViewModel(readinessProvider)
        advanceUntilIdle()
        viewModel.nextStep()
        viewModel.nextStep()
        viewModel.nextStep()

        readinessProvider.current = readiness(exactAlarmReady = true)
        viewModel.handleExactAlarmSettingsResult()
        advanceUntilIdle()

        assertEquals(OnboardingStep.SUMMARY, viewModel.state.value.step)
    }

    private fun createViewModel(
        readinessProvider: FakeAlarmReadinessProvider
    ): OnboardingViewModel = OnboardingViewModel(
        settingsStore = FakeSettingsRepository(),
        cityRepository = FakeCityLookup(),
        locationService = FakeLocationProvider(),
        alarmReadinessProvider = readinessProvider
    )

    private fun readiness(
        locationReady: Boolean = true,
        notificationsReady: Boolean = true,
        notificationChannelReady: Boolean = true,
        exactAlarmReady: Boolean = true,
        fullScreenIntentReady: Boolean = true,
        bootRescheduleReady: Boolean = true
    ): AlarmReadiness {
        val missingCapabilities = buildList {
            if (!locationReady) add(AlarmReadinessIssue.LOCATION)
            if (!notificationsReady) add(AlarmReadinessIssue.NOTIFICATIONS)
            if (!notificationChannelReady) add(AlarmReadinessIssue.NOTIFICATION_CHANNEL)
            if (!exactAlarmReady) add(AlarmReadinessIssue.EXACT_ALARM)
            if (!fullScreenIntentReady) add(AlarmReadinessIssue.FULL_SCREEN_INTENT)
            if (!bootRescheduleReady) add(AlarmReadinessIssue.BOOT_RESCHEDULE)
        }
        val repairActions = buildList {
            if (!locationReady) add(AlarmRepairAction.SELECT_LOCATION)
            if (!notificationsReady) add(AlarmRepairAction.REQUEST_NOTIFICATION_PERMISSION)
            if (!notificationChannelReady) add(AlarmRepairAction.OPEN_NOTIFICATION_CHANNEL_SETTINGS)
            if (!exactAlarmReady) add(AlarmRepairAction.REQUEST_EXACT_ALARM_PERMISSION)
            if (!fullScreenIntentReady) add(AlarmRepairAction.OPEN_FULL_SCREEN_INTENT_SETTINGS)
        }
        return AlarmReadiness(
            locationReady = locationReady,
            notificationsReady = notificationsReady,
            notificationChannelReady = notificationChannelReady,
            exactAlarmReady = exactAlarmReady,
            fullScreenIntentReady = fullScreenIntentReady,
            bootRescheduleReady = bootRescheduleReady,
            canDeliverReliableAlerts = missingCapabilities.isEmpty(),
            missingCapabilities = missingCapabilities,
            repairActions = repairActions
        )
    }

    private class FakeAlarmReadinessProvider(
        var current: AlarmReadiness
    ) : AlarmReadinessProvider {
        override suspend fun readiness(): AlarmReadiness = current
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val fixedCoordinate = Coordinate(39.7392, -104.9903)
        private var settings = UserSettings(
            locationMode = LocationMode.FIXED,
            fixedLocation = fixedCoordinate,
            sunriseConfig = SunAlarmConfig(false, SunEventType.SUNRISE, 0),
            sunsetConfig = SunAlarmConfig(false, SunEventType.SUNSET, 0),
            timeFormat24h = true,
            onboardingComplete = false,
            alarms = emptyList()
        )

        override suspend fun load(): UserSettings = settings

        override suspend fun save(settings: UserSettings) {
            this.settings = settings
        }

        override suspend fun loadAlarms(): List<SunAlarm> = settings.alarms

        override suspend fun saveAlarms(alarms: List<SunAlarm>) {
            settings = settings.copy(alarms = alarms)
        }
    }

    private class FakeCityLookup : CityLookup {
        override suspend fun searchCities(query: String, limit: Int): List<City> = emptyList()
        override suspend fun findNearestCity(lat: Double, lon: Double): City? = null
    }

    private class FakeLocationProvider : LocationProvider {
        override suspend fun currentCoordinate(): Coordinate? = null
    }
}
