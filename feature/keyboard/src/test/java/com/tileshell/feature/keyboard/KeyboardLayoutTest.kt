package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {

    @Test
    fun `letter rows follow the build spec`() {
        val rows = KeyboardLayouts.letters
        assertEquals("qwertyuiop", rows[0].keys.joinToString("") { it.label })
        assertEquals("asdfghjkl", rows[1].keys.joinToString("") { it.label })
        assertTrue(rows[1].inset)
        // Row 3: shift 1.5 · 7 letters · backspace 1.5.
        assertEquals(KeyKind.SHIFT, rows[2].keys.first().kind)
        assertEquals(KeyKind.BACKSPACE, rows[2].keys.last().kind)
        assertEquals(10f, rows[2].units)
        assertEquals("zxcvbnm", rows[2].keys.filter { it.kind == KeyKind.CHAR }.joinToString("") { it.label })
    }

    @Test
    fun `bottom row is &123 1_5, emoji, comma, space 4_5, period, enter 1_5`() {
        val bottom = KeyboardLayouts.letters[3].keys
        assertEquals(listOf("&123", "emoji", ",", "English", ".", "enter"), bottom.map { it.label })
        assertEquals(listOf(1.5f, 1f, 1f, 4.5f, 1f, 1.5f), bottom.map { it.units })
    }

    @Test
    fun `symbol pages switch back with abcd and page with 1-2`() {
        for (page in listOf(KeyboardLayouts.symbols1, KeyboardLayouts.symbols2)) {
            assertEquals("abcd", page[3].keys.first().label)
            assertEquals(KeyKind.PAGE, page[2].keys.first().kind)
            assertEquals(10, page[0].keys.size)
            assertEquals(10, page[1].keys.size)
            assertEquals(10f, page[2].units)
        }
        assertEquals("1234567890", KeyboardLayouts.symbols1[0].keys.joinToString("") { it.label })
        assertEquals("@#\$%&*-+()", KeyboardLayouts.symbols1[1].keys.joinToString("") { it.label })
    }

    @Test
    fun `function keys use the darker colour`() {
        val kinds = KeyboardLayouts.letters.flatMap { it.keys }.filter { it.isFunction }.map { it.kind }.toSet()
        assertEquals(
            setOf(KeyKind.SHIFT, KeyKind.BACKSPACE, KeyKind.LAYER, KeyKind.EMOJI, KeyKind.ENTER),
            kinds,
        )
    }

    @Test
    fun `number pad is four by four with the phone letters`() {
        val pad = KeyboardLayouts.numpad
        assertEquals(listOf(4, 4, 4, 4), pad.map { it.keys.size })
        assertEquals("ABC", pad[0].keys[1].sub)
        assertEquals("WXYZ", pad[2].keys[2].sub)
        assertEquals("*#", pad[3].keys[0].display)
        assertEquals(KeyKind.ENTER, pad[3].keys[3].kind)
        assertTrue(pad[0].keys[3].isFunction)
        assertTrue("#" in KeyPopups.options(pad[3].keys[0], upper = false))
        assertEquals(listOf("+"), KeyPopups.options(pad[3].keys[1], upper = false))
    }

    @Test
    fun `with more than one language the globe sits after emoji and space gives it a unit`() {
        val bottom = KeyboardLayouts.rowsFor(KeyboardLayer.LETTERS, languageKey = true).last().keys
        assertEquals(
            listOf(KeyKind.LAYER, KeyKind.EMOJI, KeyKind.LANGUAGE, KeyKind.SYMBOL, KeyKind.SPACE, KeyKind.SYMBOL, KeyKind.ENTER),
            bottom.map { it.kind },
        )
        assertEquals(3.5f, bottom.first { it.kind == KeyKind.SPACE }.units)
        assertEquals(10.5f, bottom.sumOf { it.units.toDouble() }.toFloat())
        assertTrue(bottom.first { it.kind == KeyKind.LANGUAGE }.isFunction)
        // Symbols too; English only has no globe.
        assertTrue(KeyboardLayouts.rowsFor(KeyboardLayer.SYMBOLS_1, true).last().keys.any { it.kind == KeyKind.LANGUAGE })
        assertTrue(KeyboardLayouts.rowsFor(KeyboardLayer.LETTERS).last().keys.none { it.kind == KeyKind.LANGUAGE })
    }
}
