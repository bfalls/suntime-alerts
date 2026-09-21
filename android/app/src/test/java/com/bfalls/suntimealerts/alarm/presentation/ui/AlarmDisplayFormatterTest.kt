package com.bfalls.suntimealerts.alarm.presentation.ui

import com.bfalls.suntimealerts.alarm.domain.model.ALL_DAYS_MASK
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import java.time.ZoneId
import java.time.ZonedDateTime
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

    @Test
    fun oneShotTimingMessageExplainsPastTrigger() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0, zone)
        val sunrise = ZonedDateTime.of(2024, 1, 1, 7, 0, 0, 0, zone)

        assertEquals(
            "Fires tomorrow.",
            oneShotTimingMessage(
                type = SunEventType.SUNRISE,
                offsetMinutes = 30,
                sunrise = sunrise,
                sunset = null,
                now = now
            )
        )
    }

    @Test
    fun oneShotTimingMessageShowsRelativeMinutes() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2024, 1, 1, 7, 20, 0, 0, zone)
        val sunrise = ZonedDateTime.of(2024, 1, 1, 7, 0, 0, 0, zone)

        assertEquals(
            "Fires in about 10 minutes.",
            oneShotTimingMessage(
                type = SunEventType.SUNRISE,
                offsetMinutes = 30,
                sunrise = sunrise,
                sunset = null,
                now = now
            )
        )
    }

    @Test
    fun oneShotTimingMessageShowsRelativeHours() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2024, 1, 1, 10, 0, 0, 0, zone)
        val sunset = ZonedDateTime.of(2024, 1, 1, 17, 0, 0, 0, zone)

        assertEquals(
            "Fires in about 7 hours.",
            oneShotTimingMessage(
                type = SunEventType.SUNSET,
                offsetMinutes = 0,
                sunrise = null,
                sunset = sunset,
                now = now
            )
        )
    }
}
