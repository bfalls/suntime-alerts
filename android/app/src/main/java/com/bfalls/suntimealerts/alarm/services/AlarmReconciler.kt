package com.bfalls.suntimealerts.alarm.services

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import com.bfalls.suntimealerts.alarm.data.LocationProvider
import com.bfalls.suntimealerts.alarm.data.LocationService
import com.bfalls.suntimealerts.alarm.data.SettingsRepository
import com.bfalls.suntimealerts.alarm.data.SettingsStore
import com.bfalls.suntimealerts.alarm.data.SunScheduleService
import com.bfalls.suntimealerts.alarm.data.SunScheduler
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import java.time.ZoneId

class AlarmReconciler(
    private val context: Context,
    private val settingsStore: SettingsRepository = SettingsStore(context),
    private val locationProvider: LocationProvider = LocationService(context.applicationContext as Application),
    private val scheduleService: SunScheduler = SunScheduleService(
        calculator = SunTimesCalculator(),
        settingsStore = settingsStore,
        notificationScheduler = NotificationScheduler(context)
    ),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val canScheduleExactAlarms: () -> Boolean = {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            true
        } else {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        }
    }
) {

    suspend fun reconcile(reason: String? = null) {
        val settings = settingsStore.load()
        val coordinate = resolveCoordinate(settings)
        if (coordinate == null) {
            warnLog(
                "Skipping alarm reconciliation${reason?.let { " ($it)" } ?: ""} because no usable coordinate is available."
            )
            return
        }
        infoLog(
            "Reconciling alarms${reason?.let { " ($it)" } ?: ""} using coordinate=$coordinate and zone=$zoneId"
        )
        scheduleService.schedule(coordinate, zoneId)
    }

    suspend fun reconcileAfterExactAlarmGrant(reason: String? = null) {
        if (!canScheduleExactAlarms()) {
            warnLog(
                "Skipping alarm reconciliation${reason?.let { " ($it)" } ?: ""} because exact alarm access is still denied."
            )
            return
        }
        reconcile(reason)
    }

    private suspend fun resolveCoordinate(settings: com.bfalls.suntimealerts.alarm.domain.model.UserSettings): Coordinate? {
        return when (settings.locationMode) {
            LocationMode.FIXED -> settings.fixedLocation
            LocationMode.DEVICE -> locationProvider.currentCoordinate()
                ?: settings.lastResolvedDeviceLocation
        }
    }

    private fun infoLog(message: String) {
        runCatching {
            Log.i("AlarmReconciler", message)
        }
    }

    private fun warnLog(message: String) {
        runCatching {
            Log.w("AlarmReconciler", message)
        }
    }
}
