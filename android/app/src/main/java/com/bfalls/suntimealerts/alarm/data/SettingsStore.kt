package com.bfalls.suntimealerts.alarm.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarmConfig
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.UserSettings
import com.bfalls.suntimealerts.alarm.domain.model.*
import kotlinx.coroutines.flow.first
import kotlin.text.get

private val Context.dataStore by preferencesDataStore("sunriseSunset")

class SettingsStore(private val context: Context) {
    private val sunriseEnabled = booleanPreferencesKey("sunrise_enabled")
    private val sunriseOffset = intPreferencesKey("sunrise_offset")
    private val sunsetEnabled = booleanPreferencesKey("sunset_enabled")
    private val sunsetOffset = intPreferencesKey("sunset_offset")
    private val time24h = booleanPreferencesKey("time_24h")
    private val fixedLat = doublePreferencesKey("fixed_lat")
    private val fixedLon = doublePreferencesKey("fixed_lon")
    private val onboarding = booleanPreferencesKey("onboarding")

    suspend fun load(): UserSettings {
        val prefs = context.dataStore.data.first()
        val sunriseConfig = SunAlarmConfig(
            prefs[sunriseEnabled] ?: true,
            SunEventType.SUNRISE,
            prefs[sunriseOffset] ?: 0
        )
        val sunsetConfig = SunAlarmConfig(
            prefs[sunsetEnabled] ?: false,
            SunEventType.SUNSET,
            prefs[sunsetOffset] ?: 0
        )
        val locationMode = if (prefs[fixedLat] != null && prefs[fixedLon] != null) {
            LocationMode.FIXED
        } else {
            LocationMode.DEVICE
        }
        val fixed = if (locationMode == LocationMode.FIXED) Coordinate(
            prefs[fixedLat] ?: 0.0,
            prefs[fixedLon] ?: 0.0
        ) else null
        return UserSettings(
            locationMode = locationMode,
            fixedLocation = fixed,
            sunriseConfig = sunriseConfig,
            sunsetConfig = sunsetConfig,
            timeFormat24h = prefs[time24h] ?: true,
            onboardingComplete = prefs[onboarding] ?: false
        )
    }

    suspend fun save(settings: UserSettings) {
        context.dataStore.edit { prefs ->
            prefs[sunriseEnabled] = settings.sunriseConfig.enabled
            prefs[sunriseOffset] = settings.sunriseConfig.offsetMinutes
            prefs[sunsetEnabled] = settings.sunsetConfig.enabled
            prefs[sunsetOffset] = settings.sunsetConfig.offsetMinutes
            prefs[time24h] = settings.timeFormat24h
            prefs[onboarding] = settings.onboardingComplete
            if (settings.locationMode == LocationMode.FIXED) {
                val loc = settings.fixedLocation ?: Coordinate(0.0, 0.0)
                prefs[fixedLat] = loc.latitude
                prefs[fixedLon] = loc.longitude
            }
        }
    }
}
