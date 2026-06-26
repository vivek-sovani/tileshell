package com.tileshell.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.tileshell.core.data.TileSize
import kotlinx.coroutines.flow.Flow

@Dao
interface LayoutDao {

    @Transaction
    @Query("SELECT * FROM tiles ORDER BY position")
    fun observeTiles(): Flow<List<TileWithFolder>>

    /** One-shot snapshot of the tiles (with folders), for merge computation. */
    @Transaction
    @Query("SELECT * FROM tiles ORDER BY position")
    suspend fun tilesOnce(): List<TileWithFolder>

    @Query("SELECT COUNT(*) FROM tiles")
    suspend fun tileCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTiles(tiles: List<TileEntity>)

    /** Count app tiles already pinned for a package (pin de-dupe, FR-5). */
    @Query("SELECT COUNT(*) FROM tiles WHERE type = 'app' AND packageName = :packageName")
    suspend fun appTileCount(packageName: String): Int

    /** Highest grid position, or -1 when empty — new tiles append after it. */
    @Query("SELECT COALESCE(MAX(position), -1) FROM tiles")
    suspend fun maxPosition(): Int

    /** Set a single tile's grid position (used by the edit-mode reorder). */
    @Query("UPDATE tiles SET position = :position WHERE id = :id")
    suspend fun updateTilePosition(id: String, position: Int)

    /** Set a tile's size (FR-3.4 resize). [size] is the stored [TileSize] name. */
    @Query("UPDATE tiles SET size = :size WHERE id = :id")
    suspend fun updateTileSize(id: String, size: String)

    /**
     * Remove a top-level tile (FR-3.5 unpin). A folder tile shares its id with
     * its `folders` row, so deleting that row too drops the folder meta and
     * cascades its children; for an app tile the folder delete is a no-op.
     */
    @Transaction
    suspend fun removeTile(id: String) {
        deleteTileById(id)
        deleteFolderById(id)
    }

