package com.tileshell.core.data

import android.content.Context

/** A hub as it appears in the app list and quick search. [id] is stable (it is stored in the recent list). */
data class HubEntry(
    val id: String,
    val label: String,
    val iconKey: String,
    val colorId: String,
    /** Other words that find the entry in a search besides its label ("personalize" finds "home settings"). */
    val keywords: List<String> = emptyList(),
)

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

    /** The most hubs (not home settings or quick settings) the app list's "recent" section shows, so hubs cannot crowd the apps out of it. */
    const val MAX_RECENT_HUBS = 2

    val ALL: List<HubEntry> = listOf(
        HubEntry("music", "music + videos", "music", "orange"),
        HubEntry("people", "people", "people", "teal"),
        HubEntry("weather", "weather", "weather", "cyan"),
        HubEntry("calendar", "calendar", "calendar", "magenta"),
        HubEntry("productivity", "productivity", "productivity", "cobalt"),
        HubEntry("clock", "clock", "clock", "cobalt"),
        HubEntry("battery", "battery", "battery", "green"),
        HubEntry("money", "money", "money", "green"),
        HubEntry("markets", "markets", "markets", "cobalt"),
        HubEntry("sports", "sports", "sportshub", "orange"),
        HubEntry("news", "news", "newshub", "red"),
        HubEntry("shopping", "shopping", "shophub", "magenta"),
        HubEntry("health", "health", "healthhub", "lime"),
        // Not hubs, but opened from the same list: the personalize sheet and the quick panel.
        HubEntry("personalize", "home settings", "settings", "steel", keywords = listOf("personalize", "personalise", "customize", "customise")),
        HubEntry("quicksettings", "quick settings", "brightness", "slate"),
    )

    /**
     * Hubs that get their own recents card (hub tasks) but are not app-list or quick-search entries: the panchang,
     * which is a calendar system on the calendar hub's "systems" page and would look odd as an app in most regions.
     */
    val TASK_ONLY: List<HubEntry> = listOf(HubEntry("panchang", "panchang", "calsys", "cobalt"))

    /** The hub with [id] for a task's name, icon and colour: an entry or a task-only hub. */
    fun hubFor(id: String): HubEntry? = ALL.firstOrNull { it.id == id } ?: TASK_ONLY.firstOrNull { it.id == id }

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

    /** Entries that open something that is not a hub (home settings, quick settings): they behave like apps in "recent". */
    private val SYSTEM_IDS = setOf("personalize", "quicksettings")

    /** Whether [app] is a real hub, so counts towards [MAX_RECENT_HUBS] in the "recent" section. */
    fun countsTowardHubCap(app: AppEntry): Boolean = idOf(app)?.let { it !in SYSTEM_IDS } == true

    /** The glyph (`TileIcons` key) for a hub entry. */
    fun iconKeyOf(app: AppEntry): String? = idOf(app)?.let { id -> ALL.firstOrNull { it.id == id }?.iconKey }

    /** The tile colour (`TileAccents` id) of a hub entry: the colour its Start tile has, so the row matches it. */
    fun colorIdOf(app: AppEntry): String? = idOf(app)?.let { id -> ALL.firstOrNull { it.id == id }?.colorId }

    /** Whether [query] (already trimmed, any case) matches the entry through one of its extra keywords. */
    fun matchesKeyword(app: AppEntry, query: String): Boolean {
        val hub = idOf(app)?.let { id -> ALL.firstOrNull { it.id == id } } ?: return false
        return query.isNotEmpty() && hub.keywords.any { it.contains(query, ignoreCase = true) }
    }

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
