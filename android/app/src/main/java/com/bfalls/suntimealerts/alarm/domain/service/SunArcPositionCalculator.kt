package com.bfalls.suntimealerts.alarm.domain.service

import com.bfalls.suntimealerts.alarm.domain.model.SkyFacingMode
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.sin

data class SunXY(val x: Float, val y: Float, val isDay: Boolean)

object SunArcPositionCalculator {

    fun computeSunT(now: ZonedDateTime, sunrise: ZonedDateTime?, sunset: ZonedDateTime?): Double {
        if (sunrise == null || sunset == null) return -1.0
        val totalMillis = Duration.between(sunrise, sunset).toMillis().coerceAtLeast(1)
        val elapsedMillis = Duration.between(sunrise, now).toMillis().toDouble()
        return elapsedMillis / totalMillis
    }

    fun computeArcScale(dayLengthMinutes: Long): Double {
        val scale = dayLengthMinutes / (16.0 * 60.0)
        return scale.coerceIn(0.35, 1.0)
    }

    fun computeSunXY(
        t: Double,
        width: Float,
        horizonY: Float,
        arcHeight: Float,
        horizontalPadding: Float,
        skyFacingMode: SkyFacingMode = SkyFacingMode.SOUTH_FACING
    ): SunXY {
        val clamped = t.coerceIn(0.0, 1.0)
        val isDay = t in 0.0..1.0
        val x = when (skyFacingMode) {
            SkyFacingMode.SOUTH_FACING ->
                lerp(horizontalPadding, width - horizontalPadding, clamped.toFloat())
            SkyFacingMode.NORTH_FACING ->
                lerp(width - horizontalPadding, horizontalPadding, clamped.toFloat())
        }
        val y = if (isDay) {
            horizonY - arcHeight * sin(PI * clamped).toFloat()
        } else {
            horizonY + arcHeight * 0.35f
        }
        return SunXY(x, y, isDay)
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }
}
