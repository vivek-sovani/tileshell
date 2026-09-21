package com.tileshell.feature.livetiles

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `one fixed place's snapshot round-trips alongside the device snapshot`() {
        val data = WeatherCacheData(
            snapshot = WeatherSnapshot(tempC = 23, condition = "clear", highC = 26, lowC = 17, place = "current"),
            places = mapOf(
                "18.52,73.86" to WeatherSnapshot(
                    tempC = 30,
                    condition = "hot",
                    highC = 33,
                    lowC = 24,
                    detail = "chance of rain · 20%",
                    place = "Pune",
                    fetchedAtMillis = 1_700_000_000_000L,
                    forecast = listOf(DailyForecast("today", 33, 24, "hot")),
                ),
            ),
        )
        assertEquals(data, WeatherCacheCodec.decode(WeatherCacheCodec.encode(data)))
    }

    @Test
    fun `several fixed places all round-trip independently`() {
        val data = WeatherCacheData(
            places = mapOf(
                "18.52,73.86" to WeatherSnapshot(tempC = 30, condition = "hot", highC = 33, lowC = 24, place = "Pune"),
                "51.51,-0.13" to WeatherSnapshot(tempC = 12, condition = "rain", highC = 14, lowC = 9, place = "London"),
            ),
        )
        val decoded = WeatherCacheCodec.decode(WeatherCacheCodec.encode(data))
        assertEquals(data.places, decoded.places)
    }

    @Test
    fun `an old cache file with no loc lines decodes to no fixed places`() {
        val decoded = WeatherCacheCodec.decode("temp=23\nhigh=26\nlow=17\nplace=Pune\ncondition=clear")
        assertEquals(emptyMap<String, WeatherSnapshot>(), decoded.places)
    }

    @Test
    fun `a malformed loc line is skipped, not thrown`() {
        val decoded = WeatherCacheCodec.decode("loc=incomplete~30")
        assertEquals(emptyMap<String, WeatherSnapshot>(), decoded.places)
    }

    @Test
    fun `hourly and detail-line fields round-trip on the device snapshot`() {
        val data = WeatherCacheData(
            snapshot = WeatherSnapshot(
                tempC = 23,
                condition = "clear",
                highC = 26,
                lowC = 17,
                place = "Pune",
                feelsLikeC = 25,
                windKph = 14,
                humidityPct = 54,
                hourly = listOf(
                    HourlyForecast("now", 23, "clear"),
                    HourlyForecast("3pm", 24, "partly cloudy"),
                ),
                forecast = listOf(DailyForecast("today", 26, 17, "clear", isoDate = "2026-03-05", precipProbabilityMax = 20)),
            ),
        )
        assertEquals(data, WeatherCacheCodec.decode(WeatherCacheCodec.encode(data)))
    }

    @Test
    fun `hourly and detail-line fields round-trip on a fixed place`() {
        val data = WeatherCacheData(
            places = mapOf(
                "18.52,73.86" to WeatherSnapshot(
                    tempC = 30,
                    condition = "hot",
                    highC = 33,
                    lowC = 24,
                    place = "Pune",
                    feelsLikeC = 34,
                    windKph = 8,
                    humidityPct = 40,
                    hourly = listOf(HourlyForecast("now", 30, "hot")),
                    forecast = listOf(DailyForecast("today", 33, 24, "hot", isoDate = "2026-03-05", precipProbabilityMax = 5)),
                ),
            ),
        )
        assertEquals(data, WeatherCacheCodec.decode(WeatherCacheCodec.encode(data)))
    }

    @Test
    fun `a cache file written before the weather hub existed still decodes`() {
        // Original 4-field forecast, no feelsLike/wind/humidity/hourly lines,
        // and an 8-field loc= line (no trailing hub fields at all).
        val text = "temp=23\nhigh=26\nlow=17\nplace=Pune\ncondition=clear\n" +
            "forecast0=today|26|17|clear\n" +
            "loc=18.52,73.86~30~33~24~0~hot~Pune~\n"
        val decoded = WeatherCacheCodec.decode(text)
        assertEquals(DailyForecast("today", 26, 17, "clear"), decoded.snapshot?.forecast?.single())
        assertNull(decoded.snapshot?.feelsLikeC)
        assertEquals(emptyList<HourlyForecast>(), decoded.snapshot?.hourly)
        val place = decoded.places.getValue("18.52,73.86")
        assertEquals(30, place.tempC)
        assertNull(place.feelsLikeC)
        assertEquals(emptyList<HourlyForecast>(), place.hourly)
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
        assertEquals(DailyForecast("today", 26, 17, "partly cloudy", isoDate = "2026-03-05"), snap.forecast[0])
        assertEquals(DailyForecast("tomorrow", 25, 16, "rain", isoDate = "2026-03-06"), snap.forecast[1])
        assertEquals(DailyForecast("saturday", 24, 15, "clear", isoDate = "2026-03-07"), snap.forecast[2])
    }

    @Test
    fun `per-day precipitation probability is kept, not just today's`() {
        val json = """
            {
              "current": { "temperature_2m": 22.6, "weather_code": 2 },
              "daily": {
                "time": ["2026-03-05", "2026-03-06"],
                "temperature_2m_max": [26.4, 25.0],
                "temperature_2m_min": [16.5, 16.0],
                "weather_code": [2, 61],
                "precipitation_probability_max": [40, 90]
              }
            }
        """.trimIndent()
        val snap = parseOpenMeteoForecast(json, place = "Pune", nowMillis = 123L)!!
        assertEquals(40, snap.forecast[0].precipProbabilityMax)
        assertEquals(90, snap.forecast[1].precipProbabilityMax)
    }

    @Test
    fun `current's feels-like, wind and humidity are parsed when present`() {
        val json = """
            {
              "current": {
                "temperature_2m": 22.6, "weather_code": 2,
                "apparent_temperature": 24.8, "wind_speed_10m": 13.6, "relative_humidity_2m": 54
              }
            }
        """.trimIndent()
        val snap = parseOpenMeteoForecast(json, place = "Pune", nowMillis = 123L)!!
        assertEquals(25, snap.feelsLikeC)
        assertEquals(14, snap.windKph)
        assertEquals(54, snap.humidityPct)
    }

    @Test
    fun `missing feels-like, wind and humidity are null, not zero`() {
        val json = """{"current": {"temperature_2m": 20, "weather_code": 0}}"""
        val snap = parseOpenMeteoForecast(json, "x", 0L)!!
        assertNull(snap.feelsLikeC)
        assertNull(snap.windKph)
        assertNull(snap.humidityPct)
    }

    @Test
    fun `hourly outlook starts from the entry nearest current time`() {
        val json = """
            {
              "current": { "temperature_2m": 20, "weather_code": 0, "time": "2026-03-05T14:00" },
              "hourly": {
                "time": ["2026-03-05T12:00", "2026-03-05T13:00", "2026-03-05T14:00", "2026-03-05T15:00"],
                "temperature_2m": [18, 19, 20, 21],
                "weather_code": [0, 1, 2, 61]
              }
            }
        """.trimIndent()
        val snap = parseOpenMeteoForecast(json, "x", 0L)!!
        assertEquals(2, snap.hourly.size)
        assertEquals(HourlyForecast("now", 20, "partly cloudy"), snap.hourly[0])
        assertEquals(HourlyForecast("3pm", 21, "rain"), snap.hourly[1])
    }

    @Test
    fun `missing hourly data yields an empty list, not a crash`() {
        val json = """{"current": {"temperature_2m": 20, "weather_code": 0}}"""
        assertEquals(emptyList<HourlyForecast>(), parseOpenMeteoForecast(json, "x", 0L)!!.hourly)
    }

    @Test
    fun `hourly time label is a 12-hour clock, noon and midnight included`() {
        assertEquals("now", hourlyForecastTimeLabel("2026-03-05T14:00", isNow = true))
        assertEquals("2pm", hourlyForecastTimeLabel("2026-03-05T14:00", isNow = false))
        assertEquals("12pm", hourlyForecastTimeLabel("2026-03-05T12:00", isNow = false))
        assertEquals("12am", hourlyForecastTimeLabel("2026-03-05T00:00", isNow = false))
        assertEquals("1am", hourlyForecastTimeLabel("2026-03-05T01:00", isNow = false))
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

/** The wake-time refresh gate that complements the worker's screen-off gate. */
class WeatherWakeRefreshTest {

    private val hour = 60 * 60 * 1000L

    @Test
    fun `waking to a stale cache refetches`() {
        // The measured case: cache written 22:24, still shown at 06:09 next day.
        val now = 10 * hour
        assertTrue(shouldRefreshWeatherOnWake(nowMillis = now, fetchedAtMillis = now - 8 * hour))
    }

    @Test
    fun `waking to a fresh cache does not refetch`() {
        val now = 10 * hour
        assertFalse(shouldRefreshWeatherOnWake(nowMillis = now, fetchedAtMillis = now - 60_000L))
    }

    @Test
    fun `an empty cache always refetches`() {
        assertTrue(shouldRefreshWeatherOnWake(nowMillis = 10 * hour, fetchedAtMillis = 0L))
    }

    @Test
    fun `exactly at the staleness boundary refetches`() {
        val now = 10 * hour
        assertTrue(shouldRefreshWeatherOnWake(now, fetchedAtMillis = now - WEATHER_STALE_AFTER_MS))
        assertFalse(shouldRefreshWeatherOnWake(now, fetchedAtMillis = now - WEATHER_STALE_AFTER_MS + 1))
    }

    @Test
    fun `a clock that jumped backwards reads as fresh, not stale`() {
        val now = 10 * hour
        assertFalse(shouldRefreshWeatherOnWake(nowMillis = now, fetchedAtMillis = now + hour))
    }
}
