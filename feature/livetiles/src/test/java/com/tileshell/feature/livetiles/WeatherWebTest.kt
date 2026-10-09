package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherWebTest {
    @Test
    fun `each site searches for the place`() {
        assertEquals("https://www.google.com/search?q=weather%20Pune", weatherWebUrl(WeatherSite.GOOGLE, "Pune"))
        assertEquals("https://www.accuweather.com/en/search-locations?query=Pune", weatherWebUrl(WeatherSite.ACCUWEATHER, " Pune "))
        assertEquals("https://www.timeanddate.com/weather/?query=Pune", weatherWebUrl(WeatherSite.TIMEANDDATE, "Pune"))
    }

    @Test
    fun `a place with spaces and accents is encoded`() {
        assertEquals("https://www.google.com/search?q=weather%20New%20York", weatherWebUrl(WeatherSite.GOOGLE, "New York"))
        assertEquals("https://www.timeanddate.com/weather/?query=S%C3%A3o%20Paulo", weatherWebUrl(WeatherSite.TIMEANDDATE, "São Paulo"))
    }

    @Test
    fun `no place falls back to a plain search`() {
        assertEquals("https://www.google.com/search?q=weather", weatherWebUrl(WeatherSite.GOOGLE, ""))
        assertEquals("https://www.accuweather.com/en/search-locations?query=", weatherWebUrl(WeatherSite.ACCUWEATHER, "  "))
    }
}
