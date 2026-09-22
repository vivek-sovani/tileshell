package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for the People Hub page-tile activityName encoding ([PeopleHubTile]). */
class PeopleHubTileTest {

    @Test
    fun `round trips a page name`() {
        assertEquals("what's new", PeopleHubTile.decode(PeopleHubTile.encode("what's new")))
        assertEquals("recent", PeopleHubTile.decode(PeopleHubTile.encode("recent")))
    }

    @Test
    fun `a real launcher activity name is not a people hub page`() {
        assertNull(PeopleHubTile.decode("com.android.contacts.activities.PeopleActivity"))
    }

    @Test
    fun `a blank activityName is not a people hub page`() {
        assertNull(PeopleHubTile.decode(""))
    }

    @Test
    fun `null activityName is not a people hub page`() {
        assertNull(PeopleHubTile.decode(null))
    }
}
