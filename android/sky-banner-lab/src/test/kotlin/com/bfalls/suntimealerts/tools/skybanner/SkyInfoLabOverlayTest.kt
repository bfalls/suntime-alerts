package com.bfalls.suntimealerts.tools.skybanner

import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.service.SkyInfoCalculator
import com.bfalls.suntimealerts.alarm.presentation.ui.SkyInfoMessages
import com.bfalls.suntimealerts.alarm.presentation.ui.SkyInfoPresentation
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkyInfoLabOverlayTest {
    private val coordinate = Coordinate(43.615, -116.2023)
    private val noon = LocalDate.of(2026, 10, 4).atTime(12, 0).atZone(ZoneId.of("America/Boise"))

    @Test
    fun `lab uses the shared messages and keeps countdown ticks independent of rotation`() {
        val calculator = SkyInfoCalculator()
        val sunset = calculator.calculate(noon, coordinate).today.sunset!!
        val overlay = SkyInfoLabOverlay()
        overlay.update(calculator.calculate(sunset.minusMinutes(42), coordinate), 0)
        assertEquals("Sunset in 42 minutes", overlay.frame(0)!!.current.text)
        val tick = calculator.calculate(sunset.minusMinutes(41), coordinate)
        overlay.update(tick, 4_000)
        assertEquals("Sunset in 41 minutes", overlay.frame(4_000)!!.current.text)
        assertEquals(SkyInfoMessages.forMetrics(tick)[1], overlay.frame(8_700)!!.current)
    }

    @Test
    fun `fade follows shared timing and does not retain expired outgoing messages`() {
        val overlay = SkyInfoLabOverlay()
        overlay.update(SkyInfoCalculator().calculate(noon, coordinate), 0)
        val before = overlay.frame(SkyInfoPresentation.ROTATION_MILLIS - 1)!!
        assertEquals(1f, before.currentAlpha)
        assertNull(before.previous)
        val start = overlay.frame(SkyInfoPresentation.ROTATION_MILLIS)!!
        assertEquals(before.current, start.previous)
        assertEquals(1f, start.previousAlpha)
        assertEquals(0f, start.currentAlpha)
        val middle = overlay.frame(SkyInfoPresentation.ROTATION_MILLIS + 225)!!
        assertTrue(middle.previousAlpha in 0f..1f && middle.previousAlpha < 1f)
        assertTrue(middle.currentAlpha > 0f && middle.currentAlpha < 1f)
        val complete = overlay.frame(SkyInfoPresentation.ROTATION_MILLIS + 650)!!
        assertEquals(1f, complete.currentAlpha)
        assertEquals(0f, complete.previousAlpha)
        assertNull(complete.previous)
    }

    @Test
    fun `scrubbing into a new period resets to that periods first message`() {
        val calculator = SkyInfoCalculator()
        val overlay = SkyInfoLabOverlay()
        overlay.update(calculator.calculate(noon, coordinate), 0)
        overlay.frame(9_000)
        val night = calculator.calculate(noon.withHour(23), coordinate)
        overlay.update(night, 10_000)
        val frame = overlay.frame(10_000)!!
        assertEquals(SkyInfoMessages.forMetrics(night).first(), frame.current)
        assertEquals(1f, frame.currentAlpha)
        assertNull(frame.previous)
    }

    @Test
    fun `renders four periods and circle option at existing lab banner size`() {
        val calculator = SkyInfoCalculator()
        val day = calculator.calculate(noon, coordinate).today
        val views = listOf(
            "dawn" to day.sunrise!!.minusMinutes(10),
            "daylight" to noon,
            "twilight" to day.sunset!!.plusMinutes(10),
            "night" to noon.withHour(23)
        )
        val output = File("build/overlay-previews").apply { mkdirs() }
        SwingUtilities.invokeAndWait {
            views.forEach { (name, time) ->
                val panel = SkyBannerPanel()
                panel.setSize(860, 220)
                panel.state = PreviewState(time, coordinate)
                assertEquals(220, panel.preferredSize.height)
                assertEquals(SkyInfoMessages.forMetrics(panel.metrics!!).joinToString(". ") { it.text }, panel.accessibleContext.accessibleDescription)
                val image = BufferedImage(860, 220, BufferedImage.TYPE_INT_ARGB)
                image.createGraphics().let { graphics -> panel.paint(graphics); graphics.dispose() }
                ImageIO.write(image, "png", File(output, "lab-$name.png"))
                panel.state = panel.state!!.copy(infoIconCircleEnabled = false)
                val noCircle = BufferedImage(860, 220, BufferedImage.TYPE_INT_ARGB)
                noCircle.createGraphics().let { graphics -> panel.paint(graphics); graphics.dispose() }
                // The toggle changes the icon background, preserving dimensions.
                assertEquals(image.width, noCircle.width)
                assertTrue(image.getRGB(0, 0, 860, 220, null, 0, 860).zip(noCircle.getRGB(0, 0, 860, 220, null, 0, 860).toList()).any { it.first != it.second })
                ImageIO.write(noCircle, "png", File(output, "lab-$name-no-circle.png"))
            }
        }
    }
}
