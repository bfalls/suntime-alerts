package com.bfalls.suntimealerts.alarm.services

import android.Manifest
import android.R
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bfalls.suntimealerts.alarm.data.SettingsStore
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.model.UserSettings
import com.bfalls.suntimealerts.alarm.domain.model.formatOffset
import com.bfalls.suntimealerts.alarm.domain.service.AlarmOccurrenceCalculator
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import com.bfalls.suntimealerts.alarm.services.SunEventIntentFactory
import com.bfalls.suntimealerts.alarm.services.SunEventIntentFactory.ACTION_SUN_EVENT_ALARM
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

interface AlarmScheduler {
    fun schedule(
        alarmId: String,
        eventType: SunEventType,
        triggerAtMillis: Long,
        zoneId: ZoneId,
        label: String,
        offsetMinutes: Int,
        date: LocalDate,
        soundUri: String?,
        vibrate: Boolean,
        coordinate: Coordinate
    )

    fun hasScheduledOccurrence(
        alarmId: String,
        eventType: SunEventType,
        date: LocalDate
    ): Boolean

    fun cancelOccurrence(
        alarmId: String,
        eventType: SunEventType,
        date: LocalDate
    )
}

class NotificationScheduler(private val context: Context) : AlarmScheduler {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(
        alarmId: String,
        eventType: SunEventType,
        triggerAtMillis: Long,
        zoneId: ZoneId,
        label: String,
        offsetMinutes: Int,
        date: LocalDate,
        soundUri: String?,
        vibrate: Boolean,
        coordinate: Coordinate
    ) {
        val identity = SunEventIntentFactory.computeIdentity(alarmId, date, eventType)
        val intent = SunEventIntentFactory.buildAlarmIntent(
            alarmId = alarmId,
            eventType = eventType,
            label = label,
            offsetMinutes = offsetMinutes,
            date = date,
            zoneId = zoneId,
            soundUri = soundUri,
            vibrate = vibrate,
            coordinate = coordinate
        )
        val pending = PendingIntent.getBroadcast(
            context,
            identity.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        Log.i(
            "NotificationScheduler",
            "Scheduling ${formatOffset(offsetMinutes)} ${eventType.name.lowercase()} (${SunEventIntentFactory.describe(identity, triggerAtMillis, zoneId)})"
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
    }

    override fun cancelOccurrence(
        alarmId: String,
        eventType: SunEventType,
        date: LocalDate
    ) {
        cancelForOccurrence(alarmId, eventType, date)
    }

    override fun hasScheduledOccurrence(
        alarmId: String,
        eventType: SunEventType,
        date: LocalDate
    ): Boolean {
        val identity = SunEventIntentFactory.computeIdentity(alarmId, date, eventType)
        val intent = SunEventIntentFactory.buildIdentityIntent(
            alarmId = alarmId,
            eventType = eventType,
            date = date,
            attachComponent = true
        )
        val pending = PendingIntent.getBroadcast(
            context,
            identity.requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) return true

        val legacyRequestCode = abs((alarmId + date.toString()).hashCode())
        val legacyPending = PendingIntent.getBroadcast(
            context,
            legacyRequestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        return legacyPending != null
    }

    private fun cancelForOccurrence(
        alarmId: String,
        eventType: SunEventType,
        date: LocalDate
    ) {
        val identity = SunEventIntentFactory.computeIdentity(alarmId, date, eventType)
        val intent = SunEventIntentFactory.buildIdentityIntent(
            alarmId = alarmId,
            eventType = eventType,
            date = date,
            attachComponent = true
        )
        val pending = PendingIntent.getBroadcast(
            context,
            identity.requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: PendingIntent.getBroadcast(
            context,
            identity.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val legacyRequestCode = abs((alarmId + date.toString()).hashCode())
        val legacyPending = PendingIntent.getBroadcast(
            context,
            legacyRequestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: PendingIntent.getBroadcast(
            context,
            legacyRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
        alarmManager.cancel(legacyPending)
        pending.cancel()
        legacyPending.cancel()
        val verification = PendingIntent.getBroadcast(
            context,
            identity.requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        Log.i(
            "NotificationScheduler",
            "Canceled alarm id=${identity.alarmId}, event=${identity.eventType.name.lowercase()}, " +
                "date=${identity.date}, requestCode=${identity.requestCode}, remainingPendingIntent=${verification != null}"
        )
    }
}

class SunEventReceiver : BroadcastReceiver() {
    companion object {
        const val channelId = "sun_event_channel"
        const val actionDismiss = "com.bfalls.suntimealerts.ACTION_DISMISS_ALARM"

        fun channelIdForAlarm(alarmId: String): String = "${channelId}_$alarmId"

        fun ensureChannelExists(
            context: Context,
            alarmId: String,
            soundUri: String?,
            vibrate: Boolean
        ): NotificationChannel? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val id = channelIdForAlarm(alarmId)
            val desiredSoundUri = parseSoundUri(soundUri)
            val existing = manager.getNotificationChannel(id)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            if (existing != null) {
                val existingSound = existing.sound
                val soundMatches = existingSound == desiredSoundUri
                val vibrationMatches = existing.shouldVibrate() == vibrate
                if (soundMatches && vibrationMatches) return existing
                manager.deleteNotificationChannel(id)
            }

            val channel = NotificationChannel(
                id,
                "Suntime Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for sunrise and sunset alerts"
                enableVibration(vibrate)
                if (vibrate) {
                    vibrationPattern = longArrayOf(0, 500, 500, 500)
                }
                enableLights(true)
                setSound(desiredSoundUri, audioAttrs)
            }
            manager.createNotificationChannel(channel)
            return channel
        }

        private fun parseSoundUri(soundUri: String?): Uri? = when {
            soundUri == null -> Settings.System.DEFAULT_ALARM_ALERT_URI
            soundUri.isBlank() -> null
            else -> runCatching { Uri.parse(soundUri) }.getOrNull()
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == actionDismiss) {
            val alarmId = intent.getStringExtra("alarmId") ?: return
            NotificationManagerCompat.from(context).cancel(alarmId.hashCode())
            return
        }
        if (intent.action != ACTION_SUN_EVENT_ALARM) {
            Log.w("SunEventReceiver", "Ignoring intent with unexpected action: ${intent.action}")
            return
        }

        val typeValue = intent.getStringExtra("type") ?: return
        val eventType = runCatching { SunEventType.valueOf(typeValue) }.getOrNull() ?: return
        val offsetMinutes = intent.getIntExtra("offsetMinutes", 0)
        val label = intent.getStringExtra("label").orEmpty()
        val alarmId = intent.getStringExtra("alarmId") ?: UUID.randomUUID().toString()
        val soundUriString = intent.getStringExtra("soundUri")
        val vibrate = intent.getBooleanExtra("vibrate", true)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(
                "SunEventReceiver",
                "Skipping ${eventType.name.lowercase()} alarm (id=$alarmId) because POST_NOTIFICATIONS is not granted."
            )
            return
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = ensureChannelExists(context, alarmId, soundUriString, vibrate)
        val notificationChannelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel?.id ?: channelIdForAlarm(alarmId)
        } else {
            channelId
        }
        val channelImportance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (channel ?: notificationManager.getNotificationChannel(notificationChannelId))?.importance
                ?: NotificationManager.IMPORTANCE_DEFAULT
        } else {
            NotificationManager.IMPORTANCE_DEFAULT
        }
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!notificationsEnabled) {
            Log.w(
                "SunEventReceiver",
                "Notifications are disabled for the app; unable to show ${eventType.name.lowercase()} alarm (id=$alarmId)."
            )
            return
        }
        if (channelImportance == NotificationManager.IMPORTANCE_NONE) {
            Log.w(
                "SunEventReceiver",
                "Notification channel \"$channelId\" is blocked; unable to show ${eventType.name.lowercase()} alarm (id=$alarmId). " +
                    "Prompt the user to re-enable Suntime Alerts notifications in system settings."
            )
            return
        }

        val title = if (eventType == SunEventType.SUNRISE) "Sunrise alarm" else "Sunset alarm"
        val anchor = if (eventType == SunEventType.SUNRISE) "sunrise" else "sunset"
        val offsetText = formatOffset(offsetMinutes)
        val parsedSoundUri = parseSoundUri(soundUriString)
        val vibrationPattern = if (vibrate) longArrayOf(0, 500, 500, 500) else longArrayOf(0L)
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

        val dismissIntent = Intent(context, SunEventReceiver::class.java).apply {
            action = actionDismiss
            putExtra("alarmId", alarmId)
        }

        val contentIntent = PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, notificationChannelId)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setDeleteIntent(contentIntent)
            .apply {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    if (parsedSoundUri != null) {
                        setSound(parsedSoundUri)
                    }
                    setVibrate(vibrationPattern)
                }
            }
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(alarmId.hashCode(), notification)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val zoneId = intent.getStringExtra("zoneId")?.let { raw ->
                    runCatching { ZoneId.of(raw) }.getOrDefault(ZoneId.systemDefault())
                } ?: ZoneId.systemDefault()
                rescheduleNextOccurrence(
                    appContext = context.applicationContext,
                    alarmId = alarmId,
                    zoneId = zoneId,
                    sourceIntent = intent
                )
            } catch (t: Throwable) {
                Log.e("SunEventReceiver", "Failed to reschedule next occurrence for alarm $alarmId", t)
            } finally {
                pendingResult.finish()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            val op = "android:use_full_screen_intent" // constant not available pre-Upside Down Cake in older SDKs
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                appOps.unsafeCheckOpNoThrow(
                    op,
                    context.applicationInfo.uid,
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    op,
                    context.applicationInfo.uid,
                    context.packageName
                )
            }
            if (mode != AppOpsManager.MODE_ALLOWED) {
                Log.w(
                    "SunEventReceiver",
                    "Full-screen intent not allowed by user/system (mode=$mode). " +
                            "Guide the user to allow full-screen notifications in system settings."
                )
            }
        }
    }

