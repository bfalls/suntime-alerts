package com.bfalls.suntimealerts.alarm.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
//import androidx.privacysandbox.tools.core.model.Type
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import java.time.ZoneId

class NotificationScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(eventType: SunEventType, triggerAtMillis: Long, zoneId: ZoneId) {
        val intent = Intent(context, SunEventReceiver::class.java).apply {
            putExtra("type", eventType.name)
        }
        val pending = PendingIntent.getBroadcast(context, eventType.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
    }

    fun cancelAll() {
        SunEventType.values().forEach { type ->
            cancel(type)
        }
    }

    fun cancel(eventType: SunEventType) {
        val intent = Intent(context, SunEventReceiver::class.java).apply {
            putExtra("type", eventType.name)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            eventType.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }
}

class SunEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // TODO: build user-visible notification; placeholder for scaffold
    }
}
