package com.bfalls.suntimealerts.alarm.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReconcileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val reason = action ?: "unknown"
                Log.i("AlarmReconcileReceiver", "Received $reason; reconciling alarms.")
                AlarmReconciler(context.applicationContext).reconcile(reason)
            } catch (t: Throwable) {
                Log.e("AlarmReconcileReceiver", "Failed to reconcile alarms on $action", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
