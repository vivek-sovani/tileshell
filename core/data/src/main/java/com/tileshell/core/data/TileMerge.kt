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

/** The apps a tile contributes to a merge: a folder's children, or itself. */
private fun TileModel.apps(): List<FolderChild> = when (this) {
    is TileModel.Folder -> children
    is TileModel.App -> listOf(FolderChild(packageName, activityName, label, iconKey, size))
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
        deduped.putIfAbsent(child.packageName + "/" + child.activityName, child)
    }
    val children = deduped.values.toList()

    return when (target) {
        is TileModel.Folder -> MergeResult(
            folderId = target.id,
            size = target.size,
            colorId = target.colorId,
            name = target.name,
            children = children,
        )

        is TileModel.App -> MergeResult(
            folderId = target.id,
            size = if (target.size == TileSize.SMALL) TileSize.MEDIUM else target.size,
            colorId = target.colorId,
            name = "folder",
            children = children,
        )
    }
}
