package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HubTileLinksTest {

    @Test
    fun `sticky note link round-trips and rejects anything else`() {
        assertEquals(42L, StickyNoteTile.decode(StickyNoteTile.encode(42)))
        assertEquals(null, StickyNoteTile.decode("buy milk"))
        assertEquals(null, StickyNoteTile.decode(""))
        assertEquals(null, StickyNoteTile.decode(null))
        assertEquals(null, StickyNoteTile.decode("note:abc"))
    }

    @Test
    fun `a tasks tile shows its linked list, else its own id`() {
        assertEquals("list-9", TaskListTile.listIdFor("live-tasks-1", TaskListTile.encode("list-9")))
        assertEquals("live-tasks-1", TaskListTile.listIdFor("live-tasks-1", ""))
        assertEquals("live-tasks-1", TaskListTile.listIdFor("live-tasks-1", null))
        assertEquals("live-tasks-1", TaskListTile.listIdFor("live-tasks-1", "tasklist:"))
    }

    @Test
    fun `default list names count up`() {
        assertEquals("tasks", defaultTaskListName(0))
        assertEquals("tasks 2", defaultTaskListName(1))
        assertEquals("tasks 4", defaultTaskListName(3))
    }
}
