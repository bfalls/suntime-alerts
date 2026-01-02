package com.bfalls.suntimealerts.alarm.data

import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.withDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {

    @Test
    fun migratesLegacyConfigsIntoAlarms() {
        val alarms = migrateLegacyAlarms(
            sunriseEnabled = true,
            sunriseOffset = 15,
            sunsetEnabled = false,
            sunsetOffset = -30
        )

        assertEquals(2, alarms.size)
        val sunrise = alarms.first { it.type == SunEventType.SUNRISE }
        val sunset = alarms.first { it.type == SunEventType.SUNSET }
        assertEquals(15, sunrise.offsetMinutes)
        assertTrue(sunrise.enabled)
        assertEquals(127, sunrise.recurrenceDays)
        assertTrue(sunrise.vibrate == true)
        assertEquals(null, sunrise.soundUri)
        assertEquals(-30, sunset.offsetMinutes)
        assertFalse(sunset.enabled)
        assertEquals(127, sunset.recurrenceDays)
        assertTrue(sunset.vibrate == true)
        assertEquals(null, sunset.soundUri)
    }

    @Test
    fun normalizesMissingFieldsWithDefaults() {
        val legacyAlarm = SunAlarm(
            type = SunEventType.SUNRISE,
            offsetMinutes = 0,
            label = "Legacy",
            enabled = true
        )
        val normalized = legacyAlarm.withDefaults()

        assertEquals(null, normalized.recurrenceDays)
        assertTrue(normalized.vibrate == true)
        assertEquals(null, normalized.soundUri)
    }
}
