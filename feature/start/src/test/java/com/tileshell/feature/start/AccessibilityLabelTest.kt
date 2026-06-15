package com.tileshell.feature.start

import com.tileshell.core.data.FolderChild
import com.tileshell.core.data.TileModel
import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the TalkBack tile label ([tileAccessibilityLabel], S27). */
class AccessibilityLabelTest {

    private fun app(
        label: String? = "Phone",
        iconKey: String? = "phone",
        size: TileSize = TileSize.MEDIUM,
    ) = TileModel.App(
        id = "t", position = 0, size = size, colorId = "blue",
        packageName = "com.x", activityName = "A", label = label, iconKey = iconKey,
    )

    private fun folder(children: Int) = TileModel.Folder(
        id = "f", position = 0, size = TileSize.MEDIUM, colorId = "blue",
        name = "social",
        children = List(children) { FolderChild("p$it", "a", "L$it") },
    )

    @Test
    fun `app label outside edit is just the name`() {
        assertEquals("Phone", tileAccessibilityLabel(app(), 0, editMode = false, selected = false))
    }

    @Test
    fun `unread count is appended`() {
        assertEquals("Phone, 3 new", tileAccessibilityLabel(app(), 3, editMode = false, selected = false))
    }

    @Test
    fun `missing label falls back to the icon key then app`() {
        assertEquals("phone", tileAccessibilityLabel(app(label = null), 0, false, false))
        assertEquals("app", tileAccessibilityLabel(app(label = null, iconKey = null), 0, false, false))
    }

    @Test
    fun `edit mode adds size and selection`() {
        assertEquals(
            "Phone, medium tile, selected",
            tileAccessibilityLabel(app(), 0, editMode = true, selected = true),
        )
        assertEquals(
            "Phone, wide tile",
            tileAccessibilityLabel(app(size = TileSize.WIDE), 0, editMode = true, selected = false),
        )
    }

    @Test
    fun `folder pluralises its child count`() {
        assertEquals("social folder, 1 app", tileAccessibilityLabel(folder(1), 0, false, false))
        assertEquals("social folder, 4 apps", tileAccessibilityLabel(folder(4), 0, false, false))
    }
}
