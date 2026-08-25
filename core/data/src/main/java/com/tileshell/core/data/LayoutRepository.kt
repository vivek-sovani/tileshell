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

    /**
     * Cycle a tile's size (FR-3.4). [largeAllowed] adds the 3×3 [TileSize.LARGE]
     * step (medium → small → wide → large → medium); otherwise the cycle is
     * medium → small → wide → medium.
     */
    suspend fun cycleTileSize(id: String, largeAllowed: Boolean = false) {
        val current = dao.tilesOnce().firstOrNull { it.tile.id == id }?.tile?.size ?: return
        dao.updateTileSize(id, current.next(largeAllowed).name)
    }

    /**
     * Set a tile's size directly, rather than stepping through [cycleTileSize]'s
     * fixed cycle — the write path for gesture-based drag resize
     * (`StartViewModel.resizeTo`), which can land on any of the eleven
     * [TileSize] presets, not just the four the tap cycle reaches.
     */
    suspend fun setTileSize(id: String, size: TileSize) = dao.updateTileSize(id, size.name)

    /** Set or clear a tile's per-tile accent override (null = follow global, FR-7). */
    suspend fun setTileAccent(id: String, accentOverride: String?) =
        dao.updateTileAccent(id, accentOverride)

    /**
     * Anchor (or, with null, un-anchor) a tile at an absolute grid cell —
     * windows-phone-style gap-preserving arrangement. No-op in dense mode
     * (nothing reads gridSlot there).
     */
    suspend fun setTileGridSlot(id: String, gridSlot: Int?) =
        dao.updateTileGridSlot(id, gridSlot)

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
            rowId = child.rowId,
            newTileId = "pin-${child.packageName}-${System.currentTimeMillis()}",
            newTileColorId = TileColors.defaultIdFor(child.packageName),
        )

    /**
     * Pull one app out of a folder and place it as a top-level tile exactly
     * where a drag released it (contrast [removeFolderChild], which always
     * appends to the bottom). [gridSlot] anchors it directly (sticky/free
     * arrangement); [reorderedIds], when given, is persisted as the complete
     * final top-level order (dense arrangement's drop-insertion point — see
     * `insertBeforeTarget` in `:feature:start`).
     */
    suspend fun placeFolderChildAtTopLevel(
        folderId: String,
        child: FolderChild,
        newTileId: String,
        gridSlot: Int? = null,
        reorderedIds: List<String>? = null,
    ) = dao.placeFolderChildAtTopLevel(
        folderId = folderId,
        rowId = child.rowId,
        newTileId = newTileId,
        newTileColorId = TileColors.defaultIdFor(child.packageName),
        gridSlot = gridSlot,
        reorderedIds = reorderedIds,
    )

    /**
     * Merge a folder child directly into another top-level tile (FR-3.3) —
     * the drag-out counterpart of [mergeTiles]: [child] (still inside
     * [folderId] until this commits) is treated as the "dragged" tile and
     * [targetId] as the merge target, using the exact same [computeMerge]
     * result-building [mergeTiles] does. [survivingOrder] is persisted the
     * same way [mergeTiles] uses it, minus the "drop the dragged tile's own
     * id" step — the pulled-out child was never a top-level tile to begin
     * with, so there's nothing of its own to remove from the order.
     */
    suspend fun mergeFolderChildIntoTile(folderId: String, child: FolderChild, targetId: String, survivingOrder: List<String>) {
        val tiles = dao.tilesOnce().map(::toModel)
        val target = tiles.firstOrNull { it.id == targetId } ?: return
        val dragged = TileModel.App(
            id = "folder-child-$folderId-${child.rowId}",
            position = 0,
            size = child.size,
            colorId = "blue",
            packageName = child.packageName,
            activityName = child.activityName,
            label = child.label,
            iconKey = child.iconKey,
            accentOverride = child.accentOverride,
        )

        val result = computeMerge(dragged, target)
        val folderTile = TileEntity(
            id = target.id,
            position = target.position,
            size = result.size,
            colorId = result.colorId,
            type = TileEntity.TYPE_FOLDER,
            folderId = result.folderId,
            gridSlot = target.gridSlot,
        )
        val folder = FolderEntity(id = result.folderId, name = result.name, showAsStack = result.isStack)
        val children = result.children.mapIndexed { index, c ->
            FolderChildEntity(
                folderId = result.folderId,
                position = index,
                packageName = c.packageName,
                activityName = c.activityName,
                label = c.label,
                iconKey = c.iconKey,
                size = c.size,
                accentOverride = c.accentOverride,
            )
        }
        val existing = tiles.mapTo(HashSet()) { it.id }
        val ordered = survivingOrder.filter { it in existing }
        dao.mergeFolderChildIntoTile(folderId, child.rowId, folderTile, folder, children, ordered)
    }

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
            // Carry over the target's anchored cell (sticky mode) — the folder
            // takes the target's spot; the dragged tile's cell (deleted below)
            // becomes a gap, not something to backfill.
            gridSlot = target.gridSlot,
        )
        val folder = FolderEntity(id = result.folderId, name = result.name, showAsStack = result.isStack)
        val children = result.children.mapIndexed { index, child ->
            FolderChildEntity(
                folderId = result.folderId,
                position = index,
                packageName = child.packageName,
                activityName = child.activityName,
                label = child.label,
                iconKey = child.iconKey,
                size = child.size,
                accentOverride = child.accentOverride,
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
     * Pin an app from the app list (FR-5) as a [defaultSize] tile (medium,
     * unless the caller overrides it — the ICONS home style pins at SMALL
     * instead, since that's the size that renders as a plain icon) in the
     * app's default colour, appended to the end of the grid. No-op (returns
     * [PinResult.ALREADY_ON_START]) if a tile for the package already exists.
     * Apps that match a default role (phone, mail, calendar, etc.) get their
     * designed WP icon key; all others default to null and show the real app icon.
     */
    suspend fun pinApp(app: AppEntry, defaultSize: TileSize = TileSize.MEDIUM): PinResult {
        if (dao.appTileCount(app.packageName) > 0) return PinResult.ALREADY_ON_START
        dao.insertTiles(
            listOf(
                TileEntity(
                    id = "pin-${app.packageName}-${System.currentTimeMillis()}",
                    position = dao.maxPosition() + 1,
                    size = defaultSize,
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
     * Pin a contact (quick search → "pin to start") as a medium tile, appended to
     * the end of the grid. Stored as a plain app tile with a blank `packageName`
     * (like the weather/calendar liveOnly tiles) — [ContactTile] encodes the
     * contact's identity into `activityName` so the tile can reopen the right
     * contact card without a schema change. No-op (returns
     * [PinResult.ALREADY_ON_START]) if that contact is already pinned.
     */
    suspend fun pinContact(contactId: Long, lookupKey: String, name: String): PinResult {
        val activityName = ContactTile.encode(contactId, lookupKey)
        if (dao.activityTileCount(activityName) > 0) return PinResult.ALREADY_ON_START
        dao.insertTiles(
            listOf(
                TileEntity(
                    id = "pin-contact-$contactId-${System.currentTimeMillis()}",
                    position = dao.maxPosition() + 1,
                    size = TileSize.MEDIUM,
                    colorId = TileColors.defaultIdFor(lookupKey),
                    type = TileEntity.TYPE_APP,
                    packageName = null,
                    activityName = activityName,
                    label = name,
                    iconKey = ContactTile.ICON_KEY,
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
            // folderChildrenOnce is ORDER BY position, so this is the user's
            // current in-folder order.
            val prevChildren = dao.folderChildrenOnce(existing.id)
            val prevPackages = prevChildren.mapTo(HashSet()) { it.packageName }
            // Preserve any size the user already set on apps that stay in the folder
            // (updateFolderContents deletes + re-inserts, so we must carry it over).
            val prevSizeByComponent = prevChildren.associate {
                (it.packageName + "/" + it.activityName) to it.size
            }
            val newPackages = children.mapTo(HashSet()) { it.packageName }
            // Remove standalone Start tiles only for apps that are newly entering the folder.
            (newPackages - prevPackages).forEach { pkg -> dao.deleteTilesByPackage(pkg) }
            // Preserve the user's in-folder order: keep surviving apps in their prior
            // order, then append newly-added apps (in the sheet's order) at the end —
            // rather than re-sorting everything into the sheet's matched-first order.
            val newByComponent = children.associateBy { it.packageName + "/" + it.activityName }
            val survivorComponents = prevChildren
                .map { it.packageName + "/" + it.activityName }
                .filter { it in newByComponent }
            val survivorSet = survivorComponents.toHashSet()
            val added = children
                .map { it.packageName + "/" + it.activityName }
                .filter { it !in survivorSet }
            val orderedComponents = survivorComponents + added
            val childRows = orderedComponents.mapIndexed { index, component ->
                val app = newByComponent.getValue(component)
                FolderChildEntity(
                    folderId = existing.id,
                    position = index,
                    packageName = app.packageName,
                    activityName = app.activityName,
                    label = app.label,
                    iconKey = roleIconKeyMap[app.packageName],
                    size = prevSizeByComponent[component] ?: TileSize.MEDIUM,
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

    /**
     * Resize a folder child (persisted immediately). Since whether a folder
     * *renders* as a stack is now an explicit toggle
     * ([TileModel.Folder.showAsStack]) rather than purely derived from
     * uniform children, an individual child resize is just that — no
     * stack-collapse/-promote bookkeeping needed here: if this happens to
     * break (or restore) the siblings' uniformity, [TileModel.Folder.isStack]
     * naturally reflects that on the next read, with [showAsStack] left
     * untouched either way. Cycles the full small→medium→wide→large steps
     * when [largeAllowed], else the tighter small↔medium toggle.
     */
    suspend fun resizeFolderChild(folderId: String, child: FolderChild, largeAllowed: Boolean = false) {
        dao.updateFolderChildSize(child.rowId, child.size.nextForFolderChild(largeAllowed))
    }

    /**
     * Set a folder child's size directly to [size] — the write path for
     * gesture-based drag resize (mirrors [setTileSize] for top-level tiles),
     * rather than stepping through [resizeFolderChild]'s fixed tap cycle. This
     * is how a folder child reaches one of the drag-only presets (e.g. BANNER
     * 4×1, COLUMN 1×4) that the tap cycle never visits. See
     * [resizeFolderChild]'s doc comment on why no stack bookkeeping is needed.
     */
    suspend fun resizeFolderChildTo(folderId: String, child: FolderChild, size: TileSize) {
        dao.updateFolderChildSize(child.rowId, size)
    }

    /**
     * Turn a folder into a widget stack in one shot (folder overlay's "show as
     * stack" action): every child resized to [size] (any [TileSize.stackable]
     * size, not just WIDE/LARGE), the folder tile matching, and
     * [TileModel.Folder.showAsStack] turned on.
     */
    suspend fun convertFolderToStack(folderId: String, size: TileSize) =
        dao.convertFolderToStack(folderId, size)

    /**
     * Turn off a folder's "show as stack" toggle (the folder-overlay "show as
     * folder" action) — the reverse of [convertFolderToStack]. Children and the
     * folder tile's own footprint are left exactly as they are; see
     * `LayoutDao.collapseStack`'s doc comment for why no resize is needed.
     */
    suspend fun collapseStack(folderId: String) = dao.collapseStack(folderId)

    /** Set or clear a folder child's own accent override (null = follow global, FR-7). */
    suspend fun setFolderChildAccent(rowId: Long, accentOverride: String?) =
        dao.updateFolderChildAccent(rowId, accentOverride)

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

    /**
     * Resolves a default-layout role id (e.g. `"settings"`) to its installed
     * package name, or null if nothing resolves on this device. Used to hide
     * the real Android Settings app from the App List once it's superseded by
     * the Quick Panel's own "android settings" tile — see `StartViewModel`.
     */
    suspend fun resolvedPackageFor(appId: String): String? =
        DefaultLayout.roleFor(appId)?.let { resolver.resolve(it)?.packageName }

    /** Updates a single tile's icon key — see [LayoutDao.updateTileIconKey]. */
    suspend fun updateTileIconKey(id: String, iconKey: String?) = dao.updateTileIconKey(id, iconKey)

    /**
     * Return the raw DB entities for a manual backup export. Reuses the
     * existing [LayoutDao.tilesOnce] snapshot; no new DAO query needed.
     */
    suspend fun tilesForBackup(): Triple<List<TileEntity>, List<FolderEntity>, List<FolderChildEntity>> {
        val all = dao.tilesOnce()
        val tiles = all.map { it.tile }
        val folders = all.mapNotNull { it.folder?.folder }
        val children = all.flatMap { it.folder?.children.orEmpty() }
        return Triple(tiles, folders, children)
    }

    /**
     * Atomically replace the persisted layout with the data from a backup
     * import. Delegates to the existing [LayoutDao.replaceLayout] transaction
     * (no new DAO code needed).
     */
    suspend fun restoreFromBackup(
        tiles: List<TileEntity>,
        folders: List<FolderEntity>,
        children: List<FolderChildEntity>,
    ) = dao.replaceLayout(tiles, folders, children)

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
            val children = row.folder.children
                .sortedBy { it.position }
                .map {
                    FolderChild(
                        it.packageName, it.activityName, it.label, it.iconKey, it.size,
                        it.rowId, it.accentOverride,
                    )
                }
            // Whether this folder is rendered as a stack now needs both the
            // explicit showAsStack toggle AND the children currently being
            // uniformly one TileSize.stackable size (TileModel.Folder.isStack) —
            // recomputed here (rather than trusting the stored tile size) since a
            // child resized individually (FR-3.4, folder overlay) may not have
            // persisted the tile-size promotion yet.
            val showAsStack = row.folder.folder.showAsStack
            val stackSize = children.firstOrNull()?.size
                ?.takeIf { it.stackable }
                ?.takeIf { size -> children.all { it.size == size } }
            TileModel.Folder(
                id = t.id,
                position = t.position,
                size = if (showAsStack && stackSize != null) stackSize else t.size,
                colorId = t.colorId,
                name = row.folder.folder.name,
                children = children,
                accentOverride = t.accentOverride,
                gridSlot = t.gridSlot,
                showAsStack = showAsStack,
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
                accentOverride = t.accentOverride,
                gridSlot = t.gridSlot,
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
