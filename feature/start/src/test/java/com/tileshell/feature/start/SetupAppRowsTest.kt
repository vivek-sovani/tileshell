package com.tileshell.feature.start

import com.tileshell.core.data.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupAppRowsTest {
    private fun app(pkg: String, label: String) = AppEntry(pkg, ".Main", label)

    private val apps = listOf(
        app("com.zoom", "Zoom"),
        app("com.phone", "Phone"),
        app("com.amazon", "Amazon"),
        app("com.camera", "Camera"),
        app("com.phone", "Phone"), // second launcher activity, same package
    )

    @Test
    fun `defaults come first, then the rest, each alphabetical and one row per package`() {
        val rows = setupAppRows(apps, setOf("com.phone", "com.camera"), "")
        assertEquals(listOf("com.camera", "com.phone", "com.amazon", "com.zoom"), rows.map { it.packageName })
    }

    @Test
    fun `query filters by label, case-insensitively`() {
        val rows = setupAppRows(apps, setOf("com.phone"), " AM ")
        assertEquals(listOf("com.amazon", "com.camera"), rows.map { it.packageName })
    }

    @Test
    fun `setting up with hubs keeps the apps already on start, in their order`() {
        val defaults = setOf("phone", "camera")
        val pinned = listOf("whatsapp", "camera", "bank", "phone")
        // "default" apps: the pinned ones the default layout doesn't already have.
        assertEquals(listOf("whatsapp", "bank"), setupExtraPackages(false, emptySet(), pinned, defaults))
        // "custom": ticked ones, pinned first in Start's order; an unticked pinned app goes.
        assertEquals(
            listOf("bank", "maps", "zoom"),
            setupExtraPackages(true, setOf("bank", "zoom", "maps", "camera"), pinned, defaults),
        )
        // "reset start layout" keeps nothing that wasn't ticked.
        assertEquals(emptyList<String>(), setupExtraPackages(false, emptySet(), emptyList(), defaults))
    }
}
