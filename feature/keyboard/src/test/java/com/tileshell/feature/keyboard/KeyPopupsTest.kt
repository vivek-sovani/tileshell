package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyPopupsTest {

    private fun char(c: String) = Key(KeyKind.CHAR, c)

    @Test
    fun `top row offers its digit first, then accents`() {
        assertEquals("3", KeyPopups.options(char("e"), upper = false).first())
        assertTrue("é" in KeyPopups.options(char("e"), upper = false))
        assertEquals(listOf("1"), KeyPopups.options(char("q"), upper = false))
    }

    @Test
    fun `accents follow shift`() {
        assertTrue("Á" in KeyPopups.options(char("a"), upper = true))
    }

    @Test
    fun `letters without variants have none`() {
        assertTrue(KeyPopups.options(char("k"), upper = false).isEmpty())
    }

    @Test
    fun `typing marathi in english letters only offers the digit`() {
        assertEquals(listOf("3"), KeyPopups.options(char("e"), upper = false, lettersOnly = true))
        assertTrue(KeyPopups.options(char("a"), upper = false, lettersOnly = true).isEmpty())
    }

    @Test
    fun `symbols have variants`() {
        assertTrue("₹" in KeyPopups.options(Key(KeyKind.SYMBOL, "$"), upper = false))
        assertTrue("?" in KeyPopups.options(Key(KeyKind.SYMBOL, "."), upper = false))
    }
}
