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
