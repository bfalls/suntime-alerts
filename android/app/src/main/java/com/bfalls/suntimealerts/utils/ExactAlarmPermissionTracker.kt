package com.bfalls.suntimealerts.utils

import android.content.Context

class ExactAlarmPermissionTracker(context: Context) {
    private val prefs = context.getSharedPreferences("exact_alarm_permissions", Context.MODE_PRIVATE)

    fun canRequestExactAlarmPermission(maxAttempts: Int = 2): Boolean {
        val deniedAttempts = prefs.getInt(denialCountKey, 0)
        return deniedAttempts < maxAttempts
    }

    fun recordDenial() {
        val deniedAttempts = prefs.getInt(denialCountKey, 0)
        prefs.edit().putInt(denialCountKey, deniedAttempts + 1).apply()
    }

    fun reset() {
        prefs.edit().remove(denialCountKey).apply()
    }

    private companion object {
        const val denialCountKey = "exact_alarm_denial_count"
    }
}
