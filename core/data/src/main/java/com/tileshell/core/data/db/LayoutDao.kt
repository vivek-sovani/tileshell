package com.tileshell.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
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

    /**
     * Persist a new tile order (FR-3.2). Each id's `position` becomes its index
     * in [orderedIds]; applied in one transaction so the layout never observes a
     * half-renumbered grid.
     */
    @Transaction
    suspend fun applyOrder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> updateTilePosition(id, index) }
    }

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
