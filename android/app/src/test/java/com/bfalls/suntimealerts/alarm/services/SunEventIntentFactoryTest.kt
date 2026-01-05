package com.bfalls.suntimealerts.alarm.services

import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class SunEventIntentFactoryTest {

    private val sampleDate = LocalDate.of(2024, 1, 1)

    @Test
    fun `request codes are deterministic for the same inputs`() {
        val first = SunEventIntentFactory.computeRequestCode("alarm-1", sampleDate, SunEventType.SUNRISE)
        val second = SunEventIntentFactory.computeRequestCode("alarm-1", sampleDate, SunEventType.SUNRISE)

        assertEquals(first, second)
    }

    @Test
    fun `request codes vary across inputs`() {
        val base = SunEventIntentFactory.computeRequestCode("alarm-1", sampleDate, SunEventType.SUNRISE)
        val differentAlarm = SunEventIntentFactory.computeRequestCode("alarm-2", sampleDate, SunEventType.SUNRISE)
        val differentDate = SunEventIntentFactory.computeRequestCode("alarm-1", sampleDate.plusDays(1), SunEventType.SUNRISE)
        val differentType = SunEventIntentFactory.computeRequestCode("alarm-1", sampleDate, SunEventType.SUNSET)

        assertNotEquals(base, differentAlarm)
        assertNotEquals(base, differentDate)
        assertNotEquals(base, differentType)
    }

    @Test
    fun `action and receiver metadata are stable`() {
        assertEquals(SunEventIntentFactory.ACTION_SUN_EVENT_ALARM, "com.bfalls.suntimealerts.SUN_EVENT_ALARM")
        assertEquals(SunEventIntentFactory.ACTION_DEBUG_RECONCILE_ALARMS, "com.bfalls.suntimealerts.DEBUG_RECONCILE_ALARMS")
        assertEquals("com.bfalls.suntimealerts.alarm.services.SunEventReceiver", SunEventReceiver::class.java.name)
    }
}
