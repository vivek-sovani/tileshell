package com.tileshell.core.data

/**
 * A People Hub *page* pinned to Start as its own tile (user-requested: "pin
 * facility for whats new and recents... this will pin recent and whats new
 * as tile" / "i want to pin the full page not a particular line" / "the full
 * tab") — tapping it opens the People Hub landing directly on that page
 * instead of the default "all" page. Same shape as [ContactTile]: a plain
 * [TileModel.App] tile with a blank `packageName` and the page encoded into
 * `activityName`, reusing the ordinary `"people"` icon key so it renders
 * exactly like the main People tile (no schema change, no new tile kind).
 *
 * The *default*, un-pinned "people" tile seeded on a fresh install resolves
 * to a real Contacts app component and has a real (non-blank) `activityName`
 * — [decode] must tolerate that and return null, which the plain
 * `startsWith(PREFIX)` guard already does safely (a real launcher activity
 * name never starts with this prefix).
 */
object PeopleHubTile {
    private const val PREFIX = "peoplehub:"

    /** Encodes a People Hub pivot name ("what's new" / "recent") into an
     * `activityName`-shaped string. */
    fun encode(page: String): String = "$PREFIX$page"

    /** Decodes an `activityName` back to the pivot name, or null if it isn't
     * one of these tiles (including the default, real-Contacts-app tile). */
    fun decode(activityName: String?): String? {
        if (activityName == null || !activityName.startsWith(PREFIX)) return null
        return activityName.removePrefix(PREFIX).ifBlank { null }
    }
}
