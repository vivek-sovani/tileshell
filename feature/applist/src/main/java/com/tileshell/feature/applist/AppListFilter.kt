package com.tileshell.feature.applist

import com.tileshell.core.data.AppEntry

/**
 * Pure app-list query logic (FR-5), kept framework-free for unit testing.
 */
object AppListFilter {

    /**
     * Case-insensitive substring match on the app label, matching the
     * prototype's `name.toLowerCase().includes(filter)` (screens.js). A blank
     * query returns everything; the query is trimmed first.
     */
    fun filter(apps: List<AppEntry>, query: String): List<AppEntry> {
        val q = query.trim()
        return if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }

    /** Section letters present in [apps] (uppercase A–Z, or "#"). */
    fun availableLetters(apps: List<AppEntry>): Set<String> =
        apps.mapTo(mutableSetOf()) { it.letter }

    /** Apps installed within [NEW_WINDOW_MS] of [nowMillis]. */
    private const val NEW_WINDOW_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
    private const val MAX_RECENT = 5
    private const val MAX_NEW = 5

    /**
     * The "recent" section shown at the top of the app list when not searching:
     * the up-to-[MAX_RECENT] most-recently-launched apps (ordered by [recentKeys],
     * most-recent first), followed by up-to-[MAX_NEW] newly-installed apps
     * (installed within the last week of [nowMillis], newest first) that aren't
     * already in the recent set. De-duplicated; only apps still in [apps] are kept.
     * Pure, so the ordering/windowing is unit-testable.
     */
    fun topApps(
        apps: List<AppEntry>,
        recentKeys: List<String>,
        nowMillis: Long,
    ): List<AppEntry> {
        val byKey = apps.associateBy { it.key }
        val recent = recentKeys.mapNotNull { byKey[it] }.take(MAX_RECENT)
        val recentKeySet = recent.mapTo(mutableSetOf()) { it.key }
        val newlyInstalled = apps
            .filter { it.firstInstallTime > 0 && nowMillis - it.firstInstallTime <= NEW_WINDOW_MS }
            .filter { it.key !in recentKeySet }
            .sortedByDescending { it.firstInstallTime }
            .take(MAX_NEW)
        return recent + newlyInstalled
    }
}
