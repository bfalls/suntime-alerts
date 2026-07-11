package com.bfalls.suntimealerts.alarm.debug

import android.content.Context
import android.content.pm.ApplicationInfo

enum class DebugAlarmOverride(val wireName: String) {
    LOCATION_PERMISSION_DENIED("location_permission_denied"),
    LOCATION_UNAVAILABLE("location_unavailable"),
    NOTIFICATION_PERMISSION_DENIED("notification_permission_denied"),
    APP_NOTIFICATIONS_DISABLED("app_notifications_disabled"),
    CHANNEL_BLOCKED("channel_blocked"),
    EXACT_ALARM_DENIED("exact_alarm_denied"),
    FULL_SCREEN_INTENT_DENIED("full_screen_intent_denied");

    companion object {
        fun fromWireName(value: String?): DebugAlarmOverride? =
            entries.firstOrNull { it.wireName == value }
    }
}

class DebugAlarmTestOverrides(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val isDebugBuild: Boolean
        get() = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun isEnabled(override: DebugAlarmOverride): Boolean {
        if (!isDebugBuild) return false
        return prefs.getBoolean(override.wireName, false)
    }

    fun setEnabled(override: DebugAlarmOverride, enabled: Boolean) {
        if (!isDebugBuild) return
        prefs.edit().putBoolean(override.wireName, enabled).apply()
    }

    fun clearAll() {
        if (!isDebugBuild) return
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "debug_alarm_test_overrides"
    }
}
