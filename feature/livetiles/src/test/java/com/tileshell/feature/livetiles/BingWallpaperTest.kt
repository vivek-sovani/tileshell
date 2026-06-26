package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** Unit tests for the pure Bing image-of-the-day URL parser + market helper. */
class BingWallpaperTest {

    private val sample = """
        {"images":[{"startdate":"20240101","url":"/th?id=OHR.Sample_EN-US1234_1920x1080.jpg&rf=LaDigue_1920x1080.jpg","urlbase":"/th?id=OHR.Sample_EN-US1234","copyright":"Somewhere (© Someone)","title":"A place"}],"tooltips":{}}
    """.trimIndent()

    @Test
    fun `prefixes the relative url with the bing host`() {
        assertEquals(
            "https://www.bing.com/th?id=OHR.Sample_EN-US1234_1920x1080.jpg&rf=LaDigue_1920x1080.jpg",
            parseBingImageUrl(sample),
        )
    }

    @Test
    fun `keeps an already-absolute url`() {
        val json = """{"images":[{"url":"https://cdn.example.com/today.jpg"}]}"""
        assertEquals("https://cdn.example.com/today.jpg", parseBingImageUrl(json))
    }

    @Test
    fun `missing images array yields null`() {
        assertNull(parseBingImageUrl("""{"tooltips":{}}"""))
    }

    @Test
    fun `empty images array yields null`() {
        assertNull(parseBingImageUrl("""{"images":[]}"""))
    }

    @Test
    fun `blank url yields null`() {
        assertNull(parseBingImageUrl("""{"images":[{"url":""}]}"""))
    }

    @Test
    fun `malformed json yields null instead of throwing`() {
        assertNull(parseBingImageUrl("not json at all"))
    }

    @Test
    fun `market combines language and country`() {
        assertEquals("en-IN", bingMarket(Locale("en", "IN")))
    }

    @Test
    fun `market falls back to US when country missing`() {
        assertEquals("en-US", bingMarket(Locale("en", "")))
    }

    private val multi = """
        {"images":[
          {"startdate":"20260626","url":"/th?id=OHR.A_1920x1080.jpg","urlbase":"/th?id=OHR.A","title":"Day A"},
          {"startdate":"20260625","url":"/th?id=OHR.B_1920x1080.jpg","urlbase":"/th?id=OHR.B","copyright":"Place B (© X)"}
        ]}
    """.trimIndent()

    @Test
    fun `parses all images with absolute full and thumb urls`() {
        val list = parseBingImages(multi)
        assertEquals(2, list.size)
        assertEquals("https://www.bing.com/th?id=OHR.A_1920x1080.jpg", list[0].fullUrl)
        assertEquals("https://www.bing.com/th?id=OHR.A_400x240.jpg", list[0].thumbUrl)
        assertEquals("jun 26", list[0].date)
        assertEquals("Day A", list[0].title)
    }

    @Test
    fun `falls back to copyright when title is missing`() {
        assertEquals("Place B (© X)", parseBingImages(multi)[1].title)
    }

    @Test
    fun `parseBingImages on malformed json is empty`() {
        assertEquals(emptyList<BingImage>(), parseBingImages("nope"))
    }

    @Test
    fun `date label formats yyyymmdd and echoes bad input`() {
        assertEquals("jan 1", bingDateLabel("20260101"))
        assertEquals("dec 31", bingDateLabel("20251231"))
        assertEquals("bogus", bingDateLabel("bogus"))
    }
}
