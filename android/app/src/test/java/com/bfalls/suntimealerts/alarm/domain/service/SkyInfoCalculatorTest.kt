package com.bfalls.suntimealerts.alarm.domain.service

import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

class SkyInfoCalculatorTest {
    private val coordinate = Coordinate(43.615, -116.2023)
    private val zone = ZoneId.of("America/Boise")
    private val date = LocalDate.of(2026, 10, 4)

    @Test
    fun `four periods follow solar events rather than fixed clock hours`() {
        val calculator = SkyInfoCalculator()
        val day = calculator.calculate(date.atTime(12, 0).atZone(zone), coordinate).today
        val dusk = day.crossings.single { !it.rising && it.altitudeDeg == -18.0 }.time
        assertEquals(SkyInfoPeriod.DAWN, calculator.calculate(day.sunrise!!.minusMinutes(10), coordinate).period)
        assertEquals(SkyInfoPeriod.DAYLIGHT, calculator.calculate(day.sunrise.plusMinutes(10), coordinate).period)
        assertEquals(SkyInfoPeriod.EVENING_TWILIGHT, calculator.calculate(day.sunset!!.plusMinutes(10), coordinate).period)
        assertEquals(SkyInfoPeriod.NIGHT, calculator.calculate(dusk.plusMinutes(10), coordinate).period)
        assertEquals(SkyInfoPeriod.DAYLIGHT, calculator.calculate(day.sunrise, coordinate).period)
        assertEquals(SkyInfoPeriod.EVENING_TWILIGHT, calculator.calculate(day.sunset, coordinate).period)
    }

    @Test
    fun `twilight and photographic crossings reach their documented altitudes`() {
        val metrics = SkyInfoCalculator().calculate(date.atTime(12, 0).atZone(zone), coordinate)
        assertEquals(10, metrics.today.crossings.size)
        metrics.today.crossings.forEach {
            val actual = SunTimesCalculator.sunAltAz(it.time, coordinate.latitude, coordinate.longitude).altitudeDeg
            assertTrue("${it.altitudeDeg}° crossing was $actual°", abs(actual - it.altitudeDeg) < 0.01)
        }
        val morning = metrics.today.crossings.filter { it.rising }
        assertEquals(listOf(-18.0, -12.0, -6.0, -4.0, 6.0), morning.map { it.altitudeDeg })
        val goldenStart = morning.single { it.altitudeDeg == -4.0 }.time
        val goldenEnd = morning.single { it.altitudeDeg == 6.0 }.time
        val during = SkyInfoCalculator().calculate(goldenStart.plusMinutes(2), coordinate)
        assertEquals(PhotoLight.GOLDEN, during.photoWindow!!.light)
        assertEquals(goldenEnd, during.photoWindow.end)
    }

    @Test
    fun `minute updates reuse daily scans but midnight and location changes invalidate them`() {
        val calculator = SkyInfoCalculator()
        val now = date.atTime(12, 0).atZone(zone)
        val first = calculator.calculate(now, coordinate)
        val tick = calculator.calculate(now.plusMinutes(1), coordinate)
        assertSame(first.today, tick.today)
        assertNotEquals(first.sunPosition, tick.sunPosition)
        val tomorrow = calculator.calculate(date.plusDays(1).atStartOfDay(zone), coordinate)
        assertEquals(date.plusDays(1), tomorrow.today.date)
        assertNotSame(first.today, tomorrow.today)
        assertTrue(tomorrow.nextSunrise!!.isAfter(tomorrow.now))
        assertNotSame(tomorrow.today, calculator.calculate(tomorrow.now, Coordinate(40.0, -105.0)).today)
    }

    @Test
    fun `darkness after midnight belongs to the adjoining night`() {
        val calculator = SkyInfoCalculator()
        val evening = calculator.calculate(date.atTime(23, 0).atZone(zone), coordinate)
        val afterMidnight = calculator.calculate(date.plusDays(1).atTime(1, 0).atZone(zone), coordinate)
        assertEquals(evening.astronomicalDarkness, afterMidnight.astronomicalDarkness)
        assertTrue(afterMidnight.nextDawn!!.time.isBefore(afterMidnight.nextSunrise))
    }

    @Test
    fun `scans follow 23 and 25 hour local days across DST`() {
        val calculator = SkyInfoCalculator()
        listOf(LocalDate.of(2026, 3, 8) to 23L, LocalDate.of(2026, 11, 1) to 25L).forEach { (date, hours) ->
            val start = date.atStartOfDay(zone)
            val end = date.plusDays(1).atStartOfDay(zone)
            assertEquals(hours, Duration.between(start, end).toHours())
            val metrics = calculator.calculate(date.atTime(12, 0).atZone(zone), coordinate)
            assertEquals(10, metrics.today.crossings.size)
            assertTrue(metrics.today.crossings.all { !it.time.isBefore(start) && it.time.isBefore(end) })
            assertTrue(metrics.today.daylight!! < Duration.ofHours(24))
        }
    }

    @Test
    fun `polar missing events stay unavailable`() {
        val calculator = SkyInfoCalculator()
        val polar = Coordinate(85.0, 0.0)
        val summer = calculator.calculate(ZonedDateTime.parse("2026-06-21T12:00:00Z"), polar)
        assertEquals(SkyInfoPeriod.DAYLIGHT, summer.period)
        assertNull(summer.nextSunset)
        assertNull(summer.today.daylight)
        assertNull(summer.astronomicalDarkness)
        val winter = calculator.calculate(ZonedDateTime.parse("2026-12-21T12:00:00Z"), polar)
        assertEquals(SkyInfoPeriod.NIGHT, winter.period)
        assertNull(winter.nextSunrise)
        assertNull(winter.photoWindow)
    }
}
