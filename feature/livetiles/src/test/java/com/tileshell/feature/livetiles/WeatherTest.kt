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

    @Test
    fun `snapshot with a forecast round-trips in order`() {
        val data = WeatherCacheData(
            snapshot = WeatherSnapshot(
                tempC = 23,
                condition = "partly cloudy",
                highC = 26,
                lowC = 17,
                place = "Pune",
                forecast = listOf(
                    DailyForecast("today", 26, 17, "partly cloudy"),
                    DailyForecast("tomorrow", 25, 16, "rain"),
                    DailyForecast("wednesday", 24, 15, "clear"),
                ),
            ),
        )
        assertEquals(data, WeatherCacheCodec.decode(WeatherCacheCodec.encode(data)))
    }

    @Test
    fun `an old cache file with no forecast lines decodes to an empty forecast`() {
        val decoded = WeatherCacheCodec.decode(
            "temp=23\nhigh=26\nlow=17\nplace=Pune\ncondition=clear",
        )
        assertEquals(emptyList<DailyForecast>(), decoded.snapshot?.forecast)
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
    fun `day label is today, tomorrow, then the weekday name`() {
        assertEquals("today", dailyForecastDayLabel(0, "2026-03-05"))
        assertEquals("tomorrow", dailyForecastDayLabel(1, "2026-03-06"))
        // 2026-03-07 is a Saturday.
        assertEquals("saturday", dailyForecastDayLabel(2, "2026-03-07"))
    }

    @Test
    fun `malformed date at index 2+ falls back to a day number instead of crashing`() {
        assertEquals("day 3", dailyForecastDayLabel(2, "not-a-date"))
    }

    @Test
    fun `multi-day forecast json parses one entry per day`() {
        val json = """
            {
              "current": { "temperature_2m": 22.6, "weather_code": 2 },
              "daily": {
                "time": ["2026-03-05", "2026-03-06", "2026-03-07"],
                "temperature_2m_max": [26.4, 25.0, 24.0],
                "temperature_2m_min": [16.5, 16.0, 15.0],
                "weather_code": [2, 61, 0]
              }
            }
        """.trimIndent()
        val snap = parseOpenMeteoForecast(json, place = "Pune", nowMillis = 123L)!!
        assertEquals(3, snap.forecast.size)
        assertEquals(DailyForecast("today", 26, 17, "partly cloudy"), snap.forecast[0])
        assertEquals(DailyForecast("tomorrow", 25, 16, "rain"), snap.forecast[1])
        assertEquals(DailyForecast("saturday", 24, 15, "clear"), snap.forecast[2])
    }

    @Test
    fun `missing daily arrays yield an empty forecast, not a crash`() {
        val json = """{"current": {"temperature_2m": 20, "weather_code": 0}}"""
        assertEquals(emptyList<DailyForecast>(), parseOpenMeteoForecast(json, "x", 0L)!!.forecast)
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
