package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTileTest {

    @Test
    fun `round-trips current location`() {
        val encoded = WeatherTile.encode(WeatherTile.Location.Current)
        assertEquals(WeatherTile.Location.Current, WeatherTile.decode(encoded))
    }

    @Test
    fun `round-trips a fixed place`() {
        val encoded = WeatherTile.encode(WeatherTile.Location.Fixed(51.5074, -0.1278, "London, England, United Kingdom"))
        assertEquals(
            WeatherTile.Location.Fixed(51.5074, -0.1278, "London, England, United Kingdom"),
            WeatherTile.decode(encoded),
        )
    }

    @Test
    fun `a place name containing the field separator survives the round trip`() {
        val encoded = WeatherTile.encode(WeatherTile.Location.Fixed(1.0, 2.0, "a|weird|name"))
        val decoded = WeatherTile.decode(encoded) as WeatherTile.Location.Fixed
        assertEquals(1.0, decoded.lat, 0.0)
        assertEquals(2.0, decoded.lon, 0.0)
        assertTrue("separator must be stripped, not left splitting the encoding", "|" !in decoded.name)
    }

    @Test
    fun `decode is null for a never-configured tile`() {
        assertNull(WeatherTile.decode(null))
        assertNull(WeatherTile.decode(""))
    }

    @Test
    fun `decode rejects an out-of-range coordinate`() {
        assertNull(WeatherTile.decode("weather:at|999|0|nowhere"))
        assertNull(WeatherTile.decode("weather:at|0|999|nowhere"))
    }

    @Test
    fun `decode rejects a malformed encoding`() {
        assertNull(WeatherTile.decode("weather:at|notanumber|0|somewhere"))
        assertNull(WeatherTile.decode("not a weather encoding at all"))
    }

    @Test
    fun `current location always keys to the same shared slot`() {
        assertEquals(WeatherTile.CURRENT_KEY, WeatherTile.key(WeatherTile.Location.Current))
        assertEquals(WeatherTile.keyFor(null), WeatherTile.key(WeatherTile.Location.Current))
    }

    @Test
    fun `two fixed places within a hundredth of a degree share one key`() {
        val a = WeatherTile.Location.Fixed(18.5204, 73.8567, "Pune")
        val b = WeatherTile.Location.Fixed(18.5204_4, 73.8567_4, "Pune (slightly different search result)")
        assertEquals(WeatherTile.key(a), WeatherTile.key(b))
    }

    @Test
    fun `a genuinely different place gets a different key`() {
        val pune = WeatherTile.Location.Fixed(18.5204, 73.8567, "Pune")
        val london = WeatherTile.Location.Fixed(51.5074, -0.1278, "London")
        assertTrue(WeatherTile.key(pune) != WeatherTile.key(london))
    }

    @Test
    fun `fixedPlaces dedupes by key and excludes current-location and unconfigured entries`() {
        val pune1 = WeatherTile.encode(WeatherTile.Location.Fixed(18.5204, 73.8567, "Pune"))
        val pune2 = WeatherTile.encode(WeatherTile.Location.Fixed(18.5205, 73.8568, "Pune again"))
        val london = WeatherTile.encode(WeatherTile.Location.Fixed(51.5074, -0.1278, "London"))
        val current = WeatherTile.encode(WeatherTile.Location.Current)

        val result = WeatherTile.fixedPlaces(listOf(pune1, pune2, london, current, null, "garbage"))

        assertEquals(2, result.size)
        assertTrue(result.containsKey(WeatherTile.key(WeatherTile.Location.Fixed(18.5204, 73.8567, "Pune"))))
        assertTrue(result.containsKey(WeatherTile.key(WeatherTile.Location.Fixed(51.5074, -0.1278, "London"))))
        // First-seen wins for a shared key, so the first tile's own display name survives.
        assertEquals("Pune", result.getValue(WeatherTile.key(WeatherTile.Location.Fixed(18.5204, 73.8567, "Pune"))).name)
    }
}
