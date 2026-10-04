package com.bfalls.suntimealerts.tools.skybanner

import com.bfalls.suntimealerts.alarm.domain.service.SkyInfoMetrics
import com.bfalls.suntimealerts.alarm.presentation.ui.SkyInfoCategory
import com.bfalls.suntimealerts.alarm.presentation.ui.SkyInfoMessage
import com.bfalls.suntimealerts.alarm.presentation.ui.SkyInfoMessages
import com.bfalls.suntimealerts.alarm.presentation.ui.SkyInfoPresentation
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Arc2D
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import kotlin.math.cos
import kotlin.math.sin

internal data class SkyInfoFadeFrame(
    val current: SkyInfoMessage,
    val currentAlpha: Float,
    val previous: SkyInfoMessage?,
    val previousAlpha: Float
)

/** Swing's animation state; all content and timing come from the app's shared code. */
internal class SkyInfoLabOverlay {
    private var metrics: SkyInfoMetrics? = null
    private var messages: List<SkyInfoMessage> = emptyList()
    private var selectedId: String? = null
    private var previous: SkyInfoMessage? = null
    private var rotatedAt = 0L

    fun update(newMetrics: SkyInfoMetrics, elapsedMillis: Long) {
        val next = SkyInfoMessages.forMetrics(newMetrics)
        val reset = metrics?.period != newMetrics.period || metrics?.coordinate != newMetrics.coordinate ||
            metrics?.now?.zone != newMetrics.now.zone || next.none { it.id == selectedId }
        val idsChanged = messages.map { it.id } != next.map { it.id }
        if (reset) {
            selectedId = next.first().id
            previous = null
            rotatedAt = elapsedMillis
        } else if (idsChanged) {
            // Match Compose: membership changes restart the timeout, while
            // ordinary clock/countdown updates preserve the selected slot.
            rotatedAt = elapsedMillis
            previous = null
        }
        messages = next
        metrics = newMetrics
    }

    fun frame(elapsedMillis: Long): SkyInfoFadeFrame? {
        if (messages.isEmpty()) return null
        val elapsed = (elapsedMillis - rotatedAt).coerceAtLeast(0)
        if (elapsed >= SkyInfoPresentation.ROTATION_MILLIS) {
            val index = messages.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
            previous = messages[index]
            val steps = elapsed / SkyInfoPresentation.ROTATION_MILLIS
            selectedId = messages[((index + steps) % messages.size).toInt()].id
            rotatedAt += steps * SkyInfoPresentation.ROTATION_MILLIS
        }
        val age = (elapsedMillis - rotatedAt).coerceAtLeast(0)
        val previousAlpha = if (previous == null) 0f else
            (1f - age.toFloat() / SkyInfoPresentation.FADE_OUT_MILLIS).coerceIn(0f, 1f)
        val currentAlpha = if (previous == null) 1f else
            ((age - SkyInfoPresentation.FADE_IN_DELAY_MILLIS).toFloat() / SkyInfoPresentation.FADE_IN_MILLIS).coerceIn(0f, 1f)
        if (previousAlpha == 0f && currentAlpha == 1f) previous = null
        return SkyInfoFadeFrame(messages.first { it.id == selectedId }, currentAlpha, previous, previousAlpha)
    }

    fun paint(graphics: Graphics2D, width: Int, height: Int, showCircle: Boolean, elapsedMillis: Long) {
        val frame = frame(elapsedMillis) ?: return
        frame.previous?.let { drawMessage(graphics, it, frame.previousAlpha, width, height, showCircle) }
        drawMessage(graphics, frame.current, frame.currentAlpha, width, height, showCircle)
    }

