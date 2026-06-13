package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppCatalogTest {

    private fun entry(label: String, pkg: String = "pkg.$label", act: String = ".Main") =
        AppEntry(packageName = pkg, activityName = act, label = label)

    @Test
    fun `sorts alphabetically case-insensitively`() {
        val sorted = AppCatalog.sorted(
            listOf(entry("Camera"), entry("alarm"), entry("Browser"), entry("bank")),
        )
        assertEquals(listOf("alarm", "bank", "Browser", "Camera"), sorted.map { it.label })
    }

    @Test
    fun `equal labels break ties by package then activity for stable order`() {
        val a = AppEntry("com.b", ".Main", "Photos")
        val b = AppEntry("com.a", ".Main", "Photos")
        val c = AppEntry("com.a", ".Alt", "Photos")
        val sorted = AppCatalog.sorted(listOf(a, b, c))
        // Same label → package ascending (com.a before com.b), then activity (.Alt before .Main).
        assertEquals(listOf("com.a" to ".Alt", "com.a" to ".Main", "com.b" to ".Main"),
            sorted.map { it.packageName to it.activityName })
    }

    @Test
    fun `letter is uppercase first letter`() {
        assertEquals("A", AppCatalog.letterFor("alarm"))
        assertEquals("C", AppCatalog.letterFor("Camera"))
    }

    @Test
    fun `non-letter starts group under hash`() {
        assertEquals("#", AppCatalog.letterFor("1Weather"))
        assertEquals("#", AppCatalog.letterFor("+Message"))
        assertEquals("#", AppCatalog.letterFor(""))
        assertEquals("#", AppCatalog.letterFor("   "))
    }

    @Test
    fun `leading whitespace is ignored for letter`() {
        assertEquals("M", AppCatalog.letterFor("  Maps"))
    }

    @Test
    fun `letter is computed automatically on AppEntry`() {
        assertEquals("B", entry("Browser").letter)
        assertEquals("#", entry("7Zip").letter)
    }

    @Test
    fun `grouping by letter yields contiguous sorted sections`() {
        val sorted = AppCatalog.sorted(
            listOf(entry("Maps"), entry("alarm"), entry("9apps"), entry("Mail"), entry("Bank")),
        )
        // Order: # (9apps), a (alarm), b (Bank), m (Mail, Maps)
        assertEquals(listOf("#", "A", "B", "M", "M"), sorted.map { it.letter })
        assertEquals(listOf("9apps", "alarm", "Bank", "Mail", "Maps"), sorted.map { it.label })
    }
}
