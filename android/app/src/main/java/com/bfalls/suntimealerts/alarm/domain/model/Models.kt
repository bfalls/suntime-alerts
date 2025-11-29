package com.bfalls.suntimealerts.alarm.domain.model

data class Coordinate(val latitude: Double, val longitude: Double)

enum class SunEventType { SUNRISE, SUNSET }

data class SunEvent(
    val dateEpochMillis: Long,
    val type: SunEventType,
    val dateTimeEpochMillis: Long,
    val locationUsed: Coordinate
)

data class SunAlarmConfig(
    val enabled: Boolean,
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
    val onboardingComplete: Boolean
)
