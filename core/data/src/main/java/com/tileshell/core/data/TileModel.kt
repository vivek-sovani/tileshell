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
sealed interface TileModel {
    val id: String
    val position: Int
    val size: TileSize
    val colorId: String

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
    ) : TileModel {
        /**
         * A folder renders as a **widget stack** (a swipeable carousel of full-size
         * live tiles) while every member is uniformly WIDE or LARGE. Derived, not
         * stored: the instant a member is resized down or a smaller tile is merged
         * in, this turns false and the folder renders as the normal mini-grid.
         */
        val isStack: Boolean
            get() = stackSize != null

        /**
         * The uniform member size driving [isStack] — WIDE or LARGE — or null if the
         * members aren't uniformly one of those sizes. A WIDE stack comes from the
         * folder overlay's "make stack · wide" action (or every member individually
         * resized to WIDE); a LARGE stack comes from merging two large tiles or
         * "make stack · large".
         */
        val stackSize: TileSize?
            get() {
                val first = children.firstOrNull()?.size ?: return null
                if (first != TileSize.WIDE && first != TileSize.LARGE) return null
                return first.takeIf { size -> children.all { it.size == size } }
            }
    }
}
