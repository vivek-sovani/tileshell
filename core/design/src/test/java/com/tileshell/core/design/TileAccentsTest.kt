package com.tileshell.core.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class TileAccentsTest {

    @Test
    fun `each accent maps to its own id`() {
        TileAccents.swatches.forEach { (id, color) ->
            assertEquals(id, TileAccents.nearestAccentId(color))
        }
    }

    @Test
    fun `a vivid red icon colour suggests red`() {
        assertEquals("red", TileAccents.nearestAccentId(Color(0xFFE53935)))
    }

    @Test
    fun `a vivid green icon colour suggests green or lime`() {
        val id = TileAccents.nearestAccentId(Color(0xFF2E9E4F))
        assertEquals(true, id == "green" || id == "lime")
    }

    @Test
    fun `a deep blue icon colour suggests a blue family accent`() {
        val id = TileAccents.nearestAccentId(Color(0xFF1565C0))
        assertEquals(true, id == "blue" || id == "cobalt" || id == "cyan")
    }

    @Test
    fun `override resolves hex, then palette id, then global`() {
        assertEquals(Color(0xFFA86B8F), TileAccents.colorForOverride("#A86B8F", "blue"))
        assertEquals(TileAccents.Red, TileAccents.colorForOverride("red", "blue"))
        assertEquals(TileAccents.Blue, TileAccents.colorForOverride(null, "blue"))
        assertEquals(TileAccents.Teal, TileAccents.colorForOverride("nonsense", "teal"))
    }

    @Test
    fun `parseHexColor accepts RRGGBB and rejects malformed`() {
        assertEquals(Color(0xFF112233), TileAccents.parseHexColor("#112233"))
        assertEquals(null, TileAccents.parseHexColor("112233"))
        assertEquals(null, TileAccents.parseHexColor("#12345"))
        assertEquals(null, TileAccents.parseHexColor("#zzzzzz"))
    }
}
