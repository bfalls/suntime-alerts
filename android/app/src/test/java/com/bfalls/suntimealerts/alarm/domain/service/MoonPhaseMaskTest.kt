package com.bfalls.suntimealerts.alarm.domain.service

import org.junit.Assert.assertTrue
import org.junit.Test

class MoonPhaseMaskTest {
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
