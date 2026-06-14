package com.tileshell.feature.livetiles

import kotlinx.coroutines.runBlocking
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

class OpenMeteoTest {

    @Test
    fun `weather codes map to lowercase phrases`() {
        assertEquals("clear", weatherCodeToCondition(0))
        assertEquals("partly cloudy", weatherCodeToCondition(2))
        assertEquals("rain", weatherCodeToCondition(63))
        assertEquals("thunderstorm", weatherCodeToCondition(95))
        assertEquals("—", weatherCodeToCondition(123))
    }

    @Test
    fun `detail line shows precipitation chance only when meaningful`() {
        assertEquals("chance of rain · 40%", weatherDetail(40))
        assertEquals("", weatherDetail(0))
        assertEquals("", weatherDetail(null))
    }

    @Test
    fun `forecast json parses into a snapshot with rounding and place`() {
        val json = """
            {
              "current": { "temperature_2m": 22.6, "weather_code": 2 },
              "daily": {
                "temperature_2m_max": [26.4],
                "temperature_2m_min": [16.5],
                "precipitation_probability_max": [40]
              }
            }
        """.trimIndent()
        val snap = parseOpenMeteoForecast(json, place = "Pune", nowMillis = 123L)!!
        assertEquals(23, snap.tempC) // 22.6 rounds up
        assertEquals("partly cloudy", snap.condition)
        assertEquals(26, snap.highC)
        assertEquals(17, snap.lowC) // 16.5 rounds to 17
        assertEquals("chance of rain · 40%", snap.detail)
        assertEquals("Pune", snap.place)
        assertEquals(123L, snap.fetchedAtMillis)
    }

    @Test
    fun `forecast without a current temperature is rejected`() {
        assertNull(parseOpenMeteoForecast("""{"daily":{}}""", "x", 0L))
        assertNull(parseOpenMeteoForecast("not json", "x", 0L))
    }

    @Test
    fun `geocode json takes the first result`() {
        val json = """
            {"results":[
              {"name":"Pune","latitude":18.52,"longitude":73.86},
              {"name":"Pune (other)","latitude":1.0,"longitude":2.0}
            ]}
        """.trimIndent()
        val place = parseOpenMeteoGeocode(json)!!
        assertEquals("Pune", place.name)
        assertEquals(18.52, place.lat, 0.0001)
        assertEquals(73.86, place.lon, 0.0001)
    }

    @Test
    fun `geocode with no results is null`() {
        assertNull(parseOpenMeteoGeocode("""{"results":[]}"""))
        assertNull(parseOpenMeteoGeocode("""{}"""))
    }

    @Test
    fun `coords query labels the snapshot from reverse geocoding`() = runBlocking {
        val provider = OpenMeteoWeatherProvider(
            reverseGeocode = { _, _ -> "Pune" },
            httpGet = { """{"current":{"temperature_2m":20,"weather_code":0}}""" },
        )
        val snap = provider.fetch(WeatherQuery.Coords(18.5, 73.8))!!
        assertEquals("Pune", snap.place)
        assertEquals(20, snap.tempC)
    }

    @Test
    fun `coords query falls back to a generic label when geocoding fails`() = runBlocking {
        val provider = OpenMeteoWeatherProvider(
            reverseGeocode = { _, _ -> null },
            httpGet = { """{"current":{"temperature_2m":20,"weather_code":0}}""" },
        )
        val snap = provider.fetch(WeatherQuery.Coords(18.5, 73.8))!!
        assertEquals("current location", snap.place)
    }
}
