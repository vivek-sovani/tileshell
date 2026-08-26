package com.tileshell.feature.start

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the Quick Panel's pure layout logic: [applyQuickPanelOrder],
 * [reorderQuickPanelTiles], [settleQuickPanelTileSize], [packQuickPanelRows].
 */
class QuickPanelLayoutTest {

    @Test
    fun `applyQuickPanelOrder honors persisted order`() {
        val live = listOf("wifi", "bluetooth", "location", "airplane")
        val persisted = listOf("airplane", "wifi", "location", "bluetooth")
        assertEquals(persisted, applyQuickPanelOrder(live, persisted))
    }

    @Test
    fun `applyQuickPanelOrder appends a newly-available live tile at the end, in natural order`() {
        val live = listOf("wifi", "bluetooth", "allow_access", "rotation_lock")
        val persisted = listOf("bluetooth", "wifi")
        assertEquals(listOf("bluetooth", "wifi", "allow_access", "rotation_lock"), applyQuickPanelOrder(live, persisted))
    }

    @Test
    fun `applyQuickPanelOrder silently drops a stale persisted id with no live match`() {
        val live = listOf("wifi", "bluetooth")
        val persisted = listOf("dnd", "wifi", "bluetooth")
        assertEquals(listOf("wifi", "bluetooth"), applyQuickPanelOrder(live, persisted))
    }

    @Test
    fun `applyQuickPanelOrder with empty persisted order falls back to natural order`() {
        val live = listOf("wifi", "bluetooth", "location")
        assertEquals(live, applyQuickPanelOrder(live, emptyList()))
    }

    @Test
    fun `reorderQuickPanelTiles moves a forward drag to land after the target`() {
        val order = listOf("wifi", "bluetooth", "location", "airplane")
        assertEquals(listOf("bluetooth", "location", "wifi", "airplane"), reorderQuickPanelTiles(order, "wifi", "location"))
    }

    @Test
    fun `reorderQuickPanelTiles moves a backward drag to land before the target`() {
        val order = listOf("wifi", "bluetooth", "location", "airplane")
        assertEquals(listOf("wifi", "airplane", "bluetooth", "location"), reorderQuickPanelTiles(order, "airplane", "bluetooth"))
    }

    @Test
    fun `reorderQuickPanelTiles is a no-op when drag and target are equal`() {
        val order = listOf("wifi", "bluetooth")
        assertEquals(order, reorderQuickPanelTiles(order, "wifi", "wifi"))
    }

    @Test
    fun `reorderQuickPanelTiles is a no-op when either id is absent`() {
        val order = listOf("wifi", "bluetooth")
        assertEquals(order, reorderQuickPanelTiles(order, "wifi", "missing"))
        assertEquals(order, reorderQuickPanelTiles(order, "missing", "wifi"))
    }

    @Test
    fun `settleQuickPanelTileSize below the midpoint settles to square`() {
        assertEquals(QuickPanelTileSize.SQUARE, settleQuickPanelTileSize(1.2f))
    }

    @Test
    fun `settleQuickPanelTileSize above the midpoint settles to wide`() {
        assertEquals(QuickPanelTileSize.WIDE, settleQuickPanelTileSize(1.8f))
    }

    @Test
    fun `settleQuickPanelTileSize at exactly the midpoint settles to wide`() {
        assertEquals(QuickPanelTileSize.WIDE, settleQuickPanelTileSize(1.5f))
    }

    @Test
    fun `packQuickPanelRows packs an all-square run 4 per row, matching today's behavior`() {
        val ids = listOf("a", "b", "c", "d", "e")
        val rows = packQuickPanelRows(ids, columns = 4) { 1 }
        assertEquals(listOf(listOf("a", "b", "c", "d"), listOf("e")), rows)
    }

    @Test
    fun `packQuickPanelRows wraps a wide tile that would not fit the remaining row width`() {
        val ids = listOf("a", "b", "c", "wide")
        val cols = mapOf("a" to 1, "b" to 1, "c" to 1, "wide" to 2)
        val rows = packQuickPanelRows(ids, columns = 4) { cols.getValue(it) }
        assertEquals(listOf(listOf("a", "b", "c"), listOf("wide")), rows)
    }

    @Test
    fun `packQuickPanelRows fits a wide tile alongside squares when there is room`() {
        val ids = listOf("wide", "a", "b")
        val cols = mapOf("wide" to 2, "a" to 1, "b" to 1)
        val rows = packQuickPanelRows(ids, columns = 4) { cols.getValue(it) }
        assertEquals(listOf(listOf("wide", "a", "b")), rows)
    }

    @Test
    fun `decodeQuickPanelSizes parses valid tokens and drops malformed ones`() {
        val decoded = decodeQuickPanelSizes(listOf("flashlight:2", "wifi:1", "garbage", "bad:9", "noSep"))
        assertEquals(
            mapOf("flashlight" to QuickPanelTileSize.WIDE, "wifi" to QuickPanelTileSize.SQUARE),
            decoded,
        )
    }

    @Test
    fun `encodeQuickPanelSizes omits square (default) entries`() {
        val encoded = encodeQuickPanelSizes(
            mapOf("flashlight" to QuickPanelTileSize.WIDE, "wifi" to QuickPanelTileSize.SQUARE),
        )
        assertEquals(listOf("flashlight:2"), encoded)
    }

    @Test
    fun `quickPanelSizes round-trip through encode then decode`() {
        val sizes = mapOf("flashlight" to QuickPanelTileSize.WIDE, "dnd" to QuickPanelTileSize.WIDE)
        assertEquals(sizes, decodeQuickPanelSizes(encodeQuickPanelSizes(sizes)))
    }
}
