package com.tileshell.core.data

/** An app pinned inside a folder. */
data class FolderChild(
    val packageName: String,
    val activityName: String,
    val label: String?,
    val iconKey: String? = null,
    val size: TileSize = TileSize.MEDIUM,
    val rowId: Long = 0,
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
    ) : TileModel
}
