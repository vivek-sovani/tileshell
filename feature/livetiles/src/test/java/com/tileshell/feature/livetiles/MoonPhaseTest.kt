package com.tileshell.feature.livetiles

import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MoonPhaseTest {

    @Test
    fun `exactly at the reference instant is a new moon`() {
        val fraction = moonPhaseFraction(REFERENCE_NEW_MOON_EPOCH_DAY)
        assertTrue(abs(fraction) < 0.0001)
        assertEquals(MoonPhaseName.NEW, moonPhaseName(fraction))
    }

    @Test
    fun `half a synodic month later is a full moon`() {
        val fraction = moonPhaseFraction(REFERENCE_NEW_MOON_EPOCH_DAY + SYNODIC_MONTH_DAYS / 2)
        assertTrue(abs(fraction - 0.5) < 0.0001)
        assertEquals(MoonPhaseName.FULL, moonPhaseName(fraction))
    }

    @Test
    fun `one full cycle later wraps back to a new moon`() {
        val fraction = moonPhaseFraction(REFERENCE_NEW_MOON_EPOCH_DAY + SYNODIC_MONTH_DAYS * 3)
        assertTrue(abs(fraction) < 0.0001)
    }

    @Test
    fun `before the reference instant still resolves a valid fraction`() {
        val fraction = moonPhaseFraction(REFERENCE_NEW_MOON_EPOCH_DAY - 2)
        assertTrue(fraction in 0.0..1.0)
    }

    @Test
    fun `illumination is 0 percent at new moon and 100 at full moon`() {
        assertEquals(0, moonIllumination(0.0))
        assertEquals(100, moonIllumination(0.5))
    }

    @Test
    fun `first and last quarter sit at 50 percent illumination`() {
        assertEquals(50, moonIllumination(0.25))
        assertEquals(50, moonIllumination(0.75))
    }

    @Test
    fun `phase name buckets the fraction into the 8 canonical phases`() {
        assertEquals(MoonPhaseName.NEW, moonPhaseName(0.0))
        assertEquals(MoonPhaseName.FIRST_QUARTER, moonPhaseName(0.25))
        assertEquals(MoonPhaseName.FULL, moonPhaseName(0.5))
        assertEquals(MoonPhaseName.LAST_QUARTER, moonPhaseName(0.75))
        // Just past new moon wraps to NEW, not WANING_CRESCENT.
        assertEquals(MoonPhaseName.NEW, moonPhaseName(0.99))
    }

    @Test
    fun `face exactly at a new moon says so today`() {
        val face = moonPhaseFace(REFERENCE_NEW_MOON_EPOCH_DAY)
        assertEquals("new moon", face.name)
        assertEquals(0, face.illuminationPercent)
        assertEquals("new moon today", face.nextEventLabel)
    }

    @Test
    fun `face a few days after new moon points at the upcoming full moon`() {
        val face = moonPhaseFace(REFERENCE_NEW_MOON_EPOCH_DAY + 3)
        assertEquals("full moon in 12d", face.nextEventLabel)
    }

    @Test
    fun `face right at a full moon says so today`() {
        val face = moonPhaseFace(REFERENCE_NEW_MOON_EPOCH_DAY + SYNODIC_MONTH_DAYS / 2)
        assertEquals("full moon today", face.nextEventLabel)
    }

    @Test
    fun `moonphase icon key maps to the moon-phase face at medium and up`() {
        assertEquals(LiveFace.MOONPHASE, LiveFace.forIconKey("moonphase", TileSize.MEDIUM))
        assertEquals(LiveFace.MOONPHASE, LiveFace.forIconKey("moonphase", TileSize.WIDE))
    }

    @Test
    fun `moonphase tile stays static at small`() {
        assertNull(LiveFace.forIconKey("moonphase", TileSize.SMALL))
    }

    @Test
    fun `moonphase face flips`() {
        assertTrue(LiveFace.MOONPHASE.flips)
    }
}
