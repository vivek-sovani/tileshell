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
    val type: String,
    val packageName: String? = null,
    val activityName: String? = null,
    val label: String? = null,
    val iconKey: String? = null,
    val folderId: String? = null,
) {
    companion object {
        const val TYPE_APP = "app"
        const val TYPE_FOLDER = "folder"
    }
}

/** Folder metadata (name); children live in [FolderChildEntity]. */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
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
