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
import com.bfalls.suntimealerts.alarm.domain.model.SkyBodySize
import com.bfalls.suntimealerts.alarm.domain.model.UserSettings
import com.bfalls.suntimealerts.alarm.services.AlarmReadiness
import com.bfalls.suntimealerts.alarm.services.AlarmReadinessProvider
import com.bfalls.suntimealerts.alarm.domain.service.MoonEphemeris
import com.bfalls.suntimealerts.alarm.domain.service.MoonTimesCalculator
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator.SunTimes
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class HomeViewModel(
    private val locationService: LocationProvider,
    private val settingsStore: SettingsRepository,
    private val scheduleService: SunScheduler,
    private val sunTimesCalculator: SunTimesCalculator,
    private val alarmReadinessProvider: AlarmReadinessProvider
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
        val skyBodySize: SkyBodySize = SkyBodySize.SMALL,
        val alarmReadiness: AlarmReadiness? = null,
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

    fun refresh() {
        viewModelScope.launch { loadState() }
    }

    fun handleExactAlarmSettingsResult() {
        viewModelScope.launch {
            val readiness = refreshReadiness()
            if (readiness.exactAlarmReady) {
                scheduleForCurrentSettings(readiness)
            }
        }
    }

    fun addAlarm(alarm: SunAlarm) {
        viewModelScope.launch {
            val current = _state.value
            val newAlarm = alarm.copy(id = alarm.id.ifBlank { UUID.randomUUID().toString() })
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

    fun duplicateAlarm(alarm: SunAlarm) {
        viewModelScope.launch {
            val current = _state.value.allAlarms()
            val sourceIndex = current.indexOfFirst { it.id == alarm.id }
            val baseAlarm = current.firstOrNull { it.id == alarm.id } ?: alarm
            val copy = baseAlarm.copy(
                id = UUID.randomUUID().toString(),
                label = if (baseAlarm.label.isNotBlank()) "${baseAlarm.label} (copy)" else baseAlarm.label
            )
            val updated = current.toMutableList().apply {
                val insertIndex = if (sourceIndex >= 0) sourceIndex + 1 else size
                add(insertIndex, copy)
            }
            persistAlarms(updated)
        }
    }

    fun restoreAlarm(alarm: SunAlarm, index: Int) {
        viewModelScope.launch {
            val existing = _state.value.allAlarms().filterNot { it.id == alarm.id }
            val insertIndex = index.coerceIn(0, existing.size)
            val updated = existing.toMutableList().apply {
                add(insertIndex, alarm)
            }
            persistAlarms(updated)
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
        try {
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
                    sunsetTimeText = formatTime(placeholderSunTimes.sunset, settings.timeFormat24h),
                    skyBodySize = settings.skyBodySize
                )
            }

            cachedSettings = settings
            val alarms = settings.alarms.ifEmpty { settingsStore.loadAlarms() }
            if (settings.alarms.isEmpty()) {
                settingsStore.saveAlarms(alarms)
            }
            val readiness = alarmReadinessProvider.readiness()
            _state.update { current -> current.copy(alarmReadiness = readiness) }

            refreshAstronomyState(settings = settings, alarms = alarms, placeholderSunTimes = placeholderSunTimes)

            scheduleForCurrentSettings(readiness)
        } catch (t: Throwable) {
            runCatching {
                Log.e("HomeViewModel", "Failed to load state.", t)
            }
            _state.update { current ->
                current.copy(
                    isLoading = false,
                    error = "Failed to load settings"
                )
            }
        }
    }

    private fun formatTime(dateTime: ZonedDateTime?, use24h: Boolean): String? {
        dateTime ?: return null
        val pattern = if (use24h) "HH:mm" else "h:mm a"
        return dateTime.format(DateTimeFormatter.ofPattern(pattern))
    }

    private suspend fun persistAlarms(alarms: List<SunAlarm>) {
        settingsStore.saveAlarms(alarms)
        cachedSettings = (cachedSettings ?: settingsStore.load()).copy(alarms = alarms)
        val readiness = alarmReadinessProvider.readiness()
        _state.value = _state.value.copy(
            sunriseAlarms = alarms.filter { it.type == SunEventType.SUNRISE }.sortedBy { it.offsetMinutes },
            sunsetAlarms = alarms.filter { it.type == SunEventType.SUNSET }.sortedBy { it.offsetMinutes },
            alarmReadiness = readiness
        )
        scheduleForCurrentSettings(readiness)
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
                skyBodySize = resolvedSettings.skyBodySize,
                now = now,
                error = if (coordinate == null) "Location unavailable" else null
            )
        }
    }

    private suspend fun scheduleForCurrentSettings(
        readiness: AlarmReadiness? = _state.value.alarmReadiness
    ) {
        val resolvedReadiness = readiness ?: refreshReadiness()
        if (!resolvedReadiness.exactAlarmReady) {
            runCatching {
                Log.w(
                    "HomeViewModel",
                    "Skipping alarm scheduling because exact alarm access is not granted."
                )
            }
            return
        }
        val settings = cachedSettings ?: settingsStore.load()
        val coordinate = resolveCoordinate(settings) ?: Coordinate(0.0, 0.0)
        scheduleService.schedule(coordinate, ZoneId.systemDefault())
    }

    private suspend fun refreshReadiness(): AlarmReadiness {
        val readiness = alarmReadinessProvider.readiness()
        _state.update { current -> current.copy(alarmReadiness = readiness) }
        return readiness
    }

    private suspend fun resolveCoordinate(settings: UserSettings): Coordinate? {
        return when (settings.locationMode) {
            LocationMode.FIXED -> settings.fixedLocation ?: locationService.currentCoordinate()
            LocationMode.DEVICE -> locationService.currentCoordinate()
        }
    }

    private fun State.allAlarms(): List<SunAlarm> = sunriseAlarms + sunsetAlarms
}
