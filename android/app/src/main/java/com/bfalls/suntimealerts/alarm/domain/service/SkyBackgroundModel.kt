package com.bfalls.suntimealerts.alarm.domain.service

import kotlin.math.roundToInt

data class SkyColorStop(val position: Float, val color: Int)

data class SkySunGlow(
    val color: Int,
    val alpha: Float,
    val radiusScale: Float
)

data class SkyLandscapeSpec(
    val groundColor: Int,
    val farHillColor: Int,
    val nearHillColor: Int,
    val hazeColor: Int,
    val hazeAlpha: Float
)

data class SkyHorizonPoint(val x01: Float, val yOffset01: Float)

data class SkyBackgroundSpec(
    val phase: SkyPhase,
    val gradientStops: List<SkyColorStop>,
    val sunGlow: SkySunGlow?,
    val starAlpha: Float,
    val horizonLineAlpha: Float,
    val landscape: SkyLandscapeSpec
)

enum class SkyPhase {
    NIGHT,
    TWILIGHT,
    LOW_SUN_GLOW,
    DAY,
    PLACEHOLDER
}

object SkyBackgroundModel {
    fun compute(
        sunAltitudeDeg: Double?,
        hasSunTimes: Boolean
    ): SkyBackgroundSpec {
        if (!hasSunTimes || sunAltitudeDeg == null) {
            return SkyBackgroundSpec(
                phase = SkyPhase.PLACEHOLDER,
                gradientStops = listOf(
                    SkyColorStop(0f, 0xFFD7E2EE.toInt()),
                    SkyColorStop(1f, 0xFFB4C1CE.toInt())
                ),
                sunGlow = null,
                starAlpha = 0f,
                horizonLineAlpha = 0f,
                landscape = SkyLandscapeSpec(
                    groundColor = 0xFF9AA6A7.toInt(),
                    farHillColor = 0xFF839198.toInt(),
                    nearHillColor = 0xFF65757A.toInt(),
                    hazeColor = 0xFFE4EDF5.toInt(),
                    hazeAlpha = 0.22f
                )
            )
        }

        return when {
            sunAltitudeDeg >= 8.0 -> day()
            sunAltitudeDeg >= 0.0 -> lowSunGlow(((8.0 - sunAltitudeDeg) / 8.0).toFloat())
            sunAltitudeDeg >= -6.0 -> civilTwilight((-sunAltitudeDeg / 6.0).toFloat())
            sunAltitudeDeg >= -12.0 -> nauticalTwilight(((-sunAltitudeDeg - 6.0) / 6.0).toFloat())
            else -> night()
        }
    }

    private fun day(): SkyBackgroundSpec {
        return SkyBackgroundSpec(
            phase = SkyPhase.DAY,
            gradientStops = listOf(
                SkyColorStop(0f, 0xFF64B5F6.toInt()),
                SkyColorStop(0.65f, 0xFF9ED0F5.toInt()),
                SkyColorStop(1f, 0xFFBBDEFB.toInt())
            ),
            sunGlow = null,
            starAlpha = 0f,
            horizonLineAlpha = 0f,
            landscape = landscape(
                ground = 0xFF5F7C62.toInt(),
                far = 0xFF71927A.toInt(),
                near = 0xFF496A50.toInt(),
                haze = 0xFFD8EEFF.toInt(),
                hazeAlpha = 0.20f
            )
        )
    }

    private fun lowSunGlow(amount: Float): SkyBackgroundSpec {
        val t = smooth(amount)
        return SkyBackgroundSpec(
            phase = SkyPhase.LOW_SUN_GLOW,
            gradientStops = listOf(
                SkyColorStop(0f, lerpColor(0xFF64B5F6.toInt(), 0xFF4C88C7.toInt(), t)),
                SkyColorStop(0.52f, lerpColor(0xFF9ED0F5.toInt(), 0xFF8BB9D2.toInt(), t)),
                SkyColorStop(0.78f, lerpColor(0xFFBFE5FF.toInt(), 0xFFFFD899.toInt(), t)),
                SkyColorStop(1f, lerpColor(0xFFDAF1FF.toInt(), 0xFFFFA65E.toInt(), t))
            ),
            sunGlow = SkySunGlow(
                color = lerpColor(0xFFFFF7C2.toInt(), 0xFFFFB15E.toInt(), t),
                alpha = 0.10f + 0.42f * t,
                radiusScale = 0.55f + 0.25f * t
            ),
            starAlpha = 0f,
            horizonLineAlpha = 0f,
            landscape = landscape(
                ground = lerpColor(0xFF5F7C62.toInt(), 0xFF725B3B.toInt(), t),
                far = lerpColor(0xFF71927A.toInt(), 0xFF8A7049.toInt(), t),
                near = lerpColor(0xFF496A50.toInt(), 0xFF4E3A2A.toInt(), t),
                haze = lerpColor(0xFFD8EEFF.toInt(), 0xFFFFD28A.toInt(), t),
                hazeAlpha = 0.20f + 0.10f * t
            )
        )
    }

