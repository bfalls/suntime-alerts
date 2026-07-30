package com.bfalls.suntimealerts.alarm.data

import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.withDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {

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
