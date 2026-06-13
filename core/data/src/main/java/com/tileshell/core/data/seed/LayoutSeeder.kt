package com.tileshell.core.data.seed

import com.tileshell.core.data.TileSize

/** A seeded tile, ready to be written to Room. */
sealed interface SeededTile {
    val id: String
    val position: Int
    val size: TileSize
    val colorId: String

    data class App(
        override val id: String,
        override val position: Int,
        override val size: TileSize,
        override val colorId: String,
        val component: ResolvedComponent,
    ) : SeededTile

    data class Folder(
        override val id: String,
        override val position: Int,
        override val size: TileSize,
        override val colorId: String,
        val name: String,
        val children: List<ResolvedComponent>,
    ) : SeededTile
}

/**
 * Maps the default layout to installed apps (first-run seeding).
 *
 * App tiles whose role has no mapping or no installed match are skipped.
 * Folders keep only resolvable, de-duplicated children and are dropped if none
 * resolve. Surviving tiles get contiguous positions so dense packing is
 * unaffected by the gaps. Pure given a [RoleResolver] — unit-tested.
 */
class LayoutSeeder {

    fun seed(
        defaults: List<DefaultTile> = DefaultLayout.DEFAULT_TILES,
        resolver: RoleResolver,
    ): List<SeededTile> {
        val out = ArrayList<SeededTile>()
        var position = 0
        for (tile in defaults) {
            if (tile.isGroup) {
                val children = tile.children
                    .mapNotNull { DefaultLayout.roleFor(it)?.let(resolver::resolve) }
                    .distinctBy { it.packageName + "/" + it.activityName }
                if (children.isEmpty()) continue
                out += SeededTile.Folder(
                    id = tile.id,
                    position = position++,
                    size = tile.size,
                    colorId = tile.colorId,
                    name = tile.name ?: "folder",
                    children = children,
                )
            } else {
                val role = tile.app?.let(DefaultLayout::roleFor) ?: continue
                val component = resolver.resolve(role) ?: continue
                out += SeededTile.App(
                    id = tile.id,
                    position = position++,
                    size = tile.size,
                    colorId = tile.colorId,
                    component = component,
                )
            }
        }
        return out
    }
}