    private suspend fun rescheduleNextOccurrence(
        appContext: Context,
        alarmId: String,
        zoneId: ZoneId,
        sourceIntent: Intent
    ) {
        val settingsStore = SettingsStore(appContext)
        val settings = settingsStore.load()
        val alarm = settings.alarms.firstOrNull { it.id == alarmId }
        if (alarm == null) {
            Log.i("SunEventReceiver", "Alarm $alarmId is missing; skipping reschedule.")
            return
        }
        if (!alarm.enabled) {
            Log.i("SunEventReceiver", "Alarm $alarmId is disabled; skipping reschedule.")
            return
        }
        if (!alarm.isRecurring()) {
            disableOneShotAlarm(alarmId, settings.alarms, settingsStore)
            return
        }
        val coordinate = resolveCoordinate(settings, sourceIntent)
        if (coordinate == null) {
            Log.w("SunEventReceiver", "No coordinate available to reschedule alarm $alarmId; skipping.")
            return
        }

        val occurrenceCalculator = AlarmOccurrenceCalculator(SunTimesCalculator())
        val nextOccurrence = occurrenceCalculator.nextOccurrence(alarm, coordinate, zoneId) ?: return

        NotificationScheduler(appContext).schedule(
            alarmId = alarm.id,
            eventType = alarm.type,
            triggerAtMillis = nextOccurrence.triggerAtMillis,
            zoneId = zoneId,
            label = alarm.label,
            offsetMinutes = alarm.offsetMinutes,
            date = nextOccurrence.date,
            soundUri = alarm.soundUri,
            vibrate = alarm.vibrate ?: true,
            coordinate = coordinate
        )
    }

    private fun SunAlarm.isRecurring(): Boolean = recurrenceDays != null && recurrenceDays != 0

    private suspend fun disableOneShotAlarm(
        alarmId: String,
        alarms: List<SunAlarm>,
        settingsStore: SettingsStore
    ) {
        val updated = alarms.map { existing ->
            if (existing.id == alarmId) existing.copy(enabled = false) else existing
        }
        settingsStore.saveAlarms(updated)
        Log.i("SunEventReceiver", "Disabled one-shot alarm $alarmId after firing.")
    }

    private fun resolveCoordinate(settings: UserSettings, sourceIntent: Intent): Coordinate? {
        return when (settings.locationMode) {
            LocationMode.FIXED -> settings.fixedLocation
            LocationMode.DEVICE -> {
                val lat = sourceIntent.getDoubleExtra("latitude", Double.NaN)
                val lon = sourceIntent.getDoubleExtra("longitude", Double.NaN)
                if (lat.isFinite() && lon.isFinite()) Coordinate(lat, lon) else null
            }
        }
    }
}
