package com.bfalls.suntimealerts.alarm.domain.service

import com.bfalls.suntimealerts.alarm.domain.model.SkyFacingMode
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.sin

data class MoonXY(val x: Float, val y: Float, val isUp: Boolean)

object MoonArcPositionCalculator {
    fun computeMoonXY(
        now: ZonedDateTime,
        rise: ZonedDateTime?,
        set: ZonedDateTime?,
        width: Float,
        horizonY: Float,
        arcHeight: Float,
        horizontalPadding: Float,
        skyFacingMode: SkyFacingMode = SkyFacingMode.SOUTH_FACING
    ): MoonXY {
        if (rise == null || set == null || !rise.isBefore(set)) {
            return MoonXY(
                x = horizontalPadding,
                y = horizonY,
                isUp = false
            )
        }
        val totalMillis = Duration.between(rise, set).toMillis().coerceAtLeast(1)
        val elapsedMillis = Duration.between(rise, now).toMillis().toDouble()
        val progress = elapsedMillis / totalMillis
        val clamped = progress.coerceIn(0.0, 1.0)
        val x = when (skyFacingMode) {
            SkyFacingMode.SOUTH_FACING ->
                lerp(horizontalPadding, width - horizontalPadding, clamped.toFloat())
            SkyFacingMode.NORTH_FACING ->
                lerp(width - horizontalPadding, horizontalPadding, clamped.toFloat())
        }
        val y = horizonY - arcHeight * sin(PI * clamped).toFloat()
        val isUp = progress in 0.0..1.0
        return MoonXY(x, y, isUp)
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }
}
