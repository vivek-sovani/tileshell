package com.tileshell.feature.start

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageLinksTest {
    private fun urls(text: String) = findLinks(text).map { it.url }

    @Test
    fun `finds a web address and leaves sentence punctuation out`() {
        val text = "Details at https://example.com/q3?x=1."
        val link = findLinks(text).single()
        assertEquals("https://example.com/q3?x=1", link.url)
        assertEquals("https://example.com/q3?x=1", text.substring(link.start, link.end))
    }

    @Test
    fun `a www address opens as https`() {
        assertEquals(listOf("https://www.example.com/a"), urls("see www.example.com/a, thanks"))
    }

    @Test
    fun `a bracket that closes its own opening stays in the address`() {
        assertEquals(listOf("https://en.wikipedia.org/wiki/Foo_(bar)"), urls("(https://en.wikipedia.org/wiki/Foo_(bar))"))
        assertEquals(listOf("https://example.com/x"), urls("(see https://example.com/x)"))
    }

    @Test
    fun `finds an email address as a mailto link`() {
        assertEquals(listOf("mailto:priya.n@example.co.in"), urls("write to priya.n@example.co.in today"))
    }

    @Test
    fun `an email inside a web address is not a second link`() {
        assertEquals(listOf("https://example.com/u/name@host.com"), urls("https://example.com/u/name@host.com"))
    }

    @Test
    fun `plain text has no links and links come in text order`() {
        assertTrue(findLinks("no links here, just 4 pm tomorrow").isEmpty())
        val order = findLinks("mail a@b.com or visit https://x.org now").map { it.start }
        assertEquals(order.sorted(), order)
    }
}
