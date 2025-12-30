package com.bfalls.suntimealerts.alarm.services

import android.Manifest
import android.R
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AppOpsManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bfalls.suntimealerts.MainActivity
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.formatOffset
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

interface AlarmScheduler {
    fun schedule(
        alarmId: String,
        eventType: SunEventType,
        triggerAtMillis: Long,
        zoneId: ZoneId,
        label: String,
        offsetMinutes: Int,
        date: LocalDate
    )

    fun cancelAll()
}

class NotificationScheduler(private val context: Context) : AlarmScheduler {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences("sun_alarm_requests", Context.MODE_PRIVATE)
    private val requestCodesKey = "request_codes"
    private val channelId = "sun_event_channel"

    override fun schedule(
        alarmId: String,
        eventType: SunEventType,
        triggerAtMillis: Long,
        zoneId: ZoneId,
        label: String,
        offsetMinutes: Int,
        date: LocalDate
    ) {
        val intent = Intent(context, SunEventReceiver::class.java).apply {
            putExtra("type", eventType.name)
            putExtra("alarmId", alarmId)
            putExtra("label", label)
            putExtra("offsetMinutes", offsetMinutes)
            putExtra("zoneId", zoneId.id)
        }
        ensureChannelExists()
        val requestCode = requestCode(alarmId, date)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val scheduledTime = Instant.ofEpochMilli(triggerAtMillis).atZone(zoneId)
        Log.i(
            "NotificationScheduler",
            "Scheduling ${eventType.name.lowercase()} alarm (id=$alarmId, label=$label, offset=${formatOffset(offsetMinutes)}) " +
                "for ${scheduledTime.toLocalDate()} ${scheduledTime.toLocalTime()} ${scheduledTime.zone}"
        )
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            Log.w(
                "NotificationScheduler",
                "Exact alarms not permitted; scheduling inexact alarm instead."
            )
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
        rememberRequestCode(requestCode)
    }

    override fun cancelAll() {
        val codes = loadRequestCodes()
        codes.forEach { cancel(it) }
        prefs.edit().remove(requestCodesKey).apply()
    }

    private fun cancel(requestCode: Int) {
        val intent = Intent(context, SunEventReceiver::class.java).apply {
            action = "CANCEL"
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }

    private fun rememberRequestCode(code: Int) {
        val updated = loadRequestCodes().apply { add(code) }.map { it.toString() }.toSet()
        prefs.edit().putStringSet(requestCodesKey, updated).apply()
    }

    private fun loadRequestCodes(): MutableSet<Int> {
        return prefs.getStringSet(requestCodesKey, emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.toMutableSet()
            ?: mutableSetOf()
    }

    private fun requestCode(alarmId: String, date: LocalDate): Int {
        return abs((alarmId + date.toString()).hashCode())
    }

    private fun ensureChannelExists() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(channelId)
        if (existing != null) return

        val alarmSound = Settings.System.DEFAULT_ALARM_ALERT_URI
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            channelId,
            "Suntime Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for sunrise and sunset alerts"
            enableVibration(true)
            enableLights(true)
            setSound(alarmSound, audioAttrs)
        }
        manager.createNotificationChannel(channel)
    }
}

class SunEventReceiver : BroadcastReceiver() {
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onReceive(context: Context, intent: Intent) {
        val typeValue = intent.getStringExtra("type") ?: return
        val eventType = runCatching { SunEventType.valueOf(typeValue) }.getOrNull() ?: return
        val offsetMinutes = intent.getIntExtra("offsetMinutes", 0)
        val label = intent.getStringExtra("label").orEmpty()
        val alarmId = intent.getStringExtra("alarmId") ?: UUID.randomUUID().toString()

        val channelId = "sun_event_channel"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Suntime Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for sunrise and sunset alerts"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val title = if (eventType == SunEventType.SUNRISE) "Sunrise alarm" else "Sunset alarm"
        val anchor = if (eventType == SunEventType.SUNRISE) "sunrise" else "sunset"
        val offsetText = formatOffset(offsetMinutes)
        val body = buildString {
            if (label.isNotBlank()) {
                append(label).append(" • ")
            }
            if (offsetMinutes == 0) {
                append("At ").append(anchor)
            } else {
                append(offsetText).append(" ").append(anchor)
            }
        }

        Log.i(
            "SunEventReceiver",
            "Firing ${eventType.name.lowercase()} alarm (id=$alarmId, label=$label, offset=$offsetText)"
        )

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            alarmId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = PendingIntent.getActivity(
            context,
            alarmId.hashCode() + 1,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(contentIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(alarmId.hashCode(), notification)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            val op = "android:use_full_screen_intent" // constant not available pre-Upside Down Cake in older SDKs
            val mode = appOps.checkOpNoThrow(
                op,
                context.applicationInfo.uid,
                context.packageName
            )
            if (mode != AppOpsManager.MODE_ALLOWED) {
                Log.w(
                    "SunEventReceiver",
                    "Full-screen intent not allowed by user/system (mode=$mode). " +
                            "Guide the user to allow full-screen notifications in system settings."
                )
            }
        }
    }
}
