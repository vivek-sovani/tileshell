package com.tileshell.core.data

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
            AppCategories.ROLE_CALENDAR,
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
    }

    @Test
    fun `maps and productivity os categories are deliberately not trusted`() {
        // CATEGORY_MAPS is commonly (mis)declared by ride-hailing apps and
        // CATEGORY_PRODUCTIVITY is an overused Play Console catch-all — neither
        // should classify an app on its own; only role/token evidence should.
        assertNull(AppCategories.classify(app("x", label = "x", category = ApplicationInfo.CATEGORY_MAPS)))
        assertNull(AppCategories.classify(app("x", label = "x", category = ApplicationInfo.CATEGORY_PRODUCTIVITY)))
        // A cab app tagged CATEGORY_MAPS still resolves via the travel token, not navigation.
        val cabApp = app("com.acme.rides", label = "Acme Cabs", category = ApplicationInfo.CATEGORY_MAPS)
        assertEquals("travel", AppCategories.classify(cabApp))
    }

    @Test
    fun `smart-prefixed app names do not fall into shopping via the mart token`() {
        listOf("SmartThings", "Smart Launcher", "Smart Tutor", "SmartLife").forEach { label ->
            assertNotEquals("shopping", AppCategories.classify(app("com.acme.smart", label = label)))
        }
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

    // ---- large-tile eligibility (any app, any column count) -----------------

    @Test
    fun `music role allows large on 5 and 6 columns`() {
        val music = app("x.music", role = AppCategories.ROLE_MUSIC)
        assertTrue(AppCategories.allowsLargeTile(iconKey = null, app = music, columns = 5))
        assertTrue(AppCategories.allowsLargeTile(iconKey = null, app = music, columns = 6))
    }

    @Test
    fun `media apps without the music role still allow large`() {
        // Apple Music / Spotify-style audio apps declare CATEGORY_AUDIO (not the
        // CATEGORY_APP_MUSIC launcher role) → entertainment → large allowed.
        val appleMusicLike = app("x.applemusic", category = ApplicationInfo.CATEGORY_AUDIO)
        assertTrue(AppCategories.allowsLargeTile(iconKey = null, app = appleMusicLike, columns = 5))
        // YouTube-style video apps declare CATEGORY_VIDEO → entertainment → large.
        val youtubeLike = app("x.video", category = ApplicationInfo.CATEGORY_VIDEO)
        assertTrue(AppCategories.allowsLargeTile(iconKey = null, app = youtubeLike, columns = 6))
        // media app classified only by token also qualifies.
        val byToken = app("com.acme.android", label = "Acme Stream Player")
        assertTrue(AppCategories.allowsLargeTile(iconKey = null, app = byToken, columns = 5))
    }

    @Test
    fun `music icon key alone allows large even without a catalogue match`() {
        assertTrue(AppCategories.allowsLargeTile(iconKey = "music", app = null, columns = 5))
    }

    @Test
    fun `news category allows large on 5 and 6 columns`() {
        val news = app("x.news", category = ApplicationInfo.CATEGORY_NEWS)
        assertTrue(AppCategories.allowsLargeTile(iconKey = null, app = news, columns = 6))
        // news by token too
        val newsByToken = app("com.acme.android", label = "Daily Headlines")
        assertTrue(AppCategories.allowsLargeTile(iconKey = null, app = newsByToken, columns = 5))
    }

    @Test
    fun `large is allowed on a 4 column grid too`() {
        val music = app("x.music", role = AppCategories.ROLE_MUSIC)
        assertTrue(AppCategories.allowsLargeTile(iconKey = "music", app = music, columns = 4))
    }

    @Test
    fun `any app allows large on any column count`() {
        // The media/news restriction was removed — large is offered for every app
        // on every grid density, including the minimum 4 columns.
        val mail = app("x.mail", role = AppCategories.ROLE_EMAIL)
        assertTrue(AppCategories.allowsLargeTile(iconKey = "mail", app = mail, columns = 6))
        val game = app("x.game", category = ApplicationInfo.CATEGORY_GAME)
        assertTrue(AppCategories.allowsLargeTile(iconKey = null, app = game, columns = 5))
        assertTrue(AppCategories.allowsLargeTile(iconKey = null, app = game, columns = 4))
        // even an unresolved tile (null app, no icon key) qualifies at any density.
        assertTrue(AppCategories.allowsLargeTile(iconKey = null, app = null, columns = 6))
        assertTrue(AppCategories.allowsLargeTile(iconKey = null, app = null, columns = 4))
    }
}
