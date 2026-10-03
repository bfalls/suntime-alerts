package com.bfalls.suntimealerts.alarm.domain.service

import com.bfalls.suntimealerts.alarm.domain.model.SkyFacingMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class MoonPhasePoint(val x01: Float, val y01: Float)

data class MoonPhaseLayer(val illumination01: Float, val alphaMultiplier: Float)

object MoonPhaseMask {
    fun softEdgeLayers(
        illumination01: Float,
        edgeFraction: Float = 0.045f
    ): List<MoonPhaseLayer> {
        val illumination = illumination01.coerceIn(0f, 1f)
        if (illumination <= 0f || illumination >= 1f) return emptyList()
        val edge = edgeFraction.coerceIn(0.005f, 0.12f)
        return listOf(
            MoonPhaseLayer((illumination + edge).coerceAtMost(1f), 0.10f),
            MoonPhaseLayer((illumination + edge * 0.66f).coerceAtMost(1f), 0.16f),
            MoonPhaseLayer((illumination + edge * 0.33f).coerceAtMost(1f), 0.24f)
        )
    }

    fun litDiscPolygon(
        illumination01: Float,
        isWaxing: Boolean,
        litDirectionRadians: Float? = null,
        samples: Int = 48
    ): List<MoonPhasePoint> {
        val illumination = illumination01.coerceIn(0f, 1f)
        val safeSamples = samples.coerceAtLeast(8)
        val fallbackDirection = if (isWaxing) 0f else PI.toFloat()
        val direction = litDirectionRadians ?: fallbackDirection
        val axisX = cos(direction)
        val axisY = sin(direction)
        val perpendicularX = -axisY
        val perpendicularY = axisX

        fun moonPoint(x: Float, y: Float): MoonPhasePoint {
            val rotatedX = x * axisX + y * perpendicularX
            val rotatedY = x * axisY + y * perpendicularY
            return MoonPhasePoint(
                x01 = (rotatedX + 1f) / 2f,
                y01 = (rotatedY + 1f) / 2f
            )
        }

        if (illumination <= 0f) return emptyList()
        if (illumination >= 1f) {
            return (0..safeSamples).map { index ->
                val angle = -PI / 2.0 + 2.0 * PI * index / safeSamples
                moonPoint(
                    x = cos(angle).toFloat(),
                    y = sin(angle).toFloat()
                )
            }
        }

        val terminatorScale = 1f - 2f * illumination
        val outerLimb = (0..safeSamples).map { index ->
            val y = -1f + 2f * index / safeSamples
            val x = sqrt((1f - y * y).coerceAtLeast(0f))
            moonPoint(x, y)
        }
        val terminator = (safeSamples downTo 0).map { index ->
            val y = -1f + 2f * index / safeSamples
            val x = terminatorScale * sqrt((1f - y * y).coerceAtLeast(0f))
            moonPoint(x, y)
        }
        return outerLimb + terminator
    }

    fun litDirectionRadians(
        sunAltAz: AltAz,
        moonAltAz: AltAz,
        skyFacingMode: SkyFacingMode
    ): Float? {
        val sunVector = horizonUnitVector(sunAltAz)
        val moonVector = horizonUnitVector(moonAltAz)
        val dot = sunVector.east * moonVector.east +
            sunVector.north * moonVector.north +
            sunVector.up * moonVector.up
        val tangent = HorizonVector(
            east = sunVector.east - dot * moonVector.east,
            north = sunVector.north - dot * moonVector.north,
            up = sunVector.up - dot * moonVector.up
        )
        val screenX = when (skyFacingMode) {
            SkyFacingMode.SOUTH_FACING -> -tangent.east
            SkyFacingMode.NORTH_FACING -> tangent.east
        }
        val screenY = -tangent.up
        if (abs(screenX) < 0.000001 && abs(screenY) < 0.000001) return null
        return atan2(screenY, screenX).toFloat()
    }

    private fun horizonUnitVector(altAz: AltAz): HorizonVector {
        val altitude = Math.toRadians(altAz.altitudeDeg)
        val azimuth = Math.toRadians(altAz.azimuthDeg)
        val cosAltitude = cos(altitude)
        return HorizonVector(
            east = cosAltitude * sin(azimuth),
            north = cosAltitude * cos(azimuth),
            up = sin(altitude)
        )
    }

    private data class HorizonVector(
        val east: Double,
        val north: Double,
        val up: Double
    )
}
