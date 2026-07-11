package com.bfalls.suntimealerts.utils

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import android.os.Build
import com.bfalls.suntimealerts.alarm.debug.DebugAlarmOverride
import com.bfalls.suntimealerts.alarm.debug.DebugAlarmTestOverrides

fun hasLocationPermission(context: Context): Boolean {
    val debugOverrides = DebugAlarmTestOverrides(context.applicationContext)
    if (debugOverrides.isEnabled(DebugAlarmOverride.LOCATION_PERMISSION_DENIED)) {
        return false
    }

    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    return fine || coarse
}

fun hasNotificationPermission(context: Context): Boolean {
    val debugOverrides = DebugAlarmTestOverrides(context.applicationContext)
    if (debugOverrides.isEnabled(DebugAlarmOverride.NOTIFICATION_PERMISSION_DENIED)) {
        return false
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}
