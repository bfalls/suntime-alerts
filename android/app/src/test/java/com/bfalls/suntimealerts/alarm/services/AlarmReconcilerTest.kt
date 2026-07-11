package com.bfalls.suntimealerts.alarm.services

import android.content.ContextWrapper
import com.bfalls.suntimealerts.alarm.data.LocationProvider
import com.bfalls.suntimealerts.alarm.data.SettingsRepository
import com.bfalls.suntimealerts.alarm.data.SunScheduler
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarmConfig
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.UserSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmReconcilerTest {
    @Test
    fun doesNotScheduleWhenNoUsableCoordinateExists() = runTest {
        val settingsRepository = FakeSettingsRepository(
            settings = baseSettings(
                locationMode = LocationMode.DEVICE,
                fixedLocation = null,
                lastResolvedDeviceLocation = null
            )
        )
        val scheduler = RecordingScheduler()
        val reconciler = AlarmReconciler(
            context = ContextWrapper(null),
            settingsStore = settingsRepository,
            locationProvider = FakeLocationProvider(null),
            scheduleService = scheduler,
            zoneId = ZoneId.of("UTC")
        )

        reconciler.reconcile("test")

        assertNull(scheduler.coordinate)
    }

    @Test
    fun usesLastResolvedDeviceCoordinateWhenLiveLookupFails() = runTest {
        val persistedCoordinate = Coordinate(39.7392, -104.9903)
        val settingsRepository = FakeSettingsRepository(
            settings = baseSettings(
                locationMode = LocationMode.DEVICE,
                fixedLocation = null,
                lastResolvedDeviceLocation = persistedCoordinate
            )
        )
        val scheduler = RecordingScheduler()
        val reconciler = AlarmReconciler(
            context = ContextWrapper(null),
            settingsStore = settingsRepository,
            locationProvider = FakeLocationProvider(null),
            scheduleService = scheduler,
            zoneId = ZoneId.of("UTC")
        )

        reconciler.reconcile("test")

        assertEquals(persistedCoordinate, scheduler.coordinate)
    }

    @Test
    fun exactAlarmGrantReconcileSkipsWhenAccessIsStillDenied() = runTest {
        val scheduler = RecordingScheduler()
        val reconciler = AlarmReconciler(
            context = ContextWrapper(null),
            settingsStore = FakeSettingsRepository(
                settings = baseSettings(
                    locationMode = LocationMode.FIXED,
                    fixedLocation = Coordinate(39.7392, -104.9903),
                    lastResolvedDeviceLocation = null
                )
            ),
            locationProvider = FakeLocationProvider(null),
            scheduleService = scheduler,
            zoneId = ZoneId.of("UTC"),
            canScheduleExactAlarms = { false }
        )

        reconciler.reconcileAfterExactAlarmGrant("android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")

        assertNull(scheduler.coordinate)
    }

    @Test
    fun exactAlarmGrantReconcileSchedulesWhenAccessIsGranted() = runTest {
        val fixedCoordinate = Coordinate(39.7392, -104.9903)
        val scheduler = RecordingScheduler()
        val reconciler = AlarmReconciler(
            context = ContextWrapper(null),
            settingsStore = FakeSettingsRepository(
                settings = baseSettings(
                    locationMode = LocationMode.FIXED,
                    fixedLocation = fixedCoordinate,
                    lastResolvedDeviceLocation = null
                )
            ),
            locationProvider = FakeLocationProvider(null),
            scheduleService = scheduler,
            zoneId = ZoneId.of("UTC"),
            canScheduleExactAlarms = { true }
        )

        reconciler.reconcileAfterExactAlarmGrant("android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")

        assertEquals(fixedCoordinate, scheduler.coordinate)
    }

    private fun baseSettings(
        locationMode: LocationMode,
        fixedLocation: Coordinate?,
        lastResolvedDeviceLocation: Coordinate?
    ): UserSettings = UserSettings(
        locationMode = locationMode,
        fixedLocation = fixedLocation,
        lastResolvedDeviceLocation = lastResolvedDeviceLocation,
        sunriseConfig = SunAlarmConfig(false, SunEventType.SUNRISE, 0),
        sunsetConfig = SunAlarmConfig(false, SunEventType.SUNSET, 0),
        timeFormat24h = true,
        onboardingComplete = true,
        alarms = emptyList()
    )

    private class FakeSettingsRepository(
        private val settings: UserSettings
    ) : SettingsRepository {
        override suspend fun load(): UserSettings = settings
        override suspend fun save(settings: UserSettings) = Unit
        override suspend fun loadAlarms(): List<SunAlarm> = settings.alarms
        override suspend fun saveAlarms(alarms: List<SunAlarm>) = Unit
    }

    private class FakeLocationProvider(
        private val coordinate: Coordinate?
    ) : LocationProvider {
        override suspend fun currentCoordinate(): Coordinate? = coordinate
    }

    private class RecordingScheduler : SunScheduler {
        var coordinate: Coordinate? = null

        override suspend fun schedule(coordinate: Coordinate, zoneId: ZoneId) {
            this.coordinate = coordinate
        }

        override suspend fun cancel(alarm: SunAlarm, zoneId: ZoneId) = Unit
    }
}
