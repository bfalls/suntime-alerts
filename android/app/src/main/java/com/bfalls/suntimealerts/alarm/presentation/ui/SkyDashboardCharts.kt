package com.bfalls.suntimealerts.alarm.presentation.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bfalls.suntimealerts.alarm.domain.service.MoonPhaseMask
import com.bfalls.suntimealerts.alarm.domain.service.PhotoLight
import com.bfalls.suntimealerts.alarm.domain.service.PhotoLightWindow
import com.bfalls.suntimealerts.alarm.domain.service.SkyInfoMetrics
import com.bfalls.suntimealerts.alarm.domain.service.SkyLightPeriod
import java.time.Duration
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal data class SkyDashboardPalette(
    val sun: Color, val moon: Color, val day: Color, val civil: Color,
    val nautical: Color, val astronomical: Color, val darkness: Color
) {
    fun color(period: SkyLightPeriod): Color = when (period) {
        SkyLightPeriod.DAYLIGHT -> day
        SkyLightPeriod.CIVIL_TWILIGHT -> civil
        SkyLightPeriod.NAUTICAL_TWILIGHT -> nautical
        SkyLightPeriod.ASTRONOMICAL_TWILIGHT -> astronomical
        SkyLightPeriod.DARKNESS -> darkness
    }
}

internal fun SkyLightPeriod.label(): String = when (this) {
    SkyLightPeriod.DAYLIGHT -> "Daylight"
    SkyLightPeriod.CIVIL_TWILIGHT -> "Civil twilight"
    SkyLightPeriod.NAUTICAL_TWILIGHT -> "Nautical twilight"
    SkyLightPeriod.ASTRONOMICAL_TWILIGHT -> "Astronomical twilight"
    SkyLightPeriod.DARKNESS -> "Darkness"
}

@Composable
internal fun SkyDayTimeline(metrics: SkyInfoMetrics, use24h: Boolean, palette: SkyDashboardPalette) {
    val segments = metrics.today.lightSegments
    if (segments.isEmpty()) return
    val start = segments.first().start
    val end = segments.last().end
    val span = Duration.between(start, end).toMillis().toDouble()
    fun fraction(time: java.time.ZonedDateTime) = (Duration.between(start, time).toMillis() / span).toFloat().coerceIn(0f, 1f)
    var selected by rememberSaveable(metrics.today.date.toString(), metrics.coordinate) { mutableIntStateOf(-1) }
    val current = segments.indexOfFirst { !metrics.now.isBefore(it.start) && metrics.now.isBefore(it.end) }.coerceAtLeast(0)
    val chosen = segments.getOrNull(selected) ?: segments[current]
    val text = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val selection = "${chosen.period.label()} · ${SkyDashboardText.duration(Duration.between(chosen.start, chosen.end))}"
    val bounds = "${SkyDashboardText.time(chosen.start, metrics.now, use24h)} → ${SkyDashboardText.time(chosen.end, metrics.now, use24h)}"
    Canvas(
        Modifier.fillMaxWidth().height(78.dp).testTag("sky_dashboard_light_timeline")
            .pointerInput(segments) {
                detectTapGestures { tap ->
                    val at = tap.x / size.width
                    selected = segments.indexOfFirst { at < fraction(it.end) }.let { if (it < 0) segments.lastIndex else it }
                }
            }
            .semantics {
                contentDescription = "Daily light timeline. $selection. $bounds"
                onClick("Select next light period") { selected = ((if (selected < 0) current else selected) + 1) % segments.size; true }
            }
    ) {
        val left = 4.dp.toPx()
        val width = size.width - 2 * left
        val top = 24.dp.toPx()
        val height = 16.dp.toPx()
        for (segment in segments) {
            val x = left + width * fraction(segment.start)
            drawRect(palette.color(segment.period), Offset(x, top), Size(width * (fraction(segment.end) - fraction(segment.start)), height))
        }
        val nowX = left + width * fraction(metrics.now)
        drawLine(text, Offset(nowX, top - 5.dp.toPx()), Offset(nowX, top + height + 5.dp.toPx()), 2.dp.toPx())
        chartLabel("Now", nowX, top - 10.dp.toPx(), text, 11.sp.toPx())
        val ticks = listOf(start, start.toLocalDate().atTime(6, 0).atZone(start.zone),
            start.toLocalDate().atTime(12, 0).atZone(start.zone), start.toLocalDate().atTime(18, 0).atZone(start.zone), end)
        val formatter = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h a")
        ticks.forEach { time -> chartLabel(time.format(formatter), left + width * fraction(time), top + height + 23.dp.toPx(), muted, 10.sp.toPx()) }
    }
    Text(selection, style = MaterialTheme.typography.labelLarge, color = text)
    Text(bounds, style = MaterialTheme.typography.bodySmall, color = muted)
}

@Composable
internal fun SkySunElevation(altitudeDeg: Double, palette: SkyDashboardPalette) {
    val structure = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    Canvas(Modifier.fillMaxWidth().height(90.dp).semantics {
        contentDescription = "Sun ${SkyDashboardText.altitude(altitudeDeg)}"
    }) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width * 0.38f, size.height * 0.38f)
        drawArc(structure, 180f, 180f, false, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2), style = Stroke(1.dp.toPx()))
        drawLine(structure, Offset(center.x - radius - 5.dp.toPx(), center.y), Offset(center.x + radius + 5.dp.toPx(), center.y), 1.dp.toPx())
        val angle = Math.toRadians(altitudeDeg.coerceIn(-90.0, 90.0))
        drawCircle(palette.sun, 9.dp.toPx(), Offset(center.x + radius * cos(angle).toFloat(), center.y - radius * sin(angle).toFloat()))
    }
}

