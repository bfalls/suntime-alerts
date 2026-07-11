package com.bfalls.suntimealerts.alarm.services

import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmReadinessEvaluatorTest {
    @Test
    fun fixedLocationWithGrantedCapabilitiesIsReady() {
        val readiness = AlarmReadinessEvaluator.evaluate(readyInputs())

        assertTrue(readiness.locationReady)
        assertTrue(readiness.notificationsReady)
        assertTrue(readiness.notificationChannelReady)
        assertEquals(null, readiness.blockedNotificationChannelId)
        assertTrue(readiness.exactAlarmReady)
        assertTrue(readiness.fullScreenIntentReady)
        assertTrue(readiness.bootRescheduleReady)
        assertTrue(readiness.canDeliverReliableAlerts)
        assertTrue(readiness.missingCapabilities.isEmpty())
        assertTrue(readiness.repairActions.isEmpty())
    }

    @Test
    fun deviceLocationRequiresLocationPermission() {
        val readiness = AlarmReadinessEvaluator.evaluate(
            readyInputs(
                locationMode = LocationMode.DEVICE,
                fixedLocationAvailable = false,
                locationPermissionGranted = false,
                lastResolvedDeviceLocationAvailable = false
            )
        )

        assertFalse(readiness.locationReady)
        assertFalse(readiness.bootRescheduleReady)
        assertFalse(readiness.canDeliverReliableAlerts)
        assertEquals(
            listOf(
                AlarmReadinessIssue.LOCATION,
                AlarmReadinessIssue.BOOT_RESCHEDULE
            ),
            readiness.missingCapabilities
        )
        assertEquals(
            listOf(AlarmRepairAction.REQUEST_LOCATION_PERMISSION),
            readiness.repairActions
        )
    }

    @Test
    fun deviceLocationRequiresPersistedCoordinateForBootReadiness() {
        val readiness = AlarmReadinessEvaluator.evaluate(
            readyInputs(
                locationMode = LocationMode.DEVICE,
                fixedLocationAvailable = false,
                locationPermissionGranted = true,
                lastResolvedDeviceLocationAvailable = false
            )
        )

        assertFalse(readiness.locationReady)
        assertFalse(readiness.bootRescheduleReady)
        assertFalse(readiness.canDeliverReliableAlerts)
        assertEquals(
            listOf(
                AlarmReadinessIssue.LOCATION,
                AlarmReadinessIssue.BOOT_RESCHEDULE
            ),
            readiness.missingCapabilities
        )
    }

    @Test
    fun fixedLocationModeRequiresCoordinate() {
        val readiness = AlarmReadinessEvaluator.evaluate(
            readyInputs(fixedLocationAvailable = false)
        )

        assertFalse(readiness.locationReady)
        assertFalse(readiness.bootRescheduleReady)
        assertEquals(
            listOf(AlarmRepairAction.SELECT_LOCATION),
            readiness.repairActions
        )
    }

    @Test
    fun notificationRuntimePermissionMissingRequiresNotificationRequest() {
        val readiness = AlarmReadinessEvaluator.evaluate(
            readyInputs(runtimeNotificationPermissionGranted = false)
        )

        assertFalse(readiness.notificationsReady)
        assertFalse(readiness.canDeliverReliableAlerts)
        assertEquals(
            listOf(AlarmReadinessIssue.NOTIFICATIONS),
            readiness.missingCapabilities
        )
        assertEquals(
            listOf(AlarmRepairAction.REQUEST_NOTIFICATION_PERMISSION),
            readiness.repairActions
        )
    }

    @Test
    fun appNotificationsDisabledRequiresSettingsRepair() {
        val readiness = AlarmReadinessEvaluator.evaluate(
            readyInputs(appNotificationsEnabled = false)
        )

        assertFalse(readiness.notificationsReady)
        assertEquals(
            listOf(AlarmRepairAction.OPEN_NOTIFICATION_SETTINGS),
            readiness.repairActions
        )
    }

    @Test
    fun blockedNotificationChannelRequiresChannelSettingsRepair() {
        val readiness = AlarmReadinessEvaluator.evaluate(
            readyInputs(notificationChannelBlocked = true)
        )

        assertFalse(readiness.notificationChannelReady)
        assertEquals("alarm_channel", readiness.blockedNotificationChannelId)
        assertEquals(
            listOf(AlarmReadinessIssue.NOTIFICATION_CHANNEL),
            readiness.missingCapabilities
        )
        assertEquals(
            listOf(AlarmRepairAction.OPEN_NOTIFICATION_CHANNEL_SETTINGS),
            readiness.repairActions
        )
    }

    @Test
    fun exactAlarmDeniedOnAndroidTwelveRequiresRepair() {
        val readiness = AlarmReadinessEvaluator.evaluate(
            readyInputs(
                apiLevel = 31,
                exactAlarmPermissionGranted = false
            )
        )

        assertFalse(readiness.exactAlarmReady)
        assertEquals(
            listOf(AlarmReadinessIssue.EXACT_ALARM),
            readiness.missingCapabilities
        )
        assertEquals(
            listOf(AlarmRepairAction.REQUEST_EXACT_ALARM_PERMISSION),
            readiness.repairActions
        )
    }

    @Test
    fun exactAlarmGrantedOnAndroidTwelveIsReady() {
        val readiness = AlarmReadinessEvaluator.evaluate(
            readyInputs(
                apiLevel = 31,
                exactAlarmPermissionGranted = true
            )
        )

        assertTrue(readiness.exactAlarmReady)
        assertTrue(readiness.canDeliverReliableAlerts)
    }

    @Test
    fun exactAlarmPermissionIsNotRequiredBeforeAndroidTwelve() {
        val readiness = AlarmReadinessEvaluator.evaluate(
            readyInputs(
                apiLevel = 30,
                exactAlarmPermissionGranted = false
            )
        )

        assertTrue(readiness.exactAlarmReady)
        assertTrue(readiness.canDeliverReliableAlerts)
    }

    @Test
    fun fullScreenIntentIsIgnoredWhenFullScreenAlarmUiIsDisabled() {
        val readiness = AlarmReadinessEvaluator.evaluate(
            readyInputs(
                usesFullScreenAlarmUi = false,
                fullScreenIntentPermissionGranted = false
            )
        )

        assertTrue(readiness.fullScreenIntentReady)
        assertTrue(readiness.canDeliverReliableAlerts)
    }

    @Test
    fun fullScreenIntentRequiresRepairWhenFullScreenAlarmUiIsEnabled() {
        val readiness = AlarmReadinessEvaluator.evaluate(
            readyInputs(
                usesFullScreenAlarmUi = true,
                fullScreenIntentPermissionGranted = false
            )
        )

        assertFalse(readiness.fullScreenIntentReady)
        assertEquals(
            listOf(AlarmReadinessIssue.FULL_SCREEN_INTENT),
            readiness.missingCapabilities
        )
        assertEquals(
            listOf(AlarmRepairAction.OPEN_FULL_SCREEN_INTENT_SETTINGS),
            readiness.repairActions
        )
    }

    private fun readyInputs(
        apiLevel: Int = 35,
        locationMode: LocationMode = LocationMode.FIXED,
        fixedLocationAvailable: Boolean = true,
        locationPermissionGranted: Boolean = true,
        lastResolvedDeviceLocationAvailable: Boolean = true,
        runtimeNotificationPermissionGranted: Boolean = true,
        appNotificationsEnabled: Boolean = true,
        notificationChannelBlocked: Boolean = false,
        blockedNotificationChannelId: String? = if (notificationChannelBlocked) "alarm_channel" else null,
        exactAlarmPermissionGranted: Boolean = true,
        usesFullScreenAlarmUi: Boolean = false,
        fullScreenIntentPermissionGranted: Boolean = true
    ): AlarmReadinessInputs = AlarmReadinessInputs(
        apiLevel = apiLevel,
        locationMode = locationMode,
        fixedLocationAvailable = fixedLocationAvailable,
        locationPermissionGranted = locationPermissionGranted,
        lastResolvedDeviceLocationAvailable = lastResolvedDeviceLocationAvailable,
        runtimeNotificationPermissionGranted = runtimeNotificationPermissionGranted,
        appNotificationsEnabled = appNotificationsEnabled,
        notificationChannelBlocked = notificationChannelBlocked,
        blockedNotificationChannelId = blockedNotificationChannelId,
        exactAlarmPermissionGranted = exactAlarmPermissionGranted,
        usesFullScreenAlarmUi = usesFullScreenAlarmUi,
        fullScreenIntentPermissionGranted = fullScreenIntentPermissionGranted
    )
}
