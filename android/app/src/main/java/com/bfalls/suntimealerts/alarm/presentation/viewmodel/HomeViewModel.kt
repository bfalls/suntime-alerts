package com.bfalls.suntimealerts.alarm.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bfalls.suntimealerts.alarm.data.LocationProvider
import com.bfalls.suntimealerts.alarm.data.SettingsRepository
import com.bfalls.suntimealerts.alarm.data.SunScheduler
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId

class HomeViewModel(
    private val locationService: LocationProvider,
    private val settingsStore: SettingsRepository,
    private val scheduleService: SunScheduler
) : ViewModel() {

    data class State(
        val sunrise: String? = null,
        val sunset: String? = null,
        val sunriseEnabled: Boolean = true,
        val sunsetEnabled: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val settings = settingsStore.load()
        _state.value = _state.value.copy(
            sunriseEnabled = settings.sunriseConfig.enabled,
            sunsetEnabled = settings.sunsetConfig.enabled
        )
    }

    fun toggleSunrise(enabled: Boolean) {
        _state.value = _state.value.copy(sunriseEnabled = enabled)
    }

    fun toggleSunset(enabled: Boolean) {
        _state.value = _state.value.copy(sunsetEnabled = enabled)
    }

    fun reschedule() {
        viewModelScope.launch {
            val settings = settingsStore.load()
            val coordinate = when (settings.locationMode) {
                LocationMode.FIXED -> settings.fixedLocation ?: locationService.currentCoordinate()
                LocationMode.DEVICE -> locationService.currentCoordinate()
            } ?: Coordinate(0.0, 0.0)
            scheduleService.schedule(coordinate, ZoneId.systemDefault())
        }
    }
}
