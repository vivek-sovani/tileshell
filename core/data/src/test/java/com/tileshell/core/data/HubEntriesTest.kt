package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HubEntriesTest {

    private fun app(label: String) = AppEntry(packageName = "pkg.${label.lowercase()}", activityName = ".Main", label = label)

    @Test
    fun `every hub entry has a blank package and a hub activity name`() {
        HubEntries.entries().forEach {
            assertEquals("", it.packageName)
            assertTrue(it.activityName.startsWith("hub:"))
        }
    }

    @Test
    fun `hub ids are unique and the entry round trips to its id`() {
        assertEquals(HubEntries.ALL.size, HubEntries.ALL.map { it.id }.toSet().size)
        HubEntries.ALL.forEach { hub ->
            assertEquals(hub.id, HubEntries.idOf(HubEntries.entryFor(hub)))
        }
    }

    @Test
    fun `an app, the personalize entry and an unknown hub are not hubs`() {
        assertNull(HubEntries.idOf(app("Maps")))
        assertNull(HubEntries.idOf("", "personalize"))
        assertNull(HubEntries.idOf("", "hub:nonsense"))
        // A real package whose activity happens to start with hub: is still an app.
        assertNull(HubEntries.idOf("com.example", "hub:music"))
        assertFalse(HubEntries.isHub(app("Maps")))
    }

    @Test
    fun `a hub entry's key is its recent key and cannot equal an installed app's key`() {
        val music = HubEntries.entryFor(HubEntries.ALL.first { it.id == "music" })
        assertEquals("/hub:music", music.key)
        assertEquals(music.key, HubEntries.recentKey("music"))
        // Installed apps always have a package, so no key starts with "/".
        assertFalse(app("Maps").key.startsWith("/"))
    }

    @Test
    fun `music hub is called music plus videos and every hub has a glyph key`() {
        val music = HubEntries.ALL.first { it.id == "music" }
        assertEquals("music + videos", music.label)
        assertTrue(HubEntries.ALL.all { it.iconKey.isNotBlank() })
        assertEquals("music", HubEntries.iconKeyOf(HubEntries.entryFor(music)))
        assertNull(HubEntries.iconKeyOf(app("Maps")))
    }

    @Test
    fun `withHubs leaves the list alone when off and adds sorted hubs when on`() {
        val apps = listOf(app("Calculator"), app("Zoom"))
        assertEquals(apps, HubEntries.withHubs(apps, enabled = false))
        val on = HubEntries.withHubs(apps, enabled = true)
        assertEquals(apps.size + HubEntries.ALL.size, on.size)
        assertEquals(on.map { it.label.lowercase() }, on.map { it.label }.sortedBy { it.lowercase() }.map { it.lowercase() })
        assertTrue(on.any { it.label == "music + videos" })
    }

    @Test
    fun `typing music finds the music hub through a label search`() {
        val on = HubEntries.withHubs(listOf(app("Music Player")), enabled = true)
        val found = on.filter { it.label.contains("music", ignoreCase = true) }.map { it.label }
        assertTrue("music + videos" in found)
        assertTrue("Music Player" in found)
    }

    @Test
    fun `every hub has a tile colour the palette knows, and the music hub is orange like its tile`() {
        val known = setOf("blue", "cobalt", "purple", "magenta", "red", "orange", "amber", "lime", "green", "teal", "cyan", "steel", "mauve", "slate")
        assertTrue(HubEntries.ALL.all { it.colorId in known })
        assertEquals("orange", HubEntries.colorIdOf(HubEntries.entryFor(HubEntries.ALL.first { it.id == "music" })))
        assertNull(HubEntries.colorIdOf(app("Maps")))
    }

    @Test
    fun `personalize and quick settings are entries too`() {
        val ids = HubEntries.ALL.map { it.id }
        assertTrue("personalize" in ids && "quicksettings" in ids)
        assertEquals("quick settings", HubEntries.ALL.first { it.id == "quicksettings" }.label)
        assertEquals("personalize", HubEntries.ALL.first { it.id == "personalize" }.label)
    }
}
