package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileColorsTest {

    @Test
    fun `default colour is deterministic for a package`() {
        assertEquals(
            TileColors.defaultIdFor("com.android.chrome"),
            TileColors.defaultIdFor("com.android.chrome"),
        )
    }

    @Test
    fun `default colour is always one of the 14 palette ids`() {
        val packages = listOf(
            "com.android.chrome", "com.google.android.gm", "com.spotify.music",
            "a", "", "org.x.y.z.really.long.package.name", "com.whatsapp",
        )
        packages.forEach { pkg ->
            assertTrue(pkg, TileColors.defaultIdFor(pkg) in TileColors.IDS)
        }
    }

    @Test
    fun `palette has the 14 prototype colour ids`() {
        assertEquals(14, TileColors.IDS.size)
        assertTrue("blue" in TileColors.IDS && "slate" in TileColors.IDS)
    }

    @Test
    fun `accent override wins when it is a valid palette id`() {
        assertEquals("red", TileColors.accentIdFor("red", globalAccentId = "blue"))
    }

    @Test
    fun `null override follows the global accent`() {
        assertEquals("blue", TileColors.accentIdFor(null, globalAccentId = "blue"))
    }

    @Test
    fun `blank or unknown override follows the global accent`() {
        assertEquals("teal", TileColors.accentIdFor("", globalAccentId = "teal"))
        assertEquals("teal", TileColors.accentIdFor("chartreuse", globalAccentId = "teal"))
    }
}
