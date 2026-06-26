package com.tileshell.core.data

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCategoriesTest {

    private fun app(
        pkg: String,
        label: String = pkg,
        category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
        role: String? = null,
    ) = AppEntry(
        packageName = pkg,
        activityName = "$pkg.Main",
        label = label,
        category = category,
        role = role,
    )

    // ---- layer 1: standard app role wins -----------------------------------

    @Test
    fun `email and messaging roles classify as communication`() {
        assertEquals("communication", AppCategories.classify(app("a.b.mail", role = AppCategories.ROLE_EMAIL)))
        assertEquals("communication", AppCategories.classify(app("a.b.sms", role = AppCategories.ROLE_MESSAGING)))
    }

    @Test
    fun `role overrides a misleading declared category`() {
        // An email app that (wrongly) declares SOCIAL must still be communication.
        val gmailLike = app("x.mail", category = ApplicationInfo.CATEGORY_SOCIAL, role = AppCategories.ROLE_EMAIL)
        assertEquals("communication", AppCategories.classify(gmailLike))
    }

    @Test
    fun `maps role classifies as navigation, not travel`() {
        assertEquals("navigation", AppCategories.classify(app("x.maps", role = AppCategories.ROLE_MAPS)))
    }

    @Test
    fun `utility roles fold into tools`() {
        listOf(
            AppCategories.ROLE_BROWSER, AppCategories.ROLE_CALCULATOR,
            AppCategories.ROLE_FILES, AppCategories.ROLE_WEATHER, AppCategories.ROLE_MARKET,
        ).forEach { role ->
            assertEquals(role, "tools", AppCategories.classify(app("x.$role", role = role)))
        }
    }

    // ---- layer 2: declared OS category -------------------------------------

    @Test
    fun `os category maps the coarse buckets`() {
        assertEquals("games", AppCategories.classify(app("x", category = ApplicationInfo.CATEGORY_GAME)))
        assertEquals("social", AppCategories.classify(app("x", category = ApplicationInfo.CATEGORY_SOCIAL)))
        assertEquals("news", AppCategories.classify(app("x", category = ApplicationInfo.CATEGORY_NEWS)))
        assertEquals("entertainment", AppCategories.classify(app("x", category = ApplicationInfo.CATEGORY_AUDIO)))
        assertEquals("entertainment", AppCategories.classify(app("x", category = ApplicationInfo.CATEGORY_VIDEO)))
        assertEquals("photos", AppCategories.classify(app("x", category = ApplicationInfo.CATEGORY_IMAGE)))
        assertEquals("navigation", AppCategories.classify(app("x", category = ApplicationInfo.CATEGORY_MAPS)))
        assertEquals("productivity", AppCategories.classify(app("x", category = ApplicationInfo.CATEGORY_PRODUCTIVITY)))
    }

    // ---- layer 3: generic dictionary tokens (no role, UNDEFINED) -----------

    @Test
    fun `generic tokens classify the os-unmodelled buckets`() {
        assertEquals("banking", AppCategories.classify(app("com.acme.mobile", label = "Acme Bank")))
        assertEquals("payments", AppCategories.classify(app("com.acme.wallet", label = "Acme Wallet")))
        assertEquals("shopping", AppCategories.classify(app("com.acme.shop", label = "Acme Shop")))
        assertEquals("food", AppCategories.classify(app("com.acme.android", label = "Food Express")))
        assertEquals("travel", AppCategories.classify(app("com.acme.android", label = "Flight Booking")))
        assertEquals("health", AppCategories.classify(app("com.acme.android", label = "Fitness Tracker")))
    }

    @Test
    fun `unknown app with no signal stays uncategorised`() {
        assertNull(AppCategories.classify(app("com.acme.zzz", label = "Zzz")))
    }

    // ---- partition / API invariants ----------------------------------------

    @Test
    fun `each app lands in at most one category`() {
        val apps = listOf(
            app("x.mail", role = AppCategories.ROLE_EMAIL),
            app("x.game", category = ApplicationInfo.CATEGORY_GAME),
            app("com.acme", label = "Acme Bank"),
            app("com.acme.zzz", label = "Zzz"),
        )
        val grouped = AppCategories.categorize(apps)
        // No app appears in two buckets.
        val placements = apps.associateWith { a -> grouped.count { (_, v) -> a in v } }
        placements.forEach { (a, n) -> assertTrue(a.packageName, n <= 1) }
        assertEquals(setOf("x.mail"), grouped["communication"]!!.map { it.packageName }.toSet())
        assertEquals(setOf("x.game"), grouped["games"]!!.map { it.packageName }.toSet())
    }

    @Test
    fun `match is empty for an unknown category id`() {
        assertTrue(AppCategories.match("nope", listOf(app("x.mail", role = AppCategories.ROLE_EMAIL))).isEmpty())
    }

    @Test
    fun `every shipped category has a unique lowercase id and label`() {
        val ids = AppCategories.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        AppCategories.ALL.forEach { assertEquals(it.label, it.label.lowercase()) }
    }
}
