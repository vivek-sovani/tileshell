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

    private fun categoriesOf(pkg: String, category: Int = ApplicationInfo.CATEGORY_UNDEFINED): Set<String> {
        val apps = listOf(app(pkg, category = category))
        return AppCategories.ALL.filter { AppCategories.match(it.id, apps).isNotEmpty() }
            .map { it.id }.toSet()
    }

    @Test
    fun `package match places payment and shopping apps`() {
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
    fun `games still match by android category`() {
        val apps = listOf(app("com.king.candycrushsaga", category = ApplicationInfo.CATEGORY_GAME))
        assertEquals(1, AppCategories.match("games", apps).size)
    }

    // ---- regressions from the reported mis-categorisations ------------------

    @Test
    fun `messaging app declaring SOCIAL is communication not social`() {
        // Google Messages declares CATEGORY_SOCIAL but must not land in "social".
        val cats = categoriesOf("com.google.android.apps.messaging", ApplicationInfo.CATEGORY_SOCIAL)
        assertEquals(setOf("communication"), cats)
    }

    @Test
    fun `email apps are communication not social`() {
        assertEquals(setOf("communication"), categoriesOf("com.google.android.gm", ApplicationInfo.CATEGORY_SOCIAL))
        assertEquals(setOf("communication"), categoriesOf("com.microsoft.office.outlook"))
    }

    @Test
    fun `meet is communication not social`() {
        assertEquals(setOf("communication"), categoriesOf("com.google.android.apps.meetings", ApplicationInfo.CATEGORY_SOCIAL))
        assertEquals(setOf("communication"), categoriesOf("com.google.android.apps.tachyon"))
    }

    @Test
    fun `x twitter is social not news`() {
        // X declares CATEGORY_NEWS; it should be social and never news.
        val cats = categoriesOf("com.twitter.android", ApplicationInfo.CATEGORY_NEWS)
        assertEquals(setOf("social"), cats)
    }

    @Test
    fun `google maps is not filed under travel`() {
        // Maps declares CATEGORY_MAPS and contains "maps" — must match nothing.
        val cats = categoriesOf("com.google.android.apps.maps", ApplicationInfo.CATEGORY_MAPS)
        assertTrue(cats.isEmpty())
    }

    @Test
    fun `messenger does not leak into social via facebook`() {
        assertEquals(setOf("communication"), categoriesOf("com.facebook.orca"))
        assertEquals(setOf("social"), categoriesOf("com.facebook.katana"))
    }

    // ---- general invariants -------------------------------------------------

    @Test
    fun `undefined android category never matches a no-keyword bucket`() {
        // games has no package list — an UNDEFINED app must not fall into it.
        val cats = categoriesOf("com.example.random", ApplicationInfo.CATEGORY_UNDEFINED)
        assertTrue(cats.isEmpty())
    }

    @Test
    fun `categorize only ever returns installed apps`() {
        val apps = listOf(app("com.whatsapp"), app("com.sbi.SBIFreedomPlus"))
        AppCategories.categorize(apps).values.flatten().forEach { assertTrue(it in apps) }
        assertTrue(AppCategories.match("communication", apps).any { it.packageName == "com.whatsapp" })
        assertTrue(AppCategories.match("banking", apps).any { it.packageName == "com.sbi.SBIFreedomPlus" })
    }

    @Test
    fun `match is empty for an unknown category id`() {
        assertTrue(AppCategories.match("nope", listOf(app("com.whatsapp"))).isEmpty())
    }

    @Test
    fun `every shipped category has a unique lowercase id and a signal`() {
        val ids = AppCategories.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        AppCategories.ALL.forEach {
            assertEquals(it.label, it.label.lowercase())
            assertFalse(it.androidCategories.isEmpty() && it.packages.isEmpty())
        }
    }
}
