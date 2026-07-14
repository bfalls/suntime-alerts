package com.bfalls.suntimealerts.alarm.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.services.AlarmReadinessIssue
import com.bfalls.suntimealerts.alarm.services.AlarmRepairAction
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.OnboardingState
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.OnboardingStep
import com.bfalls.suntimealerts.cities.data.City

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onLocationModeChanged: (LocationMode) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenNotificationChannelSettings: () -> Unit,
    onCityQueryChanged: (String) -> Unit,
    onCitySelected: (City) -> Unit,
    notificationsPermissionRequired: Boolean,
    exactAlarmPermissionRequired: Boolean,
    onNotificationsContinue: () -> Unit,
    onNotificationsSkip: () -> Unit,
    onExactAlarmsContinue: () -> Unit,
    onExactAlarmsSkip: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    canAdvance: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                Surface {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.step != OnboardingStep.WELCOME) {
                            OutlinedButton(onClick = onBack) { Text("Back") }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            when (state.step) {
                                OnboardingStep.NOTIFICATIONS -> {
                                    OutlinedButton(onClick = onNotificationsSkip) {
                                        Text("Skip")
                                    }
                                    Button(onClick = onNotificationsContinue) {
                                        Text("Continue")
                                    }
                                }
                                OnboardingStep.EXACT_ALARMS -> {
                                    OutlinedButton(onClick = onExactAlarmsSkip) {
                                        Text("Skip")
                                    }
                                    Button(onClick = onExactAlarmsContinue) {
                                        Text("Continue")
                                    }
                                }
                                else -> {
                                    Button(
                                        onClick = { if (state.step == OnboardingStep.SUMMARY) onComplete() else onNext() },
                                        enabled = canAdvance
                                    ) {
                                        Text(if (state.step == OnboardingStep.SUMMARY) "Save & Start" else "Next")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                when (state.step) {
                    OnboardingStep.WELCOME -> OnboardingBanner(
                        title = "Welcome",
                        subtitle = "Setup"
                    )
                    OnboardingStep.LOCATION -> OnboardingBanner(
                        title = "Choose location",
                        subtitle = "Setup"
                    )
                    OnboardingStep.NOTIFICATIONS -> OnboardingBanner(
                        title = "Notifications",
                        subtitle = "Setup"
                    )
                    OnboardingStep.EXACT_ALARMS -> OnboardingBanner(
                        title = "Alarms & reminders",
                        subtitle = "Setup"
                    )
                    OnboardingStep.SUMMARY -> OnboardingBanner(
                        title = "Summary",
                        subtitle = "Setup"
                    )
                }

                when (state.step) {
                    OnboardingStep.WELCOME -> WelcomeStep()
                    OnboardingStep.LOCATION -> LocationStep(
                        state = state,
                        onLocationModeChanged = onLocationModeChanged,
                        onRequestLocationPermission = onRequestLocationPermission,
                        onOpenPermissionSettings = onOpenPermissionSettings,
                        onCityQueryChanged = onCityQueryChanged,
                        onCitySelected = onCitySelected
                    )
                    OnboardingStep.NOTIFICATIONS -> NotificationsStep(
                        state = state,
                        permissionRequired = notificationsPermissionRequired,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onOpenNotificationChannelSettings = onOpenNotificationChannelSettings
                    )
                    OnboardingStep.EXACT_ALARMS -> ExactAlarmsStep(
                        state = state,
                        permissionRequired = exactAlarmPermissionRequired
                    )
                    OnboardingStep.SUMMARY -> SummaryStep(state)
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Suntime Alerts keeps you aligned with sunrise and sunset.")
        Text("Let's setup the app with location, notifications, and exact alarms permissions to get you started.")
    }
}

@Composable
private fun OnboardingBanner(
    title: String,
    subtitle: String
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0F2740),
                        Color(0xFF2D5B88),
                        Color(0xFF89BDEA)
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Suntime Alerts",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.94f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun LocationStep(
    state: OnboardingState,
    onLocationModeChanged: (LocationMode) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onCityQueryChanged: (String) -> Unit,
    onCitySelected: (City) -> Unit
) {
    val readiness = state.alarmReadiness
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CapabilityCard(
            title = "Location",
            ready = readiness?.locationReady == true && canAdvanceLocation(state),
            reason = "Suntime Alerts needs a coordinate to calculate sunrise and sunset times.",
            action = when {
                state.locationMode == LocationMode.DEVICE &&
                    readiness?.repairActions?.contains(AlarmRepairAction.REQUEST_LOCATION_PERMISSION) == true ->
                    "Allow location or choose Manual."
                state.locationMode == LocationMode.FIXED -> "Search for and select a city."
                else -> "Confirm the device location."
            },
            fallback = "If device location is unavailable, you can choose a city manually instead."
        )
        LocationPickerPane(
            state = LocationPickerUiState(
                locationMode = state.locationMode,
                locationPermissionPermanentlyDenied = state.locationPermissionPermanentlyDenied,
                locationPermissionMissing =
                    state.locationMode == LocationMode.DEVICE &&
                        state.deviceNearestCityLabel == null &&
                        readiness?.locationReady != true &&
                        !state.locationPermissionPermanentlyDenied,
                deviceLocationLookupFailed = state.deviceLocationLookupFailed,
                isResolvingDeviceLocation = state.isResolvingDeviceLocation,
                deviceNearestCityLabel = state.deviceNearestCityLabel,
                fixedLatitude = state.fixedLatitude,
                fixedLongitude = state.fixedLongitude,
                cityQuery = state.cityQuery,
                cityResults = state.cityResults,
                selectedCity = state.selectedCity,
                isCityDataLoading = state.isCityDataLoading,
                isCityDataReady = state.isCityDataReady,
                cityDataLoadProgress = state.cityDataLoadProgress,
                cityDataLoadCurrent = state.cityDataLoadCurrent,
                cityDataLoadTotal = state.cityDataLoadTotal
            ),
            onLocationModeChanged = onLocationModeChanged,
            onRequestLocationPermission = onRequestLocationPermission,
            onOpenPermissionSettings = onOpenPermissionSettings,
            onCityQueryChanged = onCityQueryChanged,
            onCitySelected = onCitySelected
        )
    }
}

@Composable
private fun NotificationsStep(
    state: OnboardingState,
    permissionRequired: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onOpenNotificationChannelSettings: () -> Unit
) {
    val readiness = state.alarmReadiness
    val action = when {
        readiness?.repairActions?.contains(AlarmRepairAction.REQUEST_NOTIFICATION_PERMISSION) == true ->
            "Tap Continue to request notification permission."
        readiness?.repairActions?.contains(AlarmRepairAction.OPEN_NOTIFICATION_SETTINGS) == true ->
            "Tap Continue to open app notification settings."
        readiness?.repairActions?.contains(AlarmRepairAction.OPEN_NOTIFICATION_CHANNEL_SETTINGS) == true ->
            "Re-enable the Suntime Alerts notification channel in system settings."
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityCard(
            title = "Notifications",
            ready = readiness?.notificationsReady == true &&
                readiness.notificationChannelReady,
            reason = "Alerts can only ring if Android allows this app and its alarm channels to notify you.",
            action = action,
            fallback = "You can skip this for now, but alerts will not be reliable until notifications are enabled."
        )
        Text(
            "Suntime Alerts can send sunrise and sunset alerts. Android requires permission to post notifications. You can enable this now or skip and enable later in Settings."
        )
        if (readiness?.repairActions?.contains(AlarmRepairAction.OPEN_NOTIFICATION_SETTINGS) == true) {
            OutlinedButton(onClick = onOpenNotificationSettings) {
                Text("Open notification settings")
            }
        }
        if (readiness?.repairActions?.contains(AlarmRepairAction.OPEN_NOTIFICATION_CHANNEL_SETTINGS) == true) {
            OutlinedButton(onClick = onOpenNotificationChannelSettings) {
                Text("Open channel settings")
            }
        }
        if (!permissionRequired) {
            Text("Not required on this Android version or already allowed.")
        }
    }
}

@Composable
private fun ExactAlarmsStep(
    state: OnboardingState,
    permissionRequired: Boolean
) {
    val readiness = state.alarmReadiness
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityCard(
            title = "Exact alarms",
            ready = readiness?.exactAlarmReady == true,
            reason = "Sunrise and sunset alerts need exact alarm access to fire at the intended time.",
            action = if (permissionRequired) {
                "Tap Continue to open Alarms & reminders settings."
            } else {
                null
            },
            fallback = "You can skip this for now, but alerts will not be scheduled reliably until exact alarms are allowed."
        )
        Text(
            "To trigger alerts at the right time, Suntime Alerts needs exact alarm access. We'll open the Alarms & reminders settings so you can allow it. If exact alarms stay off, the app will not treat alerts as reliably scheduled."
        )
        if (!permissionRequired) {
            Text("Not required on this Android version or already allowed.")
        }
    }
}

