package com.tileshell.feature.livetiles

import com.tileshell.core.data.TaskItem
import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TasksSummaryTest {

    @Test
    fun `empty list summarizes to zero counts and no preview`() {
        val summary = tasksSummary(emptyList())
        assertEquals(TasksSummary(doneCount = 0, totalCount = 0, preview = emptyList()), summary)
    }

    @Test
    fun `active tasks come before done ones in the preview`() {
        val tasks = listOf(
            TaskItem(1, "done first", done = true),
            TaskItem(2, "active", done = false),
        )
        val summary = tasksSummary(tasks)
        assertEquals(listOf("active", "done first"), summary.preview.map { it.text })
        assertEquals(1, summary.doneCount)
        assertEquals(2, summary.totalCount)
    }

    @Test
    fun `preview items carry the real task id, not a derived index`() {
        // The tile's checkbox toggles a task by id (see TaskPreviewRow's
        // onToggle) — if this ever regressed to a positional/derived id, taps
        // on the live tile would silently mark the wrong task.
        val tasks = listOf(TaskItem(id = 42, text = "buy milk", done = false))
        val summary = tasksSummary(tasks)
        assertEquals(42L, summary.preview.single().id)
    }

    @Test
    fun `preview is capped at maxPreview`() {
        val tasks = (1..5).map { TaskItem(it.toLong(), "task $it", done = false) }
        val summary = tasksSummary(tasks, maxPreview = 2)
        assertEquals(2, summary.preview.size)
    }

    @Test
    fun `once active tasks overflow the cap, the newest ones are kept`() {
        // Tasks arrive oldest-first (DB position order). A task just added
        // to an already-full list must show up immediately, not get sorted
        // past the cap by tasks added earlier.
        val tasks = (1..5).map { TaskItem(it.toLong(), "task $it", done = false) }
        val summary = tasksSummary(tasks, maxPreview = 2)
        assertEquals(listOf(4L, 5L), summary.preview.map { it.id })
    }

    @Test
    fun `when everything is done the preview falls back to completed tasks`() {
        val tasks = listOf(TaskItem(1, "a", done = true), TaskItem(2, "b", done = true))
        val summary = tasksSummary(tasks)
        assertEquals(2, summary.preview.size)
        assertTrue(summary.preview.all { it.done })
    }

    @Test
    fun `tasks icon key maps to the tasks face at medium and up`() {
        assertEquals(LiveFace.TASKS, LiveFace.forIconKey("tasks", TileSize.MEDIUM))
        assertEquals(LiveFace.TASKS, LiveFace.forIconKey("tasks", TileSize.WIDE))
    }

    @Test
    fun `tasks tile stays static at small`() {
        assertNull(LiveFace.forIconKey("tasks", TileSize.SMALL))
    }

    @Test
    fun `tasks face never flips — the checklist is the only thing worth showing`() {
        assertFalse(LiveFace.TASKS.flips)
    }
}

class TasksLayoutTest {

    @Test
    fun `medium gets more room than the old fixed cap of 3`() {
        assertTrue(maxPreviewFor(TileSize.MEDIUM) > 3)
    }

    @Test
    fun `bigger sizes get progressively more preview rows`() {
        assertTrue(maxPreviewFor(TileSize.LARGE) > maxPreviewFor(TileSize.MEDIUM))
        assertTrue(maxPreviewFor(TileSize.XLARGE) > maxPreviewFor(TileSize.LARGE))
    }

    @Test
    fun `only 3-plus column sizes get a two-column preview`() {
        assertEquals(1, previewColumnsFor(TileSize.MEDIUM))
        assertEquals(1, previewColumnsFor(TileSize.TALL_MEDIUM))
        assertEquals(2, previewColumnsFor(TileSize.WIDE))
        assertEquals(2, previewColumnsFor(TileSize.LARGE))
        assertEquals(2, previewColumnsFor(TileSize.XLARGE))
    }
}
