package com.bfalls.suntimealerts.alarm.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarmConfig
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.AppThemeMode
import com.bfalls.suntimealerts.alarm.domain.model.SkyBodySize
import com.bfalls.suntimealerts.alarm.domain.model.UserSettings
import com.bfalls.suntimealerts.alarm.domain.model.withDefaults
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first

interface SettingsRepository {
    suspend fun load(): UserSettings
    suspend fun save(settings: UserSettings)
    suspend fun loadAlarms(): List<SunAlarm>
    suspend fun saveAlarms(alarms: List<SunAlarm>)
}

private val Context.dataStore by preferencesDataStore("sunriseSunset")

class SettingsStore(private val context: Context) : SettingsRepository {
    private val sunriseOffset = intPreferencesKey("sunrise_offset")
    private val sunsetOffset = intPreferencesKey("sunset_offset")
    private val time24h = booleanPreferencesKey("time_24h")
    private val fixedLat = doublePreferencesKey("fixed_lat")
    private val fixedLon = doublePreferencesKey("fixed_lon")
    private val deviceLat = doublePreferencesKey("device_lat")
    private val deviceLon = doublePreferencesKey("device_lon")
    private val onboarding = booleanPreferencesKey("onboarding")
    private val alarmsJson = stringPreferencesKey("alarms_json")
    private val skyBodySize = stringPreferencesKey("sky_body_size")
    private val appThemeMode = stringPreferencesKey("app_theme_mode")
    private val gson = Gson()

    override suspend fun load(): UserSettings {
        val prefs = context.dataStore.data.first()
        val storedAlarms = loadAlarmsInternal(prefs)
        val alarms = if (storedAlarms.isNotEmpty()) {
            storedAlarms
        } else {
            migrateAlarms(prefs).also { saveAlarms(it) }
        }
        val sunriseEnabled = alarms.firstOrNull { it.type == SunEventType.SUNRISE }?.enabled ?: false
        val sunsetEnabled = alarms.firstOrNull { it.type == SunEventType.SUNSET }?.enabled ?: false
        val sunriseConfig = SunAlarmConfig(
            enabled = sunriseEnabled,
            eventType = SunEventType.SUNRISE,
            offsetMinutes = prefs[sunriseOffset] ?: 0
        )
        val sunsetConfig = SunAlarmConfig(
            enabled = sunsetEnabled,
            eventType = SunEventType.SUNSET,
            offsetMinutes = prefs[sunsetOffset] ?: 0
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
        val lastResolvedDeviceLocation =
            if (prefs[deviceLat] != null && prefs[deviceLon] != null) {
                Coordinate(
                    prefs[deviceLat] ?: 0.0,
                    prefs[deviceLon] ?: 0.0
                )
            } else {
                null
            }
        return UserSettings(
            locationMode = locationMode,
            fixedLocation = fixed,
            lastResolvedDeviceLocation = lastResolvedDeviceLocation,
            sunriseConfig = sunriseConfig,
            sunsetConfig = sunsetConfig,
            timeFormat24h = prefs[time24h] ?: true,
            onboardingComplete = prefs[onboarding] ?: false,
            alarms = alarms,
            skyBodySize = prefs[skyBodySize]?.let { stored ->
                runCatching { SkyBodySize.valueOf(stored) }.getOrDefault(SkyBodySize.SMALL)
            } ?: SkyBodySize.SMALL,
            appThemeMode = prefs[appThemeMode]?.let { stored ->
                runCatching { AppThemeMode.valueOf(stored) }.getOrDefault(AppThemeMode.SYSTEM)
            } ?: AppThemeMode.SYSTEM
        )
    }

    override suspend fun save(settings: UserSettings) {
        context.dataStore.edit { prefs ->
            prefs[sunriseOffset] = settings.sunriseConfig.offsetMinutes
            prefs[sunsetOffset] = settings.sunsetConfig.offsetMinutes
            prefs[time24h] = settings.timeFormat24h
            prefs[onboarding] = settings.onboardingComplete
            if (settings.locationMode == LocationMode.FIXED) {
                val loc = settings.fixedLocation ?: Coordinate(0.0, 0.0)
                prefs[fixedLat] = loc.latitude
                prefs[fixedLon] = loc.longitude
            } else {
                prefs.remove(fixedLat)
                prefs.remove(fixedLon)
            }
            val deviceLocation = settings.lastResolvedDeviceLocation
            if (deviceLocation != null) {
                prefs[deviceLat] = deviceLocation.latitude
                prefs[deviceLon] = deviceLocation.longitude
            } else {
                prefs.remove(deviceLat)
                prefs.remove(deviceLon)
            }
            prefs[alarmsJson] = gson.toJson(settings.alarms)
            prefs[skyBodySize] = settings.skyBodySize.name
            prefs[appThemeMode] = settings.appThemeMode.name
        }
    }

    override suspend fun loadAlarms(): List<SunAlarm> {
        val prefs = context.dataStore.data.first()
        val stored = loadAlarmsInternal(prefs)
        if (stored.isNotEmpty()) return stored

        val migrated = migrateAlarms(prefs)
        saveAlarms(migrated)
        return migrated
    }

    override suspend fun saveAlarms(alarms: List<SunAlarm>) {
        context.dataStore.edit { prefs ->
            prefs[alarmsJson] = gson.toJson(alarms)
        }
    }

    private fun loadAlarmsInternal(prefs: Preferences): List<SunAlarm> {
        val json = prefs[alarmsJson] ?: return emptyList()
        if (json.isBlank()) return emptyList()
        val type = object : TypeToken<List<SunAlarm>>() {}.type
        return try {
            (gson.fromJson<List<SunAlarm>>(json, type) ?: emptyList()).map { alarm ->
                alarm.withDefaults()
            }
        } catch (t: Throwable) {
            Log.w("SettingsStore", "Failed to parse alarmsJson; falling back to defaults.", t)
            emptyList()
        }
    }

    private fun migrateAlarms(prefs: Preferences): List<SunAlarm> {
        return emptyList()
    }
}
