package com.bfalls.suntimealerts.alarm.presentation.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.HomeViewModel
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.OnboardingState
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.OnboardingStep
import com.bfalls.suntimealerts.alarm.services.AlarmReadiness
import com.bfalls.suntimealerts.alarm.services.AlarmReadinessIssue
import com.bfalls.suntimealerts.alarm.services.AlarmRepairAction
import com.bfalls.suntimealerts.ui.theme.SuntimeAlertsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class ReadinessFlowScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun onboardingSummarySeparatesSetupCompletionFromAlertsReady() {
        composeTestRule.setContent {
            SuntimeAlertsTheme {
                OnboardingScreen(
                    state = OnboardingState(
                        isLoaded = true,
                        step = OnboardingStep.SUMMARY,
                        onboardingComplete = false,
                        locationMode = LocationMode.FIXED,
                        fixedLatitude = "39.7392",
                        fixedLongitude = "-104.9903",
                        alarmReadiness = degradedReadiness(
                            missingCapabilities = listOf(
                                AlarmReadinessIssue.EXACT_ALARM,
                                AlarmReadinessIssue.NOTIFICATIONS
                            ),
                            repairActions = listOf(
                                AlarmRepairAction.REQUEST_EXACT_ALARM_PERMISSION,
                                AlarmRepairAction.REQUEST_NOTIFICATION_PERMISSION
                            )
                        )
                    ),
                    onLocationModeChanged = {},
                    onRequestLocationPermission = {},
                    onOpenPermissionSettings = {},
                    onOpenNotificationSettings = {},
                    onOpenNotificationChannelSettings = {},
                    onCityQueryChanged = {},
                    onCitySelected = {},
                    notificationsPermissionRequired = true,
                    exactAlarmPermissionRequired = true,
                    onNotificationsContinue = {},
                    onNotificationsSkip = {},
                    onExactAlarmsContinue = {},
                    onExactAlarmsSkip = {},
                    onNext = {},
                    onBack = {},
                    onComplete = {},
                    canAdvance = true
                )
            }
        }

        composeTestRule.onNodeWithText("Setup complete").assertExistsCompat()
        composeTestRule.onNodeWithText("Alerts ready now").assertExistsCompat()
        composeTestRule.onNodeWithText("You can finish setup now and repair later: exact alarms, notifications").assertExistsCompat()
    }

    @Test
    fun onboardingLocationStepShowsManualFallbackWhenDeviceLocationFails() {
        composeTestRule.setContent {
            SuntimeAlertsTheme {
                OnboardingScreen(
                    state = OnboardingState(
                        isLoaded = true,
                        step = OnboardingStep.LOCATION,
                        locationMode = LocationMode.DEVICE,
                        deviceLocationLookupFailed = true,
                        alarmReadiness = degradedReadiness(
                            locationReady = false,
                            bootRescheduleReady = false,
                            missingCapabilities = listOf(
                                AlarmReadinessIssue.LOCATION,
                                AlarmReadinessIssue.BOOT_RESCHEDULE
                            ),
                            repairActions = listOf(AlarmRepairAction.REQUEST_LOCATION_PERMISSION)
                        )
                    ),
                    onLocationModeChanged = {},
                    onRequestLocationPermission = {},
                    onOpenPermissionSettings = {},
                    onOpenNotificationSettings = {},
                    onOpenNotificationChannelSettings = {},
                    onCityQueryChanged = {},
                    onCitySelected = {},
                    notificationsPermissionRequired = false,
                    exactAlarmPermissionRequired = false,
                    onNotificationsContinue = {},
                    onNotificationsSkip = {},
                    onExactAlarmsContinue = {},
                    onExactAlarmsSkip = {},
                    onNext = {},
                    onBack = {},
                    onComplete = {},
                    canAdvance = false
                )
            }
        }

        composeTestRule.onNodeWithText("Allow location or choose Manual.").assertExistsCompat()
        composeTestRule.onNodeWithText("If device location is unavailable, you can choose a city manually instead.").assertExistsCompat()
    }

    @Test
    fun onboardingCardsDoNotRenderActionOrFallbackLabels() {
        composeTestRule.setContent {
            SuntimeAlertsTheme {
                OnboardingScreen(
                    state = OnboardingState(
                        isLoaded = true,
                        step = OnboardingStep.NOTIFICATIONS,
                        alarmReadiness = degradedReadiness(
                            notificationsReady = false,
                            missingCapabilities = listOf(AlarmReadinessIssue.NOTIFICATIONS),
                            repairActions = listOf(AlarmRepairAction.REQUEST_NOTIFICATION_PERMISSION)
                        )
                    ),
                    onLocationModeChanged = {},
                    onRequestLocationPermission = {},
                    onOpenPermissionSettings = {},
                    onOpenNotificationSettings = {},
                    onOpenNotificationChannelSettings = {},
                    onCityQueryChanged = {},
                    onCitySelected = {},
                    notificationsPermissionRequired = true,
                    exactAlarmPermissionRequired = false,
                    onNotificationsContinue = {},
                    onNotificationsSkip = {},
                    onExactAlarmsContinue = {},
                    onExactAlarmsSkip = {},
                    onNext = {},
                    onBack = {},
                    onComplete = {},
                    canAdvance = true
                )
            }
        }

        composeTestRule.onNodeWithText("Action:", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Fallback:", substring = true).assertDoesNotExist()
    }

    @Test
    fun homeBannerExplainsBlockedChannelAndRepairPath() {
        composeTestRule.setContent {
            SuntimeAlertsTheme {
                HomeScreenContent(
                    state = HomeViewModel.State(
                        isLoading = false,
                        sunriseTime = sampleTime(6),
                        sunsetTime = sampleTime(20),
                        alarmReadiness = degradedReadiness(
                            notificationsReady = true,
                            notificationChannelReady = false,
                            blockedNotificationChannelId = "alarm_channel",
                            missingCapabilities = listOf(AlarmReadinessIssue.NOTIFICATION_CHANNEL),
                            repairActions = listOf(AlarmRepairAction.OPEN_NOTIFICATION_CHANNEL_SETTINGS)
                        )
                    ),
                    onAddAlarm = {},
                    onUpdateAlarm = {},
                    onToggleAlarmEnabled = { _, _ -> },
                    onDeleteAlarm = {},
                    onDuplicateAlarm = {},
                    onRestoreAlarm = { _, _ -> },
                    onOpenSettings = {},
                    onOpenNotificationSettings = {},
                    onOpenNotificationChannelSettings = {},
                    onOpenExactAlarmSettings = {},
                    onOpenFullScreenIntentSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Alerts need attention").assertExistsCompat()
        composeTestRule.onNodeWithText("An alarm notification channel is blocked, so some alerts cannot fire.").assertExistsCompat()
        composeTestRule.onNodeWithText("Channel settings").assertExistsCompat()
    }

    @Test
    fun homeBannerExplainsFullScreenFallbackSeparatelyFromReliability() {
        composeTestRule.setContent {
            SuntimeAlertsTheme {
                HomeScreenContent(
                    state = HomeViewModel.State(
                        isLoading = false,
                        sunriseTime = sampleTime(6),
                        sunsetTime = sampleTime(20),
                        alarmReadiness = degradedReadiness(
                            fullScreenIntentReady = false,
                            canDeliverReliableAlerts = true,
                            missingCapabilities = emptyList(),
                            repairActions = listOf(AlarmRepairAction.OPEN_FULL_SCREEN_INTENT_SETTINGS)
                        )
                    ),
                    onAddAlarm = {},
                    onUpdateAlarm = {},
                    onToggleAlarmEnabled = { _, _ -> },
                    onDeleteAlarm = {},
                    onDuplicateAlarm = {},
                    onRestoreAlarm = { _, _ -> },
                    onOpenSettings = {},
                    onOpenNotificationSettings = {},
                    onOpenNotificationChannelSettings = {},
                    onOpenExactAlarmSettings = {},
                    onOpenFullScreenIntentSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Full-screen alarm pop-ups are disabled, so alarms will fall back to a high-priority notification instead of opening over the lock screen.").assertExistsCompat()
        composeTestRule.onNodeWithText("Full-screen alarms").assertExistsCompat()
    }

    private fun degradedReadiness(
        locationReady: Boolean = true,
        notificationsReady: Boolean = true,
        notificationChannelReady: Boolean = true,
        blockedNotificationChannelId: String? = null,
        exactAlarmReady: Boolean = true,
        fullScreenIntentReady: Boolean = true,
        bootRescheduleReady: Boolean = true,
        canDeliverReliableAlerts: Boolean = false,
        missingCapabilities: List<AlarmReadinessIssue>,
        repairActions: List<AlarmRepairAction>
    ): AlarmReadiness = AlarmReadiness(
        locationReady = locationReady,
        notificationsReady = notificationsReady,
        notificationChannelReady = notificationChannelReady,
        blockedNotificationChannelId = blockedNotificationChannelId,
        exactAlarmReady = exactAlarmReady,
        fullScreenIntentReady = fullScreenIntentReady,
        bootRescheduleReady = bootRescheduleReady,
        canDeliverReliableAlerts = canDeliverReliableAlerts,
        missingCapabilities = missingCapabilities,
        repairActions = repairActions
    )

    private fun sampleTime(hour: Int): ZonedDateTime =
        ZonedDateTime.of(2024, 6, 1, hour, 0, 0, 0, ZoneId.of("UTC"))
}

private fun SemanticsNodeInteraction.assertExistsCompat(): SemanticsNodeInteraction =
    apply {
        fetchSemanticsNode()
    }
