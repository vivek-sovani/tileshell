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
    fun draggedApp_carriesItsSizeIntoFolder_wideDemotedToMedium() {
        // A small/medium app dropped into a folder keeps its size; a WIDE app is
        // demoted to MEDIUM (folders don't allow wide). Existing children keep
        // their sizes — no silent reset.
        val target = folder(
            "g",
            children = listOf(
                FolderChild("fb", ".Main", "fb", size = TileSize.MEDIUM),
                FolderChild("ig", ".Main", "ig", size = TileSize.SMALL),
            ),
        )
        val result = computeMerge(drag = app("tw", size = TileSize.WIDE), target = target)

        val sizes = result.children.associate { it.packageName to it.size }
        assertEquals(TileSize.MEDIUM, sizes["fb"]) // existing medium child preserved
        assertEquals(TileSize.SMALL, sizes["ig"])  // existing small child preserved
        assertEquals(TileSize.MEDIUM, sizes["tw"])  // wide dragged app demoted to medium
    }

    @Test
    fun existingWideFolderChild_isDemotedToMediumOnMerge() {
        // Defensive: if a WIDE child ever existed, a later merge normalises it.
        val target = folder(
            "g",
            children = listOf(FolderChild("fb", ".Main", "fb", size = TileSize.WIDE)),
        )
        val result = computeMerge(drag = app("tw", size = TileSize.SMALL), target = target)

        val sizes = result.children.associate { it.packageName to it.size }
        assertEquals(TileSize.MEDIUM, sizes["fb"])
        assertEquals(TileSize.SMALL, sizes["tw"])
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

    @Test
    fun draggedLargeApp_isDemotedToMediumInsideFolder() {
        // A 3×3 large music tile dragged into a folder becomes a MEDIUM child —
        // folders only hold SMALL/MEDIUM children.
        val target = folder("g", children = listOf(FolderChild("fb", ".Main", "fb", size = TileSize.MEDIUM)))
        val result = computeMerge(drag = app("music", size = TileSize.LARGE), target = target)

        assertEquals(TileSize.MEDIUM, result.children.associate { it.packageName to it.size }["music"])
    }

    @Test
    fun largeAppTarget_makesAWideFolderTile() {
        // Merging onto a LARGE music tile forms a folder; folders never carry the
        // 3×3 large size, so the folder tile collapses to WIDE.
        val result = computeMerge(drag = app("ig", size = TileSize.SMALL), target = app("music", size = TileSize.LARGE))

        assertEquals("music", result.folderId)
        assertEquals(TileSize.WIDE, result.size) // large target → wide folder
        assertEquals(listOf("music", "ig"), result.children.map { it.packageName })
    }

    private fun largeChild(pkg: String, activity: String = ".Main") =
        FolderChild(packageName = pkg, activityName = activity, label = pkg, size = TileSize.LARGE)

    @Test
    fun largeOntoLarge_formsWidgetStackKeepingLargeMembers() {
        // Dropping a large tile onto another large tile makes a widget stack: the
        // tile stays 3×3 and both members keep LARGE (so it renders as a carousel).
        val result = computeMerge(
            drag = app("yt", size = TileSize.LARGE),
            target = app("spotify", size = TileSize.LARGE, color = "teal"),
        )

        assertEquals("spotify", result.folderId)
        assertEquals("stack", result.name)
        assertEquals(TileSize.LARGE, result.size)
        assertEquals(listOf("spotify", "yt"), result.children.map { it.packageName })
        assertEquals(listOf(TileSize.LARGE, TileSize.LARGE), result.children.map { it.size })
        // The derived stack flag follows from all members being LARGE.
        assertEquals(
            true,
            folder("spotify", size = TileSize.LARGE, children = result.children).isStack,
        )
    }

    @Test
    fun largeAppOntoStack_appendsAndStaysStack() {
        val stack = folder(
            "spotify", size = TileSize.LARGE, name = "stack",
            children = listOf(largeChild("spotify"), largeChild("yt")),
        )
        val result = computeMerge(drag = app("news", size = TileSize.LARGE), target = stack)

        assertEquals(TileSize.LARGE, result.size)
        assertEquals("stack", result.name)
        assertEquals(listOf("spotify", "yt", "news"), result.children.map { it.packageName })
        assertEquals(true, result.children.all { it.size == TileSize.LARGE })
    }

    @Test
    fun nonLargeOntoStack_revertsToNormalFolder() {
        // Merging a non-large tile into a stack breaks the "all members LARGE" rule,
        // so it collapses back to a normal folder: tile → WIDE, members → MEDIUM.
        val stack = folder(
            "spotify", size = TileSize.LARGE, name = "stack",
            children = listOf(largeChild("spotify"), largeChild("yt")),
        )
        val result = computeMerge(drag = app("ig", size = TileSize.MEDIUM), target = stack)

        assertEquals(TileSize.WIDE, result.size)
        assertEquals(true, result.children.all { it.size == TileSize.MEDIUM })
        assertEquals(
            false,
            folder("spotify", size = TileSize.WIDE, children = result.children).isStack,
        )
    }
}