@Composable
private fun SummaryStep(state: OnboardingState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val locationSummary = if (state.locationMode == LocationMode.DEVICE) {
            state.deviceNearestCityLabel?.let { "Device ($it)" } ?: "Device"
        } else {
            when {
                state.selectedCity != null -> "${state.selectedCity.name}, ${state.selectedCity.admin1Code}, ${state.selectedCity.countryCode}"
                state.fixedLatitude.isNotBlank() && state.fixedLongitude.isNotBlank() -> "Lat ${state.fixedLatitude}, Lon ${state.fixedLongitude}"
                else -> "Manual coordinates"
            }
        }
        Text("Location: $locationSummary")
        ReadinessSummary(state)
    }
}

@Composable
private fun ReadinessSummary(state: OnboardingState) {
    val readiness = state.alarmReadiness
    if (readiness == null) {
        Text("Checking alarm readiness...")
        return
    }

    CapabilityCard(
        title = "Setup complete",
        ready = true,
        reason = "Finishing onboarding saves your first-run choices. Alert readiness can change later and is repaired from Home or Settings.",
        action = null,
        fallback = null
    )
    CapabilityCard(
        title = "Alerts ready now",
        ready = readiness.canDeliverReliableAlerts,
        reason = "This reflects whether sunrise and sunset alerts can fire reliably right now.",
        action = if (readiness.canDeliverReliableAlerts) {
            "Sunrise and sunset alerts can fire reliably now."
        } else {
            "You can finish setup now and repair later: ${readiness.missingCapabilities.joinToString { it.label() }}"
        },
        fallback = null
    )
}

