package com.bfalls.suntimealerts.alarm.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.OnboardingState
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.OnboardingStep

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onLocationModeChanged: (LocationMode) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    onSunriseEnabledChanged: (Boolean) -> Unit,
    onSunsetEnabledChanged: (Boolean) -> Unit,
    onSunriseOffsetChanged: (Int) -> Unit,
    onSunsetOffsetChanged: (Int) -> Unit,
    onFixedLatitudeChanged: (String) -> Unit,
    onFixedLongitudeChanged: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    canAdvance: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = when (state.step) {
                    OnboardingStep.WELCOME -> "Welcome"
                    OnboardingStep.LOCATION -> "Choose location"
                    OnboardingStep.NOTIFICATIONS -> "Notifications"
                    OnboardingStep.ALARMS -> "Initial alarms"
                    OnboardingStep.SUMMARY -> "Summary"
                },
                fontWeight = FontWeight.SemiBold
            )

            when (state.step) {
                OnboardingStep.WELCOME -> WelcomeStep()
                OnboardingStep.LOCATION -> LocationStep(state, onLocationModeChanged, onFixedLatitudeChanged, onFixedLongitudeChanged)
                OnboardingStep.NOTIFICATIONS -> NotificationStep(state.notificationsEnabled, onNotificationsChanged)
                OnboardingStep.ALARMS -> AlarmStep(state, onSunriseEnabledChanged, onSunsetEnabledChanged, onSunriseOffsetChanged, onSunsetOffsetChanged)
                OnboardingStep.SUMMARY -> SummaryStep(state)
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (state.step != OnboardingStep.WELCOME) {
                    OutlinedButton(onClick = onBack) { Text("Back") }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
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

@Composable
private fun WelcomeStep() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Suntime Alerts keeps you aligned with sunrise and sunset.")
        Text("We will ask for location, notification access, and your preferred alarms.")
    }
}

@Composable
private fun LocationStep(
    state: OnboardingState,
    onLocationModeChanged: (LocationMode) -> Unit,
    onFixedLatitudeChanged: (String) -> Unit,
    onFixedLongitudeChanged: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("How should we find your location?")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onLocationModeChanged(LocationMode.DEVICE) }) {
                Text(if (state.locationMode == LocationMode.DEVICE) "• Device" else "Device")
            }
            TextButton(onClick = { onLocationModeChanged(LocationMode.FIXED) }) {
                Text(if (state.locationMode == LocationMode.FIXED) "• Manual" else "Manual")
            }
        }
        if (state.locationMode == LocationMode.FIXED) {
            OutlinedTextField(
                value = state.fixedLatitude,
                onValueChange = onFixedLatitudeChanged,
                label = { Text("Latitude") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.fixedLongitude,
                onValueChange = onFixedLongitudeChanged,
                label = { Text("Longitude") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun NotificationStep(enabled: Boolean, onChanged: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Enable alerts")
            Text("Turn on notifications to receive sunrise and sunset reminders.")
        }
        Switch(checked = enabled, onCheckedChange = onChanged)
    }
}

@Composable
private fun AlarmStep(
    state: OnboardingState,
    onSunriseEnabledChanged: (Boolean) -> Unit,
    onSunsetEnabledChanged: (Boolean) -> Unit,
    onSunriseOffsetChanged: (Int) -> Unit,
    onSunsetOffsetChanged: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Sunrise alarm")
                Text("Offset: ${state.sunriseOffsetMinutes} min")
            }
            Switch(checked = state.sunriseEnabled, onCheckedChange = onSunriseEnabledChanged)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onSunriseOffsetChanged(state.sunriseOffsetMinutes - 5) }) { Text("-5m") }
            TextButton(onClick = { onSunriseOffsetChanged(state.sunriseOffsetMinutes + 5) }) { Text("+5m") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Sunset alarm")
                Text("Offset: ${state.sunsetOffsetMinutes} min")
            }
            Switch(checked = state.sunsetEnabled, onCheckedChange = onSunsetEnabledChanged)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onSunsetOffsetChanged(state.sunsetOffsetMinutes - 5) }) { Text("-5m") }
            TextButton(onClick = { onSunsetOffsetChanged(state.sunsetOffsetMinutes + 5) }) { Text("+5m") }
        }
    }
}

@Composable
private fun SummaryStep(state: OnboardingState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Location: ${if (state.locationMode == LocationMode.DEVICE) "Device" else "Manual"}")
        Text("Sunrise alarm: ${if (state.sunriseEnabled) "On" else "Off"} @ ${state.sunriseOffsetMinutes} min")
        Text("Sunset alarm: ${if (state.sunsetEnabled) "On" else "Off"} @ ${state.sunsetOffsetMinutes} min")
    }
}
