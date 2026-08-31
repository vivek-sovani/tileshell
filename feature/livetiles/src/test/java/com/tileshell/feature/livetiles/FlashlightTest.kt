package com.tileshell.feature.livetiles

import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FlashlightTest {

    @Test
    fun `status text follows the torch state`() {
        assertEquals("on", flashlightStatusText(true))
        assertEquals("off", flashlightStatusText(false))
    }

    @Test
    fun `flashlight icon key maps to the flashlight face at medium and up`() {
        assertEquals(LiveFace.FLASHLIGHT, LiveFace.forIconKey("flashlight", TileSize.MEDIUM))
        assertEquals(LiveFace.FLASHLIGHT, LiveFace.forIconKey("flashlight", TileSize.WIDE))
    }

    @Test
    fun `flashlight tile stays out of forIconKey at small — handled by its own small-face branch instead`() {
        assertNull(LiveFace.forIconKey("flashlight", TileSize.SMALL))
    }

    @Test
    fun `flashlight never flips — the whole tile is the control, nothing to reveal on the back`() {
        assertFalse(LiveFace.FLASHLIGHT.flips)
    }
}
