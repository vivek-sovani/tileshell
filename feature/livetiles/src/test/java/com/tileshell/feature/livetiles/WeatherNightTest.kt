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
}
