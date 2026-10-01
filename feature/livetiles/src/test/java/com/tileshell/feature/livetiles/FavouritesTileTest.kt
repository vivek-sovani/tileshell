package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test

class FavouritesTileTest {
    private fun p(key: String) = PersonSummary(contactId = key.hashCode().toLong(), lookupKey = key, name = key, photoUri = null)
    private val starred = listOf(p("anita"), p("dev"), p("mom"), p("rahul"))

    @Test
    fun `not arranged shows every starred contact`() {
        assertEquals(starred, favouritesTilePeople(starred, emptyList(), arranged = false))
        assertEquals(starred, favouritesTilePeople(starred, listOf("mom"), arranged = false))
    }

    @Test
    fun `arranged shows only pinned people in the user's order`() {
        val shown = favouritesTilePeople(starred, listOf("mom", "rahul", "anita"), arranged = true)
        assertEquals(listOf("mom", "rahul", "anita"), shown.map { it.lookupKey })
    }

    @Test
    fun `unstarred or deleted pins drop out and duplicates are ignored`() {
        val shown = favouritesTilePeople(starred, listOf("gone", "dev", "dev", "mom"), arranged = true)
        assertEquals(listOf("dev", "mom"), shown.map { it.lookupKey })
    }

    @Test
    fun `arranged with nobody pinned is empty`() {
        assertEquals(emptyList<PersonSummary>(), favouritesTilePeople(starred, emptyList(), arranged = true))
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
    fun `moveItem moves up and down and clamps`() {
        val l = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), moveItem(l, 0, 2))
        assertEquals(listOf("d", "a", "b", "c"), moveItem(l, 3, 0))
        assertEquals(listOf("b", "c", "d", "a"), moveItem(l, 0, 99))
        assertEquals(l, moveItem(l, 1, 1))
        assertEquals(l, moveItem(l, 7, 0))
    }
}
