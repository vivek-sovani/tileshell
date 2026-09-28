package com.tileshell.core.data.seed

import com.tileshell.core.data.TileColors
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
        /** Null shows the app's real icon (apps added in the setup wizard). */
        val iconKey: String?,
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
                val component = if (tile.liveOnly && tile.activityName != null) {
                    // A page/config tile (e.g. "what's new"): blank package, its
                    // identity in activityName.
                    ResolvedComponent(packageName = "", activityName = tile.activityName, label = tile.label ?: appId)
                } else {
                    resolved ?: if (tile.liveOnly) selfContainedComponent(appId) else continue
                }
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

/**
 * The default layout's ordinary app picks (not hubs or live tiles, which are
 * always kept): each seeded non-`liveOnly` app tile and every folder child.
 * These are what the setup wizard's custom list shows pre-ticked.
 */
fun defaultAppChoices(
    seeded: List<SeededTile>,
    defaults: List<DefaultTile> = DefaultLayout.DEFAULT_TILES,
): List<ResolvedComponent> {
    val hubIds = defaults.filter { it.liveOnly }.map { it.id }.toSet()
    return seeded.flatMap { tile ->
        when (tile) {
            is SeededTile.App ->
                if (tile.id in hubIds || tile.component.packageName.isBlank()) emptyList() else listOf(tile.component)
            is SeededTile.Folder -> tile.children.map { it.component }
        }
    }.distinctBy { it.packageName }
}

/**
 * The setup wizard's custom layout: the seeded default minus the default apps
 * the user unticked ([removed] packages; hubs are never removed), plus
 * [extras] appended as small tiles with their real icon. A folder left empty
 * is dropped. Positions are renumbered contiguously. Pure, unit-tested.
 */
fun customizeSeed(
    seeded: List<SeededTile>,
    removed: Set<String>,
    extras: List<ResolvedComponent>,
    defaults: List<DefaultTile> = DefaultLayout.DEFAULT_TILES,
): List<SeededTile> {
    val hubIds = defaults.filter { it.liveOnly }.map { it.id }.toSet()
    val kept = seeded.mapNotNull { tile ->
        when (tile) {
            is SeededTile.App ->
                tile.takeUnless { it.id !in hubIds && it.component.packageName in removed }
            is SeededTile.Folder -> {
                val children = tile.children.filterNot { it.component.packageName in removed }
                if (children.isEmpty()) null else tile.copy(children = children)
            }
        }
    }
    // A hub tile may resolve to an app's package (people -> contacts) but is
    // not that app's tile, so it doesn't count as already present.
    val present = kept.flatMap { tile ->
        when (tile) {
            is SeededTile.App -> if (tile.id in hubIds) emptyList() else listOf(tile.component.packageName)
            is SeededTile.Folder -> tile.children.map { it.component.packageName }
        }
    }.toSet()
    val added = extras.distinctBy { it.packageName }.filter { it.packageName !in present }.map { app ->
        SeededTile.App(
            id = "t-app-${app.packageName}",
            position = 0,
            size = TileSize.SMALL,
            colorId = TileColors.defaultIdFor(app.packageName),
            component = app,
            iconKey = null,
        )
    }
    return (kept + added).mapIndexed { i, tile ->
        when (tile) {
            is SeededTile.App -> tile.copy(position = i)
            is SeededTile.Folder -> tile.copy(position = i)
        }
    }
}
