package com.bfalls.suntimealerts.alarm.presentation.viewmodel

import com.bfalls.suntimealerts.MainDispatcherRule
import com.bfalls.suntimealerts.alarm.data.LocationProvider
import com.bfalls.suntimealerts.alarm.data.SettingsRepository
import com.bfalls.suntimealerts.alarm.data.SunScheduler
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarmConfig
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.UserSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
            onboardingComplete = true
        )
        val settingsRepo = FakeSettingsRepository(settings)
        val locationProvider = RecordingLocationProvider()
        val scheduler = RecordingScheduler()

        val viewModel = HomeViewModel(locationProvider, settingsRepo, scheduler)

        advanceUntilIdle()
        viewModel.reschedule()
        advanceUntilIdle()

        assertEquals(listOf(fixedCoordinate), scheduler.receivedCoordinates)
        assertEquals(0, locationProvider.requestCount)
    }

    private class FakeSettingsRepository(
        private val settings: UserSettings
    ) : SettingsRepository {
        override suspend fun load(): UserSettings = settings
        override suspend fun save(settings: UserSettings) = Unit
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
    }
}
