package com.bfalls.suntimealerts.alarm.domain.model

data class Coordinate(val latitude: Double, val longitude: Double)

data class SunEvent(
    val dateEpochMillis: Long,
    val type: SunEventType,
    val dateTimeEpochMillis: Long,
    val locationUsed: Coordinate
)

data class SunAlarmConfig(
    val eventType: SunEventType,
    val offsetMinutes: Int
)

enum class LocationMode {
    DEVICE,
    FIXED;
}

data class UserSettings(
    val locationMode: LocationMode,
    val fixedLocation: Coordinate? = null,
    val sunriseConfig: SunAlarmConfig,
    val sunsetConfig: SunAlarmConfig,
    val timeFormat24h: Boolean,
    val onboardingComplete: Boolean,
    val alarms: List<SunAlarm> = emptyList()
)
