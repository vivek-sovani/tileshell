package com.tileshell.core.data

/**
 * Pure, framework-free catalogue logic: alphabetical ordering and letter
 * grouping. Kept separate from [AppCatalogRepository] so it can be unit-tested
 * on the JVM without Android dependencies.
 */
object AppCatalog {

    /**
     * Section key for [label]: its first character uppercased when it is a
     * letter, otherwise "#" (digits, symbols, emoji, blank labels). Matches the
     * prototype jump grid's "#a..z" buckets (design/.../launcher/screens.js).
     */
    fun letterFor(label: String): String {
        val first = label.trim().firstOrNull() ?: return "#"
        return if (first.isLetter()) first.uppercaseChar().toString() else "#"
    }

    /**
     * Case-insensitive alphabetical sort by label, with packageName +
     * activityName as stable tie-breakers so equal labels keep a deterministic
     * order across refreshes.
     */
    fun sorted(entries: List<AppEntry>): List<AppEntry> =
        entries.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER, AppEntry::label)
                .thenBy(AppEntry::packageName)
                .thenBy(AppEntry::activityName),
        )
}
