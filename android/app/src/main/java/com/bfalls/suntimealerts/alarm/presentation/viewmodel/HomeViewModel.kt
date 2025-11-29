package com.bfalls.suntimealerts.alarm.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bfalls.suntimealerts.alarm.data.LocationService
import com.bfalls.suntimealerts.alarm.data.SettingsStore
import com.bfalls.suntimealerts.alarm.data.SunScheduleService
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId

class HomeViewModel(
    private val locationService: LocationService,
    private val settingsStore: SettingsStore,
    private val scheduleService: SunScheduleService
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
            val coord: Coordinate = locationService.currentCoordinate() ?: Coordinate(0.0, 0.0)
            scheduleService.schedule(coord, ZoneId.systemDefault())
        }
    }
}