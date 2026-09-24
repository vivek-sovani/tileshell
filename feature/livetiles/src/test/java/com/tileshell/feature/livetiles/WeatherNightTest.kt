package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherNightTest {

    private val hour = 60 * 60 * 1000L

    // Day 0: sunrise 6h, sunset 18h; day 1: +24h each.
    private fun snap(isDay: Boolean? = null, withSun: Boolean = true) = WeatherSnapshot(
        tempC = 20, condition = "clear", highC = 25, lowC = 15, isDay = isDay,
        forecast = if (!withSun) emptyList() else listOf(0, 1).map { d ->
            DailyForecast(
                dayLabel = "d$d", highC = 25, lowC = 15, condition = "clear",
                sunriseMillis = d * 24 * hour + 6 * hour,
                sunsetMillis = d * 24 * hour + 18 * hour,
            )
        },
    )

    @Test
    fun `between sunrise and sunset is day`() {
        assertFalse(snap(isDay = false).isNightAt(12 * hour))
    }

    @Test
    fun `after sunset and before the next sunrise is night`() {
        assertTrue(snap(isDay = true).isNightAt(20 * hour))
        assertTrue(snap(isDay = true).isNightAt(29 * hour))
    }

    @Test
    fun `before the first sunrise is night`() {
        assertTrue(snap().isNightAt(2 * hour))
    }

    @Test
    fun `sunset instant itself is night, sunrise instant is day`() {
        assertTrue(snap().isNightAt(18 * hour))
        assertFalse(snap().isNightAt(6 * hour))
    }

    @Test
    fun `falls back to the fetch-time flag without sun times`() {
        assertTrue(snap(isDay = false, withSun = false).isNightAt(12 * hour))
        assertFalse(snap(isDay = true, withSun = false).isNightAt(2 * hour))
        assertFalse(snap(isDay = null, withSun = false).isNightAt(2 * hour))
    }

    @Test
    fun `falls back to the flag once now is past the covered days`() {
        assertTrue(snap(isDay = false).isNightAt(24 * 10 * hour))
        assertFalse(snap(isDay = null).isNightAt(24 * 10 * hour))
    }

    @Test
    fun `local iso time converts with the place's own offset`() {
        // 06:00 at +05:30 is 00:30 UTC.
        assertEquals(30 * 60 * 1000L, localIsoToEpochMillis("1970-01-01T06:00", 19800))
        assertNull(localIsoToEpochMillis("garbage", 0))
    }

    @Test
    fun `forecast json parses is_day and sunrise-sunset`() {
        val json = """
            {
              "utc_offset_seconds": 0,
              "current": { "time": "1970-01-01T20:00", "temperature_2m": 20, "weather_code": 0, "is_day": 0 },
              "hourly": { "time": ["1970-01-01T20:00"], "temperature_2m": [20], "weather_code": [0], "is_day": [0] },
              "daily": {
                "time": ["1970-01-01"],
                "temperature_2m_max": [25], "temperature_2m_min": [15],
                "sunrise": ["1970-01-01T06:00"], "sunset": ["1970-01-01T18:00"]
              }
            }
        """.trimIndent()
        val s = parseOpenMeteoForecast(json, "x", 0L)!!
        assertEquals(false, s.isDay)
        assertEquals(false, s.hourly.single().isDay)
        assertEquals(6 * hour, s.forecast.single().sunriseMillis)
        assertEquals(18 * hour, s.forecast.single().sunsetMillis)
        assertTrue(s.isNightAt(20 * hour))
    }

    @Test
    fun `day-night fields round-trip through the cache codec`() {
        val s = snap(isDay = false).copy(
            hourly = listOf(HourlyForecast("now", 20, "clear", isDay = false), HourlyForecast("9pm", 19, "clear")),
        )
        val data = WeatherCacheData(snapshot = s, places = mapOf("k" to s))
        val back = WeatherCacheCodec.decode(WeatherCacheCodec.encode(data))
        assertEquals(s, back.snapshot)
        assertEquals(s.copy(place = "", detail = ""), back.places["k"])
    }

    @Test
    fun `an old 3-field hourly line still decodes with an unknown day flag`() {
        val back = WeatherCacheCodec.decode("temp=1\nhigh=2\nlow=0\ncondition=clear\nhourly0=now|1|clear")
        assertNull(back.snapshot!!.hourly.single().isDay)
        assertNull(back.snapshot!!.isDay)
    }

    @Test
    fun `today page range line labels max and min`() {
        assertEquals("max 31°  ·  min 22°", weatherHubHighLowLine(31, 22))
    }

    @Test
    fun `uv index bands follow the WHO scale`() {
        assertEquals("low", uvIndexCategory(2))
        assertEquals("moderate", uvIndexCategory(3))
        assertEquals("high", uvIndexCategory(7))
        assertEquals("very high", uvIndexCategory(8))
        assertEquals("extreme", uvIndexCategory(11))
    }

    @Test
    fun `sun time is shown in the place's own offset`() {
        // 00:42 UTC is 6:12 am at +05:30; 12:30 UTC is 6:00 pm there.
        assertEquals("6:12 am", sunTimeLabel(42 * 60 * 1000L, 19800))
        assertEquals("6:00 pm", sunTimeLabel(12 * hour + 30 * 60 * 1000L, 19800))
        assertEquals("12:05 am", sunTimeLabel(5 * 60 * 1000L, 0))
    }

    @Test
    fun `today stats list every present field and skip missing ones`() {
        val full = snap().copy(feelsLikeC = 30, windKph = 14, humidityPct = 54, uvIndexMax = 7, utcOffsetSeconds = 0)
        assertEquals(
            listOf(
                "feels like" to "30°", "wind speed" to "14 km/h", "humidity" to "54%",
                "uv index" to "7 · high", "sunrise" to "6:00 am", "sunset" to "6:00 pm",
            ),
            weatherHubStats(full),
        )
        assertEquals(emptyList<Pair<String, String>>(), weatherHubStats(snap(withSun = false)))
    }

    @Test
    fun `uv and offset parse from json and round-trip through the codec`() {
        val json = """
            {
              "utc_offset_seconds": 19800,
              "current": { "temperature_2m": 20, "weather_code": 0 },
              "daily": { "temperature_2m_max": [25], "temperature_2m_min": [15], "uv_index_max": [6.6] }
            }
        """.trimIndent()
        val s = parseOpenMeteoForecast(json, "x", 0L)!!
        assertEquals(7, s.uvIndexMax)
        assertEquals(19800, s.utcOffsetSeconds)
        val data = WeatherCacheData(snapshot = s, places = mapOf("k" to s))
        val back = WeatherCacheCodec.decode(WeatherCacheCodec.encode(data))
        assertEquals(7, back.snapshot!!.uvIndexMax)
        assertEquals(19800, back.snapshot!!.utcOffsetSeconds)
        assertEquals(7, back.places["k"]!!.uvIndexMax)
        assertEquals(19800, back.places["k"]!!.utcOffsetSeconds)
    }

    @Test
    fun `sunless conditions get a moon behind the cloud, sun conditions don't`() {
        listOf("overcast", "rain", "drizzle", "rain showers", "snow", "thunderstorm", "fog", "freezing rain")
            .forEach { assertTrue(it, isCloudCondition(it)) }
        listOf("clear", "mostly clear", "partly cloudy", "—").forEach { assertFalse(it, isCloudCondition(it)) }
    }

    @Test
    fun `today's sun times are the first day's, in the place's offset`() {
        assertEquals("6:00 am" to "6:00 pm", todaySunTimes(snap().copy(utcOffsetSeconds = 0)))
        assertNull(todaySunTimes(snap(withSun = false)))
    }
}
