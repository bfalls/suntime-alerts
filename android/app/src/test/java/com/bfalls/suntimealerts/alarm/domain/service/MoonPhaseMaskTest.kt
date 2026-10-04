package com.bfalls.suntimealerts.alarm.domain.service

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MoonPhaseMaskTest {
    @Test
    fun `Meridian afternoon regression matches bright limb and parallactic angle calculation`() {
        val time = ZonedDateTime.of(2026, 10, 4, 15, 1, 0, 0, ZoneId.of("America/Boise"))
        val sun = SunTimesCalculator.sunAltAz(time, 43.61211, -116.39151)
        val moon = MoonEphemeris.moonAltAz(time, 43.61211, -116.39151)
        val direction = MoonPhaseMask.litDirectionRadians(sun, moon)!!
        // Independent equatorial bright-limb position angle minus parallactic angle,
        // converted from zenith/anticlockwise to screen-right/clockwise convention.
        // The terminator endpoints are approximately 1:12 and 7:12 on a clock face.
        assertEquals(-144.2538, Math.toDegrees(direction.toDouble()), 0.001)
        val phase = MoonEphemeris.moonPhase(time)
        assertTrue(!phase.isWaxing)
        val points = MoonPhaseMask.litDiscPolygon(phase.illumination01.toFloat(), phase.isWaxing, direction)
        assertTrue(points.first().x01 < 0.25f && points.first().y01 > 0.8f)
    }

    @Test
    fun `sun directly above moon lights the top at every azimuth and elevation`() {
        for (azimuth in listOf(0.0, 45.0, 90.0, 180.0, 270.0, 359.0)) {
            for (altitude in listOf(-10.0, 0.0, 30.0, 70.0)) {
                val direction = MoonPhaseMask.litDirectionRadians(
                    AltAz(altitude + 10.0, azimuth), AltAz(altitude, azimuth)
                )!!
                assertEquals(-90.0, Math.toDegrees(direction.toDouble()), 0.001)
            }
        }
    }

    @Test
    fun `lighting is invariant when both azimuths turn together`() {
        for (altitude in listOf(0.0, 30.0, 85.0)) {
            val initial = MoonPhaseMask.litDirectionRadians(AltAz(40.0, 110.0), AltAz(altitude, 190.0))!!
            for (turn in listOf(45.0, 90.0, 180.0, 270.0)) {
                val turned = MoonPhaseMask.litDirectionRadians(
                    AltAz(40.0, (110.0 + turn) % 360.0), AltAz(altitude, (190.0 + turn) % 360.0)
                )!!
                assertEquals(initial.toDouble(), turned.toDouble(), 0.00001)
            }
        }
    }

    @Test
    fun `sun to the right or left on the horizon illuminates that side`() {
        for (azimuth in listOf(0.0, 90.0, 180.0, 270.0)) {
            val moon = AltAz(0.0, azimuth)
            val right = MoonPhaseMask.litDirectionRadians(AltAz(0.0, (azimuth + 45.0) % 360.0), moon)!!
            val left = MoonPhaseMask.litDirectionRadians(AltAz(0.0, (azimuth + 315.0) % 360.0), moon)!!
            assertEquals(0.0, Math.toDegrees(right.toDouble()), 0.001)
            assertEquals(180.0, kotlin.math.abs(Math.toDegrees(left.toDouble())), 0.001)
        }
    }

    @Test
    fun `collinear sun and moon leave direction undefined for phase fallback`() {
        val moon = AltAz(30.0, 80.0)
        assertNull(MoonPhaseMask.litDirectionRadians(moon, moon))
        assertNull(MoonPhaseMask.litDirectionRadians(AltAz(-30.0, 260.0), moon))
    }

    @Test
    fun `waning half moon lights the left side`() {
        val points = MoonPhaseMask.litDiscPolygon(
            illumination01 = 0.49f,
            isWaxing = false
        )

        assertTrue(points.any { it.x01 < 0.1f })
        assertTrue(points.none { it.x01 > 0.75f })
    }

    @Test
    fun `waxing half moon lights the right side`() {
        val points = MoonPhaseMask.litDiscPolygon(
            illumination01 = 0.49f,
            isWaxing = true
        )

        assertTrue(points.any { it.x01 > 0.9f })
        assertTrue(points.none { it.x01 < 0.25f })
    }

    @Test
    fun `lit direction rotates the illuminated side`() {
        val points = MoonPhaseMask.litDiscPolygon(
            illumination01 = 0.5f,
            isWaxing = true,
            litDirectionRadians = (Math.PI / 2.0).toFloat()
        )

        assertTrue(points.any { it.y01 > 0.9f })
        assertTrue(points.none { it.y01 < 0.25f })
    }

    @Test
    fun `soft edge layers expand illumination with decreasing opacity`() {
        val layers = MoonPhaseMask.softEdgeLayers(illumination01 = 0.5f)

        assertTrue(layers.size == 3)
        assertTrue(layers[0].illumination01 > layers[1].illumination01)
        assertTrue(layers[1].illumination01 > layers[2].illumination01)
        assertTrue(layers[0].alphaMultiplier < layers[1].alphaMultiplier)
        assertTrue(layers[1].alphaMultiplier < layers[2].alphaMultiplier)
    }
}
