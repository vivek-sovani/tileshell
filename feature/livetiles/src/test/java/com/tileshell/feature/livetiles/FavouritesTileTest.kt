package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test

class FavouritesTileTest {
    private fun p(key: String) = PersonSummary(contactId = key.hashCode().toLong(), lookupKey = key, name = key, photoUri = null)
    private val starred = listOf(p("anita"), p("dev"), p("mom"), p("rahul"))

    @Test
    fun `no order keeps the starred contacts' own order`() {
        assertEquals(starred, orderedFavourites(starred, emptyList()))
    }

    @Test
    fun `arranged people come first, newly starred ones follow`() {
        val ordered = orderedFavourites(starred, listOf("mom", "rahul"))
        assertEquals(listOf("mom", "rahul", "anita", "dev"), ordered.map { it.lookupKey })
    }

    @Test
    fun `unstarred keys and duplicates in the order are ignored`() {
        val ordered = orderedFavourites(starred, listOf("gone", "dev", "dev", "mom"))
        assertEquals(listOf("dev", "mom", "anita", "rahul"), ordered.map { it.lookupKey })
    }

    @Test
    fun `tile shows the ordered favourites minus the ones taken off`() {
        val ordered = orderedFavourites(starred, listOf("mom", "rahul"))
        assertEquals(listOf("mom", "anita", "dev"), tileFavourites(ordered, listOf("rahul")).map { it.lookupKey })
        assertEquals(emptyList<PersonSummary>(), tileFavourites(ordered, starred.map { it.lookupKey }))
    }

    @Test
    fun `split keeps what fits and counts the rest`() {
        assertEquals(starred.take(3) to 1, favouritesTileSplit(starred, 3))
        assertEquals(starred to 0, favouritesTileSplit(starred, 4))
        assertEquals(starred to 0, favouritesTileSplit(starred, 9))
        assertEquals(emptyList<PersonSummary>() to 4, favouritesTileSplit(starred, 0))
        assertEquals(emptyList<PersonSummary>() to 4, favouritesTileSplit(starred, -2))
    }

    @Test
    fun `capacity is four on a 2x2 and grows with height`() {
        assertEquals(1, favouritesTileCapacity(1))
        assertEquals(4, favouritesTileCapacity(2))
        assertEquals(6, favouritesTileCapacity(3))
        assertEquals(8, favouritesTileCapacity(4))
    }

    @Test
    fun `moveItem moves up and down and clamps`() {
        val l = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), moveItem(l, 0, 2))
        assertEquals(listOf("d", "a", "b", "c"), moveItem(l, 3, 0))
        assertEquals(listOf("b", "c", "d", "a"), moveItem(l, 0, 99))
        assertEquals(l, moveItem(l, 1, 1))
        assertEquals(l, moveItem(l, 7, 0))
    }
}
