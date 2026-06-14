package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherFormatTest {

    @Test
    fun `temp label appends a degree sign`() {
        assertEquals("23°", tempLabel(23))
        assertEquals("-4°", tempLabel(-4))
    }

    @Test
    fun `high low label matches the prototype`() {
        assertEquals("26° / 17°", highLowLabel(26, 17))
    }
}

class WeatherQueryTest {

    @Test
    fun `location wins over a manual city`() {
        val q = resolveWeatherQuery(location = 47.6 to -122.3, manualCity = "Pune")
        assertEquals(WeatherQuery.Coords(47.6, -122.3), q)
    }

    @Test
    fun `falls back to manual city when no location`() {
        assertEquals(WeatherQuery.City("Pune"), resolveWeatherQuery(null, "Pune"))
    }

    @Test
    fun `no location and no city resolves to null`() {
        assertNull(resolveWeatherQuery(null, null))
        assertNull(resolveWeatherQuery(null, "   "))
    }
}

class WeatherCacheCodecTest {

    @Test
    fun `snapshot round-trips`() {
        val data = WeatherCacheData(
            snapshot = WeatherSnapshot(
                tempC = 23,
                condition = "partly cloudy",
                highC = 26,
                lowC = 17,
                detail = "rain by 6pm · 40%",
                place = "Pune",
                fetchedAtMillis = 1_700_000_000_000L,
            ),
            manualCity = "Pune",
        )
        assertEquals(data, WeatherCacheCodec.decode(WeatherCacheCodec.encode(data)))
    }

    @Test
    fun `empty store decodes to no snapshot`() {
        val decoded = WeatherCacheCodec.decode("")
        assertNull(decoded.snapshot)
        assertNull(decoded.manualCity)
    }

    @Test
    fun `manual city survives without a snapshot`() {
        val data = WeatherCacheData(snapshot = null, manualCity = "Pune")
        assertEquals(data, WeatherCacheCodec.decode(WeatherCacheCodec.encode(data)))
    }

    @Test
    fun `a snapshot missing its numbers decodes as no snapshot`() {
        // condition present but temp/high/low absent -> not a valid snapshot.
        assertNull(WeatherCacheCodec.decode("condition=sunny").snapshot)
    }
}
