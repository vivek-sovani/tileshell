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
}
