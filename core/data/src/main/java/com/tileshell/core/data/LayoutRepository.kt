package com.tileshell.core.data

import android.content.Context
import com.tileshell.core.data.db.FolderChildEntity
import com.tileshell.core.data.db.FolderEntity
import com.tileshell.core.data.db.LayoutDao
import com.tileshell.core.data.db.TileEntity
import com.tileshell.core.data.db.TileShellDatabase
import com.tileshell.core.data.db.TileWithFolder
import com.tileshell.core.data.seed.AndroidRoleResolver
import com.tileshell.core.data.seed.DefaultLayout
import com.tileshell.core.data.seed.LayoutSeeder
import com.tileshell.core.data.seed.RoleResolver
import com.tileshell.core.data.seed.SeededTile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Source of truth for the persisted Start layout. Exposes the tiles as a
 * [Flow]<[List]<[TileModel]>> and seeds the WP default layout (mapped to
 * installed apps) on first run.
 */
class LayoutRepository(
    private val dao: LayoutDao,
    private val resolver: RoleResolver,
    private val seeder: LayoutSeeder = LayoutSeeder(),
) {

    /** Live, ordered Start tiles. */
    val tiles: Flow<List<TileModel>> =
        dao.observeTiles().map { rows -> rows.map(::toModel) }

    /** Remove all tiles/folder memberships for an uninstalled package (FR-5). */
    suspend fun removeApp(packageName: String) = dao.removeApp(packageName)

    /** Persist a new top-level tile order after an edit-mode drag (FR-3.2). */
    suspend fun reorderTiles(orderedIds: List<String>) = dao.applyOrder(orderedIds)

    /** Cycle a tile through small → medium → wide → large → small (FR-3.4). */
    suspend fun cycleTileSize(id: String) {
        val current = dao.tilesOnce().firstOrNull { it.tile.id == id }?.tile?.size ?: return
        dao.updateTileSize(id, current.next().name)
    }

    /** Unpin a top-level tile, removing it (and any folder meta) (FR-3.5). */
    suspend fun removeTile(id: String) = dao.removeTile(id)

    /** Rename a folder (FR-4). Blank names are ignored by the caller. */
    suspend fun renameFolder(id: String, name: String) = dao.updateFolderName(id, name)

    /**
     * Pull one app out of a folder back onto Start (FR-4). The app is re-pinned as
     * a fresh tile at the end of the grid; the folder dissolves to a plain tile when
     * a single app is left, or disappears when none remain (see
     * [LayoutDao.removeFolderChild]). [folderId] is the folder tile's own id.
     */
    suspend fun removeFolderChild(folderId: String, child: FolderChild) =
        dao.removeFolderChild(
            folderId = folderId,
            packageName = child.packageName,
            activityName = child.activityName,
            newTileId = "pin-${child.packageName}-${System.currentTimeMillis()}",
            newTileColorId = TileColors.defaultIdFor(child.packageName),
        )

    /**
     * Merge the dragged tile onto the target (FR-3.3): the target becomes a
     * folder holding the de-duplicated union of both tiles' apps and the dragged
     * tile is removed. No-op if either id is missing or they are the same tile.
     */
    suspend fun mergeTiles(dragId: String, targetId: String, survivingOrder: List<String>) {
        if (dragId == targetId) return
        val tiles = dao.tilesOnce().map(::toModel)
        val drag = tiles.firstOrNull { it.id == dragId } ?: return
        val target = tiles.firstOrNull { it.id == targetId } ?: return

        val result = computeMerge(drag, target)
        val folderTile = TileEntity(
            id = target.id,
            position = target.position,
            size = result.size,
            colorId = result.colorId,
            type = TileEntity.TYPE_FOLDER,
            folderId = result.folderId,
        )
        val folder = FolderEntity(id = result.folderId, name = result.name)
        val children = result.children.mapIndexed { index, child ->
            FolderChildEntity(
                folderId = result.folderId,
                position = index,
                packageName = child.packageName,
                activityName = child.activityName,
                label = child.label,
                iconKey = child.iconKey,
            )
        }
        val dragFolderId = if (drag is TileModel.Folder) drag.id else null
        // Persist only ids that still exist as top-level tiles, in the given order.
        val existing = tiles.mapTo(HashSet()) { it.id }
        val ordered = survivingOrder.filter { it != dragId && it in existing }
        dao.applyMerge(folderTile, folder, children, dragId, dragFolderId, ordered)
    }

    /**
     * Built lazily on first pin: maps every resolved default-role package to its
     * designed WP icon key. Covers both standalone tiles and folder children so
     * pinning calendar/mail/phone/etc. gets the designed glyph rather than the
     * real app icon. Resolution is one-shot per process; role changes on the
     * device are rare enough that stale values are acceptable.
     */
    private val roleIconKeyMap: Map<String, String> by lazy {
        buildMap {
            for (tile in DefaultLayout.DEFAULT_TILES) {
                if (tile.isGroup) {
                    for (childId in tile.children) {
                        val role = DefaultLayout.roleFor(childId) ?: continue
                        val pkg = resolver.resolve(role)?.packageName ?: continue
                        put(pkg, DefaultLayout.iconFor(childId))
                    }
                } else {
                    val appId = tile.app ?: continue
                    val role = DefaultLayout.roleFor(appId) ?: continue
                    val pkg = resolver.resolve(role)?.packageName ?: continue
                    put(pkg, DefaultLayout.iconFor(appId))
                }
            }
        }
    }

    /**
     * Pin an app from the app list (FR-5) as a medium tile in the app's default
     * colour, appended to the end of the grid. No-op (returns
     * [PinResult.ALREADY_ON_START]) if a tile for the package already exists.
     * Apps that match a default role (phone, mail, calendar, etc.) get their
     * designed WP icon key; all others default to null and show the real app icon.
     */
    suspend fun pinApp(app: AppEntry): PinResult {
        if (dao.appTileCount(app.packageName) > 0) return PinResult.ALREADY_ON_START
        dao.insertTiles(
            listOf(
                TileEntity(
                    id = "pin-${app.packageName}-${System.currentTimeMillis()}",
                    position = dao.maxPosition() + 1,
                    size = TileSize.MEDIUM,
                    colorId = TileColors.defaultIdFor(app.packageName),
                    type = TileEntity.TYPE_APP,
                    packageName = app.packageName,
                    activityName = app.activityName,
                    label = app.label,
                    iconKey = roleIconKeyMap[app.packageName],
                ),
            ),
        )
        return PinResult.PINNED
    }

    /**
     * Upsert a folder from a set of installed [apps] (the personalize "category
     * folders" feature). Children are de-duplicated by component, in the given
     * order, and pick up a designed WP icon key when the package resolves to a
     * default role (otherwise null → the real app icon).
     *
     * - **New folder** (no existing folder with that name): a MEDIUM tile is
     *   appended to the end of the grid, existing standalone tiles for those apps
     *   are removed.
     * - **Existing folder** (case-insensitive name match): the folder tile keeps
     *   its position/colour; only its children are replaced. Standalone Start tiles
     *   are removed for apps newly added to the folder.
     *
     * Returns false (no-op) when [apps] is empty after de-duplication.
     */
    suspend fun createFolder(name: String, apps: List<AppEntry>): Boolean {
        val deduped = LinkedHashMap<String, AppEntry>()
        for (app in apps) deduped.putIfAbsent(app.packageName + "/" + app.activityName, app)
        val children = deduped.values.toList()
        if (children.isEmpty()) return false

        val trimmed = name.trim()
        val existing = dao.folderByName(trimmed)

        if (existing != null) {
            val prevPackages = dao.folderChildrenOnce(existing.id).mapTo(HashSet()) { it.packageName }
            val newPackages = children.mapTo(HashSet()) { it.packageName }
            // Remove standalone Start tiles only for apps that are newly entering the folder.
            (newPackages - prevPackages).forEach { pkg -> dao.deleteTilesByPackage(pkg) }
            val childRows = children.mapIndexed { index, app ->
                FolderChildEntity(
                    folderId = existing.id,
                    position = index,
                    packageName = app.packageName,
                    activityName = app.activityName,
                    label = app.label,
                    iconKey = roleIconKeyMap[app.packageName],
                )
            }
            dao.updateFolderContents(existing.id, childRows)
            return true
        }

        val folderId = "folder-${System.currentTimeMillis()}"
        val folderTile = TileEntity(
            id = folderId,
            position = dao.maxPosition() + 1,
            size = TileSize.MEDIUM,
            colorId = TileColors.defaultIdFor(trimmed.ifBlank { folderId }),
            type = TileEntity.TYPE_FOLDER,
            folderId = folderId,
        )
        val folder = FolderEntity(id = folderId, name = trimmed)
        val childRows = children.mapIndexed { index, app ->
            FolderChildEntity(
                folderId = folderId,
                position = index,
                packageName = app.packageName,
                activityName = app.activityName,
                label = app.label,
                iconKey = roleIconKeyMap[app.packageName],
            )
        }
        dao.createFolder(folderTile, folder, childRows)
        // Remove any existing individual Start tiles for the apps now in the folder.
        children.forEach { app -> dao.deleteTilesByPackage(app.packageName) }
        return true
    }

    /** Resize a folder child tile; persisted immediately. */
    suspend fun resizeFolderChild(rowId: Long, size: TileSize) =
        dao.updateFolderChildSize(rowId, size)

    /** Reorder folder children by writing new positions for the given ordered rowIds. */
    suspend fun reorderFolderChildren(orderedRowIds: List<Long>) {
        orderedRowIds.forEachIndexed { index, rowId ->
            dao.updateFolderChildPosition(rowId, index)
        }
    }

    /**
     * Re-add a single default live tile (e.g. clock/weather/calendar that was
     * deleted) by [appId], appended to the grid with its designed size/colour/icon
     * key and the seeder's resolved launch target (blank for the self-contained
     * liveOnly tiles). Returns false when there is no such default tile or it can't
     * be seeded (a non-liveOnly role that doesn't resolve on this device).
     */
    suspend fun addDefaultTile(appId: String): Boolean {
        val template = DefaultLayout.DEFAULT_TILES
            .firstOrNull { !it.isGroup && it.app == appId } ?: return false
        val seeded = seeder.seed(listOf(template), resolver)
            .filterIsInstance<SeededTile.App>()
            .firstOrNull() ?: return false
        dao.insertTiles(
            listOf(
                TileEntity(
                    id = "live-$appId-${System.currentTimeMillis()}",
                    position = dao.maxPosition() + 1,
                    size = seeded.size,
                    colorId = seeded.colorId,
                    type = TileEntity.TYPE_APP,
                    packageName = seeded.component.packageName,
                    activityName = seeded.component.activityName,
                    label = seeded.component.label,
                    iconKey = seeded.iconKey,
                ),
            ),
        )
        return true
    }

    /** Seed the default layout iff the grid is empty. Safe to call repeatedly. */
    suspend fun seedIfEmpty() {
        if (dao.tileCount() > 0) return
        writeDefaultLayout()
    }

    /**
     * Reset the Start grid to the WP default layout (FR-7 reset), discarding the
     * user's tiles/folders. Always overwrites (unlike [seedIfEmpty]).
     */
    suspend fun resetLayout() = writeDefaultLayout()

    private suspend fun writeDefaultLayout() {
        val seeded = seeder.seed(DefaultLayout.DEFAULT_TILES, resolver)

        val tileRows = ArrayList<TileEntity>(seeded.size)
        val folderRows = ArrayList<FolderEntity>()
        val childRows = ArrayList<FolderChildEntity>()

        for (tile in seeded) {
            when (tile) {
                is SeededTile.App -> tileRows += TileEntity(
                    id = tile.id,
                    position = tile.position,
                    size = tile.size,
                    colorId = tile.colorId,
                    type = TileEntity.TYPE_APP,
                    packageName = tile.component.packageName,
                    activityName = tile.component.activityName,
                    label = tile.component.label,
                    iconKey = tile.iconKey,
                )

                is SeededTile.Folder -> {
                    folderRows += FolderEntity(id = tile.id, name = tile.name)
                    tileRows += TileEntity(
                        id = tile.id,
                        position = tile.position,
                        size = tile.size,
                        colorId = tile.colorId,
                        type = TileEntity.TYPE_FOLDER,
                        folderId = tile.id,
                    )
                    tile.children.forEachIndexed { index, child ->
                        childRows += FolderChildEntity(
                            folderId = tile.id,
                            position = index,
                            packageName = child.component.packageName,
                            activityName = child.component.activityName,
                            label = child.component.label,
                            iconKey = child.iconKey,
                        )
                    }
                }
            }
        }

        dao.replaceLayout(tileRows, folderRows, childRows)
    }

    private fun toModel(row: TileWithFolder): TileModel {
        val t = row.tile
        return if (t.type == TileEntity.TYPE_FOLDER && row.folder != null) {
            TileModel.Folder(
                id = t.id,
                position = t.position,
                size = t.size,
                colorId = t.colorId,
                name = row.folder.folder.name,
                children = row.folder.children
                    .sortedBy { it.position }
                    .map { FolderChild(it.packageName, it.activityName, it.label, it.iconKey, it.size, it.rowId) },
            )
        } else {
            TileModel.App(
                id = t.id,
                position = t.position,
                size = t.size,
                colorId = t.colorId,
                packageName = t.packageName.orEmpty(),
                activityName = t.activityName.orEmpty(),
                label = t.label,
                iconKey = t.iconKey,
            )
        }
    }

    companion object {
        /** Build a repository backed by the on-device database and PackageManager. */
        fun create(context: Context): LayoutRepository {
            val dao = TileShellDatabase.get(context).layoutDao()
            return LayoutRepository(dao, AndroidRoleResolver(context))
        }
    }
}
