package com.bfalls.suntimealerts.alarm.data

import android.content.Context
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
import com.bfalls.suntimealerts.alarm.domain.model.UserSettings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import java.util.UUID

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
    private val onboarding = booleanPreferencesKey("onboarding")
    private val alarmsJson = stringPreferencesKey("alarms_json")
    private val gson = Gson()

    override suspend fun load(): UserSettings {
        val prefs = context.dataStore.data.first()
        val sunriseConfig = SunAlarmConfig(
            SunEventType.SUNRISE,
            prefs[sunriseOffset] ?: 0
        )
        val sunsetConfig = SunAlarmConfig(
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
        val storedAlarms = loadAlarmsInternal(prefs)
        val alarms = if (storedAlarms.isNotEmpty()) {
            storedAlarms
        } else {
            migrateAlarms(prefs).also { saveAlarms(it) }
        }
        return UserSettings(
            locationMode = locationMode,
            fixedLocation = fixed,
            sunriseConfig = sunriseConfig,
            sunsetConfig = sunsetConfig,
            timeFormat24h = prefs[time24h] ?: true,
            onboardingComplete = prefs[onboarding] ?: false,
            alarms = alarms
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
            prefs[alarmsJson] = gson.toJson(settings.alarms)
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
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun migrateAlarms(prefs: Preferences): List<SunAlarm> = migrateLegacyAlarms(
        sunriseOffset = prefs[sunriseOffset] ?: 0,
        sunsetOffset = prefs[sunsetOffset] ?: 0
    )
}

internal fun migrateLegacyAlarms(
    sunriseOffset: Int,
    sunsetOffset: Int
): List<SunAlarm> = listOf(
    SunAlarm(
        id = UUID.randomUUID().toString(),
        type = SunEventType.SUNRISE,
        offsetMinutes = sunriseOffset,
        label = "Sunrise",
        enabled = true
    ),
    SunAlarm(
        id = UUID.randomUUID().toString(),
        type = SunEventType.SUNSET,
        offsetMinutes = sunsetOffset,
        label = "Sunset",
        enabled = false
    )
)
