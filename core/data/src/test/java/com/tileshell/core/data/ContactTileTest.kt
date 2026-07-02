package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for the contact-tile activityName encoding ([ContactTile]). */
class ContactTileTest {

    @Test
    fun `round trips a contact id and lookup key`() {
        val encoded = ContactTile.encode(42L, "0r15-2A5D392C1B")
        assertEquals(42L to "0r15-2A5D392C1B", ContactTile.decode(encoded))
    }

    @Test
    fun `lookup key may itself contain colons`() {
        val encoded = ContactTile.encode(7L, "abc:def:ghi")
        assertEquals(7L to "abc:def:ghi", ContactTile.decode(encoded))
    }

    @Test
    fun `a plain app activityName is not a contact`() {
        assertNull(ContactTile.decode("com.example.MainActivity"))
    }

    @Test
    fun `a blank liveOnly activityName is not a contact`() {
        assertNull(ContactTile.decode(""))
    }

    @Test
    fun `a malformed contact-id segment decodes to null`() {
        assertNull(ContactTile.decode("contact:not-a-number:key"))
    }
}
