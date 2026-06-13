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

    // ---- app cache ------------------------------------------------------

    @Upsert
    suspend fun upsertApps(apps: List<AppCacheEntity>)

    @Query("SELECT * FROM app_cache ORDER BY label COLLATE NOCASE")
    fun observeCachedApps(): Flow<List<AppCacheEntity>>
}
