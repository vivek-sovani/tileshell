package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [computeMerge] (FR-3.3 merge rules). Covers app→app folder
 * creation, small-target promotion, append-to-folder, de-duplication, and the
 * folder→folder union.
 */
class TileMergeTest {

    private fun app(
        id: String,
        pkg: String = id,
        size: TileSize = TileSize.MEDIUM,
        color: String = "blue",
        activity: String = ".Main",
        icon: String? = null,
    ) = TileModel.App(
        id = id,
        position = 0,
        size = size,
        colorId = color,
        packageName = pkg,
        activityName = activity,
        label = pkg,
        iconKey = icon,
    )

    private fun folder(
        id: String,
        size: TileSize = TileSize.MEDIUM,
        color: String = "magenta",
        name: String = "social",
        children: List<FolderChild>,
    ) = TileModel.Folder(
        id = id,
        position = 0,
        size = size,
        colorId = color,
        name = name,
        children = children,
    )

    private fun child(pkg: String, activity: String = ".Main") =
        FolderChild(packageName = pkg, activityName = activity, label = pkg)

    @Test
    fun appOntoApp_createsNamedFolderReusingTargetId() {
        val result = computeMerge(drag = app("drag"), target = app("target", color = "teal"))

        assertEquals("target", result.folderId) // target tile id is reused
        assertEquals("folder", result.name)
        assertEquals("teal", result.colorId) // target's colour
        // Target's app precedes the dragged app.
        assertEquals(listOf("target", "drag"), result.children.map { it.packageName })
    }

    @Test
    fun appOntoApp_keepsTargetSize() {
        val result = computeMerge(app("drag"), app("target", size = TileSize.WIDE))
        assertEquals(TileSize.WIDE, result.size)
    }

    @Test
    fun appOntoSmallApp_promotesToMedium() {
        val result = computeMerge(app("drag"), app("target", size = TileSize.SMALL))
        assertEquals(TileSize.MEDIUM, result.size)
    }

    @Test
    fun appOntoFolder_appendsAndKeepsFolderMeta() {
        val target = folder(
            "g-social",
            size = TileSize.WIDE,
            color = "magenta",
            name = "social",
            children = listOf(child("fb"), child("ig")),
        )
        val result = computeMerge(drag = app("tw"), target = target)

        assertEquals("g-social", result.folderId)
        assertEquals("social", result.name) // folder keeps its own name
        assertEquals(TileSize.WIDE, result.size)
        assertEquals("magenta", result.colorId)
        assertEquals(listOf("fb", "ig", "tw"), result.children.map { it.packageName })
    }

    @Test
    fun duplicateApp_isNotAddedTwice() {
        val target = folder("g", children = listOf(child("fb"), child("ig")))
        val result = computeMerge(drag = app("ig"), target = target) // ig already inside

        assertEquals(listOf("fb", "ig"), result.children.map { it.packageName })
    }

    @Test
    fun sameComponentDifferentActivity_isDistinct() {
        val target = folder("g", children = listOf(child("p", ".A")))
        val result = computeMerge(drag = app("drag", pkg = "p", activity = ".B"), target = target)

        assertEquals(2, result.children.size)
        assertEquals(listOf(".A", ".B"), result.children.map { it.activityName })
    }

    @Test
    fun folderOntoFolder_unionsChildrenDeduped() {
        val target = folder("g-a", name = "a", children = listOf(child("fb"), child("ig")))
        val drag = folder("g-b", name = "b", children = listOf(child("ig"), child("tw")))
        val result = computeMerge(drag = drag, target = target)

        assertEquals("g-a", result.folderId) // target folder survives
        assertEquals("a", result.name)
        // Target's children first, then the drag's new ones, ig de-duped.
        assertEquals(listOf("fb", "ig", "tw"), result.children.map { it.packageName })
    }

    @Test
    fun draggedApp_carriesItsOwnSizeIntoTheFolder() {
        // A wide/small app dropped into a folder must keep its size as a child,
        // and existing children must keep theirs (no silent reset to MEDIUM).
        val target = folder(
            "g",
            children = listOf(
                FolderChild("fb", ".Main", "fb", size = TileSize.WIDE),
                FolderChild("ig", ".Main", "ig", size = TileSize.SMALL),
            ),
        )
        val result = computeMerge(drag = app("tw", size = TileSize.WIDE), target = target)

        val sizes = result.children.associate { it.packageName to it.size }
        assertEquals(TileSize.WIDE, sizes["fb"])  // existing wide child preserved
        assertEquals(TileSize.SMALL, sizes["ig"]) // existing small child preserved
        assertEquals(TileSize.WIDE, sizes["tw"])  // dragged app keeps its size
    }

    @Test
    fun folderOntoApp_newFolderHoldsTargetThenDragChildren() {
        val drag = folder("g", children = listOf(child("fb"), child("ig")))
        val result = computeMerge(drag = drag, target = app("maps", size = TileSize.SMALL))

        assertEquals("maps", result.folderId)
        assertEquals("folder", result.name)
        assertEquals(TileSize.MEDIUM, result.size) // small promoted
        assertEquals(listOf("maps", "fb", "ig"), result.children.map { it.packageName })
    }
}
