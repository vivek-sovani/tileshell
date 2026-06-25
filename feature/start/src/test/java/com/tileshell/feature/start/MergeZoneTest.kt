package com.tileshell.feature.start

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the folder-merge target rules ([inMergeZone] / [heldAsMergeTarget]). */
class MergeZoneTest {

    private val tile = Rect(0f, 0f, 100f, 100f)

    @Test
    fun `centre is the merge zone, edges are not`() {
        assertTrue(inMergeZone(tile, Offset(50f, 50f)))
        assertTrue(inMergeZone(tile, Offset(30f, 70f)))
        assertFalse(inMergeZone(tile, Offset(10f, 50f))) // left band
        assertFalse(inMergeZone(tile, Offset(50f, 90f))) // bottom band
    }

    @Test
    fun `entering a merge needs the centre`() {
        // Not yet the target: only the 22-78% centre begins a merge.
        assertTrue(heldAsMergeTarget(tile, Offset(50f, 50f), alreadyTarget = false))
        assertFalse(heldAsMergeTarget(tile, Offset(12f, 50f), alreadyTarget = false))
    }

    @Test
    fun `an established target stays merged anywhere on the tile`() {
        // Sticky: once it's the target, an off-centre wobble keeps the merge so it
        // doesn't flip back to a reorder mid-drag.
        assertTrue(heldAsMergeTarget(tile, Offset(12f, 50f), alreadyTarget = true))
        assertTrue(heldAsMergeTarget(tile, Offset(95f, 95f), alreadyTarget = true))
    }

    @Test
    fun `a folder accepts a merge anywhere inside it`() {
        // Dropping onto a folder almost always means "add to folder", so the whole
        // tile is a merge zone — no centre bullseye required.
        assertTrue(inMergeZone(tile, Offset(10f, 50f), isFolder = true)) // left band
        assertTrue(inMergeZone(tile, Offset(50f, 95f), isFolder = true)) // bottom band
        assertTrue(heldAsMergeTarget(tile, Offset(8f, 8f), alreadyTarget = false, isFolder = true))
        // Still bounded by the tile rect.
        assertFalse(inMergeZone(tile, Offset(120f, 50f), isFolder = true))
    }

    @Test
    fun `an app still needs the centre even with the folder flag off`() {
        assertFalse(inMergeZone(tile, Offset(10f, 50f), isFolder = false))
        assertTrue(inMergeZone(tile, Offset(50f, 50f), isFolder = false))
    }
}
