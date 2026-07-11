package com.bfalls.suntimealerts.alarm.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bfalls.suntimealerts.MainActivity
import com.bfalls.suntimealerts.alarm.services.AlarmReconciler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DebugAlarmTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val overrides = DebugAlarmTestOverrides(context.applicationContext)

        when (action) {
            ACTION_SET_OVERRIDE -> {
                val override = DebugAlarmOverride.fromWireName(intent.getStringExtra(EXTRA_OVERRIDE))
                val enabled = intent.getBooleanExtra(EXTRA_ENABLED, false)
                if (override == null) {
                    Log.w("DebugAlarmTestReceiver", "Ignoring unknown override payload")
                    return
                }
                overrides.setEnabled(override, enabled)
                Log.i("DebugAlarmTestReceiver", "Set ${override.wireName}=$enabled")
            }

            ACTION_CLEAR_OVERRIDES -> {
                overrides.clearAll()
                Log.i("DebugAlarmTestReceiver", "Cleared all debug overrides")
            }

            ACTION_RECONCILE_BOOT,
            ACTION_RECONCILE_TIMEZONE -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        val reason = if (action == ACTION_RECONCILE_BOOT) {
                            "debug_boot_reconcile"
                        } else {
                            "debug_timezone_reconcile"
                        }
                        AlarmReconciler(context.applicationContext).reconcile(reason)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_SIMULATE_APP_RESUME -> {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(MainActivity.EXTRA_DEBUG_REFRESH_READINESS, true)
                }
                context.startActivity(launchIntent)
            }
        }
    }

    companion object {
        const val ACTION_SET_OVERRIDE = "com.bfalls.suntimealerts.debug.SET_OVERRIDE"
        const val ACTION_CLEAR_OVERRIDES = "com.bfalls.suntimealerts.debug.CLEAR_OVERRIDES"
        const val ACTION_RECONCILE_BOOT = "com.bfalls.suntimealerts.debug.RECONCILE_BOOT"
        const val ACTION_RECONCILE_TIMEZONE = "com.bfalls.suntimealerts.debug.RECONCILE_TIMEZONE"
        const val ACTION_SIMULATE_APP_RESUME = "com.bfalls.suntimealerts.debug.SIMULATE_APP_RESUME"
        const val EXTRA_OVERRIDE = "override"
        const val EXTRA_ENABLED = "enabled"
    }
}
