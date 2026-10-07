package com.tileshell.core.data.clock

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** A city the world clock can show: its name and IANA time zone. */
data class WorldCity(val name: String, val zoneId: String)

val WORLD_CITIES: List<WorldCity> = listOf(
    WorldCity("Pune", "Asia/Kolkata"), WorldCity("Mumbai", "Asia/Kolkata"), WorldCity("Delhi", "Asia/Kolkata"),
    WorldCity("Bengaluru", "Asia/Kolkata"), WorldCity("Kolkata", "Asia/Kolkata"), WorldCity("Chennai", "Asia/Kolkata"),
    WorldCity("Dubai", "Asia/Dubai"), WorldCity("Abu Dhabi", "Asia/Dubai"), WorldCity("Doha", "Asia/Qatar"),
    WorldCity("Riyadh", "Asia/Riyadh"), WorldCity("Karachi", "Asia/Karachi"), WorldCity("Dhaka", "Asia/Dhaka"),
    WorldCity("Kathmandu", "Asia/Kathmandu"), WorldCity("Colombo", "Asia/Colombo"), WorldCity("Bangkok", "Asia/Bangkok"),
    WorldCity("Singapore", "Asia/Singapore"), WorldCity("Kuala Lumpur", "Asia/Kuala_Lumpur"), WorldCity("Jakarta", "Asia/Jakarta"),
    WorldCity("Hong Kong", "Asia/Hong_Kong"), WorldCity("Shanghai", "Asia/Shanghai"), WorldCity("Beijing", "Asia/Shanghai"),
    WorldCity("Tokyo", "Asia/Tokyo"), WorldCity("Seoul", "Asia/Seoul"), WorldCity("Sydney", "Australia/Sydney"),
    WorldCity("Melbourne", "Australia/Melbourne"), WorldCity("Perth", "Australia/Perth"), WorldCity("Auckland", "Pacific/Auckland"),
    WorldCity("London", "Europe/London"), WorldCity("Dublin", "Europe/Dublin"), WorldCity("Paris", "Europe/Paris"),
    WorldCity("Berlin", "Europe/Berlin"), WorldCity("Amsterdam", "Europe/Amsterdam"), WorldCity("Madrid", "Europe/Madrid"),
    WorldCity("Rome", "Europe/Rome"), WorldCity("Zurich", "Europe/Zurich"), WorldCity("Moscow", "Europe/Moscow"),
    WorldCity("Istanbul", "Europe/Istanbul"), WorldCity("Cairo", "Africa/Cairo"), WorldCity("Nairobi", "Africa/Nairobi"),
    WorldCity("Lagos", "Africa/Lagos"), WorldCity("Johannesburg", "Africa/Johannesburg"),
    WorldCity("New York", "America/New_York"), WorldCity("Toronto", "America/Toronto"), WorldCity("Chicago", "America/Chicago"),
    WorldCity("Denver", "America/Denver"), WorldCity("Los Angeles", "America/Los_Angeles"), WorldCity("San Francisco", "America/Los_Angeles"),
    WorldCity("Vancouver", "America/Vancouver"), WorldCity("Mexico City", "America/Mexico_City"), WorldCity("Sao Paulo", "America/Sao_Paulo"),
    WorldCity("Buenos Aires", "America/Argentina/Buenos_Aires"), WorldCity("Honolulu", "Pacific/Honolulu"),
)

/** One row of the world page. */
data class WorldRow(
    val name: String,
    /** "4:12 pm" */
    val time: String,
    /** "tue", for a city whose day isn't the home day, else "". */
    val dayNote: String,
    /** "−4h 30m" / "+3h 30m" from home, "" for home itself. */
    val offsetNote: String,
    val day: Boolean,
    /** How far through its own day the city is (0 at midnight, 0.5 at noon), for the day strip. */
    val dayFraction: Float = 0f,
)

/**
 * The world clock row for [city] at [nowMillis], compared with [homeZone].
 * "Day" is simply 6 am to 6 pm local, the usual sun-and-moon marker. Pure.
 */
fun worldRow(city: WorldCity, nowMillis: Long, homeZone: ZoneId): WorldRow {
    val zone = ZoneId.of(city.zoneId)
    val instant = Instant.ofEpochMilli(nowMillis)
    val local = ZonedDateTime.ofInstant(instant, zone)
    val home = ZonedDateTime.ofInstant(instant, homeZone)
    val diffMinutes = (local.offset.totalSeconds - home.offset.totalSeconds) / 60
    val hour12 = (local.hour % 12).let { if (it == 0) 12 else it }
    val time = "$hour12:%02d %s".format(local.minute, if (local.hour < 12) "am" else "pm")
    val dayNote = if (local.toLocalDate() == home.toLocalDate()) "" else local.dayOfWeek.name.take(3).lowercase()
    val offsetNote = if (diffMinutes == 0) "" else {
        val sign = if (diffMinutes > 0) "+" else "−"
        val a = kotlin.math.abs(diffMinutes)
        sign + (a / 60).toString() + "h" + if (a % 60 != 0) " ${a % 60}m" else ""
    }
    return WorldRow(city.name, time, dayNote, offsetNote, day = local.hour in 6..17, dayFraction = (local.toLocalTime().toSecondOfDay() / 86400f))
}

/** Cities matching what was typed (name contains it), at most [limit]. */
fun searchCities(query: String, limit: Int = 12): List<WorldCity> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return WORLD_CITIES.filter { it.name.lowercase().contains(q) }.take(limit)
}
