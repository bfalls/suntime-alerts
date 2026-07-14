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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    @Test
    fun handleLocationPermissionResultTreatsCoarseOnlyGrantAsUsable() = runTest {
        val readinessProvider = FakeAlarmReadinessProvider(readiness(locationReady = true))
        val locationProvider = FakeLocationProvider(Coordinate(39.7392, -104.9903))
        val cityLookup = FakeCityLookup(
            nearestCity = sampleCity()
        )
        val viewModel = createViewModel(
            readinessProvider = readinessProvider,
            cityRepository = cityLookup,
            locationService = locationProvider
        )
        advanceUntilIdle()

        viewModel.handleLocationPermissionResult(
            granted = true,
            permanentlyDenied = false,
            origin = PermissionRequestOrigin.USER
        )
        advanceUntilIdle()

        assertEquals(LocationMode.DEVICE, viewModel.state.value.locationMode)
        assertEquals("39.7392, -104.9903", viewModel.state.value.deviceNearestCityLabel)
        assertFalse(viewModel.state.value.deviceLocationLookupFailed)
    }

    @Test
    fun handleLocationPermissionResultTreatsFineGrantAsUsable() = runTest {
        val readinessProvider = FakeAlarmReadinessProvider(readiness(locationReady = true))
        val locationProvider = FakeLocationProvider(Coordinate(39.7392, -104.9903))
        val cityLookup = FakeCityLookup(
            nearestCity = sampleCity()
        )
        val viewModel = createViewModel(
            readinessProvider = readinessProvider,
            cityRepository = cityLookup,
            locationService = locationProvider
        )
        advanceUntilIdle()

        viewModel.handleLocationPermissionResult(
            granted = true,
            permanentlyDenied = false,
            origin = PermissionRequestOrigin.USER
        )
        advanceUntilIdle()

        assertEquals(LocationMode.DEVICE, viewModel.state.value.locationMode)
        assertEquals("39.7392, -104.9903", viewModel.state.value.deviceNearestCityLabel)
        assertFalse(viewModel.state.value.deviceLocationLookupFailed)
    }

    @Test
    fun handleLocationPermissionResultFallsBackToManualWhenDenied() = runTest {
        val viewModel = createViewModel(FakeAlarmReadinessProvider(readiness(locationReady = false)))
        advanceUntilIdle()

        viewModel.nextStep()
        viewModel.handleLocationPermissionResult(
            granted = false,
            permanentlyDenied = false,
            origin = PermissionRequestOrigin.USER
        )
        advanceUntilIdle()

        assertEquals(LocationMode.FIXED, viewModel.state.value.locationMode)
        assertFalse(viewModel.state.value.locationPermissionPermanentlyDenied)
        assertEquals(1, viewModel.state.value.locationPermissionDeniedAttempts)
    }

    @Test
    fun secondLocationPermissionDenialMarksPermissionAsPermanentlyDenied() = runTest {
        val viewModel = createViewModel(FakeAlarmReadinessProvider(readiness(locationReady = false)))
        advanceUntilIdle()

        viewModel.nextStep()
        viewModel.handleLocationPermissionResult(
            granted = false,
            permanentlyDenied = false,
            origin = PermissionRequestOrigin.USER
        )
        advanceUntilIdle()
        viewModel.handleLocationPermissionResult(
            granted = false,
            permanentlyDenied = false,
            origin = PermissionRequestOrigin.USER
        )
        advanceUntilIdle()

        assertEquals(LocationMode.FIXED, viewModel.state.value.locationMode)
        assertTrue(viewModel.state.value.locationPermissionPermanentlyDenied)
        assertEquals(2, viewModel.state.value.locationPermissionDeniedAttempts)
    }

    @Test
    fun deviceModeShowsManualFallbackWhenLocationLookupFails() = runTest {
        val viewModel = createViewModel(
            readinessProvider = FakeAlarmReadinessProvider(readiness(locationReady = true)),
            locationService = FakeLocationProvider(null)
        )
        advanceUntilIdle()

        viewModel.updateLocationMode(LocationMode.DEVICE)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.deviceLocationLookupFailed)
        assertEquals(null, viewModel.state.value.deviceNearestCityLabel)
    }

    @Test
    fun deviceModeStaysManualWhenPermissionWasPermanentlyDenied() = runTest {
        val viewModel = createViewModel(
            readinessProvider = FakeAlarmReadinessProvider(readiness(locationReady = false)),
            locationService = FakeLocationProvider(Coordinate(39.7392, -104.9903))
        )
        advanceUntilIdle()

        viewModel.nextStep()
        viewModel.handleLocationPermissionResult(
            granted = false,
            permanentlyDenied = true,
            origin = PermissionRequestOrigin.USER
        )
        advanceUntilIdle()
        viewModel.updateLocationMode(LocationMode.DEVICE)
        advanceUntilIdle()

        assertEquals(LocationMode.FIXED, viewModel.state.value.locationMode)
        assertEquals(null, viewModel.state.value.deviceNearestCityLabel)
        assertFalse(viewModel.state.value.isResolvingDeviceLocation)
    }

    @Test
    fun handleResumeClearsPermanentDenialWhenLocationIsReadyAgain() = runTest {
        val readinessProvider = FakeAlarmReadinessProvider(readiness(locationReady = false))
        val viewModel = createViewModel(readinessProvider)
        advanceUntilIdle()

        viewModel.nextStep()
        viewModel.handleLocationPermissionResult(
            granted = false,
            permanentlyDenied = true,
            origin = PermissionRequestOrigin.USER
        )
        advanceUntilIdle()

        readinessProvider.current = readiness(locationReady = true)
        viewModel.handleResume()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.locationPermissionPermanentlyDenied)
        assertEquals(readinessProvider.current, viewModel.state.value.alarmReadiness)
    }

    @Test
    fun selectCityPersistsManualFallbackImmediately() = runTest {
        val settingsStore = FakeSettingsRepository()
        val city = sampleCity()
        val viewModel = createViewModel(
            readinessProvider = FakeAlarmReadinessProvider(readiness(locationReady = false)),
            settingsStore = settingsStore
        )
        advanceUntilIdle()

        viewModel.selectCity(city)
        advanceUntilIdle()

        assertEquals(LocationMode.FIXED, settingsStore.settings.locationMode)
        assertNotNull(settingsStore.settings.fixedLocation)
        assertEquals(city.lat, settingsStore.settings.fixedLocation?.latitude ?: 0.0, 0.0)
        assertEquals(city.lon, settingsStore.settings.fixedLocation?.longitude ?: 0.0, 0.0)
    }

    @Test
    fun selectingManualModeLoadsCityDataOnlyOnce() = runTest {
        val cityLookup = FakeCityLookup()
        val viewModel = createViewModel(
            readinessProvider = FakeAlarmReadinessProvider(readiness()),
            cityRepository = cityLookup
        )
        advanceUntilIdle()

        viewModel.nextStep()
        viewModel.updateLocationMode(LocationMode.FIXED)
        advanceUntilIdle()
        viewModel.updateLocationMode(LocationMode.FIXED)
        advanceUntilIdle()

        assertEquals(1, cityLookup.ensureCitiesLoadedCalls)
        assertTrue(viewModel.state.value.isCityDataReady)
    }

    @Test
    fun shortCityQueryDoesNotSearchButStillLoadsCityData() = runTest {
        val cityLookup = FakeCityLookup()
        val viewModel = createViewModel(
            readinessProvider = FakeAlarmReadinessProvider(readiness()),
            cityRepository = cityLookup
        )
        advanceUntilIdle()

        viewModel.updateCityQuery("D")
        advanceUntilIdle()

        assertEquals(1, cityLookup.ensureCitiesLoadedCalls)
        assertEquals(0, cityLookup.searchCitiesCalls)
        assertTrue(viewModel.state.value.cityResults.isEmpty())
    }

    @Test
    fun completeFinishesEvenWhenAlertsAreNotReady() = runTest {
        val readinessProvider = FakeAlarmReadinessProvider(
            readiness(exactAlarmReady = false)
        )
        val viewModel = createViewModel(readinessProvider)
        advanceUntilIdle()
        var finished = false

        viewModel.complete { finished = true }
        advanceUntilIdle()

        assertEquals(true, finished)
        assertEquals(true, viewModel.state.value.onboardingComplete)
    }

    @Test
    fun completeFinishesWhenRequiredReadinessIsReady() = runTest {
        val readinessProvider = FakeAlarmReadinessProvider(readiness())
        val viewModel = createViewModel(readinessProvider)
        advanceUntilIdle()
        var finished = false

        viewModel.complete { finished = true }
        advanceUntilIdle()

        assertEquals(true, finished)
        assertEquals(true, viewModel.state.value.onboardingComplete)
    }

    private fun createViewModel(
        readinessProvider: FakeAlarmReadinessProvider,
        settingsStore: FakeSettingsRepository = FakeSettingsRepository(),
        cityRepository: FakeCityLookup = FakeCityLookup(),
        locationService: FakeLocationProvider = FakeLocationProvider()
    ): OnboardingViewModel = OnboardingViewModel(
        settingsStore = settingsStore,
        cityRepository = cityRepository,
        locationService = locationService,
        alarmReadinessProvider = readinessProvider
    )

    private fun sampleCity(): City = City(
        id = 1L,
        name = "Denver",
        asciiName = "Denver",
        countryCode = "US",
        admin1Code = "CO",
        lat = 39.7392,
        lon = -104.9903,
        timezone = "America/Denver",
        population = 700000
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
            blockedNotificationChannelId = if (notificationChannelReady) null else "alarm_channel",
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
        var settings = UserSettings(
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

    private class FakeCityLookup(
        private val nearestCity: City? = null,
        private val searchResults: List<City> = emptyList()
    ) : CityLookup {
        var ensureCitiesLoadedCalls = 0
        var searchCitiesCalls = 0

        override suspend fun ensureCitiesLoaded(
            onProgress: ((com.bfalls.suntimealerts.cities.data.CityImportProgress) -> Unit)?
        ) {
            ensureCitiesLoadedCalls += 1
        }

        override suspend fun searchCities(query: String, limit: Int): List<City> {
            searchCitiesCalls += 1
            return searchResults
        }

        override suspend fun findNearestCity(lat: Double, lon: Double): City? = nearestCity
    }

    private class FakeLocationProvider(
        private val coordinate: Coordinate? = null
    ) : LocationProvider {
        override suspend fun currentCoordinate(): Coordinate? = coordinate
    }
}
