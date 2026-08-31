package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountdownTileTest {

    @Test
    fun `round-trips a date and label`() {
        val encoded = CountdownTile.encode("2026-09-15", "birthday")
        assertEquals("2026-09-15" to "birthday", CountdownTile.decode(encoded))
    }

    @Test
    fun `a label containing colons survives the round trip`() {
        val encoded = CountdownTile.encode("2026-01-01", "new year: resolutions")
        assertEquals("2026-01-01" to "new year: resolutions", CountdownTile.decode(encoded))
    }

    @Test
    fun `a blank label is valid`() {
        val encoded = CountdownTile.encode("2026-09-15", "")
        assertEquals("2026-09-15" to "", CountdownTile.decode(encoded))
    }

    @Test
    fun `decode rejects a string with no countdown prefix`() {
        assertNull(CountdownTile.decode("2026-09-15:birthday"))
    }

    @Test
    fun `decode rejects a prefixed string with no date`() {
        assertNull(CountdownTile.decode("countdown:"))
    }
}
