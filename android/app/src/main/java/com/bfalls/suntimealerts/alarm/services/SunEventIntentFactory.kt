package com.bfalls.suntimealerts.alarm.services

import android.content.ComponentName
import android.content.Intent
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

object SunEventIntentFactory {
    const val ACTION_SUN_EVENT_ALARM = "com.bfalls.suntimealerts.SUN_EVENT_ALARM"
    const val ACTION_DEBUG_RECONCILE_ALARMS = "com.bfalls.suntimealerts.DEBUG_RECONCILE_ALARMS"
    private const val RECEIVER_PACKAGE = "com.bfalls.suntimealerts"

    data class Identity(
        val alarmId: String,
        val date: LocalDate,
        val eventType: SunEventType,
        val requestCode: Int
    )

    fun computeIdentity(alarmId: String, date: LocalDate, eventType: SunEventType): Identity {
        return Identity(
            alarmId = alarmId,
            date = date,
            eventType = eventType,
            requestCode = computeRequestCode(alarmId, date, eventType)
        )
    }

    fun computeRequestCode(alarmId: String, date: LocalDate, eventType: SunEventType): Int {
        val seed = "${alarmId.lowercase()}|${eventType.name}|$date"
        return abs(seed.hashCode())
    }

    fun buildIdentityIntent(
        alarmId: String,
        eventType: SunEventType,
        date: LocalDate,
        action: String = ACTION_SUN_EVENT_ALARM,
        attachComponent: Boolean = true
    ): Intent {
        return Intent(action).apply {
            if (attachComponent) {
                component = ComponentName(RECEIVER_PACKAGE, SunEventReceiver::class.java.name)
            }
            putExtra("type", eventType.name)
            putExtra("alarmId", alarmId)
            putExtra("date", date.toString())
        }
    }

    fun buildAlarmIntent(
        alarmId: String,
        eventType: SunEventType,
        label: String,
        offsetMinutes: Int,
        date: LocalDate,
        zoneId: ZoneId,
        soundUri: String?,
        vibrate: Boolean,
        coordinate: Coordinate,
        attachComponent: Boolean = true
    ): Intent {
        return Intent(ACTION_SUN_EVENT_ALARM).apply {
            if (attachComponent) {
                component = ComponentName(RECEIVER_PACKAGE, SunEventReceiver::class.java.name)
            }
            putExtra("type", eventType.name)
            putExtra("alarmId", alarmId)
            putExtra("label", label)
            putExtra("offsetMinutes", offsetMinutes)
            putExtra("zoneId", zoneId.id)
            putExtra("soundUri", soundUri)
            putExtra("vibrate", vibrate)
            putExtra("latitude", coordinate.latitude)
            putExtra("longitude", coordinate.longitude)
            putExtra("date", date.toString())
        }
    }

    fun describe(identity: Identity, triggerAtMillis: Long, zoneId: ZoneId): String {
        val scheduledTime = java.time.Instant.ofEpochMilli(triggerAtMillis).atZone(zoneId)
        return "id=${identity.alarmId}, event=${identity.eventType.name.lowercase()}, " +
            "date=${identity.date}, requestCode=${identity.requestCode}, " +
            "trigger=${scheduledTime.toLocalDate()} ${scheduledTime.toLocalTime()} ${scheduledTime.zone}"
    }
}
