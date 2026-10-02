package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmojiCatalogTest {

    private val catalog = EmojiCatalog(File("src/main/assets/keyboard/emoji.txt").readLines().asSequence())

    @Test
    fun `every tab but recent has emoji`() {
        for (tab in EmojiTab.entries.filter { it != EmojiTab.RECENT }) assertTrue(tab.code, catalog.tab(tab).size > 50)
        assertEquals("😀", catalog.tab(EmojiTab.SMILEYS).first())
        assertTrue("🥭" in catalog.tab(EmojiTab.FOOD))
        assertTrue("🇮🇳" in catalog.tab(EmojiTab.SYMBOLS))
    }

    @Test
    fun `no skin tone variants`() {
        assertFalse(catalog.tab(EmojiTab.SMILEYS).any { "🏽" in it })
    }

    @Test
    fun `search by the start of name words, every word matching`() {
        assertTrue("❤️" in catalog.search("red heart"))
        assertTrue("😂" in catalog.search("tears of joy"))
        assertTrue(catalog.search("mang").contains("🥭"))
        assertTrue(catalog.search("india").contains("🇮🇳"))
        assertTrue(catalog.search("   ").isEmpty())
    }

    @Test
    fun `emoji the font can't draw are left out`() {
        val c = EmojiCatalog(sequenceOf("smileys\t😀\tgrinning face", "smileys\t🫨\tshaking face")) { it != "🫨" }
        assertEquals(listOf("😀"), c.tab(EmojiTab.SMILEYS))
    }

    @Test
    fun `recents move to the front and are capped`() {
        var r = listOf("😀", "👍")
        r = EmojiCatalog.pushRecent(r, "👍")
        assertEquals(listOf("👍", "😀"), r)
        repeat(40) { r = EmojiCatalog.pushRecent(r, "x$it") }
        assertEquals(EmojiCatalog.RECENT_MAX, r.size)
    }
}
