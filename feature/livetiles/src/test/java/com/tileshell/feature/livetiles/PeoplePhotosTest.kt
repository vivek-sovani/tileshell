package com.tileshell.feature.livetiles

import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MosaicCellsTest {

    private fun people(n: Int) = List(n) { Person("Person $it", "content://photo/$it") }

    @Test
    fun `fills every cell when there are enough contacts`() {
        val cells = mosaicCells(people(8), cellCount = 4)
        assertEquals(4, cells.size)
        assertEquals("Person 0", cells[0].name)
        assertEquals("Person 3", cells[3].name)
    }

    @Test
    fun `cycles contacts when there are fewer than cells`() {
        val cells = mosaicCells(people(3), cellCount = 8)
        assertEquals(8, cells.size)
        // 0,1,2,0,1,2,0,1
        assertEquals("Person 0", cells[3].name)
        assertEquals("Person 2", cells[5].name)
        assertEquals("Person 1", cells[7].name)
    }

    @Test
    fun `empty contacts give no cells`() {
        assertTrue(mosaicCells(emptyList(), cellCount = 4).isEmpty())
    }
}

class AvatarColorTest {

    @Test
    fun `colour is stable for a given name`() {
        assertEquals(colorFor("Aria Cole"), colorFor("Aria Cole"))
    }

    @Test
    fun `blank name does not crash`() {
        // Just exercises the empty-name branch.
        colorFor("")
    }
}

class PhotosCodecTest {

    @Test
    fun `round-trips a list of uris`() {
        val data = PhotosData(listOf("content://a/1", "content://b/2"))
        assertEquals(data, PhotosCodec.decode(PhotosCodec.encode(data)))
    }

    @Test
    fun `drops blank lines and trims`() {
        val decoded = PhotosCodec.decode("content://a/1\n\n  content://b/2  \n")
        assertEquals(listOf("content://a/1", "content://b/2"), decoded.uris)
    }

    @Test
    fun `empty text is empty selection`() {
        assertTrue(PhotosCodec.decode("").uris.isEmpty())
    }
}

class SampleSizeTest {

    @Test
    fun `keeps shorter side at or above target`() {
        assertEquals(1, sampleSizeFor(800, 600, 400))
        assertEquals(2, sampleSizeFor(1600, 1200, 400))
        assertEquals(4, sampleSizeFor(3200, 2400, 400))
    }

    @Test
    fun `degenerate bounds give sample size one`() {
        assertEquals(1, sampleSizeFor(0, 0, 400))
        assertEquals(1, sampleSizeFor(800, 600, 0))
    }
}

class PeoplePhotosFaceMappingTest {

    @Test
    fun `people flips and photos does not`() {
        assertTrue(LiveFace.PEOPLE.flips)
        assertTrue(!LiveFace.PHOTOS.flips)
    }

    @Test
    fun `icon keys map to the right faces`() {
        assertEquals(LiveFace.PEOPLE, LiveFace.forIconKey("people", TileSize.MEDIUM))
        assertEquals(LiveFace.PHOTOS, LiveFace.forIconKey("photos", TileSize.WIDE))
    }

    @Test
    fun `small tiles stay static`() {
        assertNull(LiveFace.forIconKey("people", TileSize.SMALL))
        assertNull(LiveFace.forIconKey("photos", TileSize.SMALL))
    }
}
