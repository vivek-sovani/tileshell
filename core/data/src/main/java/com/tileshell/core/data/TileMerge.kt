package com.tileshell.core.data

/**
 * Result of merging one tile onto another (FR-3.3): the [target] becomes a
 * folder — reusing the target tile's id as the folder id (docs/DECISIONS.md S5)
 * — holding the de-duplicated union of both tiles' apps.
 */
data class MergeResult(
    val folderId: String,
    val size: TileSize,
    val colorId: String,
    val name: String,
    val children: List<FolderChild>,
)

/** Demote a WIDE or LARGE child to MEDIUM; folder children are only SMALL / MEDIUM. */
private fun FolderChild.clampForFolder(): FolderChild =
    if (size == TileSize.WIDE || size == TileSize.LARGE) copy(size = TileSize.MEDIUM) else this

/**
 * The size a merged folder tile may take. Folders never carry the 3×3 LARGE size
 * (large is gated to music/news app tiles), so a large merge target collapses to
 * WIDE — the widest size the folder mini-grid face renders. SMALL is promoted to
 * MEDIUM; other sizes are kept.
 */
private fun TileSize.clampFolderTile(): TileSize = when (this) {
    TileSize.SMALL -> TileSize.MEDIUM
    TileSize.LARGE -> TileSize.WIDE
    else -> this
}

/** The apps a tile contributes to a merge: a folder's children, or itself. */
private fun TileModel.apps(): List<FolderChild> = when (this) {
    is TileModel.Folder -> children
    is TileModel.App -> listOf(
        FolderChild(packageName, activityName, label, iconKey, size, accentOverride = accentOverride),
    )
}

/**
 * Dedup identity for a merge. Real apps key on their launch component. Self-contained
 * `liveOnly` tiles (weather/calendar/clock) all share a blank package/activity — see
 * `DefaultTile.liveOnly` — so they'd otherwise collide onto one dedup slot and silently
 * disappear when merged together; key those on `iconKey` (distinct per live face) instead.
 */
private fun FolderChild.mergeKey(): String =
    if (packageName.isNotBlank()) "$packageName/$activityName" else "live:${iconKey ?: label}"

/**
 * A tile that can take part in a **widget stack**: a LARGE app tile, or a folder
 * that is already a stack (all members LARGE). Dropping one stackable tile onto
 * another keeps the result a stack — see [computeMerge].
 */
private fun TileModel.isStackable(): Boolean = when (this) {
    is TileModel.App -> size == TileSize.LARGE
    is TileModel.Folder -> isStack
}

/**
 * Compute the FR-3.3 merge of dropping [drag] onto [target]. Mirrors the
 * prototype `doMerge`:
 *
 * - **Target is a folder** → the dragged tile's apps are appended to the
 *   folder's children, de-duplicated by component; the folder keeps its own
 *   size, colour and name.
 * - **Target is an app** → a new folder is formed (reusing the target tile's id)
 *   whose children are the target's app followed by the dragged tile's apps,
 *   de-duplicated; size is the target's (a `small` target is promoted to
 *   `medium`), colour is the target's, name is "folder".
 *
 * The target's apps always precede the dragged apps. The caller drops the drag
 * tile separately; this function is pure and does not mutate its inputs.
 */
fun computeMerge(drag: TileModel, target: TileModel): MergeResult {
    val deduped = LinkedHashMap<String, FolderChild>()
    for (child in target.apps() + drag.apps()) {
        deduped.putIfAbsent(child.mergeKey(), child)
    }

    // Dropping a large tile onto another large tile (or onto an existing stack)
    // forms a **widget stack**: members keep their LARGE size and the tile keeps
    // the 3×3 footprint, so it renders as a swipeable carousel of live tiles. Any
    // other merge is a normal folder — children demoted to MEDIUM (the in-folder
    // resize cycle is SMALL↔MEDIUM) and a LARGE target collapsed to WIDE.
    val keepStack = drag.isStackable() && target.isStackable()
    val children = if (keepStack) {
        deduped.values.toList()
    } else {
        deduped.values.map { it.clampForFolder() }
    }
    val size = if (keepStack) TileSize.LARGE else target.size.clampFolderTile()

    return when (target) {
        is TileModel.Folder -> MergeResult(
            folderId = target.id,
            size = size,
            colorId = target.colorId,
            name = target.name,
            children = children,
        )

        is TileModel.App -> MergeResult(
            folderId = target.id,
            size = size,
            colorId = target.colorId,
            name = if (keepStack) "stack" else "folder",
            children = children,
        )
    }
}
