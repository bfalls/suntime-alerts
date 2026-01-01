package com.bfalls.suntimealerts.alarm.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bfalls.suntimealerts.alarm.data.LocationProvider
import com.bfalls.suntimealerts.alarm.data.SettingsRepository
import com.bfalls.suntimealerts.alarm.data.SunScheduler
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.UserSettings
import com.bfalls.suntimealerts.alarm.domain.service.MoonEphemeris
import com.bfalls.suntimealerts.alarm.domain.service.MoonTimesCalculator
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator.SunTimes
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class HomeViewModel(
    private val locationService: LocationProvider,
    private val settingsStore: SettingsRepository,
    private val scheduleService: SunScheduler,
    private val sunTimesCalculator: SunTimesCalculator
) : ViewModel() {

    private val fallbackSunTimes = sunTimesCalculator.calculateSunTimes(
        LocalDate.now(ZoneId.systemDefault()),
        Coordinate(0.0, 0.0),
        ZoneId.systemDefault()
    )
    private val fallbackTimeFormat24h = true

    data class State(
        val isLoading: Boolean = true,
        val coordinateUsed: Coordinate? = null,
        val sunriseTime: ZonedDateTime? = null,
        val sunsetTime: ZonedDateTime? = null,
        val sunriseTimeText: String? = null,
        val sunsetTimeText: String? = null,
        val sunriseAlarms: List<SunAlarm> = emptyList(),
        val sunsetAlarms: List<SunAlarm> = emptyList(),
        val moonRiseTime: ZonedDateTime? = null,
        val moonSetTime: ZonedDateTime? = null,
        val moonMaxAltDeg: Double = 0.0,
        val moonIllumination01: Double = 0.0,
        val moonIsWaxing: Boolean = true,
        val now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault()),
        val error: String? = null
    )

    private val _state = MutableStateFlow(
        State(
            sunriseTime = fallbackSunTimes.sunrise,
            sunsetTime = fallbackSunTimes.sunset,
            sunriseTimeText = formatTime(fallbackSunTimes.sunrise, fallbackTimeFormat24h),
            sunsetTimeText = formatTime(fallbackSunTimes.sunset, fallbackTimeFormat24h)
        )
    )
    val state: StateFlow<State> = _state

    private var cachedSettings: UserSettings? = null

    init {
        viewModelScope.launch { loadState() }
    }

    fun addAlarm(
        type: SunEventType,
        offsetMinutes: Int,
        label: String,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            val current = _state.value
            val newAlarm = SunAlarm(
                id = UUID.randomUUID().toString(),
                type = type,
                offsetMinutes = offsetMinutes,
                label = label,
                enabled = enabled
            )
            persistAlarms(current.allAlarms() + newAlarm)
        }
    }

    fun updateAlarm(alarm: SunAlarm) {
        viewModelScope.launch {
            val updated = _state.value.allAlarms().map {
                if (it.id == alarm.id) alarm else it
            }
            persistAlarms(updated)
        }
    }

    fun toggleAlarmEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val updated = _state.value.allAlarms().map {
                if (it.id == id) it.copy(enabled = enabled) else it
            }
            persistAlarms(updated)
        }
    }

    fun deleteAlarm(id: String) {
        viewModelScope.launch {
            val updated = _state.value.allAlarms().filterNot { it.id == id }
            persistAlarms(updated)
        }
    }

    fun duplicateAlarm(id: String) {
        viewModelScope.launch {
            val current = _state.value.allAlarms()
            val toCopy = current.firstOrNull { it.id == id } ?: return@launch
            val copy = toCopy.copy(
                id = UUID.randomUUID().toString(),
                label = if (toCopy.label.isNotBlank()) "${toCopy.label} (copy)" else toCopy.label
            )
            persistAlarms(current + copy)
        }
    }

    fun restoreAlarm(alarm: SunAlarm) {
        viewModelScope.launch {
            val existing = _state.value.allAlarms().filterNot { it.id == alarm.id }
            persistAlarms(existing + alarm)
        }
    }

    fun reschedule() {
        viewModelScope.launch {
            scheduleForCurrentSettings()
        }
    }

    fun refreshSunMoonPositions() {
        viewModelScope.launch {
            refreshAstronomyState()
        }
    }

    private suspend fun loadState() {
        val settings = settingsStore.load()
        val zoneId = ZoneId.systemDefault()
        val placeholderSunTimes = sunTimesCalculator.calculateSunTimes(
            LocalDate.now(zoneId),
            settings.fixedLocation ?: Coordinate(0.0, 0.0),
            zoneId
        )

        _state.update { current ->
            current.copy(
                sunriseTime = placeholderSunTimes.sunrise,
                sunsetTime = placeholderSunTimes.sunset,
                sunriseTimeText = formatTime(placeholderSunTimes.sunrise, settings.timeFormat24h),
                sunsetTimeText = formatTime(placeholderSunTimes.sunset, settings.timeFormat24h)
            )
        }

        cachedSettings = settings
        val alarms = settings.alarms.ifEmpty { settingsStore.loadAlarms() }
        if (settings.alarms.isEmpty()) {
            settingsStore.saveAlarms(alarms)
        }

        refreshAstronomyState(settings = settings, alarms = alarms, placeholderSunTimes = placeholderSunTimes)

        scheduleForCurrentSettings()
    }

    private fun formatTime(dateTime: ZonedDateTime?, use24h: Boolean): String? {
        dateTime ?: return null
        val pattern = if (use24h) "HH:mm" else "h:mm a"
        return dateTime.format(DateTimeFormatter.ofPattern(pattern))
    }

    private suspend fun persistAlarms(alarms: List<SunAlarm>) {
        settingsStore.saveAlarms(alarms)
        cachedSettings = (cachedSettings ?: settingsStore.load()).copy(alarms = alarms)
        _state.value = _state.value.copy(
            sunriseAlarms = alarms.filter { it.type == SunEventType.SUNRISE }.sortedBy { it.offsetMinutes },
            sunsetAlarms = alarms.filter { it.type == SunEventType.SUNSET }.sortedBy { it.offsetMinutes }
        )
        scheduleForCurrentSettings()
    }

    private suspend fun refreshAstronomyState(
        settings: UserSettings? = null,
        alarms: List<SunAlarm>? = null,
        placeholderSunTimes: SunTimes? = null
    ) {
        val resolvedSettings = settings ?: cachedSettings ?: settingsStore.load().also { cachedSettings = it }
        val zoneId = ZoneId.systemDefault()
        val coordinate = resolveCoordinate(resolvedSettings)
        val sunTimes = coordinate?.let {
            sunTimesCalculator.calculateSunTimes(LocalDate.now(zoneId), it, zoneId)
        } ?: placeholderSunTimes
        val now = ZonedDateTime.now(zoneId)
        val moonWindow = coordinate?.let {
            MoonTimesCalculator.computeWindow(now, it.latitude, it.longitude)
        }
        val moonPhase = MoonEphemeris.moonPhase(now)
        val sunriseTime = sunTimes?.sunrise ?: _state.value.sunriseTime ?: fallbackSunTimes.sunrise
        val sunsetTime = sunTimes?.sunset ?: _state.value.sunsetTime ?: fallbackSunTimes.sunset
        val sunriseAlarms = alarms?.filter { it.type == SunEventType.SUNRISE }?.sortedBy { it.offsetMinutes }
            ?: _state.value.sunriseAlarms
        val sunsetAlarms = alarms?.filter { it.type == SunEventType.SUNSET }?.sortedBy { it.offsetMinutes }
            ?: _state.value.sunsetAlarms
        _state.update { current ->
            current.copy(
                isLoading = false,
                coordinateUsed = coordinate ?: current.coordinateUsed,
                sunriseTime = sunriseTime,
                sunsetTime = sunsetTime,
                sunriseTimeText = formatTime(sunriseTime, resolvedSettings.timeFormat24h),
                sunsetTimeText = formatTime(sunsetTime, resolvedSettings.timeFormat24h),
                sunriseAlarms = sunriseAlarms,
                sunsetAlarms = sunsetAlarms,
                moonRiseTime = moonWindow?.rise ?: current.moonRiseTime,
                moonSetTime = moonWindow?.set ?: current.moonSetTime,
                moonMaxAltDeg = moonWindow?.maxAltDeg ?: current.moonMaxAltDeg,
                moonIllumination01 = moonPhase.illumination01,
                moonIsWaxing = moonPhase.isWaxing,
                now = now,
                error = if (coordinate == null) "Location unavailable" else null
            )
        }
    }

    private suspend fun scheduleForCurrentSettings() {
        val settings = cachedSettings ?: settingsStore.load()
        val coordinate = resolveCoordinate(settings) ?: Coordinate(0.0, 0.0)
        scheduleService.schedule(coordinate, ZoneId.systemDefault())
    }

    private suspend fun resolveCoordinate(settings: UserSettings): Coordinate? {
        return when (settings.locationMode) {
            LocationMode.FIXED -> settings.fixedLocation ?: locationService.currentCoordinate()
            LocationMode.DEVICE -> locationService.currentCoordinate()
        }
    }

    private fun State.allAlarms(): List<SunAlarm> = sunriseAlarms + sunsetAlarms
}
