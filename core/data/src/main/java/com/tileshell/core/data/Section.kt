package com.tileshell.core.data

/**
 * Display label for the trailing catch-all group of tiles that belong to no
 * real [Section] (`sectionId == null`) — shown wherever the group needs a
 * name alongside real sections' own labels (Start's section pill bar, the
 * per-tile "move to section" picker, the App List's "pin to section"
 * picker). Originally "unsectioned"; renamed per user request to read less
 * like an error/leftover state and more like a normal home area.
 */
const val UNSECTIONED_LABEL = "main"

/**
 * A named, collapsible group of top-level Start tiles ("work", "games", ...),
 * in [order]. Purely organizational: a [TileModel]'s own `sectionId` records
 * membership, so deleting a section ([LayoutRepository.deleteSection])
 * ungroups its members back to the default unsectioned area rather than
 * deleting them.
 */
data class Section(
    val id: String,
    val label: String,
    val order: Int,
    val collapsed: Boolean = false,
)

/**
 * Compute the reordered section list after swapping [id] with its immediate
 * neighbor in [direction] (-1 = up, +1 = down) — the section header's ↑/↓
 * reorder control. Pure so it's directly unit testable; returns [sections]
 * unchanged if [id] isn't found or the swap would go out of bounds (already
 * at the top/bottom of the list).
 */
fun swapSectionOrder(sections: List<Section>, id: String, direction: Int): List<Section> {
    val ordered = sections.sortedBy { it.order }
    val index = ordered.indexOfFirst { it.id == id }
    if (index < 0) return sections
    val swapWith = index + direction
    if (swapWith !in ordered.indices) return sections
    val a = ordered[index]
    val b = ordered[swapWith]
    return sections.map {
        when (it.id) {
            a.id -> it.copy(order = b.order)
            b.id -> it.copy(order = a.order)
            else -> it
        }
    }
}
