package com.bfalls.suntimealerts.alarm.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bfalls.suntimealerts.alarm.data.LocationProvider
import com.bfalls.suntimealerts.alarm.data.SettingsRepository
import com.bfalls.suntimealerts.alarm.domain.model.AppThemeMode
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.model.SkyBodySize
import com.bfalls.suntimealerts.alarm.domain.model.UserSettings
import com.bfalls.suntimealerts.alarm.presentation.ui.LocationPickerUiState
import com.bfalls.suntimealerts.alarm.services.AlarmReadiness
import com.bfalls.suntimealerts.alarm.services.AlarmReadinessProvider
import com.bfalls.suntimealerts.cities.data.City
import com.bfalls.suntimealerts.cities.data.CityImportProgress
import com.bfalls.suntimealerts.cities.data.CityRepository
import com.bfalls.suntimealerts.utils.hasLocationPermission
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val locationState: LocationPickerUiState = LocationPickerUiState(),
    val skyBodySize: SkyBodySize = SkyBodySize.SMALL,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val alarmReadiness: AlarmReadiness? = null,
    val isSavingLocation: Boolean = false,
    val errorMessage: String? = null
)

class SettingsViewModel(
    private val settingsStore: SettingsRepository,
    private val cityRepository: CityRepository,
    private val locationService: LocationProvider,
    private val applicationContext: Context,
    private val alarmReadinessProvider: AlarmReadinessProvider
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    private var searchJob: Job? = null
    private var nearestCityJob: Job? = null
    private var cityDataJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val settings = settingsStore.load()
        val locationPermissionMissing = !hasLocationPermission(applicationContext)
        _state.value = SettingsUiState(
            isLoading = false,
            locationState = LocationPickerUiState(
                locationMode = settings.locationMode,
                locationPermissionPermanentlyDenied = false,
                locationPermissionMissing = locationPermissionMissing,
                deviceNearestCityLabel = settings.lastResolvedDeviceLocation?.let { coordinate ->
                    formatCoordinateLabel(coordinate.latitude, coordinate.longitude)
                },
                isResolvingDeviceLocation = false,
                fixedLatitude = settings.fixedLocation?.latitude?.toString() ?: "",
                fixedLongitude = settings.fixedLocation?.longitude?.toString() ?: "",
                cityQuery = settings.fixedLocation?.let { "" } ?: "",
                selectedCity = null,
                isCityDataLoading = false,
                isCityDataReady = false
            ),
            skyBodySize = settings.skyBodySize,
            appThemeMode = settings.appThemeMode,
            alarmReadiness = alarmReadinessProvider.readiness()
        )
        if (settings.locationMode == LocationMode.DEVICE && !locationPermissionMissing) {
            refreshDeviceNearestCity()
        }
    }

    fun updateLocationMode(mode: LocationMode) {
        _state.update { current ->
            current.copy(
                locationState = current.locationState.copy(
                    locationMode = mode,
                    deviceNearestCityLabel = if (mode == LocationMode.DEVICE) null else current.locationState.deviceNearestCityLabel,
                    isResolvingDeviceLocation = false
                )
            )
        }
        if (mode == LocationMode.DEVICE) {
            refreshDeviceNearestCity()
        } else {
            ensureCityDataLoaded()
        }
    }

    fun updateLocationPermission(granted: Boolean) {
        _state.update { current ->
            current.copy(
                locationState = current.locationState.copy(
                    locationPermissionMissing = !granted,
                    locationPermissionPermanentlyDenied = current.locationState.locationPermissionPermanentlyDenied && !granted,
                    isResolvingDeviceLocation = false
                )
            )
        }
        if (granted && _state.value.locationState.locationMode == LocationMode.DEVICE) {
            refreshDeviceNearestCity()
        }
        refreshReadiness()
    }

    fun updateCityQuery(query: String) {
        _state.update { current ->
            current.copy(locationState = current.locationState.copy(cityQuery = query, selectedCity = null))
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            ensureCityDataLoaded()
            val results = if (query.trim().length >= 2) {
                cityRepository.searchCities(query)
            } else {
                emptyList()
            }
            _state.update { current ->
                current.copy(locationState = current.locationState.copy(cityResults = results))
            }
        }
    }

    fun selectCity(city: City) {
        _state.update { current ->
            current.copy(
                locationState = current.locationState.copy(
                    selectedCity = city,
                    cityQuery = "${city.name}, ${city.countryCode}",
                    fixedLatitude = city.lat.toString(),
                    fixedLongitude = city.lon.toString()
                )
            )
        }
    }

    fun refreshDeviceNearestCity() {
        if (!hasLocationPermission(applicationContext)) return
        nearestCityJob?.cancel()
        nearestCityJob = viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    locationState = current.locationState.copy(
                        deviceNearestCityLabel = null,
                        isResolvingDeviceLocation = true
                    )
                )
            }
            try {
                val coordinate = locationService.currentCoordinate() ?: return@launch
                val label = formatCoordinateLabel(coordinate.latitude, coordinate.longitude)
                val settings = settingsStore.load().copy(
                    locationMode = LocationMode.DEVICE,
                    lastResolvedDeviceLocation = coordinate
                )
                settingsStore.save(settings)
                _state.update { current ->
                    current.copy(locationState = current.locationState.copy(deviceNearestCityLabel = label))
                }
            } finally {
                _state.update { current ->
                    current.copy(
                        locationState = current.locationState.copy(
                            isResolvingDeviceLocation = false
                        )
                    )
                }
            }
        }
    }

    private fun ensureCityDataLoaded() {
        if (_state.value.locationState.isCityDataReady) {
            return
        }
        if (cityDataJob?.isActive == true) {
            return
        }

        cityDataJob = viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    locationState = current.locationState.copy(
                        isCityDataLoading = true,
                        cityDataLoadProgress = 0f,
                        cityDataLoadCurrent = 0,
                        cityDataLoadTotal = 0
                    )
                )
            }
            try {
                cityRepository.ensureCitiesLoaded(::updateCityDataProgress)
            } finally {
                _state.update { current ->
                    current.copy(
                        locationState = current.locationState.copy(
                            isCityDataLoading = false,
                            isCityDataReady = true,
                            cityDataLoadProgress = 1f
                        )
                    )
                }
            }
        }
    }

    private fun updateCityDataProgress(progress: CityImportProgress) {
        val fraction = if (progress.total > 0) {
            progress.current.toFloat() / progress.total.toFloat()
        } else {
            0f
        }
        _state.update { current ->
            current.copy(
                locationState = current.locationState.copy(
                    isCityDataLoading = true,
                    cityDataLoadProgress = fraction.coerceIn(0f, 1f),
                    cityDataLoadCurrent = progress.current,
                    cityDataLoadTotal = progress.total,
                    isCityDataReady = false
                )
            )
        }
    }

    fun canSaveLocation(): Boolean {
        val locationState = _state.value.locationState
        return when (locationState.locationMode) {
            LocationMode.DEVICE -> true
            LocationMode.FIXED -> {
                locationState.selectedCity != null || (
                    locationState.fixedLatitude.toDoubleOrNull() != null &&
                        locationState.fixedLongitude.toDoubleOrNull() != null
                    )
            }
        }
    }

    fun saveLocation(onSaved: () -> Unit = {}) {
        val currentState = _state.value
        if (!canSaveLocation() || currentState.isSavingLocation) return
        _state.update { it.copy(isSavingLocation = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val loaded = settingsStore.load()
                val updated = applyLocationToSettings(currentState.locationState, loaded)
                settingsStore.save(updated)
                _state.update {
                    it.copy(
                        isSavingLocation = false,
                        alarmReadiness = alarmReadinessProvider.readiness()
                    )
                }
                onSaved()
            } catch (t: Throwable) {
                _state.update { it.copy(isSavingLocation = false, errorMessage = "Failed to save location") }
            }
        }
    }

    fun updateSkyBodySize(size: SkyBodySize, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val settings = settingsStore.load().copy(skyBodySize = size)
            settingsStore.save(settings)
            _state.update { current -> current.copy(skyBodySize = size) }
            onSaved()
        }
    }

    fun updateAppThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            val settings = settingsStore.load().copy(appThemeMode = mode)
            settingsStore.save(settings)
            _state.update { current -> current.copy(appThemeMode = mode) }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun refreshReadiness() {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(alarmReadiness = alarmReadinessProvider.readiness())
            }
        }
    }

    private fun applyLocationToSettings(
        locationState: LocationPickerUiState,
        settings: UserSettings
    ): UserSettings {
        return when (locationState.locationMode) {
            LocationMode.DEVICE -> settings.copy(locationMode = LocationMode.DEVICE, fixedLocation = null)
            LocationMode.FIXED -> {
                val lat = locationState.selectedCity?.lat ?: locationState.fixedLatitude.toDoubleOrNull() ?: 0.0
                val lon = locationState.selectedCity?.lon ?: locationState.fixedLongitude.toDoubleOrNull() ?: 0.0
                settings.copy(
                    locationMode = LocationMode.FIXED,
                    fixedLocation = Coordinate(lat, lon)
                )
            }
        }
    }

    private fun formatCoordinateLabel(latitude: Double, longitude: Double): String =
        String.format("%.4f, %.4f", latitude, longitude)
}

class SettingsViewModelFactory(
    private val settingsStore: SettingsRepository,
    private val cityRepository: CityRepository,
    private val locationService: LocationProvider,
    private val applicationContext: Context,
    private val alarmReadinessProvider: AlarmReadinessProvider
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(
                settingsStore,
                cityRepository,
                locationService,
                applicationContext,
                alarmReadinessProvider
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