    private fun civilTwilight(amount: Float): SkyBackgroundSpec {
        val t = smooth(amount)
        return SkyBackgroundSpec(
            phase = SkyPhase.TWILIGHT,
            gradientStops = listOf(
                SkyColorStop(0f, lerpColor(0xFF4C88C7.toInt(), 0xFF223A63.toInt(), t)),
                SkyColorStop(0.45f, lerpColor(0xFF7599C4.toInt(), 0xFF4B527D.toInt(), t)),
                SkyColorStop(0.72f, lerpColor(0xFFFFB56B.toInt(), 0xFF87608F.toInt(), t)),
                SkyColorStop(1f, lerpColor(0xFFFF8F58.toInt(), 0xFF273557.toInt(), t))
            ),
            sunGlow = SkySunGlow(
                color = lerpColor(0xFFFFB45C.toInt(), 0xFFB76CA3.toInt(), t),
                alpha = 0.48f * (1f - t),
                radiusScale = 0.80f + 0.18f * t
            ),
            starAlpha = smooth(((t - 0.45f) / 0.55f).coerceIn(0f, 1f)) * 0.45f,
            horizonLineAlpha = 0f,
            landscape = landscape(
                ground = lerpColor(0xFF725B3B.toInt(), 0xFF253040.toInt(), t),
                far = lerpColor(0xFF7E6847.toInt(), 0xFF344157.toInt(), t),
                near = lerpColor(0xFF4E3A2A.toInt(), 0xFF141B25.toInt(), t),
                haze = lerpColor(0xFFFFC77E.toInt(), 0xFF596582.toInt(), t),
                hazeAlpha = 0.26f * (1f - t) + 0.10f * t
            )
        )
    }

    private fun nauticalTwilight(amount: Float): SkyBackgroundSpec {
        val t = smooth(amount)
        return SkyBackgroundSpec(
            phase = SkyPhase.TWILIGHT,
            gradientStops = listOf(
                SkyColorStop(0f, lerpColor(0xFF223A63.toInt(), 0xFF0D1B2A.toInt(), t)),
                SkyColorStop(0.48f, lerpColor(0xFF344866.toInt(), 0xFF132237.toInt(), t)),
                SkyColorStop(0.78f, lerpColor(0xFF4F3D63.toInt(), 0xFF081A28.toInt(), t)),
                SkyColorStop(1f, lerpColor(0xFF1E2A46.toInt(), 0xFF001219.toInt(), t))
            ),
            sunGlow = SkySunGlow(
                color = lerpColor(0xFF80549A.toInt(), 0xFF263A6C.toInt(), t),
                alpha = 0.18f * (1f - t),
                radiusScale = 0.95f
            ),
            starAlpha = 0.45f + 0.55f * t,
            horizonLineAlpha = 0f,
            landscape = landscape(
                ground = lerpColor(0xFF253040.toInt(), 0xFF06100D.toInt(), t),
                far = lerpColor(0xFF344157.toInt(), 0xFF101C20.toInt(), t),
                near = lerpColor(0xFF141B25.toInt(), 0xFF05090A.toInt(), t),
                haze = lerpColor(0xFF596582.toInt(), 0xFF1D3040.toInt(), t),
                hazeAlpha = 0.10f * (1f - t) + 0.05f * t
            )
        )
    }

    private fun night(): SkyBackgroundSpec {
        return SkyBackgroundSpec(
            phase = SkyPhase.NIGHT,
            gradientStops = listOf(
                SkyColorStop(0f, 0xFF0D1B2A.toInt()),
                SkyColorStop(1f, 0xFF001219.toInt())
            ),
            sunGlow = null,
            starAlpha = 1f,
            horizonLineAlpha = 0f,
            landscape = landscape(
                ground = 0xFF06100D.toInt(),
                far = 0xFF101C20.toInt(),
                near = 0xFF05090A.toInt(),
                haze = 0xFF142839.toInt(),
                hazeAlpha = 0.05f
            )
        )
    }

    fun farHillProfile(): List<SkyHorizonPoint> {
        return listOf(
            SkyHorizonPoint(-0.05f, -0.010f),
            SkyHorizonPoint(0.05f, -0.010f),
            SkyHorizonPoint(0.16f, -0.040f),
            SkyHorizonPoint(0.30f, -0.020f),
            SkyHorizonPoint(0.43f, -0.052f),
            SkyHorizonPoint(0.56f, -0.018f),
            SkyHorizonPoint(0.70f, -0.045f),
            SkyHorizonPoint(0.83f, -0.020f),
            SkyHorizonPoint(0.96f, -0.036f),
            SkyHorizonPoint(1.05f, -0.012f)
        )
    }

    fun nearHillProfile(): List<SkyHorizonPoint> {
        return listOf(
            SkyHorizonPoint(-0.05f, -0.015f),
            SkyHorizonPoint(0.08f, -0.025f),
            SkyHorizonPoint(0.20f, -0.060f),
            SkyHorizonPoint(0.33f, -0.025f),
            SkyHorizonPoint(0.48f, -0.075f),
            SkyHorizonPoint(0.62f, -0.030f),
            SkyHorizonPoint(0.75f, -0.065f),
            SkyHorizonPoint(0.88f, -0.020f),
            SkyHorizonPoint(1.05f, -0.016f)
        )
    }

    private fun landscape(
        ground: Int,
        far: Int,
        near: Int,
        haze: Int,
        hazeAlpha: Float
    ): SkyLandscapeSpec {
        return SkyLandscapeSpec(
            groundColor = ground,
            farHillColor = far,
            nearHillColor = near,
            hazeColor = haze,
            hazeAlpha = hazeAlpha.coerceIn(0f, 1f)
        )
    }

    private fun smooth(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun lerpColor(start: Int, end: Int, fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        val a = lerp((start ushr 24) and 0xFF, (end ushr 24) and 0xFF, t)
        val r = lerp((start ushr 16) and 0xFF, (end ushr 16) and 0xFF, t)
        val g = lerp((start ushr 8) and 0xFF, (end ushr 8) and 0xFF, t)
        val b = lerp(start and 0xFF, end and 0xFF, t)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerp(start: Int, end: Int, fraction: Float): Int {
        return (start + (end - start) * fraction).roundToInt().coerceIn(0, 255)
    }
}
