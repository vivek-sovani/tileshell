package com.tileshell.core.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class TileAccentsIdTest {
    @Test
    fun `a colour id round-trips through forId`() {
        val c = Color(0xFF3A9E6B)
        assertEquals("#3A9E6B", TileAccents.idForColor(c))
        assertEquals(c, TileAccents.forId(TileAccents.idForColor(c)))
    }

    @Test
    fun `named ids and bad ids still resolve as before`() {
        assertEquals(TileAccents.Blue, TileAccents.forId("blue"))
        assertEquals(TileAccents.Blue, TileAccents.forId("#12"))
        assertEquals(TileAccents.Blue, TileAccents.forId("#GGGGGG"))
        assertEquals(TileAccents.Blue, TileAccents.forId(null))
    }
}