    /**
     * Persist a new tile order (FR-3.2). Each id's `position` becomes its index
     * in [orderedIds]; applied in one transaction so the layout never observes a
     * half-renumbered grid.
     */
    @Transaction
    suspend fun applyOrder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> updateTilePosition(id, index) }
    }

    /** Rename a folder (FR-4 folder overlay rename). */
    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun updateFolderName(id: String, name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<FolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolderChildren(children: List<FolderChildEntity>)

    @Query("DELETE FROM tiles")
    suspend fun clearTiles()

    @Query("DELETE FROM folders")
    suspend fun clearFolders()

    /**
     * Atomically replace the whole persisted layout. Folders are inserted first
     * so child rows satisfy the foreign key; clearing folders cascades to
     * `folder_children`.
     */
    @Transaction
    suspend fun replaceLayout(
        tiles: List<TileEntity>,
        folders: List<FolderEntity>,
        children: List<FolderChildEntity>,
    ) {
        clearTiles()
        clearFolders()
        insertFolders(folders)
        insertTiles(tiles)
        insertFolderChildren(children)
    }

    // ---- create a folder directly (category folders) --------------------

    /**
     * Insert a brand-new folder tile + its meta + children in one transaction.
     * Used by the personalize "category folders" feature, which builds a folder
     * from a set of installed apps rather than by merging two tiles. Folders are
     * written before children so the foreign key is satisfied.
     */
    @Transaction
    suspend fun createFolder(
        folderTile: TileEntity,
        folder: FolderEntity,
        children: List<FolderChildEntity>,
    ) {
        insertFolders(listOf(folder))
        insertTiles(listOf(folderTile))
        insertFolderChildren(children)
    }

    /** Find an existing folder by name (case-insensitive). Used for upsert. */
    @Query("SELECT * FROM folders WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun folderByName(name: String): FolderEntity?

    /**
     * Replace all children of an existing folder in one transaction. Used when
     * the user re-creates a category folder that already exists — children are
     * swapped, the folder tile and its position/colour stay in place.
     */
    @Transaction
    suspend fun updateFolderContents(folderId: String, children: List<FolderChildEntity>) {
        deleteFolderChildren(folderId)
        insertFolderChildren(children)
    }

    // ---- merge to folder (FR-3.3) ---------------------------------------

    @Query("DELETE FROM folder_children WHERE folderId = :folderId")
    suspend fun deleteFolderChildren(folderId: String)

    @Query("DELETE FROM tiles WHERE id = :id")
    suspend fun deleteTileById(id: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderById(id: String)

    /**
     * Apply a merge (FR-3.3) in one transaction: rewrite the target tile as a
     * [folderTile] backed by [folder], replace its children with [children]
     * (the de-duplicated union), then drop the dragged tile — and, when it was
     * itself a folder ([dragFolderId] non-null), its folder meta (children
     * cascade). Folders are written before children so the FK is satisfied.
     */
    @Transaction
    suspend fun applyMerge(
        folderTile: TileEntity,
        folder: FolderEntity,
        children: List<FolderChildEntity>,
        dragTileId: String,
        dragFolderId: String?,
        survivingOrder: List<String>,
    ) {
        insertFolders(listOf(folder))
        insertTiles(listOf(folderTile))
        deleteFolderChildren(folder.id)
        insertFolderChildren(children)
        deleteTileById(dragTileId)
        if (dragFolderId != null) deleteFolderById(dragFolderId)
        // Renumber the survivors so any reorder incurred on the way to the merge
        // target is persisted alongside it (the dragged tile is now gone).
        survivingOrder.forEachIndexed { index, id -> updateTilePosition(id, index) }
    }

    // ---- remove one app from a folder (FR-4) ----------------------------

    @Query(
        "DELETE FROM folder_children WHERE folderId = :folderId " +
            "AND packageName = :packageName AND activityName = :activityName",
    )
    suspend fun deleteFolderChildComponent(
        folderId: String,
        packageName: String,
        activityName: String,
    )

    @Query("SELECT * FROM folder_children WHERE folderId = :folderId ORDER BY position")
    suspend fun folderChildrenOnce(folderId: String): List<FolderChildEntity>

    @Query("UPDATE folder_children SET position = :position WHERE rowId = :rowId")
    suspend fun updateFolderChildPosition(rowId: Long, position: Int)

    @Query("UPDATE folder_children SET size = :size WHERE rowId = :rowId")
    suspend fun updateFolderChildSize(rowId: Long, size: TileSize)

    @Query(
        "UPDATE tiles SET type = '" + TileEntity.TYPE_APP + "', packageName = :packageName, " +
            "activityName = :activityName, label = :label, iconKey = :iconKey, folderId = NULL " +
            "WHERE id = :id",
    )
    suspend fun convertFolderTileToApp(
        id: String,
        packageName: String,
        activityName: String,
        label: String?,
        iconKey: String?,
    )

    /**
     * Pull one app out of a folder back onto Start (FR-4). [folderId] is the folder
     * tile's own id (DECISIONS S5). The removed app is re-pinned as a fresh app tile
     * ([newTileId], [newTileColorId]) appended to the end of the grid — taking an app
     * out of a folder returns it to Start rather than deleting it. The folder is then
     * collapsed:
     *  - **≥2 left** → renumber the survivors and keep the folder;
     *  - **exactly 1 left** → dissolve: rewrite the folder tile as a plain app tile
     *    for the survivor (keeping its slot/size/colour) and drop the folder meta
     *    (its leftover child row cascades away);
     *  - **none left** → drop the folder tile and its meta entirely.
     */
    @Transaction
    suspend fun removeFolderChild(
        folderId: String,
        packageName: String,
        activityName: String,
        newTileId: String,
        newTileColorId: String,
    ) {
        val removed = folderChildrenOnce(folderId)
            .firstOrNull { it.packageName == packageName && it.activityName == activityName }
        deleteFolderChildComponent(folderId, packageName, activityName)
        // Re-pin the pulled-out app as a top-level Start tile (parallels pinApp).
        if (removed != null) {
            insertTiles(
                listOf(
                    TileEntity(
                        id = newTileId,
                        position = maxPosition() + 1,
                        size = TileSize.MEDIUM,
                        colorId = newTileColorId,
                        type = TileEntity.TYPE_APP,
                        packageName = removed.packageName,
                        activityName = removed.activityName,
                        label = removed.label,
                        iconKey = removed.iconKey,
                    ),
                ),
            )
        }
        val remaining = folderChildrenOnce(folderId)
        when {
            remaining.size >= 2 ->
                remaining.forEachIndexed { index, child ->
                    updateFolderChildPosition(child.rowId, index)
                }
            remaining.size == 1 -> {
                val survivor = remaining.first()
                convertFolderTileToApp(
                    id = folderId,
                    packageName = survivor.packageName,
                    activityName = survivor.activityName,
                    label = survivor.label,
                    iconKey = survivor.iconKey,
                )
                deleteFolderById(folderId) // cascade removes the survivor's child row
            }
            else -> {
                deleteTileById(folderId)
                deleteFolderById(folderId)
            }
        }
    }

    // ---- uninstall removal (FR-5) ---------------------------------------

    @Query("DELETE FROM tiles WHERE packageName = :packageName")
    suspend fun deleteTilesByPackage(packageName: String)

    @Query("DELETE FROM folder_children WHERE packageName = :packageName")
    suspend fun deleteFolderChildrenByPackage(packageName: String)

    @Query("DELETE FROM tiles WHERE type = 'folder' AND folderId NOT IN (SELECT folderId FROM folder_children)")
    suspend fun deleteEmptyFolderTiles()

    @Query("DELETE FROM folders WHERE id NOT IN (SELECT folderId FROM folder_children)")
    suspend fun deleteEmptyFolders()

    /**
     * Remove every trace of an uninstalled package: its app tiles and folder
     * memberships, then any folder left empty (its tile + meta).
     */
    @Transaction
    suspend fun removeApp(packageName: String) {
        deleteTilesByPackage(packageName)
        deleteFolderChildrenByPackage(packageName)
        deleteEmptyFolderTiles()
        deleteEmptyFolders()
    }

    // ---- app cache ------------------------------------------------------

    @Upsert
    suspend fun upsertApps(apps: List<AppCacheEntity>)

    @Query("SELECT * FROM app_cache ORDER BY label COLLATE NOCASE")
    fun observeCachedApps(): Flow<List<AppCacheEntity>>
}
