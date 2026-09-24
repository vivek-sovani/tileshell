package com.tileshell.feature.livetiles.widget

import com.tileshell.feature.livetiles.DailyForecast
import com.tileshell.feature.livetiles.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherSunAlarmTest {

    private fun snap(vararg sun: Pair<Long?, Long?>) = WeatherSnapshot(
        tempC = 20, condition = "clear", highC = 25, lowC = 15,
        forecast = sun.map { (rise, set) ->
            DailyForecast("d", 25, 15, "clear", sunriseMillis = rise, sunsetMillis = set)
        },
    )

    @Test
    fun `picks the next sunset during the day and the next sunrise at night`() {
        val s = snap(6L to 18L, 30L to 42L)
        assertEquals(18L, nextSunTransition(12L, listOf(s)))
        assertEquals(30L, nextSunTransition(20L, listOf(s)))
    }

    @Test
    fun `a transition exactly now is not next`() {
        assertEquals(30L, nextSunTransition(18L, listOf(snap(6L to 18L, 30L to 42L))))
    }

    @Test
    fun `earliest across several widgets' places wins`() {
        assertEquals(15L, nextSunTransition(12L, listOf(snap(6L to 18L), snap(3L to 15L))))
    }

    @Test
    fun `no known future transition is null`() {
        assertNull(nextSunTransition(12L, emptyList()))
        assertNull(nextSunTransition(12L, listOf(snap(null to null))))
        assertNull(nextSunTransition(50L, listOf(snap(6L to 18L))))
    }
}
