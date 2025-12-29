package com.bfalls.suntimealerts.alarm.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bfalls.suntimealerts.alarm.data.LocationProvider
import com.bfalls.suntimealerts.alarm.data.SettingsRepository
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarmConfig
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.cities.data.City
import com.bfalls.suntimealerts.cities.data.CityRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val isLoaded: Boolean = false,
    val onboardingComplete: Boolean = false,
    val locationMode: LocationMode = LocationMode.DEVICE,
    val locationPermissionPermanentlyDenied: Boolean = false,
    val locationPermissionDeniedAttempts: Int = 0,
    val deviceNearestCityLabel: String? = null,
    val fixedLatitude: String = "",
    val fixedLongitude: String = "",
    val cityQuery: String = "",
    val cityResults: List<City> = emptyList(),
    val selectedCity: City? = null,
    val notificationsEnabled: Boolean = true,
    val sunriseEnabled: Boolean = true,
    val sunriseOffsetMinutes: Int = 0,
    val sunsetEnabled: Boolean = false,
    val sunsetOffsetMinutes: Int = 0
)

enum class OnboardingStep { WELCOME, LOCATION, NOTIFICATIONS, ALARMS, SUMMARY }

enum class PermissionRequestOrigin { AUTOMATIC, USER }

class OnboardingViewModel(
    private val settingsStore: SettingsRepository,
    private val cityRepository: CityRepository,
    private val locationService: LocationProvider
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    private var searchJob: Job? = null
    private var nearestCityJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val settings = settingsStore.load()
        val sunriseEnabled = settings.alarms.firstOrNull { it.type == SunEventType.SUNRISE }?.enabled ?: true
        val sunsetEnabled = settings.alarms.firstOrNull { it.type == SunEventType.SUNSET }?.enabled ?: false
        _state.value = _state.value.copy(
            isLoaded = true,
            onboardingComplete = settings.onboardingComplete,
            locationMode = settings.locationMode,
            fixedLatitude = settings.fixedLocation?.latitude?.toString() ?: "",
            fixedLongitude = settings.fixedLocation?.longitude?.toString() ?: "",
            notificationsEnabled = sunriseEnabled || sunsetEnabled,
            sunriseEnabled = sunriseEnabled,
            sunriseOffsetMinutes = settings.sunriseConfig.offsetMinutes,
            sunsetEnabled = sunsetEnabled,
            sunsetOffsetMinutes = settings.sunsetConfig.offsetMinutes
        )
    }

    fun nextStep() {
        val next = OnboardingStep.values().getOrNull(_state.value.step.ordinal + 1) ?: return
        _state.value = _state.value.copy(step = next)
        handleStepChanged()
    }

    fun previousStep() {
        val prev = OnboardingStep.values().getOrNull(_state.value.step.ordinal - 1) ?: return
        _state.value = _state.value.copy(step = prev)
        handleStepChanged()
    }

    fun updateLocationMode(mode: LocationMode) {
        if (mode == LocationMode.DEVICE && _state.value.locationPermissionPermanentlyDenied) {
            // The user has exhausted permission attempts; keep them in manual mode until
            // they re-enable permissions via system settings and grant access.
            _state.value = _state.value.copy(locationMode = LocationMode.FIXED)
            return
        }

        _state.value = _state.value.copy(
            locationMode = mode,
            deviceNearestCityLabel = if (mode == LocationMode.DEVICE) null else _state.value.deviceNearestCityLabel
        )
        if (mode == LocationMode.DEVICE) {
            refreshDeviceNearestCity()
        }
    }

    fun handleLocationPermissionResult(
        granted: Boolean,
        permanentlyDenied: Boolean,
        origin: PermissionRequestOrigin
    ) {
        val current = _state.value

        if (granted) {
            _state.value = current.copy(
                locationPermissionPermanentlyDenied = false,
                locationPermissionDeniedAttempts = 0
            )
            updateLocationMode(LocationMode.DEVICE)
            return
        }

        val updatedAttempts = current.locationPermissionDeniedAttempts + 1
        val forcedManual = permanentlyDenied || updatedAttempts >= 2

        val updated = current.copy(
            locationPermissionPermanentlyDenied = forcedManual,
            locationPermissionDeniedAttempts = updatedAttempts
        )
        _state.value = updated

        if (
            forcedManual &&
            !current.onboardingComplete &&
            current.step == OnboardingStep.LOCATION
        ) {
            _state.value = updated.copy(locationMode = LocationMode.FIXED)
        } else if (
            !forcedManual &&
            !current.onboardingComplete &&
            current.step == OnboardingStep.LOCATION &&
            current.locationMode == LocationMode.DEVICE
        ) {
            _state.value = updated.copy(locationMode = LocationMode.FIXED)
        }
    }

    fun clearLocationPermissionDenial() {
        val current = _state.value
        if (current.locationPermissionPermanentlyDenied) {
            _state.value = current.copy(locationPermissionPermanentlyDenied = false)
        }
    }

    fun updateCityQuery(query: String) {
        _state.value = _state.value.copy(cityQuery = query, selectedCity = null)

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val results = if (query.trim().length >= 2) {
                cityRepository.searchCities(query)
            } else {
                emptyList()
            }
            _state.value = _state.value.copy(cityResults = results)
        }
    }

    fun selectCity(city: City) {
        _state.value = _state.value.copy(
            selectedCity = city,
            cityQuery = "${city.name}, ${city.countryCode}",
            fixedLatitude = city.lat.toString(),
            fixedLongitude = city.lon.toString()
        )
    }

    fun updateNotifications(enabled: Boolean) {
        val updatedSunriseEnabled = if (enabled) _state.value.sunriseEnabled else false
        val updatedSunsetEnabled = if (enabled) _state.value.sunsetEnabled else false
        _state.value = _state.value.copy(
            notificationsEnabled = enabled,
            sunriseEnabled = updatedSunriseEnabled,
            sunsetEnabled = updatedSunsetEnabled
        )
    }

    fun updateSunriseEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(sunriseEnabled = enabled)
    }

    fun updateSunsetEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(sunsetEnabled = enabled)
    }

    fun updateSunriseOffset(offset: Int) {
        _state.value = _state.value.copy(sunriseOffsetMinutes = offset)
    }

    fun updateSunsetOffset(offset: Int) {
        _state.value = _state.value.copy(sunsetOffsetMinutes = offset)
    }

    fun canAdvance(): Boolean {
        val current = _state.value
        return when (current.step) {
            OnboardingStep.LOCATION -> if (current.locationMode == LocationMode.FIXED) {
                current.selectedCity != null || (
                        current.fixedLatitude.toDoubleOrNull() != null &&
                                current.fixedLongitude.toDoubleOrNull() != null
                        )
            } else current.deviceNearestCityLabel != null

            else -> true
        }
    }

    fun complete(onFinished: () -> Unit) {
        viewModelScope.launch {
            var settings = settingsStore.load()
            val existingAlarmsByType = settings.alarms.associateBy { it.type }
            val onboardingState = _state.value
            val updatedAlarms = listOf(
                SunAlarm(
                    id = existingAlarmsByType[SunEventType.SUNRISE]?.id ?: existingAlarmsByType.values.firstOrNull { it.type == SunEventType.SUNRISE }?.id
                    ?: java.util.UUID.randomUUID().toString(),
                    type = SunEventType.SUNRISE,
                    offsetMinutes = onboardingState.sunriseOffsetMinutes,
                    label = existingAlarmsByType[SunEventType.SUNRISE]?.label ?: "Sunrise",
                    enabled = onboardingState.notificationsEnabled && onboardingState.sunriseEnabled
                ),
                SunAlarm(
                    id = existingAlarmsByType[SunEventType.SUNSET]?.id ?: existingAlarmsByType.values.firstOrNull { it.type == SunEventType.SUNSET }?.id
                    ?: java.util.UUID.randomUUID().toString(),
                    type = SunEventType.SUNSET,
                    offsetMinutes = onboardingState.sunsetOffsetMinutes,
                    label = existingAlarmsByType[SunEventType.SUNSET]?.label ?: "Sunset",
                    enabled = onboardingState.notificationsEnabled && onboardingState.sunsetEnabled
                )
            )
            settings = settings.copy(
                locationMode = if (_state.value.locationMode == LocationMode.FIXED) LocationMode.FIXED else LocationMode.DEVICE,
                sunriseConfig = SunAlarmConfig(
                    eventType = SunEventType.SUNRISE,
                    offsetMinutes = onboardingState.sunriseOffsetMinutes
                ),
                sunsetConfig = SunAlarmConfig(
                    eventType = SunEventType.SUNSET,
                    offsetMinutes = onboardingState.sunsetOffsetMinutes
                ),
                onboardingComplete = true,
                alarms = updatedAlarms
            )
            if (settings.locationMode == LocationMode.FIXED) {
                val lat = onboardingState.fixedLatitude.toDoubleOrNull() ?: 0.0
                val lon = onboardingState.fixedLongitude.toDoubleOrNull() ?: 0.0
                settings = settings.copy(
                    fixedLocation = com.bfalls.suntimealerts.alarm.domain.model.Coordinate(
                        lat,
                        lon
                    )
                )
            }
            settingsStore.save(settings)
            _state.value = _state.value.copy(onboardingComplete = true)
            onFinished()
        }
    }

    private fun handleStepChanged() {
        val current = _state.value
        if (current.step == OnboardingStep.LOCATION && current.locationMode == LocationMode.DEVICE) {
            refreshDeviceNearestCity()
        }
    }

    private fun refreshDeviceNearestCity() {
        nearestCityJob?.cancel()
        nearestCityJob = viewModelScope.launch {
            _state.value = _state.value.copy(deviceNearestCityLabel = null)
            Log.d("OnboardingViewModel", "Requesting device location for nearest city")

            val coordinate = locationService.currentCoordinate() ?: run {
                Log.w(
                    "OnboardingViewModel",
                    "Device coordinate unavailable; cannot compute nearest city"
                )
                return@launch
            }

            Log.d("OnboardingViewModel", "Got coordinate from device: $coordinate")

            val nearest = cityRepository.findNearestCity(
                coordinate.latitude,
                coordinate.longitude
            )
            Log.d("OnboardingViewModel", "Nearest city for $coordinate is $nearest")
            val label = nearest?.let { formatNearestCityLabel(it) }
            _state.value = _state.value.copy(deviceNearestCityLabel = label)
        }
    }

    private fun formatNearestCityLabel(city: City): String {
        val region = city.admin1Code.takeIf { it.isNotBlank() }
        return if (region != null) {
            "${city.name}, $region, ${city.countryCode}"
        } else {
            "${city.name}, ${city.countryCode}"
        }
    }
}

class OnboardingViewModelFactory(
    private val settingsStore: SettingsRepository,
    private val cityRepository: CityRepository,
    private val locationService: LocationProvider
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OnboardingViewModel(settingsStore, cityRepository, locationService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
