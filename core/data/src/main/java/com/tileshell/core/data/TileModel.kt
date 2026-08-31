package com.tileshell.core.data

/** An app pinned inside a folder. */
data class FolderChild(
    val packageName: String,
    val activityName: String,
    val label: String?,
    val iconKey: String? = null,
    val size: TileSize = TileSize.MEDIUM,
    val rowId: Long = 0,
    /** Per-tile accent override (FR-7), carried in/out of the folder; null = follow. */
    val accentOverride: String? = null,
)

/**
 * A Start-screen tile as consumed by the UI: either a single [App] or a
 * [Folder] of apps. Ordered by [position]; sized by [size] and tinted by
 * [colorId] (a prototype accent id resolved via TileAccents in :core:design).
 */
/**
 * True if [this] already contains the "personalize" liveOnly Start tile
 * (blank package, like weather/calendar — see `DefaultLayout`'s "personalize"
 * role). Identified by label rather than iconKey, so its icon stays a free
 * visual choice decoupled from identity. Shared between `StartViewModel`
 * (the existing-install migration backfill) and `AppListViewModel` (the App
 * List's own "pin it back" action for the synthetic personalize entry).
 */
fun List<TileModel>.hasPersonalizeTile(): Boolean = any {
    it is TileModel.App && it.packageName.isBlank() && it.label == "personalize"
}

/**
 * True if [this] already contains the shared-notepad live tile ("notes" in the
 * widget catalog, iconKey `"notepad"`). Notes has exactly one repository-backed
 * list behind every pinned tile (see `NoteRepository`), so a second pin would
 * just show the same shared content twice — the widget catalog uses this to
 * grey that entry out once one exists. A sticky note tile is the opposite: its
 * text lives on the tile's own row (`LayoutRepository.setTileText`), so each
 * pin is independent and this check doesn't apply to it.
 */
fun List<TileModel>.hasNotesTile(): Boolean = any {
    it is TileModel.App && it.packageName.isBlank() && it.iconKey == "notepad"
}

sealed interface TileModel {
    val id: String
    val position: Int
    val size: TileSize
    val colorId: String
    /**
     * Anchored absolute grid cell for the windows-phone-style gap-preserving
     * arrangement (null = never anchored, floats to the first free cell);
     * ignored entirely while the default dense-packing mode is active.
     */
    val gridSlot: Int?

    data class App(
        override val id: String,
        override val position: Int,
        override val size: TileSize,
        override val colorId: String,
        val packageName: String,
        val activityName: String,
        val label: String?,
        val iconKey: String? = null,
        /** Per-tile accent override (FR-7); null = follow the global accent. */
        val accentOverride: String? = null,
        override val gridSlot: Int? = null,
        /**
         * ICONS home style's per-tile "show as icon" vs "show as tile" choice
         * (a single app's parallel to [Folder.showAsStack]) — defaults to
         * `true` (icon) per the user's own request, so a fresh or upgraded
         * install shows icons rather than live content unless a tile is
         * explicitly switched to "show as tile." Only actually takes effect
         * when [homeStyle][com.tileshell.core.data.settings.HomeStyle] is
         * `ICONS` *and* [packageName] is non-blank — TILES mode ignores it
         * entirely, and a blank-package liveOnly tile (weather/calendar/clock/
         * personalize, which has no real app icon to show) always renders as
         * a live tile regardless of this flag's value.
         */
        val displayAsIcon: Boolean = true,
    ) : TileModel

    data class Folder(
        override val id: String,
        override val position: Int,
        override val size: TileSize,
        override val colorId: String,
        val name: String,
        val children: List<FolderChild>,
        /** Per-tile accent override (FR-7); null = follow the global accent. */
        val accentOverride: String? = null,
        override val gridSlot: Int? = null,
        /**
         * Explicit "show as stack" choice (the folder-overlay toggle), persisted
         * on [com.tileshell.core.data.db.FolderEntity] independent of [children]'s
         * sizes. Needed because [TileSize.stackable] now covers most sizes
         * (including the default MEDIUM) — deriving stack-ness from uniformity
         * alone, as before, would make almost every ordinary folder with
         * same-sized children auto-render as a stack. See [isStack].
         */
        val showAsStack: Boolean = false,
    ) : TileModel {
        /**
         * A folder renders as a **widget stack** (a swipeable carousel of
         * full-size live tiles) only when both [showAsStack] is on (the user's
         * explicit toggle) AND the children currently happen to be uniformly one
         * [TileSize.stackable] size ([stackSize] non-null). If a member's own
         * resize temporarily breaks that uniformity, this simply falls back to
         * the plain mini-grid without touching [showAsStack] — the folder
         * resumes rendering as a stack automatically once uniformity returns,
         * with no separate "re-enable" action needed.
         */
        val isStack: Boolean
            get() = showAsStack && stackSize != null

        /**
         * The uniform member size [isStack] renders at when eligible, or null if
         * the members aren't currently all one [TileSize.stackable] size. Purely
         * an eligibility/footprint check — independent of [showAsStack].
         */
        val stackSize: TileSize?
            get() {
                val first = children.firstOrNull()?.size ?: return null
                if (!first.stackable) return null
                return first.takeIf { size -> children.all { it.size == size } }
            }
    }
}
