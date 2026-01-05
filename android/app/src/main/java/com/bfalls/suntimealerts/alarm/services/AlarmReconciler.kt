package com.bfalls.suntimealerts.alarm.services

import android.app.Application
import android.content.Context
import android.util.Log
import com.bfalls.suntimealerts.alarm.data.LocationProvider
import com.bfalls.suntimealerts.alarm.data.LocationService
import com.bfalls.suntimealerts.alarm.data.SettingsRepository
import com.bfalls.suntimealerts.alarm.data.SettingsStore
import com.bfalls.suntimealerts.alarm.data.SunScheduleService
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import java.time.ZoneId

class AlarmReconciler(
    private val context: Context,
    private val settingsStore: SettingsRepository = SettingsStore(context),
    private val locationProvider: LocationProvider = LocationService(context.applicationContext as Application),
    private val scheduleService: SunScheduleService = SunScheduleService(
        calculator = SunTimesCalculator(),
        settingsStore = settingsStore,
        notificationScheduler = NotificationScheduler(context)
    ),
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    suspend fun reconcile(reason: String? = null) {
        val settings = settingsStore.load()
        val coordinate = resolveCoordinate(settings) ?: Coordinate(0.0, 0.0)
        Log.i(
            "AlarmReconciler",
            "Reconciling alarms${reason?.let { " ($it)" } ?: ""} using coordinate=$coordinate and zone=$zoneId"
        )
        scheduleService.schedule(coordinate, zoneId)
    }

    private suspend fun resolveCoordinate(settings: com.bfalls.suntimealerts.alarm.domain.model.UserSettings): Coordinate? {
        return when (settings.locationMode) {
            LocationMode.FIXED -> settings.fixedLocation
            LocationMode.DEVICE -> locationProvider.currentCoordinate()
        }
    }
}