@Composable
private fun CapabilityCard(
    title: String,
    ready: Boolean,
    reason: String,
    action: String?,
    fallback: String?
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (ready) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    if (ready) "Ready" else "Needs attention",
                    color = if (ready) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(reason)
            val guidance = listOfNotNull(action, fallback).joinToString(" ")
            if (guidance.isNotBlank()) {
                Text(guidance)
            }
        }
    }
}

private fun canAdvanceLocation(state: OnboardingState): Boolean {
    return if (state.locationMode == LocationMode.FIXED) {
        state.selectedCity != null || (
            state.fixedLatitude.toDoubleOrNull() != null &&
                state.fixedLongitude.toDoubleOrNull() != null
            )
    } else {
        state.deviceNearestCityLabel != null
    }
}

private fun AlarmReadinessIssue.label(): String = when (this) {
    AlarmReadinessIssue.LOCATION -> "location"
    AlarmReadinessIssue.NOTIFICATIONS -> "notifications"
    AlarmReadinessIssue.NOTIFICATION_CHANNEL -> "notification channel"
    AlarmReadinessIssue.EXACT_ALARM -> "exact alarms"
    AlarmReadinessIssue.FULL_SCREEN_INTENT -> "full-screen alarms"
    AlarmReadinessIssue.BOOT_RESCHEDULE -> "boot rescheduling"
}
