package com.bfalls.suntimealerts.alarm.services

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bfalls.suntimealerts.alarm.data.SettingsRepository
import com.bfalls.suntimealerts.alarm.data.SettingsStore
import com.bfalls.suntimealerts.alarm.debug.DebugAlarmOverride
import com.bfalls.suntimealerts.alarm.debug.DebugAlarmTestOverrides
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.model.UserSettings
import com.bfalls.suntimealerts.utils.hasLocationPermission
import com.bfalls.suntimealerts.utils.hasNotificationPermission

enum class AlarmReadinessIssue {
    LOCATION,
    NOTIFICATIONS,
    NOTIFICATION_CHANNEL,
    EXACT_ALARM,
    FULL_SCREEN_INTENT,
    BOOT_RESCHEDULE
}

enum class AlarmRepairAction {
    REQUEST_LOCATION_PERMISSION,
    SELECT_LOCATION,
    REQUEST_NOTIFICATION_PERMISSION,
    OPEN_NOTIFICATION_SETTINGS,
    OPEN_NOTIFICATION_CHANNEL_SETTINGS,
    REQUEST_EXACT_ALARM_PERMISSION,
    OPEN_FULL_SCREEN_INTENT_SETTINGS
}

data class AlarmReadiness(
    val locationReady: Boolean,
    val notificationsReady: Boolean,
    val notificationChannelReady: Boolean,
    val blockedNotificationChannelId: String?,
    val exactAlarmReady: Boolean,
    val fullScreenIntentReady: Boolean,
    val bootRescheduleReady: Boolean,
    val canDeliverReliableAlerts: Boolean,
    val missingCapabilities: List<AlarmReadinessIssue>,
    val repairActions: List<AlarmRepairAction>
)

data class AlarmReadinessInputs(
    val apiLevel: Int,
    val locationMode: LocationMode,
    val fixedLocationAvailable: Boolean,
    val locationPermissionGranted: Boolean,
    val lastResolvedDeviceLocationAvailable: Boolean,
    val runtimeNotificationPermissionGranted: Boolean,
    val appNotificationsEnabled: Boolean,
    val notificationChannelBlocked: Boolean,
    val blockedNotificationChannelId: String?,
    val exactAlarmPermissionGranted: Boolean,
    val usesFullScreenAlarmUi: Boolean,
    val fullScreenIntentPermissionGranted: Boolean
)

object AlarmReadinessEvaluator {
    fun evaluate(inputs: AlarmReadinessInputs): AlarmReadiness {
        val locationReady = when (inputs.locationMode) {
            LocationMode.FIXED -> inputs.fixedLocationAvailable
            LocationMode.DEVICE -> inputs.locationPermissionGranted &&
                inputs.lastResolvedDeviceLocationAvailable
        }
        val notificationsReady =
            inputs.runtimeNotificationPermissionGranted && inputs.appNotificationsEnabled
        val notificationChannelReady = !inputs.notificationChannelBlocked
        val exactAlarmReady =
            inputs.apiLevel < Build.VERSION_CODES.S || inputs.exactAlarmPermissionGranted
        val fullScreenIntentReady =
            !inputs.usesFullScreenAlarmUi || inputs.fullScreenIntentPermissionGranted
        val bootRescheduleReady = when (inputs.locationMode) {
            LocationMode.FIXED -> inputs.fixedLocationAvailable
            LocationMode.DEVICE -> inputs.lastResolvedDeviceLocationAvailable
        }

        val missingCapabilities = buildList {
            if (!locationReady) add(AlarmReadinessIssue.LOCATION)
            if (!notificationsReady) add(AlarmReadinessIssue.NOTIFICATIONS)
            if (!notificationChannelReady) add(AlarmReadinessIssue.NOTIFICATION_CHANNEL)
            if (!exactAlarmReady) add(AlarmReadinessIssue.EXACT_ALARM)
            if (!bootRescheduleReady) add(AlarmReadinessIssue.BOOT_RESCHEDULE)
        }
        val repairActions = buildList {
            if (!locationReady) {
                add(
                    if (inputs.locationMode == LocationMode.DEVICE) {
                        AlarmRepairAction.REQUEST_LOCATION_PERMISSION
                    } else {
                        AlarmRepairAction.SELECT_LOCATION
                    }
                )
            }
            if (!inputs.runtimeNotificationPermissionGranted) {
                add(AlarmRepairAction.REQUEST_NOTIFICATION_PERMISSION)
            }
            if (inputs.runtimeNotificationPermissionGranted && !inputs.appNotificationsEnabled) {
                add(AlarmRepairAction.OPEN_NOTIFICATION_SETTINGS)
            }
            if (!notificationChannelReady) {
                add(AlarmRepairAction.OPEN_NOTIFICATION_CHANNEL_SETTINGS)
            }
            if (!exactAlarmReady) {
                add(AlarmRepairAction.REQUEST_EXACT_ALARM_PERMISSION)
            }
            if (!fullScreenIntentReady) {
                add(AlarmRepairAction.OPEN_FULL_SCREEN_INTENT_SETTINGS)
            }
        }

        return AlarmReadiness(
            locationReady = locationReady,
            notificationsReady = notificationsReady,
            notificationChannelReady = notificationChannelReady,
            blockedNotificationChannelId = inputs.blockedNotificationChannelId,
            exactAlarmReady = exactAlarmReady,
            fullScreenIntentReady = fullScreenIntentReady,
            bootRescheduleReady = bootRescheduleReady,
            canDeliverReliableAlerts = missingCapabilities.isEmpty(),
            missingCapabilities = missingCapabilities,
            repairActions = repairActions.distinct()
        )
    }
}

