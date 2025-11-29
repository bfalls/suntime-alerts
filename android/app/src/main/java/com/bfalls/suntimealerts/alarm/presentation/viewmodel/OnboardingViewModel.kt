package com.bfalls.suntimealerts.alarm.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bfalls.suntimealerts.alarm.data.SettingsStore
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarmConfig
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val isLoaded: Boolean = false,
    val onboardingComplete: Boolean = false,
    val locationMode: LocationMode = LocationMode.DEVICE,
    val fixedLatitude: String = "0.0",
    val fixedLongitude: String = "0.0",
    val notificationsEnabled: Boolean = true,
    val sunriseEnabled: Boolean = true,
    val sunriseOffsetMinutes: Int = 0,
    val sunsetEnabled: Boolean = false,
    val sunsetOffsetMinutes: Int = 0
)

enum class OnboardingStep { WELCOME, LOCATION, NOTIFICATIONS, ALARMS, SUMMARY }

class OnboardingViewModel(private val settingsStore: SettingsStore) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val settings = settingsStore.load()
        _state.value = _state.value.copy(
            isLoaded = true,
            onboardingComplete = settings.onboardingComplete,
            locationMode = settings.locationMode,
            fixedLatitude = settings.fixedLocation?.latitude?.toString() ?: "0.0",
            fixedLongitude = settings.fixedLocation?.longitude?.toString() ?: "0.0",
            notificationsEnabled = settings.sunriseConfig.enabled || settings.sunsetConfig.enabled,
            sunriseEnabled = settings.sunriseConfig.enabled,
            sunriseOffsetMinutes = settings.sunriseConfig.offsetMinutes,
            sunsetEnabled = settings.sunsetConfig.enabled,
            sunsetOffsetMinutes = settings.sunsetConfig.offsetMinutes
        )
    }

    fun nextStep() {
        val next = OnboardingStep.values().getOrNull(_state.value.step.ordinal + 1) ?: return
        _state.value = _state.value.copy(step = next)
    }

    fun previousStep() {
        val prev = OnboardingStep.values().getOrNull(_state.value.step.ordinal - 1) ?: return
        _state.value = _state.value.copy(step = prev)
    }

    fun updateLocationMode(mode: LocationMode) {
        _state.value = _state.value.copy(locationMode = mode)
    }

    fun updateFixedLatitude(text: String) {
        _state.value = _state.value.copy(fixedLatitude = text)
    }

    fun updateFixedLongitude(text: String) {
        _state.value = _state.value.copy(fixedLongitude = text)
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
                current.fixedLatitude.toDoubleOrNull() != null && current.fixedLongitude.toDoubleOrNull() != null
            } else true
            else -> true
        }
    }

    fun complete(onFinished: () -> Unit) {
        viewModelScope.launch {
            var settings = settingsStore.load()
            settings = settings.copy(
                locationMode = if (_state.value.locationMode == LocationMode.FIXED) LocationMode.FIXED else LocationMode.DEVICE,
                sunriseConfig = SunAlarmConfig(
                    enabled = _state.value.notificationsEnabled && _state.value.sunriseEnabled,
                    eventType = SunEventType.SUNRISE,
                    offsetMinutes = _state.value.sunriseOffsetMinutes
                ),
                sunsetConfig = SunAlarmConfig(
                    enabled = _state.value.notificationsEnabled && _state.value.sunsetEnabled,
                    eventType = SunEventType.SUNSET,
                    offsetMinutes = _state.value.sunsetOffsetMinutes
                ),
                onboardingComplete = true
            )
            if (settings.locationMode == LocationMode.FIXED) {
                val lat = _state.value.fixedLatitude.toDoubleOrNull() ?: 0.0
                val lon = _state.value.fixedLongitude.toDoubleOrNull() ?: 0.0
                settings = settings.copy(fixedLocation = com.bfalls.suntimealerts.alarm.domain.model.Coordinate(lat, lon))
            }
            settingsStore.save(settings)
            _state.value = _state.value.copy(onboardingComplete = true)
            onFinished()
        }
    }
}

class OnboardingViewModelFactory(private val settingsStore: SettingsStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OnboardingViewModel(settingsStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
