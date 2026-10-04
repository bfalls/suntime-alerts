package com.bfalls.suntimealerts.alarm.presentation.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Locale

class SkyDashboardTextTest {
    @Test
    fun `event labels honor clock preference and distinguish other dates`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val now = ZonedDateTime.parse("2026-10-04T15:01:00-06:00[America/Boise]")
            assertEquals("15:01", SkyDashboardText.time(now, now, true))
            assertEquals("3:01 PM", SkyDashboardText.time(now, now, false))
            assertEquals("Oct 5, 7:46 AM", SkyDashboardText.time(now.plusDays(1).withHour(7).withMinute(46), now, false))
            assertTrue(SkyDashboardText.time(now.minusDays(1), now, true).startsWith("Oct 3"))
        } finally { Locale.setDefault(previous) }
    }

    @Test
    fun `countdowns use elapsed time across the daylight saving gap`() {
        val start = ZonedDateTime.parse("2026-03-08T01:45:00-07:00[America/Boise]")
        val end = ZonedDateTime.parse("2026-03-08T03:15:00-06:00[America/Boise]")
        assertEquals("in 30 minutes", SkyDashboardText.until(start, end))
        assertEquals("in 29 minutes", SkyDashboardText.until(start.plusMinutes(1), end))
        assertEquals("in 1 minute", SkyDashboardText.until(end.minusSeconds(10), end))
        assertEquals("Unavailable", SkyDashboardText.duration(null))
        assertEquals("0m", SkyDashboardText.duration(Duration.ZERO))
    }
}