@Composable
internal fun SkyMoonDisc(metrics: SkyInfoMetrics, palette: SkyDashboardPalette) {
    val background = palette.moon.copy(alpha = 0.18f)
    val phase = metrics.moonPhase
    val direction = MoonPhaseMask.litDirectionRadians(metrics.sunPosition, metrics.moonPosition)
    Canvas(Modifier.fillMaxWidth().height(90.dp).testTag("sky_dashboard_moon_disc").semantics {
        contentDescription = "${SkyInfoMessages.phaseName(phase)}, ${(phase.illumination01 * 100).toInt()} percent illuminated. Upright local Moon orientation."
    }) {
        val diameter = min(size.height * 0.84f, size.width * 0.84f)
        val left = (size.width - diameter) / 2f
        val top = (size.height - diameter) / 2f
        drawCircle(background, diameter / 2f, Offset(size.width / 2, size.height / 2))
        val points = MoonPhaseMask.litDiscPolygon(phase.illumination01.toFloat(), phase.isWaxing, direction, 96)
        val path = Path().apply {
            points.forEachIndexed { i, point ->
                val x = left + point.x01 * diameter
                val y = top + point.y01 * diameter
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            if (points.isNotEmpty()) close()
        }
        drawPath(path, palette.moon)
    }
}

@Composable
internal fun SkyCompass(metrics: SkyInfoMetrics, palette: SkyDashboardPalette) {
    val structure = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val text = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(146.dp).semantics {
        contentDescription = "North-up compass. Sun ${SkyDashboardText.bearing(metrics.sunPosition.azimuthDeg)}. Moon ${SkyDashboardText.bearing(metrics.moonPosition.azimuthDeg)}."
    }) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = min(size.width * 0.28f, size.height * 0.34f)
        drawCircle(structure, radius, center, style = Stroke(1.dp.toPx()))
        drawLine(structure, center - Offset(radius, 0f), center + Offset(radius, 0f), 1.dp.toPx())
        drawLine(structure, center - Offset(0f, radius), center + Offset(0f, radius), 1.dp.toPx())
        chartLabel("N", center.x, center.y - radius - 9.dp.toPx(), text, 12.sp.toPx())
        chartLabel("S", center.x, center.y + radius + 17.dp.toPx(), text, 12.sp.toPx())
        chartLabel("E", center.x + radius + 14.dp.toPx(), center.y + 4.dp.toPx(), text, 12.sp.toPx())
        chartLabel("W", center.x - radius - 14.dp.toPx(), center.y + 4.dp.toPx(), text, 12.sp.toPx())
        listOf(metrics.sunPosition.azimuthDeg to palette.sun, metrics.moonPosition.azimuthDeg to palette.moon).forEach { (bearing, color) ->
            val angle = Math.toRadians(bearing)
            val tip = center + Offset(radius * sin(angle).toFloat(), -radius * cos(angle).toFloat())
            drawLine(color, center, tip, 2.dp.toPx())
            drawCircle(color, 5.dp.toPx(), tip)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Sun · ${SkyDashboardText.bearing(metrics.sunPosition.azimuthDeg)}", color = palette.sun, style = MaterialTheme.typography.bodySmall)
        Text("Moon · ${SkyDashboardText.bearing(metrics.moonPosition.azimuthDeg)}", color = palette.moon, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun SkyPhotoWindowBars(metrics: SkyInfoMetrics, windows: List<PhotoLightWindow>, palette: SkyDashboardPalette, use24h: Boolean) {
    val start = metrics.today.date.atStartOfDay(metrics.now.zone)
    val end = metrics.today.date.plusDays(1).atStartOfDay(metrics.now.zone)
    val span = Duration.between(start, end).toMillis().toDouble()
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    listOf(true to "Morning", false to "Evening").forEach { (morning, label) ->
        val pass = windows.filter { it.isMorning == morning }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(60.dp))
            Canvas(Modifier.weight(1f).height(18.dp).semantics { contentDescription = "$label photography windows. ${if (pass.isEmpty()) "No complete window" else pass.joinToString { it.light.name.lowercase() }}" }) {
                drawRect(track, Offset(0f, size.height / 4), Size(size.width, size.height / 2))
                pass.forEach { window ->
                    val left = (Duration.between(start, window.start).toMillis() / span).toFloat().coerceIn(0f, 1f)
                    val right = (Duration.between(start, window.end).toMillis() / span).toFloat().coerceIn(0f, 1f)
                    if (right > left) drawRect(if (window.light == PhotoLight.GOLDEN) palette.sun else palette.moon,
                        Offset(left * size.width, size.height / 4), Size((right - left) * size.width, size.height / 2))
                }
            }
        }
    }
    Canvas(Modifier.fillMaxWidth().padding(start = 68.dp).height(24.dp)) {
        val noon = metrics.today.date.atTime(12, 0).atZone(metrics.now.zone)
        val noonX = (Duration.between(start, noon).toMillis() / span).toFloat() * size.width
        val baseline = 16.dp.toPx()
        chartLabel(if (use24h) "00:00" else "12 AM", 0f, baseline, axisColor, 10.sp.toPx())
        chartLabel(if (use24h) "12:00" else "12 PM", noonX, baseline, axisColor, 10.sp.toPx())
        chartLabel(if (use24h) "24:00" else "12 AM", size.width, baseline, axisColor, 10.sp.toPx())
    }
}

private fun DrawScope.chartLabel(label: String, x: Float, baseline: Float, color: Color, fontSize: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb(); textSize = fontSize }
    val width = paint.measureText(label)
    val left = (x - width / 2).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
    drawContext.canvas.nativeCanvas.drawText(label, left, baseline, paint)
}
