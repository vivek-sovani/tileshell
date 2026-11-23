package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test

class GenericCountWordTest {
    @Test
    fun `a mail app says unread and anything else says new`() {
        assertEquals("unread", genericCountWord(PeopleCategory.MAIL))
        assertEquals("new", genericCountWord(PeopleCategory.CHAT))
        assertEquals("new", genericCountWord(PeopleCategory.MESSAGES))
        assertEquals("new", genericCountWord(null))
    }
}
