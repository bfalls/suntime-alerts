package com.bfalls.suntimealerts.alarm.domain.service

import com.bfalls.suntimealerts.alarm.domain.model.SkyFacingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SunArcPositionCalculatorTest {

    @Test
    fun middayRisesAboveHorizon() {
        val zone = ZoneId.of("UTC")
        val sunrise = ZonedDateTime.of(2024, 6, 1, 6, 0, 0, 0, zone)
        val sunset = ZonedDateTime.of(2024, 6, 1, 18, 0, 0, 0, zone)
        val noon = ZonedDateTime.of(2024, 6, 1, 12, 0, 0, 0, zone)
        val t = SunArcPositionCalculator.computeSunT(noon, sunrise, sunset)
        val position = SunArcPositionCalculator.computeSunXY(
            t = t,
            width = 400f,
            horizonY = 200f,
            arcHeight = 80f,
            horizontalPadding = 20f
        )

        assertTrue(position.isDay)
        assertEquals(200f, position.y + 80f, 0.5f) // near peak
        val sunrisePos = SunArcPositionCalculator.computeSunXY(
            t = 0.0,
            width = 400f,
            horizonY = 200f,
            arcHeight = 80f,
            horizontalPadding = 20f
        )
        assertTrue(position.y < sunrisePos.y)
    }

    @Test
    fun arcScaleReflectsSeason() {
        val winter = SunArcPositionCalculator.computeArcScale(8 * 60L)
        val summer = SunArcPositionCalculator.computeArcScale(15 * 60L)

        assertTrue(winter < summer)
        assertTrue(winter >= 0.35)
        assertTrue(summer <= 1.0)
    }

    @Test
    fun nightPlacesSunBelowHorizon() {
        val zone = ZoneId.of("UTC")
        val sunrise = ZonedDateTime.of(2024, 6, 1, 6, 0, 0, 0, zone)
        val sunset = ZonedDateTime.of(2024, 6, 1, 18, 0, 0, 0, zone)
        val night = ZonedDateTime.of(2024, 6, 1, 2, 0, 0, 0, zone)
        val t = SunArcPositionCalculator.computeSunT(night, sunrise, sunset)
        val position = SunArcPositionCalculator.computeSunXY(
            t = t,
            width = 300f,
            horizonY = 180f,
            arcHeight = 80f,
            horizontalPadding = 20f
        )

        assertFalse(position.isDay)
        assertTrue(position.y > 180f)
    }

    @Test
    fun northFacingModeMirrorsHorizontalArc() {
        val southFacing = SunArcPositionCalculator.computeSunXY(
            t = 0.25,
            width = 400f,
            horizonY = 200f,
            arcHeight = 80f,
            horizontalPadding = 20f,
            skyFacingMode = SkyFacingMode.SOUTH_FACING
        )
        val northFacing = SunArcPositionCalculator.computeSunXY(
            t = 0.25,
            width = 400f,
            horizonY = 200f,
            arcHeight = 80f,
            horizontalPadding = 20f,
            skyFacingMode = SkyFacingMode.NORTH_FACING
        )

        assertEquals(400f, southFacing.x + northFacing.x, 0.5f)
        assertEquals(southFacing.y, northFacing.y, 0.01f)
    }
}
