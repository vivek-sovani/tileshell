package com.tileshell.core.data

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCategoriesTest {

    private fun app(
        pkg: String,
        label: String = pkg,
        category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
    ) = AppEntry(packageName = pkg, activityName = "$pkg.Main", label = label, category = category)

    @Test
    fun `keyword match places payment and shopping apps`() {
        val apps = listOf(
            app("com.phonepe.app"),
            app("com.flipkart.android"),
            app("net.one97.paytm"),
        )
        assertEquals(
            setOf("com.phonepe.app", "net.one97.paytm"),
            AppCategories.match("payments", apps).map { it.packageName }.toSet(),
        )
        assertEquals(
            listOf("com.flipkart.android"),
            AppCategories.match("shopping", apps).map { it.packageName },
        )
    }

    @Test
    fun `android social category matches even without a keyword`() {
        val apps = listOf(app("com.example.chatter", category = ApplicationInfo.CATEGORY_SOCIAL))
        assertEquals(1, AppCategories.match("social", apps).size)
    }

    @Test
    fun `undefined android category never matches by category alone`() {
        // CATEGORY_UNDEFINED must not slip into a bucket that lists no keyword hit.
        val apps = listOf(app("com.example.random", category = ApplicationInfo.CATEGORY_UNDEFINED))
        assertTrue(AppCategories.match("social", apps).isEmpty())
        assertTrue(AppCategories.match("games", apps).isEmpty())
    }

    @Test
    fun `categorize only ever returns installed apps`() {
        val apps = listOf(app("com.whatsapp"), app("com.sbi.SBIFreedomPlus"))
        val map = AppCategories.categorize(apps)
        map.values.flatten().forEach { entry ->
            assertTrue(entry in apps)
        }
        assertTrue(AppCategories.match("social", apps).any { it.packageName == "com.whatsapp" })
        assertTrue(AppCategories.match("banking", apps).any { it.packageName == "com.sbi.SBIFreedomPlus" })
    }

    @Test
    fun `match is empty for an unknown category id`() {
        assertTrue(AppCategories.match("nope", listOf(app("com.whatsapp"))).isEmpty())
    }

    @Test
    fun `every shipped category has a unique lowercase id and label`() {
        val ids = AppCategories.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        AppCategories.ALL.forEach {
            assertEquals(it.label, it.label.lowercase())
            assertFalse(it.androidCategories.isEmpty() && it.keywords.isEmpty())
        }
    }
}
