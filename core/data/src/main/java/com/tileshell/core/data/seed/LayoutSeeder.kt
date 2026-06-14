package com.tileshell.core.data.seed

import com.tileshell.core.data.TileSize

/** A resolved folder child paired with its monoline glyph key. */
data class SeededChild(val component: ResolvedComponent, val iconKey: String)

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
        val iconKey: String,
    ) : SeededTile

    data class Folder(
        override val id: String,
        override val position: Int,
        override val size: TileSize,
        override val colorId: String,
        val name: String,
        val children: List<SeededChild>,
    ) : SeededTile
}

/**
 * Maps the default layout to installed apps (first-run seeding).
 *
 * App tiles whose role has no mapping or no installed match are skipped — except
 * `liveOnly` tiles (weather, calendar), whose live face is self-contained: those
 * always seed, taking a resolved launch target when one exists and a blank, inert
 * one otherwise. Folders keep only resolvable, de-duplicated children and are
 * dropped if none resolve. Surviving tiles get contiguous positions so dense
 * packing is unaffected by the gaps. Pure given a [RoleResolver] — unit-tested.
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
                    .mapNotNull { childId ->
                        DefaultLayout.roleFor(childId)?.let(resolver::resolve)?.let { resolved ->
                            SeededChild(resolved, DefaultLayout.iconFor(childId))
                        }
                    }
                    .distinctBy { it.component.packageName + "/" + it.component.activityName }
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
                val appId = tile.app ?: continue
                val resolved = DefaultLayout.roleFor(appId)?.let(resolver::resolve)
                val component = resolved
                    ?: if (tile.liveOnly) selfContainedComponent(appId) else continue
                out += SeededTile.App(
                    id = tile.id,
                    position = position++,
                    size = tile.size,
                    colorId = tile.colorId,
                    component = component,
                    iconKey = DefaultLayout.iconFor(appId),
                )
            }
        }
        return out
    }

    /**
     * The launch target for a self-contained live tile with no resolvable app: a
     * blank component. The live face renders from its own provider; tapping is
     * inert (the UI skips a blank package rather than launching it).
     */
    private fun selfContainedComponent(appId: String): ResolvedComponent =
        ResolvedComponent(packageName = "", activityName = "", label = appId)
}
