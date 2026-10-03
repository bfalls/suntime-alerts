package com.bfalls.suntimealerts.alarm.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyBackgroundModelTest {
    @Test
    fun `high sun uses day sky without stars`() {
        val sky = SkyBackgroundModel.compute(sunAltitudeDeg = 30.0, hasSunTimes = true)

        assertEquals(SkyPhase.DAY, sky.phase)
        assertEquals(0f, sky.starAlpha)
        assertEquals(null, sky.sunGlow)
    }

    @Test
    fun `low sun uses warm glow without stars`() {
        val sky = SkyBackgroundModel.compute(sunAltitudeDeg = 2.0, hasSunTimes = true)

        assertEquals(SkyPhase.LOW_SUN_GLOW, sky.phase)
        assertNotNull(sky.sunGlow)
        assertEquals(0f, sky.starAlpha)
    }

    @Test
    fun `deep twilight fades stars in`() {
        val sky = SkyBackgroundModel.compute(sunAltitudeDeg = -9.0, hasSunTimes = true)

        assertEquals(SkyPhase.TWILIGHT, sky.phase)
        assertTrue(sky.starAlpha > 0.45f)
    }

    @Test
    fun `deep night uses full star alpha`() {
        val sky = SkyBackgroundModel.compute(sunAltitudeDeg = -18.0, hasSunTimes = true)

        assertEquals(SkyPhase.NIGHT, sky.phase)
        assertEquals(1f, sky.starAlpha)
    }

    @Test
    fun `landscape profiles provide stable low horizon hills`() {
        val far = SkyBackgroundModel.farHillProfile()
        val near = SkyBackgroundModel.nearHillProfile()

        assertTrue(far.first().x01 < 0f)
        assertTrue(far.first().yOffset01 < 0f)
        assertTrue(far.last().x01 > 1f)
        assertTrue(far.last().yOffset01 < 0f)
        assertTrue(near.first().yOffset01 < 0f)
        assertTrue(near.any { it.yOffset01 < -0.05f })
        assertTrue(near.last().x01 > 1f)
        assertTrue(near.last().yOffset01 < 0f)
    }
}
