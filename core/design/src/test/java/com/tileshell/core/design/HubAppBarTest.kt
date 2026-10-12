package com.tileshell.core.design

import org.junit.Assert.assertEquals
import org.junit.Test

class HubAppBarTest {
    private fun a(icon: String, description: String) = HubAppBarAction(icon, description) {}

    @Test fun `second screen gets its own section's buttons without back or repeats`() {
        val left = listOf(a("back", "back"), a("plus", "new note"), a("pin", "pin notes to start"))
        val right = listOf(a("back", "back"), a("plus", "new list"), a("pin", "pin notes to start"))
        assertEquals(listOf("new list"), rightScreenActions(left, right).map { it.description })
    }

    @Test fun `no second section means no second screen buttons`() {
        assertEquals(emptyList<HubAppBarAction>(), rightScreenActions(listOf(a("back", "back")), null))
    }

    @Test fun `panorama reports how many sections fit a screen`() {
        assertEquals(1, panoramaGeometry(1110f, PanelSplit(540f, 30f, 540f)).perScreen)
        assertEquals(2, panoramaGeometry(1500f, PanelSplit(720f, 60f, 720f)).perScreen)
        assertEquals(1, panoramaGeometry(393f, null).perScreen)
    }
}
