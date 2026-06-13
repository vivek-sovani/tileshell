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
}
