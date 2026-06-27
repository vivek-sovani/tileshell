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
}
