package com.bfalls.suntimealerts.alarm.presentation.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Observe format changes independently of the astronomy snapshot or clock ticks. */
@Composable
internal fun rememberSystem24HourFormat(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var use24h by remember(context) { mutableStateOf(DateFormat.is24HourFormat(context)) }

    DisposableEffect(context, lifecycleOwner) {
        val resolver = context.contentResolver
        fun refresh() { use24h = DateFormat.is24HourFormat(context) }
        val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { refresh() }
        }
        val resumeObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.TIME_12_24), false, settingsObserver)
        lifecycleOwner.lifecycle.addObserver(resumeObserver)
        refresh()
        onDispose {
            resolver.unregisterContentObserver(settingsObserver)
            lifecycleOwner.lifecycle.removeObserver(resumeObserver)
        }
    }
    return use24h
}
