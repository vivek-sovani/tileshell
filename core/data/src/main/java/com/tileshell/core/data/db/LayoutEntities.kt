package com.tileshell.core.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.tileshell.core.data.TileSize

/**
 * A tile on the Start grid, in [position] order. [type] is "app" or "folder";
 * app tiles carry [packageName]/[activityName]/[label], folder tiles carry a
 * [folderId] that points at a [FolderEntity] (folder tiles reuse their own id
 * as the folder id — see docs/DECISIONS.md S5).
 */
@Entity(tableName = "tiles")
data class TileEntity(
    @PrimaryKey val id: String,
    val position: Int,
    val size: TileSize,
    val colorId: String,
    // Per-tile accent override (FR-7): a palette id that wins over the global
    // accent, or null to follow it. Added in schema v4; existing rows decode to
    // null (= follow global), preserving the prior uniform-accent look.
    val accentOverride: String? = null,
    // Absolute grid cell (row * 1000 + col, see GridPacker.encodeSlot in
    // :feature:start), used while LauncherSettings.tilePackMode is STICKY or
    // FREE (see TilePackMode.isAnchored) — FREE reuses the same anchored cell,
    // just with different push-down/collapse behaviour on top of it. Null
    // means "never anchored" — the tile floats to the first free cell after
    // the current bottom row rather than a fixed spot. Added in schema v6;
    // ignored entirely in the default DENSE mode.
    val gridSlot: Int? = null,
    val type: String,
    val packageName: String? = null,
    val activityName: String? = null,
    val label: String? = null,
    val iconKey: String? = null,
    val folderId: String? = null,
    // ICONS home style's per-tile "show as icon" (true) vs "show as tile"
    // (false) toggle — see TileModel.App.displayAsIcon's doc comment. Added
    // in schema v8; defaults true (icon) for every row, including ones that
    // existed before this column did.
    val displayAsIcon: Boolean = true,
    // Named-section membership (Start screen "sections" feature): the owning
    // SectionEntity's id, or null to render in the default unsectioned area
    // at the bottom of the screen — the same place every tile renders today.
    // Added in schema v13; every existing row decodes to null, so an
    // upgrading install's layout is visually unchanged until the user
    // explicitly creates a section.
    val sectionId: String? = null,
) {
    companion object {
        const val TYPE_APP = "app"
        const val TYPE_FOLDER = "folder"
    }
}

/**
 * A named, collapsible group of top-level Start tiles ("work", "games", ...),
 * in [sortOrder]. Purely organizational — a [TileEntity] carries its own
 * `sectionId`, so deleting a section (`LayoutDao.mergeSectionInto`) merges its
 * member tiles into whichever page the user picks (default: unsectioned)
 * rather than deleting them. Added in schema v13.
 */
@Entity(tableName = "sections")
data class SectionEntity(
    @PrimaryKey val id: String,
    val label: String,
    val sortOrder: Int,
    val collapsed: Boolean = false,
)

/** Folder metadata (name); children live in [FolderChildEntity]. */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    // Explicit "show as stack" toggle (see TileModel.Folder.showAsStack's doc
    // comment) — independent of the children's own sizes. Added in schema v7;
    // defaults false so every existing folder keeps rendering as a plain
    // mini-grid on upgrade (a pre-existing WIDE/LARGE-uniform stack is
    // re-flagged true by the same migration, so it doesn't visually flip to a
    // folder on upgrade either — see MIGRATION_6_7).
    val showAsStack: Boolean = false,
)

/** One app inside a folder, in [position] order. Cascades when the folder dies. */
@Entity(
    tableName = "folder_children",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("folderId")],
)
data class FolderChildEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val folderId: String,
    val position: Int,
    val packageName: String,
    val activityName: String,
    val label: String? = null,
    val iconKey: String? = null,
    val size: TileSize = TileSize.MEDIUM,
    // Per-tile accent override carried with the app in/out of the folder (v5).
    val accentOverride: String? = null,
)

/**
 * Cached app metadata so tiles can render labels/letters before the live
 * catalogue loads and so uninstalls can be detected. Keyed by component string
 * "packageName/activityName".
 */
@Entity(tableName = "app_cache")
data class AppCacheEntity(
    @PrimaryKey val component: String,
    val packageName: String,
    val activityName: String,
    val label: String,
    val letter: String,
    val lastSeen: Long,
)

/**
 * One item in the Tasks live tile's checklist, in [position] order within its
 * own [listId] — the owning pinned tile/gadget's own stable id, so each
 * pinned Tasks instance keeps an independent list (v10→v11 migration; rows
 * written before that default to `"default"`, backfilled to the oldest
 * existing Tasks tile so its data isn't orphaned on upgrade — see
 * `TileShellDatabase.MIGRATION_10_11`).
 */
// Indexed on listId: every TaskDao query filters by it (each pinned Tasks
// tile/widget keeps its own list), so without this each one is a full table
// scan. folder_children already had the equivalent index for its own
// WHERE folderId queries; tasks never got it when listId was introduced.
@Entity(tableName = "tasks", indices = [Index("listId")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val done: Boolean = false,
    val listId: String = "default",
    val position: Int,
    val createdAt: Long,
)

/**
 * A named task list ("work", "home") — v13→v14. [id] is the same `listId`
 * the list's [TaskEntity] rows carry: a pinned Tasks tile's own id for older
 * lists, or a `list-…` id for a list created in the productivity hub. A list
 * outlives any tile showing it, so unpinning a Tasks tile never loses it.
 */
@Entity(tableName = "task_lists")
data class TaskListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

/** A [TaskListEntity] with its count of unfinished tasks, for the hub. */
data class TaskListSummaryRow(
    val id: String,
    val name: String,
    val openCount: Int,
)

/**
 * One note in the Notes live tile's notepad, most-recently-edited first
 * ([updatedAt] desc). Independent of any specific pinned tile — one shared
 * notepad. A sticky note tile is one of these notes pinned to Start (its
 * tile's `activityName` links to the note, see `StickyNoteTile`), so every
 * note — pinned or not — also shows in the notepad and the productivity hub.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val updatedAt: Long,
)

// ---- relations ----------------------------------------------------------

/** A folder with its ordered children, assembled by Room. */
data class FolderWithChildren(
    @Embedded val folder: FolderEntity,
    @Relation(parentColumn = "id", entityColumn = "folderId")
    val children: List<FolderChildEntity>,
)

/** A tile with its folder (null for app tiles), assembled by Room. */
data class TileWithFolder(
    @Embedded val tile: TileEntity,
    @Relation(entity = FolderEntity::class, parentColumn = "folderId", entityColumn = "id")
    val folder: FolderWithChildren?,
)

// ---- converters ---------------------------------------------------------

class Converters {
    @TypeConverter
    fun fromTileSize(size: TileSize): String = size.name

    @TypeConverter
    fun toTileSize(name: String): TileSize =
        runCatching { TileSize.valueOf(name) }.getOrDefault(TileSize.MEDIUM)
}
