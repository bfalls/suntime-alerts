package com.bfalls.suntimealerts.alarm.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.cities.data.City

data class LocationPickerUiState(
    val locationMode: LocationMode = LocationMode.DEVICE,
    val locationPermissionPermanentlyDenied: Boolean = false,
    val locationPermissionMissing: Boolean = false,
    val deviceNearestCityLabel: String? = null,
    val fixedLatitude: String = "",
    val fixedLongitude: String = "",
    val cityQuery: String = "",
    val cityResults: List<City> = emptyList(),
    val selectedCity: City? = null
)

@Composable
fun LocationPickerPane(
    state: LocationPickerUiState,
    onLocationModeChanged: (LocationMode) -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onCityQueryChanged: (String) -> Unit,
    onCitySelected: (City) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("How should we find your location?")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(
                LocationMode.DEVICE to "Device",
                LocationMode.FIXED to "Manual"
            )
            options.forEachIndexed { index, (mode, label) ->
                val disabledDeviceOption =
                    mode == LocationMode.DEVICE && state.locationPermissionPermanentlyDenied
                SegmentedButton(
                    modifier = if (disabledDeviceOption) Modifier.alpha(0.6f) else Modifier,
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    selected = state.locationMode == mode,
                    enabled = !disabledDeviceOption,
                    colors = SegmentedButtonDefaults.colors(),
                    onClick = { onLocationModeChanged(mode) },
                    icon = {
                        when (mode) {
                            LocationMode.DEVICE -> Icon(
                                imageVector = Icons.Filled.MyLocation,
                                contentDescription = null
                            )
                            LocationMode.FIXED -> Icon(
                                imageVector = Icons.Filled.TouchApp,
                                contentDescription = null
                            )
                        }
                    },
                    label = { Text(label) }
                )
            }
        }
        if (state.locationPermissionPermanentlyDenied || state.locationPermissionMissing) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (state.locationPermissionPermanentlyDenied) {
                        "Location permission is disabled. Open Settings to enable device location."
                    } else {
                        "Location permission is not granted. Open Settings to enable device location."
                    }
                )
                OutlinedButton(onClick = onOpenPermissionSettings) {
                    Text("Open Settings")
                }
            }
        }
        if (state.locationMode == LocationMode.FIXED) {
            OutlinedTextField(
                value = state.cityQuery,
                onValueChange = onCityQueryChanged,
                label = { Text("City") },
                placeholder = { Text("Start typing a city name") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
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
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        role = Role.Button,
                                        onClick = { onCitySelected(city) }
                                    ),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("${city.name}, ${city.countryCode}", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${city.admin1Code} - ${city.lat}, ${city.lon}",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
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
                        Text("${selected.name}, ${selected.admin1Code}, ${selected.countryCode}")
                        Text("Lat/Lon: ${selected.lat}, ${selected.lon}")
                    }
                }

                state.fixedLatitude.isNotBlank() && state.fixedLongitude.isNotBlank() -> {
                    Text("Current coordinates: ${state.fixedLatitude}, ${state.fixedLongitude}")
                }
            }
        }
        if (state.locationMode == LocationMode.DEVICE) {
            when (val label = state.deviceNearestCityLabel) {
                null -> Text("Finding nearest city...")
                else -> Text("Nearest city: $label")
            }
        }
    }
}
