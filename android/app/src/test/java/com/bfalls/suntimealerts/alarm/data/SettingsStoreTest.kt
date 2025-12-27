package com.bfalls.suntimealerts.alarm.data

import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
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
        assertEquals(-30, sunset.offsetMinutes)
        assertFalse(sunset.enabled)
    }
}
