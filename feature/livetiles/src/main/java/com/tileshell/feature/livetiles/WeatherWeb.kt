package com.tileshell.feature.livetiles

import android.content.Context
import android.content.Intent
import android.net.Uri

/** A weather website the hub can open for the forecast's place. */
enum class WeatherSite(val label: String) {
    GOOGLE("google weather"),
    ACCUWEATHER("accuweather"),
    TIMEANDDATE("timeanddate"),
}

/**
 * The page of [site] for [place] ("Pune"), found by searching the site for the place name. A blank place (no forecast
 * yet) gives the site's own search with no place, which uses where the phone is. Pure.
 */
fun weatherWebUrl(site: WeatherSite, place: String): String {
    val name = place.trim()
    val q = encode(name)
    return when (site) {
        WeatherSite.GOOGLE -> "https://www.google.com/search?q=" + encode(("weather $name").trim())
        WeatherSite.ACCUWEATHER -> "https://www.accuweather.com/en/search-locations?query=$q"
        WeatherSite.TIMEANDDATE -> "https://www.timeanddate.com/weather/?query=$q"
    }
}

private fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

/** Opens [site]'s page for [place] in the phone's browser; does nothing if no browser handles it. */
fun openWeatherWeb(context: Context, site: WeatherSite, place: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(weatherWebUrl(site, place))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
