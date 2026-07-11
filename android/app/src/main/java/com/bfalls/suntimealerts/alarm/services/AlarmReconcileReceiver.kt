package com.bfalls.suntimealerts.alarm.services

import android.app.AlarmManager
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
                val reconciler = AlarmReconciler(context.applicationContext)
                if (action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
                    reconciler.reconcileAfterExactAlarmGrant(reason)
                } else {
                    reconciler.reconcile(reason)
                }
            } catch (t: Throwable) {
                Log.e("AlarmReconcileReceiver", "Failed to reconcile alarms on $action", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
