package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class SunTimesTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun epochMillisUtc(year: Int, month1to12: Int, day: Int): Long {
        val cal = java.util.Calendar.getInstance(utc)
        cal.clear()
        cal.set(year, month1to12 - 1, day, 12, 0, 0)
        return cal.timeInMillis
    }

    @Test
    fun `equator at an equinox has sunrise-sunset close to 6am-6pm local solar time, and roughly a 12h day`() {
        // 2024-09-22 is close to the September equinox.
        val epochMillis = epochMillisUtc(2024, 9, 22)
        val result = SunTimes.sunriseSunsetFor(epochMillis, latitude = 0.0, longitude = 0.0, zone = utc)
        assertNotNull(result)
        result!!
        val riseCal = java.util.Calendar.getInstance(utc).apply { timeInMillis = result.sunriseMillis }
        val setCal = java.util.Calendar.getInstance(utc).apply { timeInMillis = result.sunsetMillis }
        val riseHour = riseCal.get(java.util.Calendar.HOUR_OF_DAY) + riseCal.get(java.util.Calendar.MINUTE) / 60.0
        val setHour = setCal.get(java.util.Calendar.HOUR_OF_DAY) + setCal.get(java.util.Calendar.MINUTE) / 60.0
        // Equation of time near the equinox is a few minutes at most.
        assertTrue("sunrise $riseHour should be close to 6:00 UTC", riseHour in 5.75..6.25)
        assertTrue("sunset $setHour should be close to 18:00 UTC", setHour in 17.75..18.25)
        val dayLengthHours = (result.sunsetMillis - result.sunriseMillis) / TimeUnit.HOURS.toMillis(1).toDouble()
        // The "equinox" day is traditionally ~12h6m, not exactly 12h — the
        // -0.833° reference altitude (atmospheric refraction + the solar
        // disk's own radius) extends both sunrise and sunset a little.
        assertTrue("day length $dayLengthHours should be close to 12h6m", dayLengthHours in 12.0..12.3)
    }

    @Test
    fun `sunrise always precedes sunset on an ordinary day`() {
        val epochMillis = epochMillisUtc(2026, 3, 15)
        val result = SunTimes.sunriseSunsetFor(epochMillis, latitude = 18.52, longitude = 73.86, zone = utc)
        assertNotNull(result)
        assertTrue(result!!.sunriseMillis < result.sunsetMillis)
    }

    @Test
    fun `day length grows toward the local summer at a mid-latitude`() {
        // Northern hemisphere: December (winter) day is shorter than June (summer).
        val decemberDay = SunTimes.sunriseSunsetFor(epochMillisUtc(2025, 12, 21), latitude = 40.0, longitude = 0.0, zone = utc)!!
        val juneDay = SunTimes.sunriseSunsetFor(epochMillisUtc(2025, 6, 21), latitude = 40.0, longitude = 0.0, zone = utc)!!
        val decemberLength = decemberDay.sunsetMillis - decemberDay.sunriseMillis
        val juneLength = juneDay.sunsetMillis - juneDay.sunriseMillis
        assertTrue(juneLength > decemberLength)
    }

    @Test
    fun `polar night returns null instead of a nonsense time`() {
        // 80 degrees north in the depths of winter never sees the sun rise.
        val result = SunTimes.sunriseSunsetFor(epochMillisUtc(2025, 12, 21), latitude = 80.0, longitude = 0.0, zone = utc)
        assertNull(result)
    }

    @Test
    fun `midnight sun returns null instead of a nonsense time`() {
        // 80 degrees north at midsummer never sees the sun set.
        val result = SunTimes.sunriseSunsetFor(epochMillisUtc(2025, 6, 21), latitude = 80.0, longitude = 0.0, zone = utc)
        assertNull(result)
    }

    // Latitude 18.52 (Pune-like) but longitude 0 (not Pune's real ~73.86) —
    // deliberately, so local sunrise/sunset land mid-day in UTC (~6am/~6pm)
    // rather than near the UTC midnight boundary, which would make these
    // `zone = utc` tests' own hour-offset arithmetic flaky around a real
    // longitude's date-line-relative sunrise time.
    @Test
    fun `nextSunriseSunset before today's sunrise returns both times unchanged`() {
        val today = SunTimes.sunriseSunsetFor(epochMillisUtc(2026, 3, 15), latitude = 18.52, longitude = 0.0, zone = utc)!!
        val beforeSunrise = today.sunriseMillis - TimeUnit.HOURS.toMillis(1)
        val next = SunTimes.nextSunriseSunset(beforeSunrise, latitude = 18.52, longitude = 0.0, zone = utc)!!
        assertEquals(today.sunriseMillis, next.sunriseMillis)
        assertEquals(today.sunsetMillis, next.sunsetMillis)
    }

    @Test
    fun `nextSunriseSunset between today's sunrise and sunset rolls only sunrise forward`() {
        val today = SunTimes.sunriseSunsetFor(epochMillisUtc(2026, 3, 15), latitude = 18.52, longitude = 0.0, zone = utc)!!
        val midday = (today.sunriseMillis + today.sunsetMillis) / 2
        val next = SunTimes.nextSunriseSunset(midday, latitude = 18.52, longitude = 0.0, zone = utc)!!
        // Sunrise already passed today, so the "next" sunrise is tomorrow's — after today's sunset.
        assertTrue(next.sunriseMillis > today.sunsetMillis)
        // Sunset hasn't passed yet — still today's, unchanged.
        assertEquals(today.sunsetMillis, next.sunsetMillis)
    }

    @Test
    fun `nextSunriseSunset after today's sunset rolls both forward to tomorrow`() {
        val today = SunTimes.sunriseSunsetFor(epochMillisUtc(2026, 3, 15), latitude = 18.52, longitude = 0.0, zone = utc)!!
        val afterSunset = today.sunsetMillis + TimeUnit.HOURS.toMillis(1)
        val next = SunTimes.nextSunriseSunset(afterSunset, latitude = 18.52, longitude = 0.0, zone = utc)!!
        val tomorrow = SunTimes.sunriseSunsetFor(epochMillisUtc(2026, 3, 16), latitude = 18.52, longitude = 0.0, zone = utc)!!
        assertEquals(tomorrow.sunriseMillis, next.sunriseMillis)
        assertEquals(tomorrow.sunsetMillis, next.sunsetMillis)
    }
}
