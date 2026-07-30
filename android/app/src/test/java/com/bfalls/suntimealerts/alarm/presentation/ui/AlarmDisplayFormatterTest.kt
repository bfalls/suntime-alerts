package com.bfalls.suntimealerts.alarm.presentation.ui

import com.bfalls.suntimealerts.alarm.domain.model.ALL_DAYS_MASK
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmDisplayFormatterTest {
    @Test
    fun zeroOffsetDisplaysAtSunrise() {
        assertEquals("At sunrise", alarmTimingText(SunEventType.SUNRISE, 0))
    }

    @Test
    fun zeroOffsetDisplaysAtSunset() {
        assertEquals("At sunset", alarmTimingText(SunEventType.SUNSET, 0))
    }

    @Test
    fun omitsZeroMinutesForHourOnlyOffsets() {
        assertEquals("1h after sunrise", alarmTimingText(SunEventType.SUNRISE, 60))
        assertEquals("1h before sunset", alarmTimingText(SunEventType.SUNSET, -60))
    }

    @Test
    fun includesHoursAndMinutesForMixedOffsets() {
        assertEquals("1h 30m after sunset", alarmTimingText(SunEventType.SUNSET, 90))
        assertEquals("1h 15m before sunrise", alarmTimingText(SunEventType.SUNRISE, -75))
    }

    @Test
    fun secondaryTextSuppressesDefaultEventLabel() {
        val alarm = SunAlarm(
            type = SunEventType.SUNRISE,
            offsetMinutes = 0,
            label = "Sunrise",
            enabled = true,
            recurrenceDays = ALL_DAYS_MASK
        )

        assertEquals("every day", alarmSecondaryText(alarm))
    }

    @Test
    fun secondaryTextKeepsCustomLabel() {
        val alarm = SunAlarm(
            type = SunEventType.SUNSET,
            offsetMinutes = 15,
            label = "Gym",
            enabled = true,
            recurrenceDays = ALL_DAYS_MASK
        )

        assertEquals("Gym, every day", alarmSecondaryText(alarm))
    }
}
