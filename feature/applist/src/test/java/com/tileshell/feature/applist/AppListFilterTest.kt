package com.tileshell.feature.applist

import com.tileshell.core.data.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class AppListFilterTest {

    private fun app(label: String) =
        AppEntry(packageName = "pkg.${label.lowercase()}", activityName = ".Main", label = label)

    private val apps = listOf(
        app("Calculator"), app("Calendar"), app("Camera"), app("Maps"), app("Messages"), app("9 Weather"),
    )

    @Test
    fun `blank query returns all apps`() {
        assertEquals(apps, AppListFilter.filter(apps, ""))
        assertEquals(apps, AppListFilter.filter(apps, "   "))
    }

    @Test
    fun `filter is case-insensitive substring on label`() {
        assertEquals(
            listOf("Calculator", "Calendar"),
            AppListFilter.filter(apps, "cal").map { it.label },
        )
        assertEquals(
            listOf("Messages"),
            AppListFilter.filter(apps, "SAGE").map { it.label },
        )
    }

    @Test
    fun `query is trimmed`() {
        assertEquals(listOf("Camera"), AppListFilter.filter(apps, "  camera  ").map { it.label })
    }

    @Test
    fun `no match yields empty list`() {
        assertEquals(emptyList<AppEntry>(), AppListFilter.filter(apps, "zzz"))
    }

    @Test
    fun `available letters are the distinct section keys`() {
        assertEquals(setOf("C", "M", "#"), AppListFilter.availableLetters(apps))
    }

    @Test
    fun `available letters of empty list is empty`() {
        assertEquals(emptySet<String>(), AppListFilter.availableLetters(emptyList()))
    }

    // ---- top section (recent + newly installed) ------------------------------

    private val now = 1_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun app(label: String, installed: Long) = AppEntry(
        packageName = "pkg.${label.lowercase()}", activityName = ".Main",
        label = label, firstInstallTime = installed,
    )

    @Test
    fun `recent apps come first, most-recent order, capped at 5`() {
        val list = ('a'..'h').map { app("App$it", installed = 0) }
        // recentKeys most-recent first; ask for 6, only 5 are kept.
        val keys = list.take(6).map { it.key }
        val top = AppListFilter.topApps(list, keys, now)
        assertEquals(list.take(5).map { it.key }, top.map { it.key })
    }

    @Test
    fun `newly installed (last 7 days) follow recents, newest first, excluding recents`() {
        val recent = app("Recent", installed = now - 2 * day)
        val fresh = app("Fresh", installed = now - 1 * day)
        val older = app("Older", installed = now - 3 * day)
        val stale = app("Stale", installed = now - 30 * day) // outside the window
        val list = listOf(recent, fresh, older, stale)

        val top = AppListFilter.topApps(list, recentKeys = listOf(recent.key), now)
        // Recent first; then newly-installed newest-first (Fresh, Older); Stale and
        // the already-recent Recent are excluded from the newly-installed group.
        assertEquals(listOf("Recent", "Fresh", "Older"), top.map { it.label })
    }

    @Test
    fun `recent keys for uninstalled apps are ignored`() {
        val a = app("Alpha", installed = 0)
        val top = AppListFilter.topApps(listOf(a), recentKeys = listOf("pkg.gone/.X", a.key), now)
        assertEquals(listOf("Alpha"), top.map { it.label })
    }

    @Test
    fun `no recents and nothing new yields an empty top section`() {
        val list = listOf(app("Old", installed = now - 100 * day))
        assertEquals(emptyList<AppEntry>(), AppListFilter.topApps(list, emptyList(), now))
    }

    @Test
    fun `apps with a pending notification are appended after recent and new`() {
        val recent = app("Recent", installed = 0)
        val notified = app("Notified", installed = 0)
        val untouched = app("Untouched", installed = 0)
        val list = listOf(recent, notified, untouched)

        val top = AppListFilter.topApps(
            list,
            recentKeys = listOf(recent.key),
            nowMillis = now,
            notifiedPackages = setOf(notified.packageName),
        )
        assertEquals(listOf("Recent", "Notified"), top.map { it.label })
    }

    @Test
    fun `a notified app already in recent or new is not duplicated`() {
        val recent = app("Recent", installed = 0)
        val list = listOf(recent)

        val top = AppListFilter.topApps(
            list,
            recentKeys = listOf(recent.key),
            nowMillis = now,
            notifiedPackages = setOf(recent.packageName),
        )
        assertEquals(listOf("Recent"), top.map { it.label })
    }
}
