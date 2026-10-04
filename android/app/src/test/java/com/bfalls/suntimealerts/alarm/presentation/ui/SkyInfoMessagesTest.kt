package com.bfalls.suntimealerts.alarm.presentation.ui

import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.service.SkyInfoCalculator
import com.bfalls.suntimealerts.alarm.domain.service.SkyInfoPeriod
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SkyInfoMessagesTest {
    private val coordinate = Coordinate(43.615, -116.2023)
    private val zone = ZoneId.of("America/Boise")
    private val noon = LocalDate.of(2026, 10, 4).atTime(12, 0).atZone(zone)

    @Test
    fun `visible countdown changes at the next minute without changing its identity`() {
        val calculator = SkyInfoCalculator()
        val sunset = calculator.calculate(noon, coordinate).today.sunset!!
        val first = SkyInfoMessages.forMetrics(calculator.calculate(sunset.minusMinutes(42), coordinate)).first()
        val next = SkyInfoMessages.forMetrics(calculator.calculate(sunset.minusMinutes(41), coordinate)).first()
        assertEquals("Sunset in 42 minutes", first.text)
        assertEquals("Sunset in 41 minutes", next.text)
        assertEquals(first.id, next.id)
    }

    @Test
    fun `each period supplies two to five distinct pertinent messages`() {
        val calculator = SkyInfoCalculator()
        val day = calculator.calculate(noon, coordinate).today
        val viewingTimes = listOf(day.sunrise!!.minusMinutes(10), noon, day.sunset!!.plusMinutes(10), noon.withHour(23))
        assertEquals(SkyInfoPeriod.entries.toSet(), viewingTimes.map { calculator.calculate(it, coordinate).period }.toSet())
        viewingTimes.forEach { time ->
            val messages = SkyInfoMessages.forMetrics(calculator.calculate(time, coordinate))
            assertTrue(messages.size in 2..5)
            assertEquals(messages.size, messages.map { it.id }.distinct().size)
            assertTrue(messages.none { it.text.contains("\n") || it.text.contains("in 0 ") })
        }
    }

    @Test
    fun `expired light window is never advertised as active`() {
        val calculator = SkyInfoCalculator()
        val day = calculator.calculate(noon, coordinate).today
        val goldenEnd = day.crossings.single { !it.rising && it.altitudeDeg == -4.0 }.time
        val during = calculator.calculate(goldenEnd.minusMinutes(1), coordinate)
        assertTrue(SkyInfoMessages.forMetrics(during).any { it.text == "Golden hour ends in 1 minute" })
        val after = SkyInfoMessages.forMetrics(calculator.calculate(goldenEnd.plusSeconds(1), coordinate))
        assertTrue(after.none { it.text.startsWith("Golden hour ends") })
        assertTrue(after.any { it.text.startsWith("Blue hour ends") })
    }

    @Test
    fun `polar locations retain useful messages without invented countdowns`() {
        val calculator = SkyInfoCalculator()
        listOf("2026-06-21T12:00:00Z", "2026-12-21T12:00:00Z").forEach {
            val messages = SkyInfoMessages.forMetrics(calculator.calculate(ZonedDateTime.parse(it), Coordinate(85.0, 0.0)))
            assertTrue(messages.size in 2..5)
            assertTrue(messages.none { message -> message.text.startsWith("Sunrise in") || message.text.startsWith("Sunset in") })
        }
    }
}