class AlarmReadinessService(
    private val context: Context,
    private val settingsStore: SettingsRepository = SettingsStore(context),
    private val usesFullScreenAlarmUi: Boolean = false
) : AlarmReadinessProvider {
    private val debugOverrides = DebugAlarmTestOverrides(context.applicationContext)

    override suspend fun readiness(): AlarmReadiness {
        return AlarmReadinessEvaluator.evaluate(inputs(settingsStore.load()))
    }

    private fun inputs(settings: UserSettings): AlarmReadinessInputs {
        return AlarmReadinessInputs(
            apiLevel = Build.VERSION.SDK_INT,
            locationMode = settings.locationMode,
            fixedLocationAvailable = settings.fixedLocation != null,
            locationPermissionGranted = hasLocationPermission(context),
            lastResolvedDeviceLocationAvailable =
                settings.lastResolvedDeviceLocation != null &&
                    !debugOverrides.isEnabled(DebugAlarmOverride.LOCATION_UNAVAILABLE),
            runtimeNotificationPermissionGranted = hasNotificationPermission(context),
            appNotificationsEnabled =
                NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                    !debugOverrides.isEnabled(DebugAlarmOverride.APP_NOTIFICATIONS_DISABLED),
            notificationChannelBlocked = blockedAlarmNotificationChannel(settings) != null,
            blockedNotificationChannelId = blockedAlarmNotificationChannel(settings),
            exactAlarmPermissionGranted = canScheduleExactAlarms(),
            usesFullScreenAlarmUi = usesFullScreenAlarmUi,
            fullScreenIntentPermissionGranted = canUseFullScreenIntent()
        )
    }

    private fun canScheduleExactAlarms(): Boolean {
        if (debugOverrides.isEnabled(DebugAlarmOverride.EXACT_ALARM_DENIED)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    private fun blockedAlarmNotificationChannel(settings: UserSettings): String? {
        if (debugOverrides.isEnabled(DebugAlarmOverride.CHANNEL_BLOCKED)) {
            return "debug_blocked_channel"
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        settings.alarms
            .filter { it.enabled }
            .forEach { alarm ->
                SunEventReceiver.ensureChannelExists(
                    context = context,
                    alarmId = alarm.id,
                    soundUri = alarm.soundUri,
                    vibrate = alarm.vibrate ?: true
                )
            }
        return settings.alarms
            .filter { it.enabled }
            .map { alarm -> SunEventReceiver.channelIdForAlarm(alarm.id) }
            .firstOrNull { channelId ->
                notificationManager.getNotificationChannel(channelId)?.importance ==
                    NotificationManager.IMPORTANCE_NONE
            }
    }

    private fun canUseFullScreenIntent(): Boolean {
        if (debugOverrides.isEnabled(DebugAlarmOverride.FULL_SCREEN_INTENT_DENIED)) return false
        if (!usesFullScreenAlarmUi) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return notificationManager.canUseFullScreenIntent()
        }

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.USE_FULL_SCREEN_INTENT
        ) == PackageManager.PERMISSION_GRANTED
    }
}

interface AlarmReadinessProvider {
    suspend fun readiness(): AlarmReadiness
}