    private fun drawMessage(graphics: Graphics2D, message: SkyInfoMessage, alpha: Float, width: Int, height: Int, showCircle: Boolean) {
        if (alpha <= 0f) return
        val g = graphics.create() as Graphics2D
        g.composite = AlphaComposite.SrcOver.derive(alpha)
        val scale = (height * 0.25 / 48.0).coerceIn(0.5, 1.0)
        g.font = Font("SansSerif", Font.BOLD, (14 * scale).toInt())
        val fontMetrics = g.fontMetrics
        val maximumTextWidth = (width - 32 - 36 * scale).toInt().coerceAtLeast(0)
        var text = message.text
        if (fontMetrics.stringWidth(text) > maximumTextWidth) {
            while (text.isNotEmpty() && fontMetrics.stringWidth("$text…") > maximumTextWidth) text = text.dropLast(1)
            text += "…"
        }
        val textWidth = fontMetrics.stringWidth(text)
        val startX = (width - textWidth - 36 * scale) / 2.0
        val centerY = height - 24 * scale
        val circle = Ellipse2D.Double(startX, centerY - 14 * scale, 28 * scale, 28 * scale)
        if (showCircle) {
            g.color = Color(SkyInfoPresentation.iconCircleArgb, true)
            g.fill(circle)
        }
        val icon = g.create() as Graphics2D
        icon.translate(startX + 4.5 * scale, centerY - 9.5 * scale)
        icon.scale(scale, scale)
        icon.color = Color(message.category.colorArgb, true)
        icon.stroke = BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        drawIcon(icon, message.category)
        icon.dispose()
        val textX = (startX + 36 * scale).toInt()
        val baseline = (centerY + (fontMetrics.ascent - fontMetrics.descent) / 2.0).toInt()
        g.color = Color(0, 24, 14, 180)
        g.drawString(text, textX, baseline + 1)
        g.color = Color.WHITE
        g.drawString(text, textX, baseline)
        g.dispose()
    }

    /** Native outlines with the same category silhouettes as the Compose icons. */
    private fun drawIcon(g: Graphics2D, category: SkyInfoCategory) {
        when (category) {
            SkyInfoCategory.SUNRISE, SkyInfoCategory.SUNSET, SkyInfoCategory.TWILIGHT -> {
                g.draw(Arc2D.Double(3.5, 7.0, 12.0, 12.0, 0.0, 180.0, Arc2D.OPEN))
                g.drawLine(1, 14, 18, 14)
                g.drawLine(1, 17, 18, 17)
                g.drawLine(9, 1, 9, 4)
                g.drawLine(2, 4, 4, 6)
                g.drawLine(17, 4, 15, 6)
            }
            SkyInfoCategory.DAYLIGHT -> {
                g.draw(Ellipse2D.Double(5.5, 5.5, 8.0, 8.0))
                repeat(8) { i ->
                    val angle = i * Math.PI / 4
                    g.draw(java.awt.geom.Line2D.Double(9.5 + cos(angle) * 7, 9.5 + sin(angle) * 7,
                        9.5 + cos(angle) * 9, 9.5 + sin(angle) * 9))
                }
            }
            SkyInfoCategory.DAYLIGHT_GAIN, SkyInfoCategory.DAYLIGHT_LOSS -> {
                if (category == SkyInfoCategory.DAYLIGHT_LOSS) { g.translate(0.0, 19.0); g.scale(1.0, -1.0) }
                g.draw(Path2D.Double().apply { moveTo(1.0, 15.0); lineTo(7.0, 9.0); lineTo(11.0, 12.0); lineTo(18.0, 4.0) })
                g.drawLine(12, 4, 18, 4)
                g.drawLine(18, 4, 18, 10)
            }
            SkyInfoCategory.GOLDEN_HOUR, SkyInfoCategory.BLUE_HOUR -> {
                g.drawRoundRect(1, 5, 17, 12, 3, 3)
                g.drawRect(5, 2, 8, 3)
                g.draw(Ellipse2D.Double(6.0, 8.0, 7.0, 7.0))
            }
            SkyInfoCategory.SUN_POSITION -> {
                g.draw(Ellipse2D.Double(1.0, 1.0, 17.0, 17.0))
                g.draw(Path2D.Double().apply { moveTo(13.0, 5.0); lineTo(10.0, 10.0); lineTo(5.0, 13.0); lineTo(8.0, 8.0); closePath() })
            }
            SkyInfoCategory.DARKNESS, SkyInfoCategory.MOON_PHASE, SkyInfoCategory.MOON_EVENT -> {
                val moon = Area(Ellipse2D.Double(1.0, 1.0, 16.0, 16.0))
                moon.subtract(Area(Ellipse2D.Double(6.0, -2.0, 16.0, 16.0)))
                g.draw(moon)
            }
        }
    }
}
