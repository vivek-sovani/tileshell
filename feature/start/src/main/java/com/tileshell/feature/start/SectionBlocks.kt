package com.tileshell.feature.start

import com.tileshell.core.data.Section
import com.tileshell.core.data.TileModel

/**
 * One rendered group of top-level tiles: either a real, named [Section]
 * ([sectionId] non-null) or the leading catch-all group for tiles with no
 * section ([sectionId] null, [label] null, never [collapsed]) — shown
 * *first*, before every real section (user-requested, once sections became
 * swipeable pages rather than a vertically-stacked list: "main" is the one
 * page that's always there, so it reads as the anchor/home page you land on
 * before swiping into named ones — the opposite ordering made sense in the
 * earlier vertical-stack model, where sections were meant to read as the
 * organized front-and-center content with "main" as the leftover area below
 * them). [ids] is the subset of the working `order` list that belongs here,
 * in their existing relative order.
 */
data class TileBlock(
    val sectionId: String?,
    val label: String?,
    val collapsed: Boolean,
    val ids: List<String>,
)

/** A section header's fixed height (dp) — reserved space, not measured. */
const val SECTION_HEADER_HEIGHT_DP = 42f

/** Vertical gap (dp) between one block's content and the next. */
const val SECTION_BLOCK_GAP_DP = 8f

/**
 * One block's full render info: its own tile specs, its own packed
 * placements (empty while [TileBlock.collapsed]), and where its content
 * starts within the scrolling page — [topOffsetPx] is where its header (if
 * any) begins, [gridTopOffsetPx] is where its own grid's row 0 begins (the
 * same value when there's no header).
 */
data class BlockRender(
    val block: TileBlock,
    val specs: List<TileSpec>,
    val placements: List<TilePlacement>,
    val topOffsetPx: Float,
    val gridTopOffsetPx: Float,
)

/**
 * Groups [order] into [TileBlock]s: one leading unsectioned block for tiles
 * with no section (or whose `sectionId` refers to a section that no longer
 * exists — defensive; the repository's own delete path ungroups tiles
 * before removing a section, so this should never actually happen),
 * followed by every real [sections] entry (sorted by its own
 * [Section.order]) as its own block holding just the ids whose
 * [TileModel.sectionId] matches it.
 *
 * Pure and order-preserving: filtering (not sorting) means a block's ids
 * keep their exact relative order from [order]; nothing here requires
 * [order] to already be grouped by section.
 */
fun blocksFor(order: List<String>, byId: Map<String, TileModel>, sections: List<Section>): List<TileBlock> {
    val validSectionIds = sections.mapTo(HashSet()) { it.id }
    val bySection = order.groupBy { id -> byId[id]?.sectionId?.takeIf { it in validSectionIds } }
    val unsectioned = TileBlock(sectionId = null, label = null, collapsed = false, ids = bySection[null].orEmpty())
    val sectionBlocks = sections.sortedBy { it.order }.map { section ->
        TileBlock(section.id, section.label, section.collapsed, bySection[section.id].orEmpty())
    }
    return listOf(unsectioned) + sectionBlocks
}

/**
 * Replaces one block's ids within the full working [order] list with
 * [newBlockOrder] (that same set of ids, reordered), leaving every other id
 * — including ids of other blocks interleaved among them — exactly where it
 * already sits. This is what keeps an edit-mode drag from ever reordering a
 * tile past a tile in a *different* section: [newBlockOrder] only ever
 * contains one block's own ids (see [blocksFor]), so a member can only ever
 * trade places with another member of the same block.
 *
 * [newBlockOrder] must contain exactly the same ids as appear in [order] for
 * that block, just possibly reordered — the same size assumption
 * [reorderTiles] already guarantees for its own output.
 */
fun spliceBlockOrder(order: List<String>, blockIds: Set<String>, newBlockOrder: List<String>): List<String> {
    val iterator = newBlockOrder.iterator()
    return order.map { id -> if (id in blockIds) iterator.next() else id }
}
