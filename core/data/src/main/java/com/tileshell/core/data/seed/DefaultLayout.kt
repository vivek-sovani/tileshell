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

    /** A specific app by package, for apps with no Android role (YouTube,
     * Google, a wallet). Resolves only when it's installed. */
    data class Package(val packageName: String) : RoleQuery

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
    /** Seeds this `activityName`/label on a liveOnly tile with no app, e.g. a
     * People Hub page tile (`PeopleHubTile.encode`). */
    val activityName: String? = null,
    val label: String? = null,
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
        // The "essentials" folder: apps on nearly every Android phone that no
        // hub already covers (user-requested). None has an Android role.
        "youtube" -> RoleQuery.Package("com.google.android.youtube")
        "google" -> RoleQuery.Package("com.google.android.googlequicksearchbox")
        "chrome" -> RoleQuery.Package("com.android.chrome")
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
        // The gear glyph, by explicit user request (kept consistent with the
        // real Android Settings tile's own identity as "settings-shaped") —
        // the real Settings tile is instead given a distinct look by showing
        // its *actual* device icon (see StartViewModel.migrateSettingsTile),
        // not by picking a different glyph for personalize.
        "personalize" -> "settings"
        // A People Hub page tile carries the people icon key; its page is in
        // its activityName (see PeopleHubTile).
        "whatsnew" -> "people"
        "youtube" -> "video"
        "google" -> "search"
        "chrome" -> "web"
        else -> appId
    }

    /**
     * The default Start layout (redesigned 2026-09-27, user-approved; see
     * DECISIONS.md "Default layout redesign"). First screen: clock; weather and
     * calendar; people plus phone / messages / mail / camera; productivity.
     * Below: music and photos; battery and what's new; an "essentials" folder
     * (YouTube, Google, Chrome, calculator, files) beside settings, store,
     * maps, personalize. [DefaultTile.colorId]s follow the approved group colours,
     * shown when tile colours are "multicolour" (which a fresh install and
     * "reset start layout" switch on): time cobalt, weather cyan, people teal,
     * calls and messages green, productivity purple, media orange/magenta,
     * battery lime, tools steel.
     */
    val DEFAULT_TILES: List<DefaultTile> = listOf(
        DefaultTile("t-clock", TileSize.WIDE, "cobalt", app = "clock", liveOnly = true),
        DefaultTile("t-weather", TileSize.MEDIUM, "cyan", app = "weather", liveOnly = true),
        DefaultTile("t-cal", TileSize.MEDIUM, "cobalt", app = "calendar", liveOnly = true),
        // liveOnly so it seeds even when no app declares the contacts role:
        // the tile opens the people hub either way.
        DefaultTile("t-people", TileSize.MEDIUM, "teal", app = "people", liveOnly = true),
        DefaultTile("t-phone", TileSize.SMALL, "green", app = "phone"),
        DefaultTile("t-msg", TileSize.SMALL, "green", app = "messages"),
        DefaultTile("t-mail", TileSize.SMALL, "teal", app = "mail"),
        DefaultTile("t-camera", TileSize.SMALL, "steel", app = "camera"),
        DefaultTile("t-productivity", TileSize.WIDE, "purple", app = "productivity", liveOnly = true),
        // liveOnly: the tile shows real now-playing content (MediaCenter) and
        // opens the music hub, so a resolved music role only mattered for its
        // tap target (CATEGORY_APP_MUSIC resolves on few devices).
        DefaultTile("t-music", TileSize.MEDIUM, "orange", app = "music", liveOnly = true),
        DefaultTile("t-photos", TileSize.MEDIUM, "magenta", app = "photos"),
        DefaultTile("t-battery", TileSize.MEDIUM, "lime", app = "battery", liveOnly = true),
        // The People Hub's "what's new" page as its own tile.
        DefaultTile(
            "t-whatsnew", TileSize.MEDIUM, "teal", app = "whatsnew", liveOnly = true,
            activityName = "peoplehub:what's new", label = "what's new",
        ),
        // Apps on nearly every phone (user-picked); any that isn't installed
        // is left out, and the folder is dropped if none are.
        DefaultTile(
            "g-essentials", TileSize.MEDIUM, "steel", isGroup = true, name = "essentials",
            children = listOf("youtube", "google", "chrome", "calc", "files"),
        ),
        // Android settings in the small row (not a browser tile, which would
        // duplicate the folder's Chrome on most phones).
        DefaultTile("t-settings", TileSize.SMALL, "steel", app = "settings"),
        DefaultTile("t-store", TileSize.SMALL, "steel", app = "store"),
        DefaultTile("t-maps", TileSize.SMALL, "steel", app = "maps"),
        // Opens this app's own Personalize sheet, not the real Android Settings app —
        // that's retired as a separate Start pin in favor of the Quick Panel's
        // "android settings" tile (see docs/DECISIONS.md).
        DefaultTile("t-personalize", TileSize.SMALL, "steel", app = "personalize", liveOnly = true),
    )

    /**
     * Templates for widgets a user adds explicitly via the Start edit-mode "add
     * widgets" list (`WidgetListSheet`) — unlike [DEFAULT_TILES], these are never
     * part of the seeded fresh-install layout or a "reset start layout," only
     * reachable through [LayoutRepository.addDefaultTile] via [ALL_TILE_TEMPLATES].
     * All three are liveOnly/blank-package (no real app backs a battery, alarm, or
     * moon-phase tile), same as weather/calendar/clock.
     */
    val OPT_IN_WIDGET_TILES: List<DefaultTile> = listOf(
        DefaultTile("t-alarm", TileSize.MEDIUM, "purple", app = "alarm", liveOnly = true),
        DefaultTile("t-moonphase", TileSize.MEDIUM, "slate", app = "moonphase", liveOnly = true),
        DefaultTile("t-tasks", TileSize.MEDIUM, "blue", app = "tasks", liveOnly = true),
        DefaultTile("t-notepad", TileSize.MEDIUM, "amber", app = "notepad", liveOnly = true),
        DefaultTile("t-stickynote", TileSize.MEDIUM, "amber", app = "stickynote", liveOnly = true),
        DefaultTile("t-flashlight", TileSize.MEDIUM, "steel", app = "flashlight", liveOnly = true),
        DefaultTile("t-countdown", TileSize.MEDIUM, "magenta", app = "countdown", liveOnly = true),
        DefaultTile("t-steps", TileSize.MEDIUM, "lime", app = "steps", liveOnly = true),
        DefaultTile("t-sports", TileSize.MEDIUM, "red", app = "sports", liveOnly = true),
        DefaultTile("t-stock", TileSize.MEDIUM, "teal", app = "stock", liveOnly = true),
        DefaultTile("t-commodity", TileSize.MEDIUM, "mauve", app = "commodity", liveOnly = true),
        DefaultTile("t-calsys", TileSize.MEDIUM, "cobalt", app = "calsys", liveOnly = true),
        DefaultTile("t-money", TileSize.MEDIUM, "green", app = "money", liveOnly = true),
        DefaultTile("t-markets", TileSize.MEDIUM, "cobalt", app = "markets", liveOnly = true),
        DefaultTile("t-sportshub", TileSize.MEDIUM, "orange", app = "sportshub", liveOnly = true),
        DefaultTile("t-clockhub", TileSize.MEDIUM, "steel", app = "clockhub", liveOnly = true),
        DefaultTile("t-newshub", TileSize.MEDIUM, "red", app = "newshub", liveOnly = true),
    )

    /** Every known tile template — the default-layout set plus opt-in-only widgets. */
    val ALL_TILE_TEMPLATES: List<DefaultTile> = DEFAULT_TILES + OPT_IN_WIDGET_TILES
}
