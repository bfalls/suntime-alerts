package com.bfalls.suntimealerts.alarm.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.bfalls.suntimealerts.cities.data.City

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onLocationModeChanged: (LocationMode) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    onSunriseEnabledChanged: (Boolean) -> Unit,
    onSunsetEnabledChanged: (Boolean) -> Unit,
    onSunriseOffsetChanged: (Int) -> Unit,
    onSunsetOffsetChanged: (Int) -> Unit,
    onCityQueryChanged: (String) -> Unit,
    onCitySelected: (City) -> Unit,
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
                        Button(
                            onClick = { if (state.step == OnboardingStep.SUMMARY) onComplete() else onNext() },
                            enabled = canAdvance
                        ) {
                            Text(if (state.step == OnboardingStep.SUMMARY) "Save & Start" else "Next")
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
                        OnboardingStep.ALARMS -> "Initial alarms"
                        OnboardingStep.SUMMARY -> "Summary"
                    },
                    fontWeight = FontWeight.SemiBold
                )

                when (state.step) {
                    OnboardingStep.WELCOME -> WelcomeStep()
                    OnboardingStep.LOCATION -> LocationStep(
                        state,
                        onLocationModeChanged,
                        onCityQueryChanged,
                        onCitySelected
                    )
                    OnboardingStep.NOTIFICATIONS -> NotificationStep(state.notificationsEnabled, onNotificationsChanged)
                    OnboardingStep.ALARMS -> AlarmStep(state, onSunriseEnabledChanged, onSunsetEnabledChanged, onSunriseOffsetChanged, onSunsetOffsetChanged)
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
private fun LocationStep(
    state: OnboardingState,
    onLocationModeChanged: (LocationMode) -> Unit,
    onCityQueryChanged: (String) -> Unit,
    onCitySelected: (City) -> Unit
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
                value = state.cityQuery,
                onValueChange = onCityQueryChanged,
                label = { Text("City") },
                placeholder = { Text("Start typing a city name") },
                modifier = Modifier.fillMaxWidth()
            )
            if (state.cityQuery.trim().length >= 2) {
                if (state.cityResults.isEmpty()) {
                    Text("No matching cities yet")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(state.cityResults) { city ->
                            OutlinedButton(
                                onClick = { onCitySelected(city) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text("${city.name}, ${city.countryCode}")
                                    Text("${city.admin1Code} · ${city.lat}, ${city.lon}")
                                }
                            }
                        }
                    }
                }
            }

            when {
                state.selectedCity != null -> {
                    val selected = state.selectedCity
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Selected city")
                        Text("${selected.name}, ${selected.countryCode}")
                        Text("Lat/Lon: ${selected.lat}, ${selected.lon}")
                    }
                }

                state.fixedLatitude.isNotBlank() && state.fixedLongitude.isNotBlank() -> {
                    Text("Current coordinates: ${state.fixedLatitude}, ${state.fixedLongitude}")
                }
            }
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
        val locationSummary = if (state.locationMode == LocationMode.DEVICE) {
            "Device"
        } else {
            when {
                state.selectedCity != null -> "${state.selectedCity.name}, ${state.selectedCity.countryCode}"
                state.fixedLatitude.isNotBlank() && state.fixedLongitude.isNotBlank() -> "Lat ${state.fixedLatitude}, Lon ${state.fixedLongitude}"
                else -> "Manual coordinates"
            }
        }
        Text("Location: $locationSummary")
        Text("Sunrise alarm: ${if (state.sunriseEnabled) "On" else "Off"} @ ${state.sunriseOffsetMinutes} min")
        Text("Sunset alarm: ${if (state.sunsetEnabled) "On" else "Off"} @ ${state.sunsetOffsetMinutes} min")
    }
}
