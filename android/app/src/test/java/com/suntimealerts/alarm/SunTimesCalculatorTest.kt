package com.suntimealerts.alarm

import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

class SunTimesCalculatorTest {
    @Test
    fun `nyc solstice times roughly match`() {
        val calculator = SunTimesCalculator()
        val date = LocalDate.of(2023, 6, 21)
        val zone = ZoneId.of("America/New_York")
        val times = calculator.calculateSunTimes(date, Coordinate(40.7128, -74.0060), zone)
        val sunrise = times.sunrise ?: error("sunrise missing")
        val sunset = times.sunset ?: error("sunset missing")

        assertTrue(abs(sunrise.hour * 60 + sunrise.minute - (5 * 60 + 24)) <= 5)
        assertTrue(abs(sunset.hour * 60 + sunset.minute - (20 * 60 + 30)) <= 5)
    }
}
