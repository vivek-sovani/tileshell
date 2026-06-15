package com.tileshell.core.data.seed

import com.tileshell.core.data.TileSize

/**
 * How a prototype role id is resolved to an installed app. String action /
 * category values are used (not Android constants) so this table stays pure
 * and unit-testable; the Android resolver turns them into intents.
 */
sealed interface RoleQuery {
    /** `Intent(ACTION_MAIN).addCategory(category)` — resolves a launcher app. */
    data class Category(val category: String) : RoleQuery

    /** `Intent(action)` with optional data URI — resolves an action handler. */
    data class Action(val action: String, val dataUri: String? = null) : RoleQuery

    /** The user's default SMS app (Telephony.Sms.getDefaultSmsPackage). */
    data object DefaultSms : RoleQuery

    /**
     * Try each query in order; the first that resolves wins. Lets a role tolerate
     * device variation — e.g. clock apps that expose `SET_ALARM` but not the more
     * common `SHOW_ALARMS`.
     */
    data class AnyOf(val queries: List<RoleQuery>) : RoleQuery
}

/**
 * A default-layout entry, ported from `DEFAULT_TILES` in data.js.
 *
 * @property liveOnly a self-contained live tile (clock, weather, calendar) whose
 *   content comes from a provider/the system clock, not a launched app. These seed
 *   even when no app resolves their role — the live face still renders — so they
 *   always appear on first run. A resolvable role is still used when present (so
 *   tapping opens the matching app); otherwise the tile gets a blank, inert target.
 */
data class DefaultTile(
    val id: String,
    val size: TileSize,
    val colorId: String,
    val app: String? = null,
    val isGroup: Boolean = false,
    val name: String? = null,
    val children: List<String> = emptyList(),
    val liveOnly: Boolean = false,
)

object DefaultLayout {

    /**
     * Role → resolution strategy. Roles with no standard Android equivalent
     * (weather, notes, bank, …) return null and their tiles are skipped.
     */
    fun roleFor(appId: String): RoleQuery? = when (appId) {
        // Clock apps vary in which alarm action they export; try the common ones
        // in order so the tile resolves a launch target (and pinning the clock app
        // gets the live-clock glyph) on as many devices as possible. The tile is
        // liveOnly regardless, so it always seeds with the live face even if none
        // of these resolve.
        "clock" -> RoleQuery.AnyOf(
            listOf(
                RoleQuery.Action("android.intent.action.SHOW_ALARMS"),
                RoleQuery.Action("android.intent.action.SET_ALARM"),
                RoleQuery.Action("android.intent.action.SHOW_TIMERS"),
            ),
        )
        "phone" -> RoleQuery.Action("android.intent.action.DIAL")
        "camera" -> RoleQuery.Action("android.media.action.STILL_IMAGE_CAMERA")
        "messages" -> RoleQuery.DefaultSms
        "settings" -> RoleQuery.Action("android.settings.SETTINGS")
        "people", "contacts" -> RoleQuery.Category("android.intent.category.APP_CONTACTS")
        "mail" -> RoleQuery.Category("android.intent.category.APP_EMAIL")
        // VIEW on the calendar provider resolves the default calendar app on most
        // devices (more reliable than the APP_CALENDAR launcher category, which is
        // often undeclared); the resolver still launches that package's main entry.
        "calendar" -> RoleQuery.Action(
            "android.intent.action.VIEW",
            "content://com.android.calendar/time",
        )
        "photos" -> RoleQuery.Category("android.intent.category.APP_GALLERY")
        "music" -> RoleQuery.Category("android.intent.category.APP_MUSIC")
        "maps" -> RoleQuery.Category("android.intent.category.APP_MAPS")
        "store" -> RoleQuery.Category("android.intent.category.APP_MARKET")
        "browser" -> RoleQuery.Category("android.intent.category.APP_BROWSER")
        "fitness" -> RoleQuery.Category("android.intent.category.APP_FITNESS")
        "files" -> RoleQuery.Category("android.intent.category.APP_FILES")
        "calc" -> RoleQuery.Category("android.intent.category.APP_CALCULATOR")
        else -> null
    }

    /**
     * Monoline glyph key (TileIcons in :core:design) for a prototype role id,
     * from the `ic` field of `window.APPS` in data.js. Identity for most ids;
     * the two that differ are mapped explicitly.
     */
    fun iconFor(appId: String): String = when (appId) {
        "browser" -> "web"
        "notes" -> "note"
        else -> appId
    }

    /** The default Start layout, ordered, from `window.DEFAULT_TILES()` in data.js. */
    val DEFAULT_TILES: List<DefaultTile> = listOf(
        DefaultTile("t-clock", TileSize.WIDE, "cobalt", app = "clock", liveOnly = true),
        DefaultTile("t-phone", TileSize.MEDIUM, "green", app = "phone"),
        DefaultTile("t-camera", TileSize.MEDIUM, "slate", app = "camera"),
        DefaultTile("t-people", TileSize.MEDIUM, "teal", app = "people"),
        DefaultTile("t-weather", TileSize.MEDIUM, "cyan", app = "weather", liveOnly = true),
        DefaultTile("t-mail", TileSize.MEDIUM, "purple", app = "mail"),
        DefaultTile("t-msg", TileSize.MEDIUM, "amber", app = "messages"),
        DefaultTile("t-cal", TileSize.WIDE, "magenta", app = "calendar", liveOnly = true),
        DefaultTile("t-photos", TileSize.WIDE, "cyan", app = "photos"),
        DefaultTile("t-music", TileSize.WIDE, "orange", app = "music"),
        DefaultTile(
            "g-social", TileSize.MEDIUM, "magenta", isGroup = true, name = "social",
            children = listOf("contacts", "mail", "messages", "people"),
        ),
        DefaultTile("t-maps", TileSize.SMALL, "green", app = "maps"),
        DefaultTile("t-store", TileSize.SMALL, "cobalt", app = "store"),
        DefaultTile("t-settings", TileSize.SMALL, "slate", app = "settings"),
        DefaultTile("t-browser", TileSize.SMALL, "blue", app = "browser"),
        DefaultTile("t-notes", TileSize.SMALL, "amber", app = "notes"),
        DefaultTile("t-fitness", TileSize.SMALL, "lime", app = "fitness"),
        DefaultTile("t-bank", TileSize.MEDIUM, "green", app = "bank"),
        DefaultTile("t-files", TileSize.SMALL, "amber", app = "files"),
        DefaultTile("t-calc", TileSize.SMALL, "steel", app = "calc"),
    )
}
