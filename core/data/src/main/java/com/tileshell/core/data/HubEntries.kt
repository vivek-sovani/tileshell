package com.tileshell.core.data

import android.content.Context

/** A hub as it appears in the app list and quick search. [id] is stable (it is stored in the recent list). */
data class HubEntry(val id: String, val label: String, val iconKey: String)

/**
 * The hubs as searchable, launchable entries (experiment, switch
 * `tileshell.hubEntries` in gradle.properties).
 *
 * A hub entry is an [AppEntry] with a blank package, like the synthetic
 * "home settings" entry, and the activity name `hub:<id>` (see [entryFor]); its
 * recent-list key is therefore `/hub:<id>`, which no installed app's
 * `package/activity` key can equal, and which older builds simply never find in
 * their catalogue (so keys written by this build are harmless to them, and the
 * keys they wrote are untouched here). Pure, so the encoding is unit-tested.
 */
object HubEntries {
    /** Build switch, from gradle.properties → `tileshell.hubEntries`. */
    val ENABLED: Boolean = BuildConfig.HUB_ENTRIES

    private const val PREFIX = "hub:"

    /** The most hubs the app list's "recent" section shows, so hubs cannot crowd the apps out of it. */
    const val MAX_RECENT_HUBS = 2

    val ALL: List<HubEntry> = listOf(
        HubEntry("music", "music + videos", "music"),
        HubEntry("people", "people", "people"),
        HubEntry("weather", "weather", "weather"),
        HubEntry("calendar", "calendar", "calendar"),
        HubEntry("productivity", "productivity", "productivity"),
        HubEntry("clock", "clock", "clock"),
        HubEntry("battery", "battery", "battery"),
        HubEntry("money", "money", "money"),
        HubEntry("markets", "markets", "markets"),
        HubEntry("sports", "sports", "sportshub"),
        HubEntry("news", "news", "newshub"),
        HubEntry("shopping", "shopping", "shophub"),
        HubEntry("health", "health", "healthhub"),
        HubEntry("panchang", "panchang", "calsys"),
    )

    fun entryFor(hub: HubEntry): AppEntry =
        AppEntry(packageName = "", activityName = PREFIX + hub.id, label = hub.label)

    /** Every hub as a catalogue entry. */
    fun entries(): List<AppEntry> = ALL.map(::entryFor)

    /** The hub id an entry stands for, or null for an app or another synthetic entry. */
    fun idOf(packageName: String, activityName: String): String? =
        if (packageName.isBlank() && activityName.startsWith(PREFIX)) {
            activityName.removePrefix(PREFIX).takeIf { id -> ALL.any { it.id == id } }
        } else {
            null
        }

    fun idOf(app: AppEntry): String? = idOf(app.packageName, app.activityName)

    fun isHub(app: AppEntry): Boolean = idOf(app) != null

    /** The glyph (`TileIcons` key) for a hub entry. */
    fun iconKeyOf(app: AppEntry): String? = idOf(app)?.let { id -> ALL.firstOrNull { it.id == id }?.iconKey }

    /** The recent-list key a launch of hub [id] is stored under (same shape as [AppEntry.key]). */
    fun recentKey(id: String): String = "/$PREFIX$id"

    /** Records opening hub [id] for the app list's "recent" section. */
    fun recordOpen(context: Context, id: String) {
        RecentApps.record(context, "", PREFIX + id)
    }

    /**
     * Where hub entries go in a catalogue: with [ENABLED] off, the list is returned unchanged;
     * on, the hub entries are added and the whole list is kept alphabetical.
     */
    fun withHubs(apps: List<AppEntry>, enabled: Boolean = ENABLED): List<AppEntry> =
        if (enabled) AppCatalog.sorted(apps + entries()) else apps
}
