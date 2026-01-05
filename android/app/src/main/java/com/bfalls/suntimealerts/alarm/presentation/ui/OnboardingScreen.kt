package com.bfalls.suntimealerts.alarm.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.OnboardingState
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.OnboardingStep
import com.bfalls.suntimealerts.cities.data.City

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onLocationModeChanged: (LocationMode) -> Unit,
    onOpenPermissionSettings: () -> Unit,
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
                Text(
                    text = when (state.step) {
                        OnboardingStep.WELCOME -> "Welcome"
                        OnboardingStep.LOCATION -> "Choose location"
                        OnboardingStep.NOTIFICATIONS -> "Notifications"
                        OnboardingStep.EXACT_ALARMS -> "Alarms & reminders"
                        OnboardingStep.SUMMARY -> "Summary"
                    },
                    fontWeight = FontWeight.SemiBold
                )

                when (state.step) {
                    OnboardingStep.WELCOME -> WelcomeStep()
                    OnboardingStep.LOCATION -> LocationPickerPane(
                        state = LocationPickerUiState(
                            locationMode = state.locationMode,
                            locationPermissionPermanentlyDenied = state.locationPermissionPermanentlyDenied,
                            locationPermissionMissing = state.locationPermissionPermanentlyDenied,
                            deviceNearestCityLabel = state.deviceNearestCityLabel,
                            fixedLatitude = state.fixedLatitude,
                            fixedLongitude = state.fixedLongitude,
                            cityQuery = state.cityQuery,
                            cityResults = state.cityResults,
                            selectedCity = state.selectedCity
                        ),
                        onLocationModeChanged = onLocationModeChanged,
                        onOpenPermissionSettings = onOpenPermissionSettings,
                        onCityQueryChanged = onCityQueryChanged,
                        onCitySelected = onCitySelected
                    )
                    OnboardingStep.NOTIFICATIONS -> NotificationsStep(
                        permissionRequired = notificationsPermissionRequired
                    )
                    OnboardingStep.EXACT_ALARMS -> ExactAlarmsStep(
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
        Text("We will ask for location, notification access, and your preferred alarms.")
    }
}

@Composable
private fun NotificationsStep(permissionRequired: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Suntime Alerts can send sunrise and sunset alerts. Android requires permission to post notifications. You can enable this now or skip and enable later in Settings."
        )
        if (!permissionRequired) {
            Text("Not required on this Android version or already allowed.")
        }
    }
}

@Composable
private fun ExactAlarmsStep(permissionRequired: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "To trigger alerts at the right time, Suntime Alerts schedules alarms. We'll open the Alarms & reminders settings so you can allow exact alarms. You can skip and enable later."
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
            state.deviceNearestCityLabel?.let { "Device (near $it)" } ?: "Device"
        } else {
            when {
                state.selectedCity != null -> "${state.selectedCity.name}, ${state.selectedCity.admin1Code}, ${state.selectedCity.countryCode}"
                state.fixedLatitude.isNotBlank() && state.fixedLongitude.isNotBlank() -> "Lat ${state.fixedLatitude}, Lon ${state.fixedLongitude}"
                else -> "Manual coordinates"
            }
        }
        Text("Location: $locationSummary")
    }
}
