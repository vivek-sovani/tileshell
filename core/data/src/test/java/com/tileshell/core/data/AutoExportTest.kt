package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoExportTest {
    @Test fun fileNamesSortByTimeAndAreRecognised() {
        val a = AutoExportNaming.fileName(1_700_000_000_000L)
        val b = AutoExportNaming.fileName(1_700_000_000_000L + 3 * 86_400_000L)
        assertTrue(a < b)
        assertTrue(AutoExportNaming.isAutoExport(a))
        assertTrue(a.startsWith("tileshell-auto-") && a.endsWith(".json"))
    }

    @Test fun onlyOurOwnFilesAreEverDeleted() {
        assertFalse(AutoExportNaming.isAutoExport("tileshell-backup.json"))
        assertFalse(AutoExportNaming.isAutoExport("holiday.jpg"))
        assertFalse(AutoExportNaming.isAutoExport("tileshell-auto-notes.json"))
        // A cloud folder may add " (1)" to a clashing name; still ours.
        assertTrue(AutoExportNaming.isAutoExport("tileshell-auto-2026-10-06-1405 (1).json"))
    }

    @Test fun keepsTheNewestAndDeletesTheRest() {
        val names = (1..8).map { "tileshell-auto-2026-10-0$it-0900.json" } + listOf("tileshell-backup.json", "notes.txt")
        val doomed = AutoExportNaming.namesToDelete(names, keep = 5)
        assertEquals(listOf("tileshell-auto-2026-10-03-0900.json", "tileshell-auto-2026-10-02-0900.json", "tileshell-auto-2026-10-01-0900.json"), doomed)
    }

    @Test fun aRepeatInTheSameMinuteCountsAsNewer() {
        val names = listOf(
            "tileshell-auto-2026-10-06-1617.json",
            "tileshell-auto-2026-10-06-1617 (1).json",
            "tileshell-auto-2026-10-06-1617 (2).json",
        )
        assertEquals(listOf("tileshell-auto-2026-10-06-1617.json"), AutoExportNaming.namesToDelete(names, keep = 2))
    }

    @Test fun fewerThanKeepDeletesNothingAndKeepIsAtLeastOne() {
        assertEquals(emptyList<String>(), AutoExportNaming.namesToDelete(listOf("tileshell-auto-2026-10-01-0900.json"), 5))
        assertEquals(
            listOf("tileshell-auto-2026-10-01-0900.json"),
            AutoExportNaming.namesToDelete(listOf("tileshell-auto-2026-10-01-0900.json", "tileshell-auto-2026-10-02-0900.json"), 0),
        )
    }

    @Test fun statusLine() {
        val now = 10_000_000_000L
        assertNull(AutoExportNaming.statusLine(AutoExportState(), now))
        assertEquals("last export just now · saved", AutoExportNaming.statusLine(AutoExportState(lastRunAt = now - 30_000), now))
        assertEquals("last export 3h ago · saved", AutoExportNaming.statusLine(AutoExportState(lastRunAt = now - 3 * 3_600_000L), now))
        assertEquals("last export 4d ago · saved", AutoExportNaming.statusLine(AutoExportState(lastRunAt = now - 4 * 86_400_000L), now))
        assertEquals(
            "last try 5m ago · folder not available",
            AutoExportNaming.statusLine(AutoExportState(lastRunAt = now - 5 * 60_000L, lastError = "folder not available"), now),
        )
    }

    @Test fun readyNeedsSwitchAndFolder() {
        assertFalse(AutoExportState(enabled = true).ready)
        assertFalse(AutoExportState(folderUri = "content://x").ready)
        assertTrue(AutoExportState(enabled = true, folderUri = "content://x").ready)
    }
}
