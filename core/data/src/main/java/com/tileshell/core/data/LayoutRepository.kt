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

    /** Seed the default layout iff the grid is empty. Safe to call repeatedly. */
    suspend fun seedIfEmpty() {
        if (dao.tileCount() > 0) return
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
                    .map { FolderChild(it.packageName, it.activityName, it.label, it.iconKey) },
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
