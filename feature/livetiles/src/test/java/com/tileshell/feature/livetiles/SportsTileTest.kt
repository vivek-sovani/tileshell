package com.tileshell.feature.livetiles

import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SportsStateLabelTest {

    @Test
    fun `maps ESPN's state strings to plain words`() {
        assertEquals("live", sportsStateLabel("in"))
        assertEquals("final", sportsStateLabel("post"))
        assertEquals("upcoming", sportsStateLabel("pre"))
    }

    @Test
    fun `an unrecognised state still degrades to upcoming rather than crashing`() {
        assertEquals("upcoming", sportsStateLabel("something-new"))
    }
}

class SportsIconMappingTest {

    @Test
    fun `sports icon key maps to the sports face at medium and up`() {
        assertEquals(LiveFace.SPORTS, LiveFace.forIconKey("sports", TileSize.MEDIUM))
        assertEquals(LiveFace.SPORTS, LiveFace.forIconKey("sports", TileSize.WIDE))
    }

    @Test
    fun `sports stays out of forIconKey at small`() {
        assertNull(LiveFace.forIconKey("sports", TileSize.SMALL))
    }

    @Test
    fun `sports flips — front is the score, back is who you're following`() {
        assertEquals(true, LiveFace.SPORTS.flips)
    }
}
