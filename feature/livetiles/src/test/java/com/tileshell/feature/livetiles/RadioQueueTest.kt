package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test

class RadioQueueTest {
    private fun s(id: String) = RadioStationRef(id, "station $id", "https://$id", null)

    @Test
    fun `a favourite plays within the favourites, at its own position`() {
        val (queue, index) = radioQueue(s("b"), listOf(s("a"), s("b"), s("c")))
        assertEquals(listOf("a", "b", "c"), queue.map { it.stationId })
        assertEquals(1, index)
    }

    @Test
    fun `a non-favourite goes first, then the favourites`() {
        val (queue, index) = radioQueue(s("x"), listOf(s("a"), s("b")))
        assertEquals(listOf("x", "a", "b"), queue.map { it.stationId })
        assertEquals(0, index)
    }

    @Test
    fun `no favourites is a queue of one`() {
        val (queue, index) = radioQueue(s("x"), emptyList())
        assertEquals(listOf("x"), queue.map { it.stationId })
        assertEquals(0, index)
    }
}
